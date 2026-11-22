package com.cirserver.createciraddition.boss;

/** Pure final-health policy; native adapters supply damage after armor and absorption. */
public final class BossCombatRules {
    private BossCombatRules() {}
    public enum Kind { NORMAL, HALF_HEALTH, IMMUNE, OVERKILL, BURST }
    public record Resolution(Kind kind, float healthLoss) {}
    public static float capIncoming(float raw, float maxHp, BossGuardConfig.Global global) {
        return (float)Math.max(0, Math.min(raw, maxHp * global.singleHitCapRatio));
    }
    public static Resolution resolveFinal(float naturalLoss, float raw, float maxHp, float curHp,
            boolean player, boolean active, boolean ready, boolean halfDone,
            BossGuardConfig.Group group, BossGuardConfig.Global global) {
        float cappedNatural = capIncoming(naturalLoss, maxHp, global);
        boolean reflected = overkill(raw, maxHp, ready);
        float threshold = (float)(maxHp * global.lowHealthThresholdRatio);
        if (!halfDone && raw > 0 && curHp >= threshold
                && (curHp - cappedNatural <= threshold || raw > maxHp && reflectedHealth(curHp) <= threshold)) {
            return new Resolution(Kind.HALF_HEALTH, Math.max(0, curHp - threshold));
        }
        if (active) return new Resolution(Kind.IMMUNE, 0);
        if (reflected) return new Resolution(Kind.OVERKILL, Math.max(0, curHp - reflectedHealth(curHp)));
        if (burst(raw, player, ready, global)) return new Resolution(Kind.BURST, 0);
        float damage = cappedNatural;
        if (curHp < threshold && raw > group.lowHealthSkipThreshold) {
            float cap = (float)Math.max(curHp * group.lowHealthDamageRatio, group.lowHealthMinDamage);
            damage = capIncoming((float)Math.min(Math.max(naturalLoss, group.lowHealthMinDamage), cap), maxHp, global);
            if (damage >= curHp && raw < curHp) damage = Math.max(0, curHp - 1.0F);
        }
        return new Resolution(Kind.NORMAL, Math.max(0, damage));
    }
    public static double healAmount(float initialMaxHp, BossGuardConfig.Global global) {
        return initialMaxHp <= global.halfHealthHealThresholds.get(0) ? global.halfHealthHealSmall
            : initialMaxHp <= global.halfHealthHealThresholds.get(1) ? global.halfHealthHealMedium : global.halfHealthHealLarge;
    }
    public static boolean overkill(float raw, float maxHp, boolean ready) { return ready && raw > maxHp; }
    public static boolean burst(float raw, boolean player, boolean ready, BossGuardConfig.Global global) {
        return ready && player && raw > global.burstTriggerDamage;
    }
    public static float reflectedHealth(float current) { return Math.max(1.0F, current * 0.5F); }
    public static int nextBlockedCount(int current, int threshold) {
        return (int)Math.min((long)threshold, (long)Math.max(0, current) + 1L);
    }
}
