# FO — Meteor Client 生存辅助插件（MC 26.3）

FO 是 [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) 的 Fabric 客户端插件，提供鞘翅全自动采集、自动种树、自动扔垃圾、自动 LogPlus、自动挖沙、自动挖矿、杀戮光环、古城战利品搜索、自动搬运等功能。

本工作区是 **上游最新 main（6.7p36）从 MC 1.21.11 到 MC 26.3 的移植版**：

- 构建产物：`build/libs/FO-6.7p36-26.3.jar`
- Minecraft：26.3（官方命名，不再使用 Yarn / intermediary）
- Fabric Loader：0.19.5+
- Meteor Client：26.3
- Java：25
- Loom：1.18.4，Gradle 9.8.1
- Access Widener 已迁移为 26.3 Class Tweaker：`src/main/resources/fo.classtweaker`
- Baritone：运行时可选；`BaritonePathManager` 通过反射调用，无需编译期 jar
- 古城群系验证：Linux 使用随包 `natives/linux-x86_64/` 下的 xpple/cubiomes 原生库（支持 MC 26.3），Windows 保留原 DLL

## 构建

```bash
JAVA_HOME=/path/to/jdk-25 ./gradlew build
```

产物在 `build/libs/FO-6.7p36-26.3.jar`。构建会同时运行全部 JUnit 测试（当前 478 个（54 个测试套件），0 failures / 0 errors）。

## 运行

把 `FO-6.7p36-26.3.jar` 与 Meteor Client 26.3、Fabric API 26.3 一起放进 `mods/`。
需要 Baritone 寻路时，再放入 26.3 版 Baritone。

## 26.3 运行时冒烟测试

`itest/` 是一个独立测试 mod，不随正式 jar 发布。它会自动打开测试世界，对 9 个模块（含 StashMover 自动搬运）做序列化、设置界面、启用/关闭，验证 Class Tweaker、Baritone 反射桥、鞘翅搜索和古城搜索。

```bash
./itest/build.sh
```

详细步骤与判定方式见 [`itest/README.md`](../itest/README.md)。

## 开发规范

仓库根目录的 [`AGENTS.md`](../AGENTS.md) 是构建、测试、命名、提交与发版的唯一权威说明。26.3 移植的注意事项在文件顶部的「26.3 移植分支」一节。

## 文档

完整文档索引见 [`../docs/README.md`](../docs/README.md)：架构、变更记录、测试、CI、自动搬运配置。
