# FO 26.3 集成冒烟测试

这个独立测试 mod 不属于正式发布内容。它会在客户端启动后自动打开名为 `FoTest` 的世界，然后：

1. 枚举 FO 分类下的 9 个模块（含 StashMover 自动搬运）；
2. 对每个模块执行 NBT 序列化/反序列化；
3. 打开模块设置界面（含自定义「附魔书列表」「战果」控件）渲染数帧；
4. 启用 10 tick 再关闭；
5. 验证 26.3 class tweaker（假破坏 sequence）可访问；
6. 验证 Baritone 反射桥可初始化、可 `moveTo` / `stop`；
7. 启动鞘翅采集搜索（小半径）；
8. 启动古城搜索（异步线程/资源读取）；
9. 输出 `FO_INTEGRATION_TESTS_PASSED` 后退出。

## 构建

```bash
cd fo && ./gradlew build
cd ..
./itest/build.sh
```

产物：`itest/build/libs/fo-it.jar`。

## 运行

在一个**测试用**游戏目录中放：

- `FO-6.7p36-26.3.jar`（`fo/build/libs/`）
- `fo-it.jar`
- `meteor-client-26.3-*.jar`
- `fabric-api-0.16x.x+26.3.jar`
- `baritone-fabric-26.3-*.jar`（用于验证 Baritone 反射桥；没有也能跑，但会跳过/失败该步）
- 需要 Xaero 相关功能时再放 `xaerominimap` / `xaeroworldmap`

把任意一个 26.3 世界复制为 `<gameDir>/saves/FoTest`，然后以 JDK 25 启动 26.3 Fabric 客户端。测试 mod 会在主菜单出现后自动打开世界。

参考启动命令（与 HMCL 的 26.3 Fabric 实例一致，使用其 classpath）：

```bash
java -Xmx3G \
  -Djava.library.path="$INSTANCE/natives-linux-x86_64/java" \
  -Dorg.lwjgl.system.SharedLibraryExtractPath="$INSTANCE/natives-linux-x86_64/lwjgl" \
  -cp "$INSTANCE_CLASSPATH" \
  net.fabricmc.loader.impl.launch.knot.KnotClient \
  --username TestPlayer --version 26.3-Fabric \
  --gameDir "$GAMEDIR" --assetsDir "$ASSETS" --assetIndex 34 \
  --uuid 00000000000000000000000000000001 --accessToken 0 \
  --versionType release --width 1280 --height 720 --graphicsBackend opengl
```

无显示器环境可用：

```bash
xvfb-run -a -s '-screen 0 1280x720x24' java ... 
```

## 判定

日志中出现下面这一行即通过：

```text
[FO-IT] FO_INTEGRATION_TESTS_PASSED modules=9
```

若出现 `FO_INTEGRATION_TESTS_FAILED` 或进程退出码非 0，请查看 `logs/latest.log` 中的 `[FO-IT]` 行。
