# Compatibility regression checks

`src/test/java/CompatibilityRegression.java` is a standalone Java 21 static regression harness. It reads class files with ASM and does not launch Minecraft or execute third-party mod classes.

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
