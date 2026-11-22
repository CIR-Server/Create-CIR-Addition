# Boss Guard offline regression

`src/com/cirserver/createciraddition/boss/BossGuardRegression.java` is a standalone
JVM regression suite. It does not start Minecraft. Compile against built project
classes, Gson, ASM and the normal compile dependencies; pass these arguments:

1. Built `create_cir_addition-3.5.0.jar`.
2. Apotheosis 8.9.0 JAR (or the unchanged extracted ExecutingAffix class archive).
3. NeoForge 21.1.267 patched client JAR, for damage-hook bytecode inspection.
4. Output path for the validated default Boss Guard JSON.

It checks rule boundaries, killability at low HP, per-group floors, once-only
half-health behavior, cooldown timing, invalid numeric values, all strict protocol
pairs, ASM stack validity, optional linkage, and the actual Apotheosis injection
position before totem use/death sound/health mutation.

The optional API dependencies for a Gradle build may be supplied with
`-Pcir_optional_mods_dir=<local mods directory>`. The release handover records the
actual manual javac build and its logs; a Gradle build was not run for 3.5.0.

This suite does not prove in-game particles, boss-specific damage overrides,
multiplayer delivery, or affix-component behavior under other patch mods. Use the
manual acceptance checklist in the release handover for those checks.

The unreleased 3.5.0 repair additionally checks inclusive half-health boundaries,
mandatory priority during green immunity/cooldown, overkill overlap, final floors
through a real DamageContainer, and the native final-damage slice on NeoForge
21.1.255, 21.1.265 and 21.1.267. Health anchors precede the separately applied heal.
