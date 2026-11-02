# Changelog

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

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
