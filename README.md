# Create CIR Addition

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1%2B-orange)](https://neoforged.net/)
[![Create](https://img.shields.io/badge/Create-6.x-blue)](https://modrinth.com/mod/create)
[![License: CC BY-SA 4.0](https://img.shields.io/badge/License-CC%20BY--SA%204.0-lightgrey)](https://creativecommons.org/licenses/by-sa/4.0/)

面向 **机械动力（Create）服务器**的优化与网络管理附属。

An optimization and network management add-on built for **Create-based servers**.

**中文** ｜ [English](#english)

---

# 中文

## 简介

Create CIR Addition 是一个针对机械动力综合服务器的服务端附属，主要解决三件事：

1. **性能** —— 减少 Create 方块实体和方块实体的网络同步与 tick 开销
2. **规则** —— （可选）限制 AE2 量子环的过度堆叠，迫使玩家用物流网络而不是无脑并联；限制AE2本身的发电，迫使玩家使用CC&A的发电机基于应力发电
3. **运维** —— MSPT 预警、应急扫地、垃圾桶找回等

该模组每一项优化都对应一处具体的 Create 内部实现，每一个改动均为了方便服务器的构建和管理。绝大多数功能可以通过修改配置文件调节。

## 功能

### 机械动力机械运行优化

| 目标 | 优化内容 |
|---|---|
| `SmartBlockEntity` | 同步包节流（1/3）、按玩家距离动态调整 lazyTick 频率 |
| `MountedStorageManager` | 提高同步冷却，减少挂载库存的重复发包 |
| `PackageEntity` | 减少包裹实体的追踪更新 |
| `BeltBlockEntity` | 传送带 tick 优化 |
| `ChainConveyorBlockEntity` | 锁链运输 tick 间隔调整 |
| `BasinBlockEntity` | 工作盆溢出检查间隔 |
| `FunnelBlockEntity` | 漏斗 tick 优化 |

以上间隔全部可配置，默认值经过实测。

### 红石信号终端优化

缓存 `LinkBehaviour.isAlive()` 的查询结果（按游戏 tick 粒度）。

Create 的 `RedstoneLinkNetworkHandler.updateNetworkOf` 每次被调用都要遍历整个同频率网络，对每个节点做 4 次状态查询。终端一多、或者区块反复加载，就是 O(n²) 的开销。

**这个优化不会影响信号实时性** —— 信号值 `getTransmittedStrength()` 依然是实时读取，缓存的只是「这个节点还活不活着」。最坏情况是同一 tick 内被拆掉的终端晚 1 tick 才被移出网络。

可用 `/cir redstonelinkoptimization true|false` 热切换。

### 网络压缩

客户端与服务端之间的 Create 同步包会按大小自动选择 LZ4 或 Zstd 压缩，小包直接放行避免压缩反而变大。

### （可选）量子环限制

> 需要 AE2。没装的话整个功能跳过，其他功能不受影响。

限制 ME 量子环（`ae2:quantum_ring` + `ae2:quantum_link`）的密集程度以迫使玩家更多使用机械动力的物理系统：

- **主世界**：半径 1024 格内最多 2 座、4096 格内最多 4 座（两级同时满足）
- **其他维度**：每个维度最多 4 座，不看距离
- **基地豁免**：主基地半径 256 格内不限制

首次安装量子环，当多方块结构成型时会立刻收到提示，并提示附近剩余可建造数量

超出限制的新环会在成型瞬间被拆除，原地留一块黑曜石，并召唤一道**纯视觉效果**的闪电。

计数基于一张持久化的「已登记环坐标表」，不依赖区块加载 —— 半径 4096 格等于 512×512 个区块，实时扫描根本不现实。

基地位置可以通过指令 设置 (op level=4) 或 查询 (op level >=0) ，扫描指令 `/cir quantum scan`可用于扫描添加已加载的所有量子环。

扫描获得的量子环坐标将被保存在文件中而非缓存，因此游戏重启后仍会被调用。

数量上限、基地豁免半径、以及是否启用本功能，都可以在 `config/create_cir_addition.toml` 里调整。

P.S. 为防止玩家使用活塞，实体方块化等方式绕开方块放置检测创建量子环，本mod采用的方案为检测到量子环成型（未被加载的量子环被加载时也会被判定为多方块成型）后对照已被扫描的量子环清单决定该量子环是否触发提示或拆除。

### （可选）AE2 发电机调节

> 需要 AE2。没装的话指令会提示「未检测到 AE2」，其他功能不受影响。

AE2 自带两个能给 ME 网络发电的方块。本模组提供运行时调节，不用改 `ae2-common.toml`，也不用重启：

| 方块 | 中文名 | 调节方式 |
|---|---|---|
| `ae2:crystal_resonance_generator` | 水晶谐振发电机 | 开 / 关 |
| `ae2:vibration_chamber` | 谐振仓 | 三档：`low` / `normal` / `boost` |

**谐振仓三档**以「切档前的值」为基准整体缩放：

| 档位 | 燃料系数 | 下限 AE/t | 上限 AE/t |
|---|---|---|---|
| `low` | ×1/4 | ×1/4 | ×1/4 |
| `normal` | ×1 | ×1 | ×1 |
| `boost` | ×4 | ×4 | ×4 |

以 AE2 默认的 5 / 4 / 40 为例，三档分别是 **1.25 / 1 / 10**、**5 / 4 / 40**、**20 / 16 / 160**。

基准会在第一次切档时快照进 `create_cir_addition.toml`（`ae2Vibration*Snapshot` 相关项）。所以如果手动把 `ae2-common.toml` 调成 10 / 8 / 80，`normal` 恢复的是对应文件的值，不会被恢复为 AE2 默认设置。

**切完立即生效，包括已放置的机器。** AE2 把这三项缓存在方块实体的 `final` 字段里，构造之后再也不看配置；本mod会逐个顶掉该缓存，聊天栏会提示当前同步数量。

**谐振仓参数无法设置为零。** AE2 自身下限是 `baseEnergyPerFuelTick ≥ 0.1`、`maxEnergyPerGameTick ≥ 1`，`low` 只是压到 1/4，不是关闭。需要完全禁用只能靠删配方或拦截交互。

> 数值写于 AE2 的 `ae2-common.toml` ，所以这条调节是**全局**的 —— 一台机器切档，全服所有谐振仓都会更新配置。

### 基地系统

首次进入新地图时，世界出生点被自动设为基地。可用 `/cir setHome` 更改（需要管理员权限）。

**全世界只有一个基地**，只在主世界生效，同维度才有豁免。

### MSPT 预警

每 30 秒采样一次服务器 MSPT，四档：**40 / 45 / 50 / 70**（颜色：白 → 黄 → 金 → 红）。

**读到超线不会立刻报，要等 3 秒复检一次。**

- 第二次仍在**同一档位或更高** → 根据第二次的读数和档位触发全局播报
- 第二次数值下降 → 保持完全静默

重复确认为避免自动保存或偶然的卡顿误触报警的操作，只有持续性负载才会报出来。

`≥ 70` 时每次确认都会触发广播，直到mspt不再高于该值。

> 前三档处于「每 tick 有余量」的范围内（50 MSPT刚好占满 20 TPS），其作用为提前告知趋势。70MSPT已经可以导致明显可见的卡顿。

### 扫地功能与垃圾桶

**没有定时扫地。** 触发方式只有两种：

- MSPT ≥ 70（红色档位确认后）自动触发清理
- 管理员执行扫地命令 `/cir cleanup`

被清理的物品**不会消失**，而是进垃圾桶，**有效期 1 小时**。玩家用 `/cir trashbin` 或点击聊天栏的垃圾桶文字提示打开界面取回。

带自定义名称的物品默认受保护（可在配置里关闭）。

## 前置

| 依赖 | 必需性 | 版本 |
|---|---|---|
| Minecraft | **必需** | 1.21.1 |
| NeoForge | **必需** | 21.1+ |
| Create | **必需** | 6.0.0 – 6.x |
| AE2 | 可选 | 19+（不装则量子环限制和发电机调节都跳过） |

## 安装

1. 下载 `create_cir_addition-3.4.2.jar`
2. 放进 `mods/` 目录（若为服务器，客户端和服务端都要放！）
3. 启动一次，生成 `config/create_cir_addition.toml`
4. 按需调整配置，改完 `/reload` 或重启

## 命令

所有命令在 `/cir` 下。

| 命令 | 权限 | 说明 |
|---|---|---|
| `/cir setHome <x> <y> <z>` | 4 | 设置主基地（固定落在主世界） |
| `/cir home` | 0 | 查看当前基地坐标 |
| `/cir quantum scan` | 4 | 扫描当前维度已加载区域，把现有量子环补登记进表 |
| `/cir cleanup` | 4 | 立即清理地面掉落物 |
| `/cir trashbin` | 0 | 打开垃圾桶（**所有玩家可用**） |
| `/cir redstonelinkoptimization <true\|false>` | 4 | 热切换红石终端优化 |
| `/cir ae2generator` | 4 | 查看两个 AE2 发电方块的当前状态 |
| `/cir ae2generator crystalresonance <true\|false>` | 4 | 开关水晶谐振发电机 |
| `/cir ae2generator vibrationchamber <low\|normal\|boost>` | 4 | 切换谐振仓档位（带 Tab 补全） |

## 配置

配置文件：`config/create_cir_addition.toml`

### 模块开关

```toml
network = true    # 网络压缩
create  = true    # Create 优化 mixin 总开关
```

### 性能

```toml
smartBlockInterval = 3
chainConveyorInterval = 2
basinCheckInterval = 5
mountedStorageSyncCooldown = 20
```

### 量子环

```toml
quantumLimitNear = 2
quantumRadiusNear = 1024
quantumLimitFar = 4
quantumRadiusFar = 4096
otherDimensionLimit = 4
homeRadius = 256
quantumLimiterEnabled = true    # 总开关，改这个必须重启
```

> **`quantumLimiterEnabled` 只在服务器启动时读取一次。**
>
> `/reload` 和配置热重载都会刻意忽略它。因为限制依赖一张持久化登记表，运行中途切换会让表内内容和游戏对不上：关掉时表还在（重新打开会把老记录当有效量子环），打开时现有量子环全部未登记（下次区块加载会被当新桥判一遍）。
>
> 要改必须重启游戏。

### 红石终端

```toml
redstoneLinkOptimization = true    # 支持 /cir 指令热切换
```

### 扫地

```toml
cleanupIgnoreNamed = true     # 保护带自定义名称的物品
cleanupOnHighMspt = true      # MSPT 到红色档（≥ 70）时自动清理
```

### 预警

```toml
msptWarnEnabled = true
```

## 迁移提示

从旧版本升级时：

- **老的量子环不会自动登记**。升级后进游戏跑一次 `/cir quantum scan`，把现有环补进表里，否则它们会被当成新桥重新判定一遍
- 如果你的存档里有大量量子环且已超出当前限制，**建议先跑 scan 再改限制值**

## 致谢

- **KawaiiMIra114** —— 本项目的前身 *Duolingo Super* 的作者。早期的 Create 优化
  mixin 与网络压缩层源自他的工作，CIR 服务器在此基础上继续开发。原作者已停止维护并退出项目，原始仓库现已不可访问。
- CIR 服务器的早期成员 —— 2025 年的立项与初期规划。

## AI 使用披露

该项目由 CIR 服务器成员于 2025 年立项进行，后续因成员变动本项目被搁置，2026 年 11 月通过 DeepSeek v4.1 Flash 1017 模型结合此前开发计划和已完成代码补全，因此部分 JAVA 代码由 AI 生成。

## 许可

本项目采用 **[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/)**（署名—相同方式共享 4.0 国际）。

你可以自由地：

- **共享** —— 以任何媒介或格式复制、发行本作品
- **演绎** —— 修改、转换或以本作品为基础进行创作

但必须遵守以下条款：

- **署名** —— 必须给出适当的署名，提供指向本许可协议的链接，并说明是否作了修改。可以以任何合理的方式署名，但不得暗示作者为你或你的使用背书
- **相同方式共享** —— 如果你再混合、转换或以本作品为基础进行创作，必须基于与原作品相同的许可协议分发你的作品

完整法律条款见 [LICENSE](LICENSE)。

---

# English

## Overview

Create CIR Addition is a server-side add-on for Create-focused modpacks. It covers three areas:

1. **Performance** — reduces the network sync and tick overhead of Create's block entities
2. **Rules** — (optional) caps how densely AE2 quantum rings can be stacked, pushing players toward a proper logistics network instead of brute-force parallel bridges; and throttles AE2's own power generation so players fall back on CC&A stress-based generation
3. **Operations** — MSPT warnings, emergency item cleanup, trash bin recovery

Each optimization maps to a specific piece of Create's internals, and every change exists to make a server easier to build and run. Nearly all of it is tunable through the config file.

## Features

### Create machinery tick optimization

| Target | What it does |
|---|---|
| `SmartBlockEntity` | Sync packet throttling (1/3), `lazyTick` rate scaled by player distance |
| `MountedStorageManager` | Longer sync cooldown, fewer repeated packets for mounted storage |
| `PackageEntity` | Fewer tracking updates for package entities |
| `BeltBlockEntity` | Belt tick optimization |
| `ChainConveyorBlockEntity` | Chain conveyor tick interval |
| `BasinBlockEntity` | Basin overflow check interval |
| `FunnelBlockEntity` | Funnel tick optimization |

All intervals are configurable; the defaults are values measured in practice.

### Redstone link optimization

Caches `LinkBehaviour.isAlive()` results per game tick.

Create's `RedstoneLinkNetworkHandler.updateNetworkOf` walks the entire same-frequency network on every call, performing four state lookups per node. With many links, or with chunks loading and unloading repeatedly, this becomes O(n²) work.

**Signal timing is unaffected** — `getTransmittedStrength()` is still read live; only the "is this node still alive" check is cached. Worst case, a link destroyed mid-tick leaves the network one tick later.

Toggle at runtime with `/cir redstonelinkoptimization true|false`.

### Network compression

Create sync packets between client and server pick LZ4 or Zstd by size. Small packets pass through untouched, so compression cannot make them larger.

### Quantum ring limiter (optional)

> Requires AE2. Without it the feature is skipped entirely; nothing else is affected.

Caps how densely ME quantum rings (`ae2:quantum_ring` + `ae2:quantum_link`) can be placed, so players lean on Create's physical systems instead:

- **Overworld**: at most 2 within 1024 blocks and at most 4 within 4096 blocks (both conditions must hold)
- **Other dimensions**: at most 4 per dimension, distance-independent
- **Base exemption**: unlimited within 256 blocks of the main base

The first time a ring multiblock completes, the player is notified on the spot, along with how many placements remain nearby.

A ring that breaks the limit is dismantled the instant it completes, leaving a single obsidian block and a **visual-only** lightning strike.

Counting runs off a persistent registry of ring positions rather than chunk scanning — a 4096-block radius covers 512×512 chunks, so live scanning is simply not viable.

The base can be set with a command (op level 4) and queried by anyone (op level ≥ 0). `/cir quantum scan` can be used to register every loaded ring into the table.

Scanned positions are written to disk rather than kept in memory, so they survive a restart.

Limits, the base exemption radius, and whether the feature is enabled at all are configurable in `config/create_cir_addition.toml`.

> **P.S.** To keep players from sidestepping placement detection with pistons, block-entity conversion or similar tricks, the mod does not hook block placement at all. It hooks the moment a ring multiblock *completes* — which also covers a ring that was loaded from an unloaded chunk — and then compares it against the scanned registry to decide whether it gets the notification or the axe.

### AE2 power generator control (optional)

> Requires AE2. Without it the command reports "AE2 not found"; nothing else is affected.

AE2 ships two blocks that generate power for an ME network. This mod exposes both at runtime, with no need to edit `ae2-common.toml` and no restart:

| Block | Name | Mode |
|---|---|---|
| `ae2:crystal_resonance_generator` | Crystal Resonance Generator | on / off |
| `ae2:vibration_chamber` | Vibration Chamber | three tiers: `low` / `normal` / `boost` |

**Vibration chamber tiers** scale the three underlying values against whatever was in effect before the first switch:

| Tier | Fuel factor | Min AE/t | Max AE/t |
|---|---|---|---|
| `low` | ×1/4 | ×1/4 | ×1/4 |
| `normal` | ×1 | ×1 | ×1 |
| `boost` | ×4 | ×4 | ×4 |

With AE2's defaults of 5 / 4 / 40, the tiers come out as **1.25 / 1 / 10**, **5 / 4 / 40** and **20 / 16 / 160**.

The baseline is snapshotted into `create_cir_addition.toml` (the `ae2Vibration*Snapshot` entries) the first time a tier is applied. If you have hand-tuned `ae2-common.toml` to 10 / 8 / 80, `normal` restores those values rather than AE2's factory defaults.

**Changes take effect immediately, including on machines already placed.** AE2 caches these three values in `final` fields on the block entity and never re-reads the config after construction; this mod overwrites that cache and reports in chat how many chambers it refreshed.

**The vibration chamber cannot be driven to zero.** AE2's own floors are `baseEnergyPerFuelTick ≥ 0.1` and `maxEnergyPerGameTick ≥ 1`, so `low` is a 1/4 throttle, not a disable. Shutting it down entirely still requires removing the recipe or blocking interaction.

> These values live in AE2's `ae2-common.toml`, so the adjustment is **global** — switching one tier updates every vibration chamber on the server.

### Base system

On first world load the spawn point becomes the base. Change it with `/cir setHome` (admin permission required).

**There is exactly one base in the world.** It lives in the Overworld, and only rings in the same dimension benefit from the exemption.

### MSPT warnings

Samples server MSPT every 30 seconds, four tiers: **40 / 45 / 50 / 70** (white → yellow → gold → red).

**A reading above the line does not broadcast immediately** — it waits 3 seconds and samples again:

- Second reading still in the **same tier or higher** → broadcast using the second reading's value and tier
- Second reading fell back → complete silence

The repeat sample exists so that an autosave or an incidental hitch does not trip the alarm; only sustained load gets reported.

At `≥ 70` every confirmed reading broadcasts until MSPT drops back below it.

> The first three tiers all sit inside "still has headroom per tick" territory (50 MSPT is exactly 20 TPS); their role is to surface a trend early. At 70 MSPT stutter is already plainly visible.

### Item cleanup and trash bin

**No timer-based cleanup.** Two triggers only:

- Automatic, once MSPT reaches the red tier (≥ 70) and is confirmed
- Manual, via `/cir cleanup`

Swept items are **not destroyed** — they go into a trash bin valid for **1 hour**. Players reclaim them with `/cir trashbin`, or by clicking the trash-bin link in chat.

Items carrying a custom name are protected by default (configurable).

## Requirements

| Dependency | Required | Version |
|---|---|---|
| Minecraft | **Yes** | 1.21.1 |
| NeoForge | **Yes** | 21.1+ |
| Create | **Yes** | 6.0.0 – 6.x |
| AE2 | Optional | 19+ (quantum limiter and generator control are skipped without it) |

## Installation

1. Download `create_cir_addition-3.4.2.jar`
2. Drop it into `mods/` — **on a server, both the client and the server need it**
3. Launch once to generate `config/create_cir_addition.toml`
4. Adjust the config as needed, then `/reload` or restart

## Commands

All commands live under `/cir`.

| Command | Permission | Description |
|---|---|---|
| `/cir setHome <x> <y> <z>` | 4 | Set the main base (always lands in the Overworld) |
| `/cir home` | 0 | Show current base coordinates |
| `/cir quantum scan` | 4 | Scan loaded chunks of this dimension and register existing rings |
| `/cir cleanup` | 4 | Sweep dropped items immediately |
| `/cir trashbin` | 0 | Open the trash bin (**available to everyone**) |
| `/cir redstonelinkoptimization <true\|false>` | 4 | Toggle the redstone link cache at runtime |
| `/cir ae2generator` | 4 | Show the current state of both AE2 generators |
| `/cir ae2generator crystalresonance <true\|false>` | 4 | Enable or disable the crystal resonance generator |
| `/cir ae2generator vibrationchamber <low\|normal\|boost>` | 4 | Switch the vibration chamber tier (tab-completed) |

## Configuration

File: `config/create_cir_addition.toml`

### Module switches

```toml
network = true    # network compression
create  = true    # master switch for the Create optimization mixins
```

### Performance

```toml
smartBlockInterval = 3
chainConveyorInterval = 2
basinCheckInterval = 5
mountedStorageSyncCooldown = 20
```

### Quantum rings

```toml
quantumLimitNear = 2
quantumRadiusNear = 1024
quantumLimitFar = 4
quantumRadiusFar = 4096
otherDimensionLimit = 4
homeRadius = 256
quantumLimiterEnabled = true    # master switch, restart required
```

> **`quantumLimiterEnabled` is read once at startup.**
>
> `/reload` and config hot-reload deliberately ignore it. The limiter depends on a persistent registry, and flipping the switch mid-run leaves the table out of sync with the world: turn it off and the table stays behind (switching back on would treat stale entries as live rings); turn it on and every existing ring is unregistered (the next chunk load judges them as new).
>
> Changing it requires a restart.

### Redstone links

```toml
redstoneLinkOptimization = true    # the /cir command can toggle this at runtime
```

### Item cleanup

```toml
cleanupIgnoreNamed = true     # protect items with a custom name
cleanupOnHighMspt = true      # auto-clean once MSPT hits the red tier (≥ 70)
```

### Warnings

```toml
msptWarnEnabled = true
```

## Migration notes

Upgrading from an older version:

- **Existing rings are not registered automatically.** After upgrading, run `/cir quantum scan` once in-game to pull them into the table; otherwise they are judged as new rings
- If the save already contains more rings than the current limits allow, **run scan before tightening the limits**

## Credits

- **KawaiiMIra114** — author of *Duolingo Super*, from which this project descends. The early Create optimization mixins and the network compression layer originated in their work; the CIR server continued development from there. The original author has stopped maintaining the project and the upstream repository is no longer available.
- Early CIR server members — the 2025 kickoff and initial planning.

## AI Usage Disclosure

This project was started by CIR server members in 2025 and later shelved due to membership changes. In November 2026, the previously planned design and the existing code were completed with the DeepSeek v4.1 Flash 1017 model, so part of the Java code is AI-generated.

## License

This project is licensed under **[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/)** (Attribution-ShareAlike 4.0 International).

You are free to:

- **Share** — copy and redistribute the material in any medium or format
- **Adapt** — remix, transform, and build upon the material

Under the following terms:

- **Attribution** — you must give appropriate credit, link to the license, and indicate whether changes were made. You may do so in any reasonable manner, but not in any way that suggests the licensor endorses you or your use
- **ShareAlike** — if you remix, transform, or build upon the material, you must distribute your contributions under the same license as the original

Full legal text in [LICENSE](LICENSE).
