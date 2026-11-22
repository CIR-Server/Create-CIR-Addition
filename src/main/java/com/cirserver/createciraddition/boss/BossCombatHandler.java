package com.cirserver.createciraddition.boss;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Server-thread-only protection. No Apotheosis types appear in common bytecode. */
public final class BossCombatHandler {
    private static final ResourceLocation ATTACK_BONUS = ResourceLocation.fromNamespaceAndPath("create_cir_addition", "boss_berserk_attack");
    private static final String HALF_USED = "cir_boss_guard_half_used";
    private static final String INITIAL_MAX = "cir_boss_guard_initial_max";
    static final String EXECUTION_COUNTERS = "cir_boss_guard_execution_attempts";
    private static final Map<LivingEntity, BerserkState> STATES = new WeakHashMap<>();
    private static final Map<DamageContainer, Float> RAW_HITS = new WeakHashMap<>();
    private static final class PendingHit {
        final float raw;
        final float absorptionBefore;
        final DamageSource source;
        boolean resolved;
        boolean halfHealth;
        PendingHit(float raw, float absorptionBefore, DamageSource source) {
            this.raw = raw; this.absorptionBefore = absorptionBefore; this.source = source;
        }
    }
    private static final Map<LivingEntity, Deque<PendingHit>> PENDING_HITS = new WeakHashMap<>();
    private static boolean registered;

    public static void register() {
        if (!BossGuardConfig.enabled() || registered) return;
        registered = true;
        BossGuardConfig.reload();
        NeoForge.EVENT_BUS.register(new BossCombatHandler());
    }

    public static boolean isProtected(LivingEntity entity) {
        return !entity.level().isClientSide() && BossGuardConfig.enabled()
            && BossGuardConfig.current().bosses().containsKey(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    private static boolean adminKill(DamageSource source) { return source.is(DamageTypes.GENERIC_KILL); }

    private static BerserkState state(LivingEntity entity) {
        return STATES.computeIfAbsent(entity, ignored -> {
            float initial = entity.getPersistentData().getFloat(INITIAL_MAX);
            if (!Float.isFinite(initial) || initial <= 0) initial = entity.getMaxHealth();
            entity.getPersistentData().putFloat(INITIAL_MAX, initial);
            return new BerserkState(initial);
        });
    }

    @SubscribeEvent
    public void join(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity entity && isProtected(entity)) state(entity);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void incoming(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (!isProtected(entity)) return;
        if (adminKill(event.getSource())) { clear(entity); return; }
        BerserkState state = state(entity);
        long now = System.nanoTime();
        var global = BossGuardConfig.current().global();
        boolean pendingPhase = !entity.getPersistentData().getBoolean(HALF_USED)
            && entity.getHealth() >= entity.getMaxHealth() * global.lowHealthThresholdRatio;
        if (state.active(now) && !pendingPhase) { event.setCanceled(true); return; }
        if (!state.active(now)) removeBonus(entity);
        float raw = event.getAmount();
        if (Float.isNaN(raw) || raw <= 0) return;
        RAW_HITS.put(event.getContainer(), raw);
        // Keep raw input intact for vanilla reductions; A/B are enforced at the final hook.
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void beforeDamage(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        Float incoming = RAW_HITS.remove(event.getContainer());
        if (!isProtected(entity) || adminKill(event.getSource())) return;
        float damage = event.getNewDamage();
        if (Float.isNaN(damage)) { event.setNewDamage(0); return; }
        float raw = incoming == null ? event.getOriginalDamage() : Math.max(incoming, damage);
        PENDING_HITS.computeIfAbsent(entity, ignored -> new ArrayDeque<>())
            .push(new PendingHit(raw, entity.getAbsorptionAmount(), event.getSource()));
        // Armor is done, but absorption is not. Do not prematurely cap the input here.
    }

    /** Gated native hook: armor, resistance and absorption have already been applied. */
    public static float finalDamage(LivingEntity entity, DamageSource source, DamageContainer container, float natural) {
        if (!isProtected(entity) || adminKill(source)) return natural;
        PendingHit hit = pending(entity, source);
        if (hit == null || hit.resolved) return natural;
        hit.resolved = true;
        BerserkState state = state(entity);
        long now = System.nanoTime();
        var snapshot = BossGuardConfig.current();
        var group = snapshot.bosses().get(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
        var resolution = BossCombatRules.resolveFinal(natural, hit.raw, entity.getMaxHealth(), entity.getHealth(),
            source.getEntity() instanceof ServerPlayer, state.active(now), state.ready(now),
            entity.getPersistentData().getBoolean(HALF_USED), group, snapshot.global());
        if (resolution.kind() == BossCombatRules.Kind.HALF_HEALTH) {
            entity.getPersistentData().putBoolean(HALF_USED, true);
            hit.halfHealth = true;
            enter(entity, source, state, true, now);
        } else if (resolution.kind() == BossCombatRules.Kind.OVERKILL || resolution.kind() == BossCombatRules.Kind.BURST) {
            entity.setAbsorptionAmount(hit.absorptionBefore);
            if (resolution.kind() == BossCombatRules.Kind.OVERKILL && entity.getHealth() < 1.0F) entity.setHealth(1.0F);
            enter(entity, source, state, false, now);
        } else if (resolution.kind() == BossCombatRules.Kind.IMMUNE) {
            entity.setAbsorptionAmount(hit.absorptionBefore);
        }
        container.setNewDamage(resolution.healthLoss());
        return resolution.healthLoss();
    }

    private static PendingHit pending(LivingEntity entity, DamageSource source) {
        Deque<PendingHit> hits = PENDING_HITS.get(entity);
        PendingHit hit = hits == null ? null : hits.peek();
        return hit != null && hit.source == source ? hit : null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void afterDamage(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        PendingHit hit = pending(entity, event.getSource());
        if (hit == null) return;
        Deque<PendingHit> hits = PENDING_HITS.get(entity);
        hits.pop();
        if (hits.isEmpty()) PENDING_HITS.remove(entity);
        if (!hit.halfHealth || !isProtected(entity) || adminKill(event.getSource())) return;
        var global = BossGuardConfig.current().global();
        float before = entity.getHealth();
        double heal = BossCombatRules.healAmount(state(entity).initialMaxHealth, global);
        entity.setHealth((float)Math.min(entity.getMaxHealth(), before + heal));
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("event.cir.berserk.half", seconds(global.berserkDurationMs),
                String.format(java.util.Locale.ROOT, "%.1f", entity.getHealth() - before)).withStyle(ChatFormatting.RED), true);
        }
    }

    private static String seconds(long millis) {
        return millis % 1000 == 0 ? Long.toString(millis / 1000) : Double.toString(millis / 1000.0);
    }

    private static void enter(LivingEntity entity, DamageSource source, BerserkState state, boolean half, long now) {
        var config = BossGuardConfig.current().global();
        state.startedAt = now;
        state.hasTriggered = true;
        state.berserkUntil = now + TimeUnit.MILLISECONDS.toNanos(config.berserkDurationMs);
        state.readyAt = state.berserkUntil + TimeUnit.MILLISECONDS.toNanos(config.berserkCooldownMs);
        state.nextRingAt = now;
        state.halfHealth = half;
        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.removeModifier(ATTACK_BONUS);
            attack.addTransientModifier(new AttributeModifier(ATTACK_BONUS, config.berserkAttackBonus, AttributeModifier.Operation.ADD_VALUE));
        }
        if (!half && source.getEntity() instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("event.cir.berserk", seconds(config.berserkDurationMs)).withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide()) return;
        BerserkState state = STATES.get(entity);
        if (state == null) return;
        if (!isProtected(entity) || !entity.isAlive()) { clear(entity); return; }
        long now = System.nanoTime();
        if (!state.active(now)) { removeBonus(entity); return; }
        if (now < state.nextRingAt || !(entity.level() instanceof ServerLevel level)) return;
        ParticleOptions particle = state.halfHealth ? ParticleTypes.FLAME : ParticleTypes.HAPPY_VILLAGER;
        double radius = BossGuardConfig.current().global().particleRadius;
        double y = entity.getY() + entity.getBbHeight() / 2.0;
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI * 2 / 24;
            level.sendParticles(particle, entity.getX() + Math.cos(angle)*radius, y, entity.getZ() + Math.sin(angle)*radius, 1, 0, 0, 0, 0);
        }
        long intervalMs = now - state.startedAt < TimeUnit.SECONDS.toNanos(1) ? 100 : 2000;
        state.nextRingAt = now + TimeUnit.MILLISECONDS.toNanos(intervalMs);
    }

    private static void removeBonus(LivingEntity entity) {
        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null && attack.hasModifier(ATTACK_BONUS)) attack.removeModifier(ATTACK_BONUS);
    }
    private static void clear(LivingEntity entity) {
        removeBonus(entity);
        STATES.remove(entity);
        PENDING_HITS.remove(entity);
    }
    @SubscribeEvent
    public void leave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity entity) clear(entity);
    }
    @SubscribeEvent
    public void stopping(ServerStoppingEvent event) { clearAll(); }
    @SubscribeEvent
    public void clonePlayer(PlayerEvent.Clone event) {
        if (event.getOriginal().getPersistentData().contains(EXECUTION_COUNTERS)) {
            event.getEntity().getPersistentData().put(EXECUTION_COUNTERS,
                event.getOriginal().getPersistentData().getCompound(EXECUTION_COUNTERS).copy());
        }
    }
    private static void clearAll() {
        for (LivingEntity entity : STATES.keySet()) removeBonus(entity);
        STATES.clear(); RAW_HITS.clear(); PENDING_HITS.clear();
    }
    @SubscribeEvent
    public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cir").then(Commands.literal("bossguard")
            .requires(source -> source.hasPermission(2)).then(Commands.literal("reload").executes(context -> {
                if (!BossGuardConfig.reload()) {
                    context.getSource().sendFailure(Component.translatable("command.cir.boss_guard.reload_failed"));
                    return 0;
                }
                clearAll();
                context.getSource().sendSuccess(() -> Component.translatable("command.cir.boss_guard.reloaded", BossGuardConfig.current().bosses().size()), true);
                return 1;
            }))));
    }
}
