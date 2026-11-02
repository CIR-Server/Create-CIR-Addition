# 贡献指南

## 在动手之前

这是一个**服务端优化模组**，所有改动都会跑在别人的服务器上。请记住两条底线：

1. **不能改变 Create 的原有行为。** 优化只能是「少做重复的事」，不能是「少做该做的事」。
   如果某个优化会让红石信号晚一拍、让传送带慢一格，那它就不该进主干。
2. **不确定的优化宁可不做。** 宁可慢一点，也不要引入难以复现的时序问题。

## 报告问题

请使用 [Issue 模板](.github/ISSUE_TEMPLATE/bug_report.yml) 提交，并尽量包含：

- 完整的 `logs/latest.log`（不是截图）
- 服务端 MSPT 数据（`/spark tps` 或 F3 的调试信息）
- 复现步骤
- 相关的其他模组及版本

**不要**贴无关的聊天记录或服务器地址。

## 提交代码

### 分支

- `main` 保持随时可发布
- 功能开发走 `feature/<描述>`
- 修复走 `fix/<描述>`

### 代码风格

- Java 21，目标 Java 21 字节码
- 4 空格缩进（配置文件里的示例是 4 空格；`.java` 里也用 4 空格）
- 类名 PascalCase，方法/字段 camelCase，常量 UPPER_SNAKE_CASE
- Mixin 的 `@Unique` 成员统一加 `cir$` 前缀，避免和 Create 的字段撞名

### 日志

**`LOGGER` 里不允许出现非 ASCII 字符。**

服务端可能跑在 Linux 上，中文日志在 GBK 终端里会变成乱码，排查问题时会很痛苦。
玩家可见的文本一律走语言文件（`assets/create_cir_addition/lang/`）。

```java
// 对
CreateCIRAddition.LOGGER.info("Create CIR Addition: Base initialized at {}", pos);

// 错
CreateCIRAddition.LOGGER.info("Create CIR Addition: 基地已初始化到 {}", pos);
```

### Mixin 规则

1. **所有注入必须写 `require = 0`**
   Create 升级时方法签名可能变化，`require = 0` 让注入失败时静默跳过而不是崩服。

2. **优先用 `@Mixin(targets = "...", remap = false)` 字符串形式**
   这样编译期不需要 Create 的类，也避免映射问题。

3. **可选依赖必须门控**
   如果 mixin 的目标类来自可选模组（比如 AE2），在 `CirMixinPlugin.shouldApplyMixin` 里判断，
   不装时整类不加载。

4. **捕获所有异常并退回原逻辑**
   ```java
   try {
       // 优化逻辑
   } catch (Throwable t) {
       // 什么都不做，让原方法正常执行
   }
   ```

### 语言文件

新增玩家可见文本时，**三个语言文件都要加**，键名必须完全一致：

```
assets/create_cir_addition/lang/en_us.json   ← 默认
assets/create_cir_addition/lang/zh_cn.json
assets/create_cir_addition/lang/zh_tw.json
```

不要用 `.displayName("中文")` 硬编码。

### 配置文件

新增配置项时：

- 写在 `CirConfig` 里，加 `BUILDER.comment(...)` 说明用途
- 注释用英文
- 如果是**运行中途切换会导致状态不一致**的开关，在注释里写明「需要重启」，
  并按 `quantumLimiterEnabled` 的方式做启动时缓存

## 测试

提交 PR 前请至少验证：

1. **客户端能启动**，模组列表显示正确版本
2. **服务端能启动**，`Scanned 0 target(s)` 不出现
3. 你改动的那部分功能实际跑一遍
4. `logs/latest.log` 里没有本模组的 ERROR

如果改的是性能相关代码，附上一份 Spark 采样对比会很有说服力。

## 授权

提交贡献即表示你同意你的代码以本项目的许可证（All Rights Reserved）发布，
并授权项目维护者在必要时调整许可证。
