# CI 说明

CI 配置：`.github/workflows/dev_build.yml`

## 矩阵

| 平台 | 内容 |
| --- | --- |
| `ubuntu-latest` | 先构建 Linux x86_64 cubiomes 原生库，再 `./gradlew build`（含 478 个单测 + Linux 原生库测试），上传 jar 和 `.so` |
| `windows-latest` | `gradlew.bat build`，验证 Java 代码在 Windows 下可编译、单测通过 |
| `macos-latest` | `./gradlew build`，验证 Java 代码在 macOS 下可编译、单测通过 |

- JDK：Temurin 25
- Gradle：仓库内 wrapper（9.8.1）
- 触发：push / pull_request（仅 `fo/**`、`itest/**`、`PORTING.md`、workflow 变更）/ 手动 `workflow_dispatch`
- 缓存：`gradle/actions/setup-gradle@v4`
- 测试报告：`fo/build/test-results/test/*.xml`，失败也会上传
- 测试报告始终上传；Linux 额外上传 `FO-native-linux-x86_64`（源码中已包含的原生库，便于排查构建问题）。
- **不上传 jar**：按用户要求，公开仓库的 CI 不发布构建产物 jar；需要 jar 时本地构建。

## Linux 原生库

Linux job 会先运行：

```bash
cd fo
./natives/build-linux.sh
```

因此 CI 测试的是**当前 runner 上重新构建的** `.so`，不是仓库里预编译的二进制；这样可以验证：

- xpple/cubiomes 固定 commit 能正常下载/编译；
- JNI 符号和 Java native 方法匹配；
- `CubiomesJniLinuxTest` 能加载并验证 MC 26.3 古城。

## 本地等价命令

```bash
cd fo
JAVA_HOME=/path/to/jdk-25 ./natives/build-linux.sh
JAVA_HOME=/path/to/jdk-25 ./gradlew build --no-daemon
```

Windows：

```powershell
cd fo
.\gradlew.bat build --no-daemon
```

macOS：

```bash
cd fo
JAVA_HOME=/path/to/jdk-25 ./gradlew build --no-daemon
```

## 未纳入自动 CI 的部分

- `itest/` 需要真实的 MC 26.3 客户端、Meteor/Fabric API/Baritone/Xaero 和测试世界，目前按 `itest/README.md` 手动运行；
  后续可做成独立的 Loom `runClient` 或 GameTest job。
- Windows/macOS 的 cubiomes 原生库构建（当前仓库仍使用原 Windows DLL；macOS 走纯 Java 回退）。
