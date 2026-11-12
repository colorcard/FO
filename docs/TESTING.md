# 测试指南

## 单元测试

```bash
cd fo
JAVA_HOME=/path/to/jdk-25 ./gradlew test --rerun-tasks --no-build-cache
```

当前规模：**56 个测试套件 / 486 tests / 0 failures / 0 errors**（Linux 本机跳过 8 个 Windows/macOS 条件测试）。

测试主要在 `fo/src/test/java/com/fo/addon/`：

| 目录/文件 | 覆盖内容 |
| --- | --- |
| `ancient/core/` | 古城定位、拼图、战利品表、箱子记录、统计、Xaero 桥 |
| `utils/` 相关测试 | 方向过滤、种植/丢弃/存盒/寻路/旋转/珍珠/搬运等纯逻辑 |
| `CubiomesJniLinuxTest` / `CubiomesJniWindowsTest` / `CubiomesJniMacTest` | 各平台原生库加载、MC 26.3 群系验证、区域找城 |

原则：**能脱离 Minecraft 的判定逻辑尽量放 `utils/` 并写单测**；与客户端时序/世界强耦合的路径靠运行时冒烟测试。

## 原生库测试

```bash
cd fo
JAVA_HOME=/path/to/jdk-25 ./natives/build-linux.sh      # 或 build-windows.sh / build-macos.sh
JAVA_HOME=/path/to/jdk-25 ./gradlew test --tests 'com.fo.addon.CubiomesJni*Test'
```

覆盖：

- `libCubiomesJNI.so` / `libcubiomes.so` 从资源目录加载；
- `initGenerator(24, seed)` 建立 MC 26.3 生成器；
- 已知古城坐标的 `isViableAncientCity` 为 true；
- 纯 Java 古城定位与 xpple/cubiomes 的 26.3 结果一致；
- `findCityChunks` 返回已知区域城市。

## 运行时集成冒烟测试

`itest/` 是独立测试 mod，不随正式 jar 发布。流程见 [`../itest/README.md`](../itest/README.md)。

它会在 26.3 客户端里：

1. 自动打开 `FoTest` 世界；
2. 枚举 9 个 FO 模块；
3. 对每个模块做 NBT 序列化/反序列化、打开设置界面、启用 10 tick、关闭；
4. 验证 Class Tweaker、Baritone 反射桥、AutoMining 带装备启动；
5. 启动鞘翅搜索、古城搜索；
6. 输出 `[FO-IT] FO_INTEGRATION_TESTS_PASSED modules=9`。

构建测试 mod：

```bash
./itest/build.sh
```

## CI

三平台矩阵（Ubuntu / Windows / macOS）见 [`CI.md`](CI.md)。Linux job 会先重建原生库再跑单测。

## 常见注意点

- `gradlew test` 默认走 build cache；改探针/参数时加 `--rerun-tasks --no-build-cache`。
- Gradle 的 `-D` **不会**自动传给 fork 出来的测试 JVM；需要读参数时用环境变量。
- 运行时测试需要真实客户端、Meteor/Fabric API/Baritone/Xaero 和测试世界，目前不在自动 CI 中。
- 涉及世界交互的模块（StashMover、AutoMineSand、AutoTree、AutoTrash）除纯逻辑单测外，仍建议在测试世界手动跑一轮。
