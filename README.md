# Create CIR Addition

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1%2B-orange)](https://neoforged.net/)
[![Create](https://img.shields.io/badge/Create-6.x-blue)](https://modrinth.com/mod/create)
[![License: CC BY-SA 4.0](https://img.shields.io/badge/License-CC%20BY--SA%204.0-lightgrey)](https://creativecommons.org/licenses/by-sa/4.0/)

面向机械动力整合包和服务器，提供性能优化、兼容修复、管理工具与可配置的模组联动。

Performance optimizations, compatibility fixes, administration tools and configurable integrations for Create-based modpacks and servers.

点击查看中文介绍视频：https://www.bilibili.com/video/BV1vHy2zSEVa

Tap to watch introduction video in English: https://youtu.be/kJ3f6Zn2YAQ

[简体中文](#中文) | [English](#english)

支持简体中文、繁体中文与英文 / Available in Simplified Chinese, Traditional Chinese and English.

---

# 中文

Create CIR Addition 最初为 CIR 服务器开发。功能分为机械动力优化、机械动力修复、新增功能与模组联动四部分。Create 是必需前置；联机时客户端和服务端都需要安装本模组。AE2、Apotheosis 等只在安装后启用对应功能。

[机械动力优化](#机械动力优化) · [机械动力修复](#机械动力修复) · [新增功能](#新增功能) · [模组联动](#模组联动) · [注册指令](#注册指令) · [配置文件](#配置文件) · [前置与安装](#前置与安装)

## 机械动力优化

### 机械运行与库存同步

| 目标 | 优化内容 |
|---|---|
| `SmartBlockEntity` | 同步包节流（1/3）、按玩家距离动态调整 lazyTick 频率 |
| `MountedStorageManager` | 提高同步冷却，减少挂载库存的重复发包 |
| `PackageEntity` | 减少包裹实体的追踪更新 |
| `BeltBlockEntity` | 传送带 tick 优化 |
| `ChainConveyorBlockEntity` | 锁链运输 tick 间隔调整 |
| `BasinBlockEntity` | 工作盆溢出检查间隔 |
| `FunnelBlockEntity` | 漏斗 tick 优化 |

相关间隔可配置；调大间隔会影响更新频率，请结合实际机器规模调整。

### 红石信号终端优化

缓存 `LinkBehaviour.isAlive()` 的查询结果（按游戏 tick 粒度）。

Create 的 `RedstoneLinkNetworkHandler.updateNetworkOf` 每次被调用都要遍历整个同频率网络，对每个节点做 4 次状态查询。终端一多、或者区块反复加载，就是 O(n²) 的开销。

**这个优化不会影响信号实时性** —— 信号值 `getTransmittedStrength()` 依然是实时读取，缓存的只是「这个节点是否存活」。最坏情况是同一 tick 内被拆掉的终端晚 1 tick 才被移出网络。

可用 `/cir redstoneLinkOptimization true|false` 热切换。

### 网络压缩

客户端与服务端之间的 Create 同步包会按大小自动选择 LZ4 或 Zstd 压缩，小包直接放行避免因压缩反而变大。

## 机械动力修复

### 动力冲压机与工作盆空粒子修复

动力冲压机运行时，如果下方工作盆恰好被拆除，同步数据可能包含数量为 0 的物品。客户端用空物品创建粒子时会抛出：

```text
java.lang.IllegalArgumentException: Empty stacks are not allowed
```

玩家看到的通常是“网络协议错误”并掉线。补丁会在创建粒子前过滤空物品。

**仅当 Create 版本严格等于 `6.0.10` 时启用。** 其他 Create 版本自动跳过该修复，不影响上一节中的其他优化。该项最初加入于 3.4.7fix，在 3.4.8 起加入版本检测；上游修复后可移除这一临时补丁。

## 新增功能

### MSPT 预警

每 30 秒采样一次服务器 MSPT，四档：**40 / 45 / 50 / 70**（颜色：白 → 黄 → 金 → 红）。

**读到超线不会立刻报，要等 3 秒复检一次。**

- 第二次仍在**同一档位或更高** → 根据第二次的读数和档位触发全局播报
- 第二次数值下降 → 保持完全静默

重复确认为避免自动保存或偶然的卡顿误触报警的操作，只有持续性负载才会报出来。

`≥ 70` 时每次确认都会触发广播，直到mspt不再高于该值。

> 前两档仍有每 tick 余量，50 MSPT 刚好占满维持 20 TPS 的预算，这些档位用于告知负载趋势。70MSPT已经可以导致明显可见的卡顿。

### 清扫与垃圾桶

**没有定时扫地。** 触发方式只有两种：

- MSPT ≥ 70（红色档位确认后）自动触发清理
- 管理员执行扫地命令 `/cir cleanup`，或使用MCDR，KubeJS等外部包装方法间接执行该指令

普通清理收集的物品会进入垃圾桶，**有效期 1 小时**。玩家用 `/cir trashBin` 或点击聊天栏的垃圾桶文字提示打开界面取回。

手动执行 `/cir cleanup` 时，这条提示会全服广播，附带可点击的「打开垃圾桶」按钮。

带自定义名称的物品默认受保护（可在配置里关闭）。

#### 常规清扫会跳过哪些东西

为了避免垃圾桶在编码物品时造成客户端被踢下线，下列物品**不会被清理**，留在地面等玩家自行拾取：

| 跳过 | 原因 |
|---|---|
| 所有旗帜 | 图案是静态注册表条目，两端未必一致 |
| 17 种原版潜影盒 | 内部物品表随物品走 |
| **不可堆叠且带附魔的物品** | 避免重载后失效的附魔引用造成编码错误 |

可堆叠的物品（石头、圆木这类）不做上述检查，直接清理。

普通清理还会尝试编码准备放入垃圾桶的物品；编码失败的物品会被销毁并记录日志，因此不能保证所有被清理物品都能找回。

#### 强制清扫 `/cir forceCleanup`

常规清扫指令无法缓解服务器压力时的最终解决办法：

- 全服播报 **20 / 10 / 5 / 3 / 2 / 1** 秒倒计时
- 倒计时结束后**清空所有掉落物**

**通过本方法清空的掉落物无法收入垃圾桶**

清空完成后，全服广播「强制清扫执行完毕」（红色加粗）。

### 兑换码系统

面向公益服务器的非商业兑换码功能。所有兑换码对全体玩家开放，**每人每个码限兑一次**，不支持按人发放或一次性码。

- `/cir giftCode` —— 打开兑换界面（无权限要求）
- `/cir manageGiftCode` —— 打开管理界面，权限 4

兑换码可配置物品、经验值和状态效果。管理界面支持新建、作废、删除；作废后视同过期，删除会连同兑换记录一起清掉。

配置保存在 `config/create_cir_addition/gift_codes.json`，首次运行自动生成。兑换记录存在世界存档里。

两个界面打开前都会先弹出免责声明，说明本系统不支持任何商业性质的兑换码。



### 公共基地坐标

首次进入新地图时，世界出生点被自动设为基地。可用 `/cir setHome` 更改（需要管理员权限）。

**全世界只有一个基地**，只在主世界生效，同维度才有豁免。

## 模组联动

### AE2 限制与发电管理

#### 量子环数量与密度限制

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

#### 发电机调节

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

### BOSS 防秒杀保护（需要 Apotheosis）

仅在安装 **Apotheosis** 时启用，缺少该模组时整套功能不注册。

| 规则 | 行为 |
|---|---|
| 单次伤害上限 | 不超过最大生命 × 50% |
| 低血量保护 | 低于半血时，将真实扣血限制在按当前生命比例与分组下限计算的区间；原始伤害 ≤15 的攻击跳过此规则 |
| 过量反制 | 单次伤害超过最大生命时取消伤害，扣掉当前血量一半并进入狂暴 |
| 半血强制 | 首次会从半血以上降至 50% 或以下时，先把实际扣血截断到恰好半血，再回复 40 / 60 / 80 并进入火焰狂暴 |
| 爆发狂暴 | 玩家单次伤害 > 100 触发 |

默认保护原版、灾变与暮色森林的指定 BOSS；按 `minecraft`、`cataclysm`、`twilightforest` 分组，未安装对应模组时跳过该组。

默认低血量比例为当前生命的 10%，分组下限为 15（原版 BOSS 为 20），在护甲、抗性、附魔和吸收之后处理；弱输入不会被抬高，原本不致命的攻击也不会仅因下限补足而击杀。半血阶段每只 BOSS 只触发一次，优先于已有狂暴免伤、冷却及其他爆发规则。回血量按初始最大生命分为 40 / 60 / 80，回血后生命可以高于 50%。

狂暴持续 10 秒，期间免疫一切伤害、攻击力 +10；结束后冷却 20 秒。粒子按触发时机区分颜色：半血阶段为火焰，其余为绿色。

处决拦截：神化的处决词缀对受保护 Boss 无效，拦截发生在图腾检查和死亡音效之前。同一玩家被拦 5 次后，移除其主手武器的处决词条并全服播报。

管理员 `/kill` 与 `generic_kill` 始终放行。

### 宝石切割与物品名称联动

- **宝石切割器**：需要神化（Apotheosis）与机械动力：附魔工业（Create Enchantment Industry）。按输入宝石的纯度决定是否允许切割升阶；默认允许“碎裂 → 开裂 → 有瑕”，不再允许从有瑕继续切割到更高纯度。
- **彩虹名称**：需要 Apotheosis 与 KubeJS。可在配置的 `perfectRainbowItems` 列表中指定物品，让名称使用神化的 PERFECT 彩虹颜色；默认列表为空，目标物品不存在或取色失败时保留原名称。

本模组只提供切割限制和名称显示，不附带 CIR 整合包的 KubeJS 宝石配方；需要其他升级路线时应自行编写配方。

### 其他模组兼容性修复

相关补丁在启动时检查对应模组及版本；缺少依赖或不符合条件时，在解析目标类之前跳过。启动日志会记录检测结果，可选模组不会变成必装前置。

| 修复 | 自动启用条件 | 作用范围 |
|---|---|---|
| ME 吞吐量检测任务（ThroughputQuest）旧访问器异常 | 同时安装 `throughput_quest` 与 `ae2` | 修复服务端检测与客户端渲染；针对 1.0.0 的旧调用验证，只替换明确匹配的访问器调用，新版没有这些调用时不替换 |
| 卷帘门（RollingGate）加载配置时卡死 | **客户端**的 `rolling_gate` 版本严格为 **`1.1.0+build.66`** | 将窗口属性更新交给游戏主线程，避免加载线程调用窗口接口；专用服务器不加载此补丁 |

ThroughputQuest 补丁用 AE2 公开 getter 替换已移除的 AdvancedAE 私有访问器调用，不需要修改第三方 JAR，也不整体替换其他版本的模组逻辑。

#### 库存桥接优化（3.5.0fix）

仅当 Create: AE2 Stock Bridge（`createstockbridge`）版本严格为 **0.2.0**，且安装 Create 与 AE2 时启用。

- 为每个桥接器缓存远程物品键，真实预约扣减后使缓存失效。
- 库存轮询结果未变化时，跳过重复重新挂载。
- 保留上游 20 tick 轮询、请求处理，以及优先级和在线状态变化触发的重新挂载。
- 同时遵循 `stockBridgeOptimization` 与 `create` 配置开关。

**这项修复仅需服务端更新，已有 3.5.0 客户端可继续使用；客户端仍须安装 CIR Addition。** 该修复已完成实机验证，并通过编译及离线缓存、字节码、协议与 BOSS 回归检查。库存桥接优化自 3.5.0fix 加入。

## 注册指令

所有命令在 `/cir` 下。

> **3.4.6 起所有命令改为驼峰命名**，旧名（`forcecleanup`、`trashbin`、`redstonelinkoptimization` 等）不再可用。

| 命令 | 权限 | 说明 |
|---|---|---|
| `/cir setHome <x> <y> <z>` | 4 | 设置主基地（固定落在主世界） |
| `/cir home` | 0 | 查看当前基地坐标（**所有玩家可用**） |
| `/cir quantum scan` | 4 | 扫描当前维度已加载区域，把现有量子环补登记进表 |
| `/cir cleanup` | 4 | 立即清理地面掉落物（旗帜、潜影盒、附魔物品会跳过） |
| `/cir forceCleanup` | 4 | 强制清扫：倒计时后清空所有掉落物，不进垃圾桶 |
| `/cir trashBin` | 0 | 打开垃圾桶（**所有玩家可用**） |
| `/cir giftCode` | 0 | 打开兑换码界面（**所有玩家可用**） |
| `/cir manageGiftCode` | 4 | 打开兑换码管理界面 |
| `/cir redstoneLinkOptimization <true\|false>` | 4 | 热切换红石终端优化 |
| `/cir ae2Generator` | 4 | 查看两个 AE2 发电方块的当前状态 |
| `/cir ae2Generator crystalResonance <true\|false>` | 4 | 开关水晶谐振发电机 |
| `/cir ae2Generator vibrationChamber <low\|normal\|boost>` | 4 | 切换谐振仓档位（Tab 补全） |
| `/cir bossguard reload` | 2 | 重新加载 BOSS 保护配置；新配置有误时继续使用原配置 |

## 配置文件

主配置：`config/create_cir_addition.toml`。下面按功能顺序列出常用项目；具体默认值和全部项目以运行后生成的配置为准。

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

### Boss 防爆发保护

独立配置文件：`config/create_cir_addition-boss-guard.json`

```jsonc
{
  "bossGroups": [
    {
      "mod": "cataclysm",
      "bossList": ["cataclysm:ignis", "..."],
      "lowHealthDamageRatio": 0.10,
      "lowHealthMinDamage": 15.0,
      "lowHealthSkipThreshold": 15.0
    }
  ],
  "global": {
    "singleHitCapRatio": 0.50,
    "lowHealthThresholdRatio": 0.50,
    "burstTriggerDamage": 100.0,
    "berserkDurationMs": 10000,
    "berserkCooldownMs": 20000,
    "berserkAttackBonus": 10.0,
    "halfHealthHealSmall": 40.0,
    "halfHealthHealMedium": 60.0,
    "halfHealthHealLarge": 80.0,
    "halfHealthHealThresholds": [400.0, 700.0],
    "particleRadius": 2.5,
    "executionBlockStripCount": 5
  }
}
```

- `mod` 是分组键，同时作为启用开关：该模组未安装时整组跳过。
- `lowHealthMinDamage` 可逐组配置，默认 minecraft 组 20，其余 15。
- 改完执行 `/cir bossguard reload`，不需要重启。

### 宝石切割与彩虹名称

```toml
# 判断输入纯度，true 只是放行原有加工规则
gemCutAllowCracked = true
gemCutAllowChipped = true
gemCutAllowFlawed = false
gemCutAllowNormal = false
gemCutAllowFlawless = false
gemCutAllowPerfect = false

# 默认空列表，按需填写实际存在的物品 ID
perfectRainbowItems = []
```

切割限制需要 Apotheosis + Create Enchantment Industry；彩虹名称需要 Apotheosis + KubeJS。修改这些配置后重启游戏。

### 库存桥接优化（3.5.0fix）

```toml
stockBridgeOptimization = true
```

仅在 Stock Bridge 版本严格为 0.2.0 且安装 Create、AE2 时生效，同时遵循 `create` 总开关。此配置项从 3.5.0fix 开始提供。

### 兑换码数据

文件：`config/create_cir_addition/gift_codes.json`。首次使用时生成，兑换记录保存在世界存档中，日常管理可使用游戏内界面。

## 前置与安装

| 依赖 | 必需性 | 版本 |
|---|---|---|
| Minecraft | **必需** | 1.21.1 |
| NeoForge | **必需** | 21.1+ |
| Create | **必需** | 6.0.0 – 6.x |
| AE2 | 可选 | 19+（不装则量子环限制和发电机调节都跳过） |
| Apotheosis | 可选 | BOSS 保护及相关宝石联动所需 |
| Create Enchantment Industry / KubeJS | 可选 | 分别用于宝石切割限制 / 彩虹名称 |
| ThroughputQuest / RollingGate | 可选 | 仅对下述兼容范围启用修复 |

### 安装步骤

1. 从[发行页面](https://github.com/CIR-Server/Create-CIR-Addition/releases)下载需要的版本，放入 `mods/`；联机时客户端和服务端都需要安装本模组。
2. 启动一次，生成主配置；安装 Apotheosis 时还会生成独立 BOSS 配置。
3. 按需调整配置。BOSS 配置可用 `/cir bossguard reload` 重载；启动时读取的项目必须重启，不能统一用 `/reload` 代替。

AE2、Apotheosis 等属于可选联动，不会因本模组变成必装项。

## 升级与版本兼容

| 构建 | 网络协议 | 联机要求 |
|---|---|---|
| 3.5.0 | `3.5.0` | 客户端与服务端都需要 CIR Addition 3.5.0；不接受 3.4.x 或其他协议 |
| 3.5.0fix | `3.5.0` | 沿用原有数据包与编解码，服务端可接受已有 3.5.0 客户端；不接受 3.4.x |

3.5.0fix 只增加库存桥接修复，并没有把整个模组改成“客户端可选”。由 3.4.x 升级到 3.5.0 协议时仍需同时升级客户端和服务端。

### 量子环登记迁移

从旧版本升级时：

- **老的量子环不会自动登记**。升级后进游戏跑一次 `/cir quantum scan`，把现有环补进表里，否则它们会被当成新桥重新判定一遍
- 如果你的存档里有大量量子环且已超出当前限制，**建议先跑 scan 再改限制值**

## 更新记录

README 按功能组织，逐版本的改动集中在 [CHANGELOG.md](CHANGELOG.md)；下载见[发行页面](https://github.com/CIR-Server/Create-CIR-Addition/releases)。3.4.7 / 3.4.8 等旧网络协商规则仅适用于对应历史版本，不适用于当前 3.5.0 协议。

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

Create CIR Addition was originally developed for the CIR server. Features are grouped into Create optimization, Create fixes, new features and mod integrations. Create is required, and multiplayer needs CIR Addition on both client and server. AE2, Apotheosis and other integrations activate only when their required mods are installed.

[Create optimization](#create-optimization) · [Create fixes](#create-fixes) · [New features](#new-features) · [Mod integrations](#mod-integrations) · [Commands](#commands) · [Configuration](#configuration) · [Requirements and installation](#requirements-and-installation)

## Create optimization

### Machinery ticks and storage synchronization

| Target | What it does |
|---|---|
| `SmartBlockEntity` | Sync packet throttling (1/3), `lazyTick` rate scaled by player distance |
| `MountedStorageManager` | Longer sync cooldown, fewer repeated packets for mounted storage |
| `PackageEntity` | Fewer tracking updates for package entities |
| `BeltBlockEntity` | Belt tick optimization |
| `ChainConveyorBlockEntity` | Chain conveyor tick interval |
| `BasinBlockEntity` | Basin overflow check interval |
| `FunnelBlockEntity` | Funnel tick optimization |

The intervals are configurable. Increasing them changes update frequency, so tune them for the machinery in your world.

### Redstone link optimization

Caches `LinkBehaviour.isAlive()` results per game tick.

Create's `RedstoneLinkNetworkHandler.updateNetworkOf` walks the entire same-frequency network on every call, performing four state lookups per node. With many links, or with chunks loading and unloading repeatedly, this becomes O(n²) work.

**Signal timing is unaffected** — `getTransmittedStrength()` is still read live; only the "is this node still alive" check is cached. Worst case, a link destroyed mid-tick leaves the network one tick later.

Toggle at runtime with `/cir redstoneLinkOptimization true|false`.

### Network compression

Create sync packets between client and server pick LZ4 or Zstd by size. Small packets pass through untouched, so compression cannot make them larger.

## Create fixes

### Empty mechanical-press / basin particles

If the basin beneath a running press is removed at the wrong moment, its sync data can contain an item with count 0. Building particles from that empty stack throws:

```text
java.lang.IllegalArgumentException: Empty stacks are not allowed
```

Players typically see a "network protocol error" disconnect. The patch filters empty stacks before creating particles.

**Enabled only when Create is exactly `6.0.10`.** Other Create versions skip this fix without changing the optimizations above. Introduced in 3.4.7fix and version-gated since 3.4.8, it is a temporary workaround that can be removed after the upstream bug is fixed.

## New features

### MSPT warnings

Samples server MSPT every 30 seconds, four tiers: **40 / 45 / 50 / 70** (white → yellow → gold → red).

**A reading above the line does not broadcast immediately** — it waits 3 seconds and samples again:

- Second reading still in the **same tier or higher** → broadcast using the second reading's value and tier
- Second reading fell back → complete silence

The repeat sample exists so that an autosave or an incidental hitch does not trip the alarm; only sustained load gets reported.

At `≥ 70` every confirmed reading broadcasts until MSPT drops back below it.

> The first two tiers still leave headroom within the tick budget; 50 MSPT uses the full budget for 20 TPS. These tiers surface the load trend. At 70 MSPT stutter is already plainly visible.

### Item cleanup and trash bin

**No timer-based cleanup.** Two triggers only:

- Automatic, once MSPT reaches the red tier (≥ 70) and is confirmed
- Manual, via `/cir cleanup`, or invoked indirectly through external wrappers such as MCDR or KubeJS

Items collected by regular cleanup go into a trash bin valid for **1 hour**. Players reclaim them with `/cir trashBin`, or by clicking the trash-bin link in chat.

When `/cir cleanup` is run manually, this message is broadcast to the whole server with a clickable trash bin button.

Items carrying a custom name are protected by default (configurable).

#### What the regular cleanup skips

To avoid the trash bin getting a client kicked while encoding items, the following are **not cleaned up at all** — they stay on the ground for players to pick up themselves:

| Skipped | Reason |
|---|---|
| All banners | Patterns are static registry entries that may differ between client and server |
| The 17 vanilla shulker boxes | Their contents travel with the item |
| **Non-stackable enchanted items** | Avoid encoding errors from stale enchantment references after a reload |

Stackable items (stone, logs and the like) skip all these checks and are cleared directly.

Regular cleanup also checks whether collected items can be encoded for the trash bin. An item that fails this check is destroyed and logged, so recovery is not guaranteed for every removed item.

#### Force cleanup `/cir forceCleanup`

The final recourse when the regular cleanup command can no longer relieve server load:

- Broadcasts a countdown at **20 / 10 / 5 / 3 / 2 / 1** seconds
- When it reaches zero, **every dropped item is cleared**

**Dropped items cleared this way cannot be retrieved from the trash bin.**

Once the clear is done, "Force cleanup complete." is broadcast to the whole server in bold red.

### Gift code system

A non-commercial gift code feature for community servers. Every code is open to all players and **can be redeemed once per player**; per-player or single-use codes are not supported.

- `/cir giftCode` — open the redeem screen (no permission needed)
- `/cir manageGiftCode` — open the management screen (permission 4)

A code can grant items, experience and status effects. The management screen supports creating, disabling and deleting codes; a disabled code counts as expired, and deleting removes its redemption records too.

Codes live in `config/create_cir_addition/gift_codes.json`, generated on first launch. Redemption records are stored in the world save.

Both screens show a disclaimer first, stating that this system does not support gift codes of any commercial nature.



### Shared base coordinates

On first world load the spawn point becomes the base. Change it with `/cir setHome` (admin permission required).

**There is exactly one base in the world.** It lives in the Overworld, and only rings in the same dimension benefit from the exemption.

## Mod integrations

### AE2 limits and generator management

#### Quantum ring limits

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

#### Generator control

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

### Boss Guard (requires Apotheosis)

Requires **Apotheosis**; the entire module is skipped when it is absent.

| Rule | Behaviour |
|---|---|
| Per-hit cap | No more than 50% of max health |
| Low-health protection | Below half health, final HP loss is bounded using current-health scaling and the group floor; raw hits ≤15 skip this rule |
| Overkill response | Damage above max health is cancelled, halves current HP and triggers berserk |
| Half-health phase | The first hit that would take the boss from at least half health to 50% or below stops final HP loss at exactly half health, then heals 40 / 60 / 80 and starts flame berserk |
| Burst berserk | A single player hit above 100 triggers it |

Defaults cover selected vanilla, Cataclysm and Twilight Forest bosses, grouped under `minecraft`, `cataclysm` and `twilightforest`. A group is skipped when its owning mod is absent.

The default low-health ratio is 10% of current HP, with a group floor of 15 (20 for vanilla bosses), applied after armor, resistance, enchantments and absorption. Weak inputs are not inflated, and a non-finishing raw hit cannot become a floor-only kill. The once-per-boss half-health phase takes priority over active immunity, cooldown and other burst rules. Healing is 40 / 60 / 80 based on initial maximum HP, so final health may exceed 50%.

Berserk lasts 10 seconds with full damage immunity and +10 attack; a 20-second cooldown follows. Particle colour depends on the trigger: flame for the half-health phase, green otherwise.

Execution blocking: Apotheosis execution affixes cannot kill protected bosses; the block happens before totem checks and the death sound. Five blocked attempts strip the executing affix from the main-hand item and broadcast server-wide.

Administrator `/kill` and `generic_kill` always bypass protection.

### Gem cutting and item-name integrations

- **Gem cutter**: requires Apotheosis and Create Enchantment Industry. Cutting permission is checked against input purity. Defaults allow CRACKED → CHIPPED → FLAWED, then block further cutting from FLAWED or above.
- **Rainbow names**: requires Apotheosis and KubeJS. Add item IDs to `perfectRainbowItems` to use Apotheosis' PERFECT rainbow color for their display names. The list is empty by default; missing items or unavailable colors retain the original name.

These features provide cutting restrictions and name styling. CIR's custom KubeJS gem recipes are not bundled; pack authors must supply their own alternative upgrade recipes.

### Other compatibility fixes

At startup, each patch checks its required mods and versions. Missing dependencies or unmatched conditions skip the patch before target classes are resolved. Startup logs report the result; optional integrations remain optional.

| Fix | Activation condition | Scope |
|---|---|---|
| ThroughputQuest legacy accessor failure | Both `throughput_quest` and `ae2` are installed | Repairs server detection and client rendering; validated against 1.0.0's legacy calls. Only exact accessor-call matches are rewritten; newer versions without those calls are untouched |
| RollingGate configuration-loading hang | A **client** has `rolling_gate` version **exactly `1.1.0+build.66`** | Dispatches window attribute updates to the game thread instead of calling window APIs from the loading worker; dedicated servers do not load the patch |

The ThroughputQuest patch replaces calls to AdvancedAE's removed private accessor with AE2 public getters. No third-party JAR changes are needed, and it does not replace the complete behavior of other versions.

#### Stock Bridge optimization (3.5.0fix)

Requires Create: AE2 Stock Bridge (`createstockbridge`) version **exactly 0.2.0**, plus Create and AE2.

- Cache remote item keys per bridge and invalidate after actual reservation erasure.
- Skip repeated remounts when polled contents have not changed.
- Preserve upstream 20-tick polling, request handling, and remounts caused by priority or online-state changes.
- Follow both the `stockBridgeOptimization` and `create` config switches.

**Only the server needs this fix update; existing 3.5.0 clients remain compatible and must still have CIR Addition installed.** This fix has been verified in-game and passed compilation and offline cache, bytecode, protocol and Boss Guard checks. Stock Bridge optimization was added in 3.5.0fix.

## Commands

All commands live under `/cir`.

> **Since 3.4.6 all commands use camelCase.** The old names (`forcecleanup`, `trashbin`, `redstonelinkoptimization`, etc.) no longer work.

| Command | Permission | Description |
|---|---|---|
| `/cir setHome <x> <y> <z>` | 4 | Set the main base (always lands in the Overworld) |
| `/cir home` | 0 | Show current base coordinates |
| `/cir quantum scan` | 4 | Scan loaded chunks of this dimension and register existing rings |
| `/cir cleanup` | 4 | Sweep dropped items immediately (banners, shulker boxes and enchanted items are skipped) |
| `/cir forceCleanup` | 4 | Force sweep: countdown, then clear every dropped item without binning it |
| `/cir trashBin` | 0 | Open the trash bin (**available to everyone**) |
| `/cir giftCode` | 0 | Open the gift code redemption screen |
| `/cir manageGiftCode` | 4 | Open the gift code management screen |
| `/cir redstoneLinkOptimization <true\|false>` | 4 | Toggle the redstone link cache at runtime |
| `/cir ae2Generator` | 4 | Show the current state of both AE2 generators |
| `/cir ae2Generator crystalResonance <true\|false>` | 4 | Enable or disable the crystal resonance generator |
| `/cir ae2Generator vibrationChamber <low\|normal\|boost>` | 4 | Switch the vibration chamber tier (tab-completed) |
| `/cir bossguard reload` | 2 | Reload the Boss Guard configuration; invalid settings leave the current configuration in use |

## Configuration

Main file: `config/create_cir_addition.toml`. Common settings are listed below in feature order; the generated config contains the full set and defaults.

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

### Boss Guard

Separate config file: `config/create_cir_addition-boss-guard.json`

```jsonc
{
  "bossGroups": [
    {
      "mod": "cataclysm",
      "bossList": ["cataclysm:ignis", "..."],
      "lowHealthDamageRatio": 0.10,
      "lowHealthMinDamage": 15.0,
      "lowHealthSkipThreshold": 15.0
    }
  ],
  "global": {
    "singleHitCapRatio": 0.50,
    "lowHealthThresholdRatio": 0.50,
    "burstTriggerDamage": 100.0,
    "berserkDurationMs": 10000,
    "berserkCooldownMs": 20000,
    "berserkAttackBonus": 10.0,
    "halfHealthHealSmall": 40.0,
    "halfHealthHealMedium": 60.0,
    "halfHealthHealLarge": 80.0,
    "halfHealthHealThresholds": [400.0, 700.0],
    "particleRadius": 2.5,
    "executionBlockStripCount": 5
  }
}
```

- `mod` is the group key and also the activation gate: the group is skipped when that mod is absent.
- `lowHealthMinDamage` is per-group; defaults are 20 for `minecraft` and 15 for the others.
- Apply changes with `/cir bossguard reload`; no restart needed.

### Gem cutting and rainbow item names

```toml
# Input purity; true only permits the original cutting rules
gemCutAllowCracked = true
gemCutAllowChipped = true
gemCutAllowFlawed = false
gemCutAllowNormal = false
gemCutAllowFlawless = false
gemCutAllowPerfect = false

# Empty by default; add existing item IDs as needed
perfectRainbowItems = []
```

Gem cutting requires Apotheosis + Create Enchantment Industry; rainbow names require Apotheosis + KubeJS. Restart after changing these settings.

### Stock Bridge optimization (3.5.0fix)

```toml
stockBridgeOptimization = true
```

Requires exactly Stock Bridge 0.2.0, Create and AE2, and also follows the `create` master switch. This option is available starting with 3.5.0fix.

### Gift code data

File: `config/create_cir_addition/gift_codes.json`. Generated on first use; redemption records live in the world save. Use the in-game management screen for routine administration.

## Requirements and installation

| Dependency | Required | Version |
|---|---|---|
| Minecraft | **Yes** | 1.21.1 |
| NeoForge | **Yes** | 21.1+ |
| Create | **Yes** | 6.0.0 – 6.x |
| AE2 | Optional | 19+ (quantum limiter and generator control are skipped without it) |
| Apotheosis | Optional | Required for Boss Guard and the related gem integrations |
| Create Enchantment Industry / KubeJS | Optional | Gem cutting / rainbow item names, respectively |
| ThroughputQuest / RollingGate | Optional | Patches apply only within the compatibility scope above |

### Installation steps

1. Download the required build from [Releases](https://github.com/CIR-Server/Create-CIR-Addition/releases) and put it in `mods/`. Multiplayer requires this mod on both the client and server.
2. Launch once to generate the main config. The separate Boss Guard config is generated when Apotheosis is installed.
3. Adjust the settings. Use `/cir bossguard reload` for Boss Guard; startup-only settings require a restart, and `/reload` is not a substitute for every config change.

AE2, Apotheosis and the other integrations remain optional dependencies.

## Upgrading and version compatibility

| Build | Wire protocol | Multiplayer requirements |
|---|---|---|
| 3.5.0 | `3.5.0` | CIR Addition is required on both client and server; 3.4.x and other protocols are rejected |
| 3.5.0fix | `3.5.0` | Reuses existing payloads and codecs; an updated server accepts existing 3.5.0 clients, but not 3.4.x |

3.5.0fix only adds the Stock Bridge fix; it does not make the entire mod optional on clients. Moving from 3.4.x to protocol 3.5.0 still requires updating both sides.

### Quantum ring registry migration

Upgrading from an older version:

- **Existing rings are not registered automatically.** After upgrading, run `/cir quantum scan` once in-game to pull them into the table; otherwise they are judged as new rings
- If the save already contains more rings than the current limits allow, **run scan before tightening the limits**

## Changelog

This README is organized by feature. Per-version changes live in [CHANGELOG.md](CHANGELOG.md), with downloads on [Releases](https://github.com/CIR-Server/Create-CIR-Addition/releases). Old 3.4.7 / 3.4.8 negotiation rules apply only to those historical builds, not to the current 3.5.0 protocol.

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
