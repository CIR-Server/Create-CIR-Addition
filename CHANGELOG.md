# Changelog

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

从 3.4.7fix 起，每条变更中英并列（英文用缩进跟在中文条目下面）。
Entries are bilingual starting from 3.4.7fix; the English line is indented under its Chinese counterpart.

## [3.5.0] - 2026-11-22

### 新增 / Added

- 原生实现可配置、按模组分组的 Boss 限伤、低血量保护、过量伤害反制、半血回复、限时狂暴与粒子提示。
  Native configurable per-mod Boss Guard with damage caps, berserk/cooldown, overkill response and once-per-boss half-health healing.
- 必须安装 Apotheosis 才启用整套 Boss Guard；缺少各 Boss 所属模组时跳过该组。处决拦截在真正满足处决条件后、图腾检查与死亡音效之前执行，五次后只移除主手处决词条。
  The ENTIRE feature requires Apotheosis; optional classes are gated before mixin application. Block only eligible executions before totem checks/death sound; strip only the executing affix after five attempts.
- 新增三语提示及 `/cir bossguard reload`；配置无效时保留现行策略，成功重载清理临时状态。
  Add three locales and explicit safe config reload.
- 狂暴粒子按触发时机区分：半血阶段用火焰，其余用绿色；半径 2.5 格，首秒密集后每 2 秒一次。
  Berserk particles differ by trigger: flame for the half-health phase, green otherwise; 2.5-block radius, dense for the first second then every 2 seconds.

### 修复 / Fixed

- 半血阶段改为强制一次性事件，优先于狂暴免伤、冷却和爆发／过量伤害。真实扣血先截断至恰好 50%，随后按 40／60／80 独立回血并刷新火焰狂暴。
  Make the one-time half-health phase mandatory: truncate final HP loss at exactly 50%, then apply the configured heal and refresh flame berserk, including during green immunity/cooldown.
- 低血量下限在护甲、抗性、附魔和吸收之后的真实扣血点执行，弱输入不抬伤，非原本致命的补足伤害不单独击杀。
  Enforce the floor at final HP damage after reductions, without inflating weak inputs or turning a non-finishing raw hit into a floor-only kill.
- 普通和半血狂暴三语提示明确说明无敌；半血提示报告实际回复生命。
  Explicitly report invincibility in both messages and actual recovered HP for the half-health phase.

### 变更 / Changed

- 网络协议严格为 3.5.0，不再接受 3.4.x 或其他版本。保留已有条件补丁和 3.4.9 内容。
  Require exact protocol 3.5.0 on both sides; older protocol versions are incompatible. Keep existing conditional patches and 3.4.9 content.
- 保持展示版本及网络协议为 3.5.0；未安装神化时包括新增原生扣血钩子在内，整套保护均跳过。
  Keep version/protocol 3.5.0; the additional native damage hook is also gated on Apotheosis.

## [3.4.9] - 2026-11-21

### 新增 / Added

- 宝石切割器限制：附魔工业的宝石切割升阶只允许输入 CRACKED、CHIPPED 两档，更高纯度必须走序列组装。通过 Mixin 拦截 `GemCutting.canCut`，仅在神化与附魔工业同时安装时启用。
  Gem cutter restriction: gem cutting only accepts CRACKED/CHIPPED input purity; higher grades must go through sequenced assembly. Implemented by intercepting `GemCutting.canCut`, enabled only when Apotheosis and Create Enchantment Industry are both installed.
- PERFECT 彩虹名字：`appgen:singularity_generator_256m` 与 `extendedae:infinity_water_cell` 显示神话品质的彩虹名字。反射取 Placebo 的 `GradientColor.RAINBOW`，运行时判定物品是否存在，取不到颜色就退回原组件。
  PERFECT rainbow names for `appgen:singularity_generator_256m` and `extendedae:infinity_water_cell`. Reads Placebo's `GradientColor.RAINBOW` reflectively, checks item existence at runtime, and falls back to the original component when the colour is unavailable.

### 变更 / Changed

- 网络通道版本升至 3.4.9，仍接受 3.4.7 与 3.4.8 对端 —— payload 定义未变，4.0 服务端可直接连接。
  The payload channel version is now 3.4.9 and still accepts 3.4.7/3.4.8 peers. Payload definitions are unchanged, so 4.0 servers connect directly.

### 说明 / Notes

- `cir_apotheosis_policy` 已并入本模组，部署时必须移除该独立 jar，否则 `canCut` 会被两处同时注入。
  `cir_apotheosis_policy` is merged into this mod; the standalone jar must be removed on deployment or `canCut` will be injected twice.

## [3.4.8] - 2026-11-20

### 修复 / Fixes

- 修复 RollingGate 1.1.0+build.66 在加载配置的后台线程调用 GLFW 窗口函数造成的启动卡死：窗口属性更新改为非阻塞地交给游戏主线程。仅在对应版本的客户端启用，专用服务器不加载此补丁。
  Fix a startup hang in RollingGate 1.1.0+build.66 caused by invoking GLFW window functions on the configuration-loading worker. Window updates are dispatched to the game thread without blocking loading. The patch is client-only and gated to that version.
- ThroughputQuest 1.0.0 直接引用已移除的 AdvancedAE 私有 Mixin 访问器，导致配置吞吐量检测面板时服务端崩溃；现在用 AE2 等价公开 getter 替换服务端与客户端的三处调用，保留原有检测、渲染与任务逻辑。
  ThroughputQuest 1.0.0 directly references a removed AdvancedAE private mixin accessor, crashing the server when configuring its monitor. Three calls in server and client code now use equivalent AE2 public getters, preserving detection, rendering and quest behavior.
- 修复补丁在缺少可选模组时的加载边界：吞吐量补丁要求 ThroughputQuest 与 AE2 同时存在；动力冲压机空物品粒子补丁严格限定 Create 6.0.10，不改变其他工作盆优化。
  Gate the throughput patch on both ThroughputQuest and AE2. Restrict the empty press-particle fix to Create 6.0.10 without changing other basin optimizations.
- 补齐 HomeEvents 使用但语言文件缺失的 20 个键（三种语言），并修正 Gradle manifest 的 Mixin 配置文件名以匹配实际资源。
  Supply 20 missing HomeEvents translation keys in all three languages and correct the Gradle manifest's mixin configuration filename to match the packaged resource.

### 说明 / Notes

- 网络协议升级为 3.4.8；新版服务器仅接受 3.4.7 或 3.4.8。3.4.7fix 的协议仍是 3.4.7，因此兼容。更早版本不再兼容，其他模组协商、通道方向与必需通道检查均保留。
  The payload protocol is now 3.4.8. Updated servers accept only 3.4.7 or 3.4.8; release 3.4.7fix uses protocol 3.4.7 and remains accepted. Older protocols are no longer supported. Other mods, packet flow checks and required channel validation retain their original rules.
- 本次没有更改数据包格式，也没有修改 NeoForge 版本。新版客户端仍不能绕过旧版服务器自身的严格协议检查。
  Payload formats and the NeoForge version are unchanged. Updated clients cannot bypass strict version checks performed by an older server.

## [3.4.7fix] - 2026-11-16

### 修复 / Fixes

- **工作盆工作时被拆除引发的客户端网络错误**：动力冲压机在工作盆上运行的瞬间，如果下方工作盆被拆除，服务端会把数量为 0 的物品写进方块实体的粒子同步包；客户端解析为空物品栈，创建粒子时抛出 `IllegalArgumentException: Empty stacks are not allowed`，直接掉线。现在同步前会先把空物品栈从粒子列表里剔除。
  **Client network error when a basin is destroyed mid-press**: if the basin under a mechanical press is destroyed at the moment the press runs, the server writes an item stack with count 0 into the block entity's particle sync packet. The client parses it as an empty stack and throws `IllegalArgumentException: Empty stacks are not allowed`, dropping the connection. Fixed: empty stacks are now stripped from the particle list before the packet is sent.
- 同步修复了 `PressingBehaviour` 中因物品被消耗而残留在粒子列表里的空物品栈（同一根因）。
  Also fixed leftover empty stacks in `PressingBehaviour` after an item is consumed (same root cause).

### 说明 / Notes

- 网络通道协商版本保持 `3.4.7` 不变，旧客户端仍可连接；模组列表显示 `3.4.7fix`。
  The payload channel version stays at `3.4.7`, so older clients can still connect; the mod list shows `3.4.7fix`.

## [3.4.6-fix] - 2026-11-06

### 修复

- 管理界面的「新兑换码」「增加经验值」「作废」「删除」等文字硬编码为中文，英文环境下不翻译。
- 垃圾桶的翻页控件（上一页 / 下一页 / 页码）同样硬编码。
- 「点击打开垃圾桶」的点击事件仍指向旧命令名 `/cir trashbin`，点击报「指令的引数不正确」。
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
