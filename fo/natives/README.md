# Cubiomes 原生库（Linux x86_64）

古城模块的「群系验证」使用 [xpple/cubiomes](https://github.com/xpple/cubiomes)（Cubitect/cubiomes 的活跃 fork，支持 MC 26.3）。

- 上游固定 commit：`8c37ab26f2b47740839eb0045b34efc5141eb065`
- License：MIT（见 cubiomes 仓库 `LICENSE`）
- 产物：
  - `src/main/resources/natives/linux-x86_64/libcubiomes.so`
  - `src/main/resources/natives/linux-x86_64/libCubiomesJNI.so`
- Windows 仍使用原有的 `natives/windows/CubiomesJNI.dll` + `cubiomes.dll`，未改动。

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

`libCubiomesJNI.so` 通过 `RUNPATH=$ORIGIN` 依赖同目录的 `libcubiomes.so`；Java 侧会先把两个文件解压到同一临时目录再依次 `System.load`。

## 重新构建

需要 gcc、cmake、make、curl 和 JDK 25（`jni.h`）：

```bash
cd fo
JAVA_HOME=/path/to/jdk-25 ./natives/build-linux.sh
```

脚本会把 xpple/cubiomes 下载/构建到 `/tmp/opencode/cubiomes-linux-build`，然后重新生成上面两个 `.so`。
可用 `CUBIOMES_BUILD_DIR` 和 `CUBIOMES_COMMIT` 覆盖默认值。

## 测试

```bash
cd fo
JAVA_HOME=/path/to/jdk-25 ./gradlew test --tests com.fo.addon.CubiomesJniLinuxTest
```

测试会验证：

- Linux 原生库能从 jar/资源目录加载；
- `initGenerator(24, seed)` 能建立 MC 26.3 生成器；
- 已知古城坐标的群系验证为 true；
- 纯 Java 古城定位与 xpple/cubiomes 的 26.3 结果一致；
- `findCityChunks` 能返回已知区域的城市区块。
