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

Create CIR Addition 是一个针对机械动力服务器的服务端附属，主要解决三件事：

1. **性能** —— 减少 Create 方块实体和方块实体的网络同步与 tick 开销
2. **规则** —— 限制 AE2 量子环的过度堆叠，逼玩家用物流网络而不是无脑并联
3. **运维** —— MSPT 预警、应急扫地、垃圾桶找回

它不是那种「装了就变快」的玄学优化包，每一项都对应一处具体的 Create 内部实现。

## 功能

### 机械动力优化（7 个 mixin）

| 目标 | 优化内容 |
|---|---|
| `SmartBlockEntity` | 同步包节流（1/3）、按玩家距离动态调整 lazyTick 频率 |
| `MountedStorageManager` | 提高同步冷却，减少挂载库存的重复发包 |
| `PackageEntity` | 减少包裹实体的追踪更新 |
| `BeltBlockEntity` | 传送带 tick 优化 |
| `ChainConveyorBlockEntity` | 链条传送带 tick 间隔调整 |
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

限制 ME 量子环（`ae2:quantum_ring` + `ae2:quantum_link`）的密集程度：

- **主世界**：半径 1024 格内最多 2 座、4096 格内最多 4 座（两级同时满足）
- **其他维度**：每个维度最多 4 座，不看距离
- **基地豁免**：主基地半径 256 格内不限制

超出限制的新环会在成型瞬间被拆除，原地留一块黑曜石，并召唤一道**纯视觉效果**的闪电。

计数基于一张持久化的「已登记环坐标表」，不依赖区块加载 —— 半径 4096 格等于 512×512 个区块，实时扫描根本不现实。

数量上限、基地豁免半径、以及是否启用本功能，都可以在 `config/create_cir_addition.toml` 里调整。

### 基地系统

首次进入新地图时自动把世界出生点设为基地。之后用 `/cir setHome` 更改。

**全世界只有一个基地**，只在主世界生效，同维度才有豁免。

### MSPT 预警

每 30 秒采样一次服务器 MSPT，四档：**40 / 45 / 50 / 70**（白 → 黄 → 金 → 红）。

**读到超线不会立刻报，要等 3 秒复检一次。**

- 第二次仍在**同一档位或更高** → 按第二次的读数和档位广播
- 第二次掉档 → 完全静默

这样一次磁盘 IO、一次 GC 不会惊动任何人，只有持续性负载才会报出来。

`≥ 70` 时每次确认都会广播，直到降下来。

> 40–50 这三档都还在「每 tick 有余量」的范围内（50 才是刚好用满 20 TPS），它们的作用是提前告知趋势。真正需要处理的是 70。

### 扫地与垃圾桶

**没有定时扫地。** 触发方式只有两种：

- MSPT ≥ 70（红色档确认后）自动清理
- 管理员执行 `/cir cleanup`

被清理的物品**不会消失**，而是进垃圾桶，**有效期 1 小时**。玩家用 `/cir trashbin` 打开界面取回。

带自定义名称的物品默认受保护（可在配置里关闭）。

## 前置

| 依赖 | 必需性 | 版本 |
|---|---|---|
| Minecraft | **必需** | 1.21.1 |
| NeoForge | **必需** | 21.1+ |
| Create | **必需** | 6.0.0 – 6.x |
| AE2 | 可选 | 19+（不装则量子环限制功能跳过） |

## 安装

1. 下载 `create_cir_addition-3.3.2.jar`
2. 放进 `mods/` 目录（客户端和服务端都要）
3. 启动一次，生成 `config/create_cir_addition.toml`
4. 按需调整配置，改完 `/reload` 或重启

## 命令

所有命令在 `/cir` 下。

| 命令 | 权限 | 说明 |
|---|---|---|
| `/cir setHome <x> <y> <z>` | 4 | 设置主基地（固定落在主世界） |
| `/cir home` | 4 | 查看当前基地坐标 |
| `/cir quantum scan` | 4 | 扫描当前维度已加载区域，把现有量子环补登记进表 |
| `/cir cleanup` | 4 | 立即清理地面掉落物 |
| `/cir trashbin` | 0 | 打开垃圾桶（**所有玩家可用**） |
| `/cir redstonelinkoptimization <true\|false>` | 4 | 热切换红石终端优化 |

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
> `/reload` 和配置热重载都会刻意忽略它。因为限制依赖一张持久化登记表，运行中途切换会让表和实际方块对不上：关掉时表还在（重新打开会把老记录当有效桥），打开时现有桥全部未登记（下次区块加载会被当新桥判一遍）。
>
> 要改必须重启。

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

模组的代码基础来自团队早期的一个内部优化项目，那个项目已经停止维护，所有可识别的名称已全部替换。

## AI 使用披露

该项目由 CIR 服务器成员于 2025 年立项进行，后续因成员变动本项目被搁置，2026 年 11 月通过 DeepSeek v4.1 Flash 1017 模型结合此前开发计划和已完成代码补全，因此部分 JAVA 代码由 AI 生成。（模组里面不含 KubeJS）

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

Create CIR Addition is a server-side add-on for Create-based modpacks. It addresses three things:

1. **Performance** — cuts redundant network sync and tick work in Create's block entities
2. **Rules** — caps AE2 quantum ring stacking so players rely on logistics instead of brute-forcing
3. **Operations** — MSPT warnings, emergency item cleanup, and a trash bin to reclaim swept items

It is not a "install and everything gets faster" mod. Every feature maps to a specific place in Create's internals.

## Features

### Create optimizations (7 mixins)

| Target | What it does |
|---|---|
| `SmartBlockEntity` | Throttles sync packets (1/3), scales `lazyTick` rate by player distance |
| `MountedStorageManager` | Longer sync cooldown, fewer repeated packets |
| `PackageEntity` | Reduced tracking updates for package entities |
| `BeltBlockEntity` | Belt tick optimization |
| `ChainConveyorBlockEntity` | Chain conveyor tick interval adjustment |
| `BasinBlockEntity` | Basin overflow check interval |
| `FunnelBlockEntity` | Funnel tick optimization |

All intervals are configurable; defaults are values observed in practice.

### Redstone link optimization

Caches `LinkBehaviour.isAlive()` results per game tick.

Create's `RedstoneLinkNetworkHandler.updateNetworkOf` walks the entire same-frequency network on every call, performing 4 state lookups per node. With many links — or frequent chunk load/unload — this becomes O(n²) work.

**Signal timing is unaffected.** `getTransmittedStrength()` is still read live; only the "is this node still alive" check is cached. Worst case: a link destroyed mid-tick is removed from the network one tick later.

Toggle at runtime with `/cir redstonelinkoptimization true|false`.

### Network compression

Create sync packets between client and server pick LZ4 or Zstd based on size. Small packets bypass compression entirely so they don't grow.

### Quantum ring limiter (optional)

> Requires AE2. Without it the whole feature is skipped; everything else keeps working.

Caps how densely ME quantum rings (`ae2:quantum_ring` + `ae2:quantum_link`) can be placed:

- **Overworld**: max 2 within 1024 blocks, max 4 within 4096 blocks (both must hold)
- **Other dimensions**: max 4 per dimension, distance-independent
- **Base exemption**: unlimited within 256 blocks of the main base

Rings that exceed the limit collapse the instant they form, leaving a single obsidian block and a **visual-only** lightning strike.

Counting is backed by a persistent registry of ring positions, not chunk scanning — a 4096-block radius covers 512×512 chunks, so live scanning is not viable.

The limits, the base exemption radius, and whether the feature is enabled at all are all configurable in `config/create_cir_addition.toml`.

### Base system

On first world load the world spawn becomes the base. Change it later with `/cir setHome`.

**There is exactly one base in the world**, it always lives in the Overworld, and only same-dimension rings benefit from the exemption.

### MSPT warnings

Samples server MSPT every 30 seconds, four tiers: **40 / 45 / 50 / 70** (white → yellow → gold → red).

**A reading above a threshold does not broadcast immediately.** The monitor waits 3 seconds and samples again:

- Second reading still in the **same tier or higher** → broadcast using the second reading's value and tier
- Tier dropped → complete silence

One disk flush or one GC pause will not bother anyone; only sustained load gets reported.

At `≥ 70` every confirmed reading is broadcast until the value drops.

> Tiers 40–50 all sit inside "still has headroom per tick" territory (50 is exactly 20 TPS). Their job is to show a trend early. 70 is the one that actually needs attention.

### Item cleanup and trash bin

**No timer-based cleanup.** Only two triggers:

- Automatic when MSPT reaches the red tier (≥ 70) and is confirmed
- Manual via `/cir cleanup`

Swept items are **not destroyed** — they go into a trash bin that stays valid for **1 hour**, reopened with `/cir trashbin`.

Items with a custom name are protected by default (configurable).

## Requirements

| Dependency | Required | Version |
|---|---|---|
| Minecraft | **Yes** | 1.21.1 |
| NeoForge | **Yes** | 21.1+ |
| Create | **Yes** | 6.0.0 – 6.x |
| AE2 | Optional | 19+ (quantum limiter is skipped without it) |

## Installation

1. Download `create_cir_addition-3.3.2.jar`
2. Drop it into `mods/` (client **and** server)
3. Launch once to generate `config/create_cir_addition.toml`
4. Adjust the config as needed, then `/reload` or restart

## Commands

All commands live under `/cir`.

| Command | Permission | Description |
|---|---|---|
| `/cir setHome <x> <y> <z>` | 4 | Set the main base (always lands in the Overworld) |
| `/cir home` | 4 | Show current base coordinates |
| `/cir quantum scan` | 4 | Scan loaded chunks and register existing rings |
| `/cir cleanup` | 4 | Sweep dropped items immediately |
| `/cir trashbin` | 0 | Open the trash bin (**available to everyone**) |
| `/cir redstonelinkoptimization <true\|false>` | 4 | Toggle the redstone link cache at runtime |

## Configuration

File: `config/create_cir_addition.toml`

### Module switches

```toml
network = true    # network compression
create  = true    # master switch for the Create mixins
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
quantumLimiterEnabled = true    # master switch — restart required
```

> **`quantumLimiterEnabled` is read exactly once at server startup.**
>
> `/reload` and config hot-reload deliberately ignore it. The limiter relies on a persistent registry of ring positions; toggling at runtime desynchronises the registry from the actual blocks:
>
> - Turned off: the registry stays, so turning it back on counts stale entries as live rings
> - Turned on: existing rings are unregistered, so the next chunk load re-evaluates them as new and may tear them down
>
> Changing this requires a restart.

### Redstone links

```toml
redstoneLinkOptimization = true    # togglable via /cir at runtime
```

### Item cleanup

```toml
cleanupIgnoreNamed = true     # protect custom-named items
cleanupOnHighMspt = true      # auto-sweep when MSPT reaches the red tier (≥ 70)
```

### Warnings

```toml
msptWarnEnabled = true
```

## Migration notes

When upgrading from an older version:

- **Existing quantum rings are not registered automatically.** After upgrading, run `/cir quantum scan` once so current rings enter the registry — otherwise they get re-evaluated as new bridges
- If a world already holds more rings than the current limits allow, **run scan before tightening the limits**

## Credits

The codebase grew out of an internal optimization project the team has since retired. All identifying names have been replaced.

## AI Usage Disclosure

This project was started by CIR server members in 2025 and later shelved due to membership changes. In November 2026, the previously planned design and the existing code were completed with the DeepSeek v4.1 Flash 1017 model, so part of the Java code is AI-generated. (The mod itself does not contain KubeJS.)

## License

This project is licensed under **[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/)** (Attribution-ShareAlike 4.0 International).

You are free to:

- **Share** — copy and redistribute the material in any medium or format
- **Adapt** — remix, transform, and build upon the material

Under the following terms:

- **Attribution** — You must give appropriate credit, provide a link to the license, and indicate if changes were made. You may do so in any reasonable manner, but not in any way that suggests the licensor endorses you or your use
- **ShareAlike** — If you remix, transform, or build upon the material, you must distribute your contributions under the same license as the original

See [LICENSE](LICENSE) for the full legal text.
