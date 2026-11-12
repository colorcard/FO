# Cubiomes 原生库（三平台）

古城模块的「群系验证」使用 [xpple/cubiomes](https://github.com/xpple/cubiomes)（Cubitect/cubiomes 的活跃 fork，支持 MC 26.3）。

- 上游固定 commit：`8c37ab26f2b47740839eb0045b34efc5141eb065`
- License：MIT（见 cubiomes 仓库 `LICENSE`）
- 三平台产物：
  - Linux x86_64：`src/main/resources/natives/linux-x86_64/libCubiomesJNI.so` + `libcubiomes.so`
  - Windows x86_64：`src/main/resources/natives/windows/CubiomesJNI.dll`（静态链接 cubiomes，无 `cubiomes.dll` 依赖）
  - macOS universal（x86_64 + arm64）：`src/main/resources/natives/macos/libCubiomesJNI.dylib`

## JNI 兼容性

`src/cubiomes_jni.c` 复刻了原 Windows DLL 暴露的 7 个方法（同名、同签名），并保留其版本 ID 语义：

| Java `versionId` | 映射 |
| --- | --- |
| 19 | MC 1.19.2 |
| 20 | MC 1.19.4 |
| 21 | MC 1.20.6 |
| 22 | MC 1.21 Winter Drop |
| 23（FO `<26.1` 档） | MC 1.21.11 |
| 24（FO `>=26.1` 档） | MC 26.3 |

Java 侧加载逻辑：

- Linux：先加载同目录 `libcubiomes.so`，再加载 `libCubiomesJNI.so`（`RUNPATH=$ORIGIN`）。
- Windows：新版 DLL 已静态链接，只需加载 `CubiomesJNI.dll`；若旧资源里仍有 `cubiomes.dll` 会先加载以兼容。
- macOS：直接加载 universal `libCubiomesJNI.dylib`。

## 重新构建

三平台都从同一个 xpple commit 构建。需要 cmake、make/编译器和 JDK 25（`jni.h`）。

Linux（x86_64 或 arm64 原生 runner）：

```bash
cd fo
JAVA_HOME=/path/to/jdk-25 ./natives/build-linux.sh
```

Windows（MSYS2 MinGW64）：

```bash
cd fo
JAVA_HOME=/path/to/jdk-25 ./natives/build-windows.sh
```

macOS：

```bash
cd fo
JAVA_HOME=/path/to/jdk-25 ./natives/build-macos.sh
```

脚本默认下载/构建到 `/tmp/opencode/cubiomes-*-build`，可用 `CUBIOMES_BUILD_DIR` 和 `CUBIOMES_COMMIT` 覆盖。
CI 会在三平台 runner 上分别重新构建，再跑对应平台的 native 测试。

## 测试

```bash
cd fo
JAVA_HOME=/path/to/jdk-25 ./gradlew test --tests 'com.fo.addon.CubiomesJni*Test'
```

- `CubiomesJniLinuxTest`：Linux x86_64 `.so`
- `CubiomesJniWindowsTest`：Windows `CubiomesJNI.dll`
- `CubiomesJniMacTest`：macOS universal dylib

测试会验证原生库加载、`initGenerator(24, seed)`、已知古城群系验证、纯 Java 定位一致性、`findCityChunks`。
