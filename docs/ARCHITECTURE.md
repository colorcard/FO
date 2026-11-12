# 架构概览

> 面向接手开发/排查的人。版本演进历史见 [`CHANGELOG.md`](CHANGELOG.md)，测试见 [`TESTING.md`](TESTING.md)。

## 目录结构

| 路径 | 作用 |
| --- | --- |
| `fo/src/main/java/com/fo/addon/` | 主代码（包 `com.fo.addon`） |
| `fo/src/main/java/com/custom/addon/util/` | 原生库 JNI 桥（历史包名，JNI 符号依赖它） |
| `fo/src/main/resources/` | 图标、古城结构/战利品数据、Class Tweaker、原生库 |
| `fo/natives/` | cubiomes JNI 源码与构建脚本 |
| `fo/src/test/java/` | JUnit 5 单测（纯逻辑为主） |
| `itest/` | 独立运行时冒烟测试 mod（不随正式 jar 发布） |
| `docs/` | 本目录：架构、测试、CI、自动搬运、变更记录 |
| `PORTING.md` | 上游同步与 26.3 移植说明 |

## 模块

所有模块注册在 `AddonTemplate.onInitialize()`，分类为 `Category("FO")`。

| 模块 | 主要文件 | 说明 |
| --- | --- | --- |
| FO 鞘翅采集 | `modules/ElytraCollector` | 末地城搜索、飞行、龙头扫描、存鞘翅/补货 |
| FO 自动种树 | `modules/AutoTree` | 树苗/骨粉/种植循环 |
| FO 自动扔垃圾 | `modules/AutoTrash` | 黑白名单、附魔书规则、批量丢弃 |
| FO 自动LogPlus | `modules/AutoLog` | 低血/低Y/传送门/服务器无响应等下线保护 |
| FO 自动挖沙 | `modules/AutoMineSand` | Baritone/Nuker 双模式、补给盒、存沙盒 |
| FO 自动挖矿 | `modules/AutoMining` | 钻石/远古残骸状态机、工作台/潜影盒/末影箱 |
| FO 杀戮光环 | `modules/FOKillAura` | 目标筛选、旋转、切武器、切换回来 |
| FO 古城战利品搜索 | `ancient/modules/FOAncientCitySearch` | 古城定位、群系验证、战利品预测、Xaero 路径点、自动偷取 |
| FO 自动搬运 | `modules/StashMover` | 双号搬运端/拉珍珠端/三号模式；配置见 [`STASHMOVER.md`](STASHMOVER.md) |

纯逻辑通常放在 `utils/`（如 `TrashLogic`、`FacingLogic`、`StashMoveLogic`、`AncientCityLocatorLogic`），
以便不启动 Minecraft 也能单测。

## 线程与事件模型

- **客户端线程**：Meteor 的 `TickEvent`、`Render3DEvent`、`PacketEvent`、`InventoryEvent` 等回调。
  所有 UI、Xaero 路径点、方块交互、移动按键都必须在这里执行。
- **后台线程**：
  - `ElytraCollector` 的末地城搜索线程；
  - `FOAncientCitySearch` 的 `CompletableFuture` 扫描。
  后台线程只做纯计算，不直接写 Xaero/UI/共享容器；结果通过 `pendingMarks` 等队列回到客户端线程。
- **网络线程**：收包回调只记录状态/置标志，真正处理放回 tick 线程（例如 `TeleportFloodDetector`）。
- **原生库**：每次搜索创建独立 `Generator`，`freeGenerator` 释放；不在多个线程共享同一个生成器。

## 古城管线

1. **定位**：`AncientCityLocatorLogic` 用纯 Java 复刻 24×24 区域网格与 `getFeaturePos`，得到候选古城原点/旋转/起始件。
2. **群系验证**：`CubiomesJNI`（Windows DLL / Linux `.so` / macOS 纯 Java 回退）调用 `isViableStructurePos`，过滤实际不会生成的候选。
3. **结构重放**：`AncientJigsawSimulator` 复刻拼图，`AncientLootEvaluator` 按版本选择战利品表并掷骰，得到箱子坐标/物品。
4. **记录与展示**：`AncientChestStore` 按「服务器+维度」持久化；`XaeroWaypointBridge` 写路径点；`Render3DEvent` 画目标柱/高亮。
5. **自动偷取**：`InventoryEvent` 快照容器槽位，tick 线程逐个取；只对命中的箱子生效（默认）。

## 原生库

- Linux x86_64：`natives/linux-x86_64/libCubiomesJNI.so` + `libcubiomes.so`
- Windows x86_64：`natives/windows/CubiomesJNI.dll`（静态链接 cubiomes，无 `cubiomes.dll` 依赖）
- macOS universal（x86_64 + arm64）：`natives/macos/libCubiomesJNI.dylib`
- 三平台均由 xpple/cubiomes（MC 26.3）构建；构建/重新生成见 [`../fo/natives/README.md`](../fo/natives/README.md)。

## 构建与发布

- 构建/测试：`cd fo && ./gradlew build`（JDK 25）
- CI：三平台矩阵 + Linux 原生库，见 [`CI.md`](CI.md)
- 发布：见根 [`AGENTS.md`](../AGENTS.md) 的发布限制与命名规范；**未经用户明确授权不得公开发布**。
