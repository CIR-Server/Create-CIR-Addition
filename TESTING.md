# Compatibility regression checks

`tools/compatibility-regression/src/CompatibilityRegression.java` is a standalone Java 21 static regression harness. It reads class files with ASM and does not launch Minecraft or execute third-party mod classes. Both standalone harnesses live outside Gradle's JUnit source set because they require explicitly supplied mod JARs and are invoked manually; a successful Gradle build does not mean these harnesses were run.

Compile it with the project's runtime dependencies (ASM core/tree/analysis/util included) and compiled main classes on the classpath. Run:

```text
java -cp "<dependencies;main classes;test classes>" CompatibilityRegression <throughput-quest.jar> <ae2.jar> <neoforge-universal.jar> <patched-class-output>
```

Tested inputs for 3.4.8: ThroughputQuest 1.21.1-1.0.0, AE2 19.2.20, NeoForge 21.1.267. Checks cover both server/client accessor sites, unchanged surrounding instructions, idempotence, ASM stack/structure verification, the equivalent public AE2 APIs, missing optional mods, exact Create version gating, and the CIR protocol allowlist. All 2,301 assertions passed.

Before publishing, perform the runtime checks in CONTRIBUTING.md, including:

- Start a client and dedicated server; inspect mixin and payload registration logs.
- Enter a test world, configure a ThroughputQuest monitor, watch its item count, and confirm its client renderer works.
- With Create 6.0.10, remove a basin while its press is running and confirm there is no empty-stack disconnect.
- Confirm a 3.4.8 server accepts protocol 3.4.7 and 3.4.8 clients and rejects earlier protocols. A 3.4.8 client is not expected to connect to an unmodified 3.4.7 server.
- Check a setup without ThroughputQuest/AE2 and a Create version other than 6.0.10: optional patches must be skipped in the log.

Static checks cannot establish that runtime mixin application or a live connection succeeds. The 3.4.8 local build has not yet completed these runtime checks.

## RollingGate startup regression

Four live thread snapshots on 2026-11-20 showed RollingGate 1.1.0+build.66's configuration-loading worker stuck in `GLFW.glfwSetWindowAttrib`, while the render thread waited for that worker through `ModLoader.waitForFuture`. GLFW permits this window call only on the main thread.

`tools/rollinggate-regression/src` is an isolated test harness using fake Minecraft/GLFW endpoints. Compile it separately, then put its output directory first on the classpath, followed by compiled CIR classes and ASM dependencies. Run `RollingGateRegression <rolling-gate.jar>`. Never package these test doubles in the mod JAR.

All 14 assertions passed: the loading worker returns without waiting, no off-thread native call occurs, queued updates preserve handles/values/order, main-thread updates work, other/missing versions skip the patch, and both real target calls match the redirect descriptor. The original 2,301 regression assertions also passed again.

Runtime acceptance: start the previously hanging client, confirm the version-gated patch enables and the loading screen reaches the main menu, then enter a test world and verify ThroughputQuest. Recheck logs for mixin errors. A dedicated server must not load `MixinRollingGateWindow` or its client helper.

## Observed client runtime result (2026-11-20)

The repaired client loaded MixinRollingGateWindow at 20:54:56, registered CIR payload handlers at 20:55:24, started an integrated server at 20:55:52, and applied MixinNetworkComponentNegotiator at 20:55:59. The player entered the world successfully. The user then confirmed that placing/configuring the ThroughputQuest monitor produced correct counts and rendering. Startup, world entry, protocol 3.4.8 local connection and the reported ThroughputQuest reproduction now pass.

Dedicated-server startup, live protocol 3.4.7 client compatibility, and the full optional-mod/version runtime matrix have not been performed. No remote deployment has occurred. GitHub release v3.4.8 was subsequently published at the user's request with these validation limits documented in its release notes.
