# Changelog

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [3.4.6-fix] - 2026-11-06

### 修复

- 管理界面的「新兑换码」「增加经验值」「作废」「删除」等文字硬编码为中文，英文环境下不翻译。
- 垃圾桶的翻页控件（上一页 / 下一页 / 页码）同样硬编码。
## [3.4.6] - 2026-11-06

### 新增

- 兑换码系统。`/cir giftCode` 打开兑换界面（无权限），`/cir manageGiftCode` 打开管理界面（权限 4）。
- 兑换码可配置物品、经验值与状态效果，每个码每人限兑一次。
- 管理界面支持新建、作废、删除；作废视同过期，删除会清掉兑换记录。
- 打开兑换界面时先弹出免责声明，说明本系统不支持任何商业性质的兑换码。

### 变更

- **全部指令改为驼峰命名**：`forceCleanup`、`trashBin`、`redstoneLinkOptimization`、`ae2Generator`、`giftCode` 等。旧名不再可用，升级后需同步更新服务端的命令配置。

## [3.4.5] - 2026-11-05

### 新增

- `/cir forcecleanup` 强制清扫。全服倒计时 20 / 10 / 5 / 3 / 2 / 1 秒后清空所有掉落物，不进垃圾桶。
- `/cir cleanup` 改为全服广播，附带「打开垃圾桶」按钮。

### 变更

- 常规清扫的判定顺序：旗帜跳过 → 可堆叠直接清理 → 潜影盒（17 种原版）跳过 → 附魔跳过 → 其余清理。

### 修复

- 垃圾桶在物品较多时导致客户端断连。
- `TrashBinMenu` 翻页与取物后缺少同步。
- 恢复 `MixinMountedStorageManager` 与 `PacketRouter` 中丢失的方法体（3.4.4 及更早版本中为空实现）。

## [3.4.3] - 2026-11-03

### 变更

- **`/cir home` 权限要求降到 0**。查看基地坐标不再需要管理员，所有玩家都能用。设置基地（`/cir setHome`）仍然要求权限 4。

## [3.4.2] - 2026-11-03

### 修复

- **发电机开关提示多了一个「已」**。`/cir ae2generator crystalresonance` 原本显示「水晶谐振发电机已已启用」——语言模板自带「已」，参数里又带了一个。现在参数改为「开启 / 关闭」。

## [3.4.1] - 2026-11-03

### 修复

- **切换谐振仓档位对已放置的机器不生效**。配置本身写对了，但 AE2 的 `VibrationChamberBlockEntity` 在构造函数里把 `minEnergyRate` / `baseMaxEnergyRate` / `initialEnergyRate` 三个值读进了 `final` 字段，之后再也不看配置。表现是切到 `low` 档后发电量不变、耗煤速度变成 4 倍。

  现在切档后会扫描已加载区域内的谐振仓，覆盖这三个缓存字段，并在聊天栏提示同步了几台。覆盖手段不可用时只记录一条警告，不影响其他功能。

  未加载区块里的机器不需要处理：它们下次加载时会重新构造，自然读到新配置。

## [3.4.0] - 2026-11-03

### 新增

- **AE2 发电机运行时调节**（`/cir ae2generator`，权限 4）

  AE2 自带两个能给 ME 网络发电的方块，现在可以在游戏内调整，不需要改 `ae2-common.toml`，也不需要重启。

  | 命令 | 作用 |
  |---|---|
  | `/cir ae2generator` | 查看当前状态 |
  | `/cir ae2generator crystalresonance <true\|false>` | 开关水晶谐振发电机 |
  | `/cir ae2generator vibrationchamber <low\|normal\|boost>` | 切换谐振仓档位 |

  谐振仓三档以切档前的值为基准整体缩放：`low` 是 1/4，`normal` 是原值，`boost` 是 4 倍。基准会在第一次切档时快照进配置文件，所以手动调过 `ae2-common.toml` 的数值不会被冲成 AE2 出厂默认。

  谐振仓无法归零 —— AE2 自身的下限是 `baseEnergyPerFuelTick ≥ 0.1`、`maxEnergyPerGameTick ≥ 1`，`low` 只是压到 1/4。

### 技术细节

- 反射访问 `AEConfig$CommonConfig` 的 public final 字段，调用 `ConfigValue.set()` + `save()` 写回 `ae2-common.toml`
- AE2 未安装时指令返回「未检测到 AE2」，不抛异常
- `CirConfig` 新增 6 个快照键（`ae2*Snapshot`），记录切档前的基准值

## [3.3.2] - 2026-11-02

### 变更

- **MSPT 预警阈值调整**：`35 / 40 / 45 / 50` → **`40 / 45 / 50 / 70`**

  原来的 50 是「tick 预算刚好用满」（满速 20 TPS），拿它当「严重超限」报警不合理——服务器正常运转时一次磁盘 IO 就能触发。现在 70 才是真正的红色档（TPS 掉到约 14）。

- **MSPT 预警加入复检机制**

  第一次采样超过阈值时**不再立即广播**，而是等 3 秒后重新采样。只有第二次数值仍落在**同一档位或更高**时才广播，且播报用第二次的读数和等级；掉档则完全静默。

  动机：单次采样很容易被一次性开销抬高（Sable 保存 sub-level、区块加载、玩家进服），那时候服务器其实完全正常，报警纯属噪音。

  采样周期仍是 30 秒，复检的 3 秒包含在这个周期内。

  ```
  t = 0s     第一次采样，读到 46 → 档位 2，进入待验证
  t = 3s     复检，读到 48 → 档位 2，48 >= 档位 2 → 广播「档位 2」
             若读到 43 → 档位 1 < 档位 2 → 静默
  t = 30s    下一次第一次采样
  ```

- 自动扫地跟随红色档（≥70），不再在 50 触发

### 技术细节

- `MsptMonitor` 从单阶段采样改为两阶段状态机：
  `tickCounter`（30 秒计时）→ `verifying`（等 60 tick）→ 广播 / 静默
- 两个阶段互斥，同一 tick 不会同时执行第一次采样与复检

## [3.3.1] - 2026-11-02

### 修复

- **垃圾桶界面打不开**。`/cir trashbin` 之前点了没任何反应——服务端正常打开了菜单，客户端却什么都不显示。

  原因是 NeoForge 的自定义 `MenuType` 必须在客户端注册一个 Screen 工厂。漏了这一步时，服务端的
  `openMenu` 发出的 `OpenScreen` 包到达客户端后会去 `MenuScreens` 里查界面工厂，查不到就返回 null，
  接着 `Minecraft.setScreen(null)`，结果是**静默失败**：不显示界面、不报错、服务端还以为已经打开了。

  新增两个类：
  - `client/TrashBinScreen` —— 复用原版 6 行箱子贴图（`generic_54`，176×222，和 6×9 槽位布局完全匹配）
  - `client/ClientSetup` —— 在 `RegisterMenuScreensEvent` 里注册，整个类用 `Dist.CLIENT` 限定，
    避免服务端加载时因为引用 `AbstractContainerScreen` 而抛 `NoClassDefFoundError`

  > 这个问题从老版本 Duolingo Super 就存在，是移植时原样带过来的。老版本 jar 里同样没有任何 `*Screen` 类。

## [3.3.0] - 2026-11-02

### 新增

- **红石信号终端优化**：缓存 `LinkBehaviour.isAlive()` 查询结果（按游戏 tick 粒度）。
  解决 `RedstoneLinkNetworkHandler.updateNetworkOf` 在终端密集或区块反复加载时的重复状态查询开销。
  可通过 `/cir redstonelinkoptimization true|false` 热切换，改动会写回配置文件。
- **`/cir trashbin` 命令**：打开垃圾桶界面，取回被清理的物品。**所有玩家可用**，不需要权限。
- **红石终端优化独立开关** `redstoneLinkOptimization`，与 `modules.create` 解耦。
- **量子环限制总开关** `quantumLimiterEnabled`。

### 变更

- **扫地功能恢复**：从早期版本移植 `ItemCleaner`，但**取消了定时自动清理**。
  现在只有两种触发：MSPT ≥ 50 时自动清理，或管理员执行 `/cir cleanup`。
- 被清理的物品进入垃圾桶，**有效期 1 小时**（可通过 `TrashBin.DEFAULT_DURATION_SECONDS` 调整）。
- `/cir` 根节点不再统一要求权限 4，改为各子命令自行声明。
  `trashbin` 对所有玩家开放，其余仍为权限 4。
- 量子环提示中的「当前已有 x 座」改为包含刚建成的这一座。
- 某个半径的限制关闭时，不再显示对应的「当前已有」括号。

### 修复

- **量子环判定在区块加载时误触发**。
  之前每次走进有量子环的区域都会重新走一遍判定并弹提示。
  现在改为只查登记表：已登记的直接跳过，未登记的一律走完整判定。
- **移除了放置事件追踪**。
  之前曾用 `BlockEvent.EntityPlaceEvent` 辅助判断，但活塞推、`/setblock`、结构方块都不会触发该事件，
  玩家可以借此绕过判定。现在只依赖登记表。
- 基地内的量子环现在也会登记进表（统计配额时排除），避免每次区块加载重复触发。

### 技术细节

- 新增 `QuantumLimiterSwitch`：量子环总开关只在服务器启动时读取一次，
  `/reload` 和配置热重载刻意忽略，避免运行中途切换导致登记表与实际方块不一致。
- 新增 `MixinLinkBehaviour`：`isAlive()` 按 tick 缓存。
  信号值 `getTransmittedStrength()` 保持实时读取，不影响红石信号时序。
  最坏情况是同一 tick 内被拆掉的终端晚 1 tick 才移出网络。

## [3.2.0] - 2026-11-01

- 包名重写：`com.kawaiimira.duolingosuper` → `com.cirserver.createciraddition`
- 模组更名为 Create CIR Addition
- 新增 ME 量子环数量限制（需要 AE2）
- 新增基地系统与 `/cir setHome`
- 新增 MSPT 负载预警
- 移除了实体优化、TPS 报告和所有旧命令

## [3.1.0] - 2026-10-31

- 保留 Create 优化 mixin 与网络压缩，移除扫地、OP 命令、实体优化

## [3.0.0] - 2026-10-30

- 初始内部版本
