# AGENTS.md

## ⛔ 发布限制（用户明确指令，优先级最高）

> 已授权记录（2026-11-12）：
> - 用户明确要求建立公开 fork，已创建 https://github.com/colorcard/FO（`main` + `port/26.3`）。
> - 用户随后明确要求「release 一份」，已发布 https://github.com/colorcard/FO/releases/tag/6.7p36-26.3（`FO-6.7p36-26.3.jar`）。
> - 以上授权仅限本次 fork 与本次 Release；后续新的公开发布仍需逐次授权。

**未经用户明确命令，严禁公开发布本 mod**，包括但不限于：`git push` 到任何公开远端、创建 GitHub Release/fork/公开仓库、上传 jar 到 Modrinth/CurseForge/群/网盘/论坛/社交媒体。允许本地 commit、本地 tag、本地构建和本地测试。需要发布时必须先得到用户逐次明确授权；本节与下方任何发布流程冲突时，以本节为准。

## 26.3 移植分支（当前工作区）

> 本工作区以上游 `jialebot6666/FO` 最新 `main`（**6.7p36**，9 个模块，含 `StashMover`）为基线，已移植到 **MC 26.3 + 官方命名 + Loom 1.18.4 + JDK 25**。
> - 产物：`FO-6.7p36-26.3.jar`；26.1 起游戏不再混淆，构建无需 `mappings`/Yarn/intermediary。
> - Access Widener 已改为 Class Tweaker：`fo/src/main/resources/fo.classtweaker`。
> - Baritone 不再需要编译期 jar：`BaritonePathManager` 全程反射；运行时由 26.3 的 baritone mod 提供。
> - 26.3 运行时冒烟测试：`itest/`（独立测试 mod，不随正式 jar 发布），判定 `FO_INTEGRATION_TESTS_PASSED modules=9`。
> - 古城群系验证三平台均接入 xpple/cubiomes（MC 26.3）：Linux `natives/linux-x86_64/`、Windows `natives/windows/CubiomesJNI.dll`（静态链接）、macOS `natives/macos/libCubiomesJNI.dylib`（universal）；构建/说明见 `fo/natives/README.md`。
> - CI：`.github/workflows/dev_build.yml` 在 Ubuntu / Windows / macOS 上分别重建原生库并跑单测；说明见 `docs/CI.md`。
> - 旧 6.6 移植保留在本地分支 `port/26.3-6.6`，仅作对照；当前交付以本分支为准。
>
> 下方 1.21.11 时代的历史记录保留，用于追溯功能语义；构建/运行参数以本节和「构建与测试」为准。


## 项目

FO — Minecraft **Meteor Client (Fabric) 生存辅助全自动插件**（当前工作区：MC 26.3，模块统一带 `FO ` 前缀、全汉化）。
- 代码根：`fo/`（包 `com.fo.addon`）；`ref-src/` 是本机外部参考源码目录，被 `.gitignore` 排除、不入库
- 当前版本：`mod-version = 6.7p36`（见 `fo/gradle/libs.versions.toml`），产物名 = `FO-6.7p36-26.3.jar`（详见「命名规范」）
- 现有模块（全部注册在 `AddonTemplate.onInitialize()`，统一放进 `Category("FO")`，按注册顺序）：
  1. FO 鞘翅采集（`modules/ElytraCollector`）
  2. FO 自动种树（`modules/AutoTree`）
  3. FO 自动扔垃圾（`modules/AutoTrash`）
  4. FO 自动LogPlus（`modules/AutoLog`）
  5. FO 自动挖沙（`modules/AutoMineSand`）
  6. FO 自动挖矿（`modules/AutoMining`）
  7. FO 杀戮光环（`modules/FOKillAura`）
  8. FO 古城战利品搜索（`ancient/modules/FOAncientCitySearch`，6.3 新增：附魔金苹果/迅捷潜行3 搜索 + Xaero 路径点，半自动）
  9. FO 自动搬运（`modules/StashMover`，6.7 新增：珍珠超穿自动搬运，三种模式——搬运端（双号）/拉珍珠端（双号）/三号模式）
- **历史与详细设计**：见 [`docs/CHANGELOG.md`](docs/CHANGELOG.md)（版本演进）、[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)（模块/线程/古城管线）、[`docs/TESTING.md`](docs/TESTING.md)（单测/集成/CI）、[`docs/STASHMOVER.md`](docs/STASHMOVER.md)（自动搬运配置）、[`docs/CI.md`](docs/CI.md)（CI 矩阵）。

## 硬性规范（每次改动必须执行）
1. **每次改动完成后，都必须创建一个对应的 Git commit**，以便后续追踪和回滚。
   - **推送前必须做一次凭据自检**（仓库是公开的）：`git grep -nE 'ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}'`，
     除文档里的占位符（如 `ghp_xxx`）外不得有任何命中；一旦命中，先吊销 token 再谈推送。
     本地 token 只许放 `.ghtoken`（已在 `.gitignore` 里），用完删掉。
2. **每次改动后，都必须编写或更新相关测试**，并在交付给用户前，确保所有测试和验证全部通过。
3. **前端全中文**：所有用户可见的界面文本（模块名、设置名、设置描述、下拉框选项、开关、按钮、提示消息、通知、聊天输出）一律使用中文，不得出现英文 UI 文本（代码标识符、物品 ID、内部类名除外）。
4. **务实说话**：没从代码里实际看到的、没验证过的，不说。不根据猜测推断功能，不编结论。不确定就说"不知道"或"需要看代码确认"。
5. **先讨论后动手**：涉及功能方案、模块设计、功能取舍、行为变化、默认值调整等方向性决策，必须先给出方案由用户拍板，未经确认不得直接改代码（与 `.github/skills/fo-development/SKILL.md` 铁律 5 一致）。
6. **每次发版在 GitHub 建正式 Release（jar 唯一分发渠道，且仓库里只保留最新版一个 jar）**：
   每个版本（`x.y`）交付时，构建产物 `FO-x.y-<MC版本>.jar` **不再提交到 releases/ 目录**
   （`.gitignore` 已用 `releases/*.jar` 拦截），流程固定为五步：
   ① commit（信息含模块名+摘要）→
   ② 打 tag `x.y`（**不带 V 前缀**，指向该版本 commit）→
   ③ 在 GitHub（jialebot6666/FO）创建正式 Release：标题 `FO x.y-<MC版本>`、说明写该版本更新介绍、
      **附件就是构建产物本身，附件名必须与产物同名**（`FO-<mod版本>-<MC版本>.jar`）。
      ⚠️ **禁止**改名、加 `V`、加短哈希/日期后缀 —— 名字对不上就会被当成另一个文件，新旧两版并存；
   ④ **删除此前所有 Release 里的 `.jar` 附件**（做法见下方「Release 附件清理」），
      保证仓库里有且只有一个 jar，就是本次这个 →
   ⑤ 在 `releases/README.md` 版本清单加一行（版本 | commit | 说明）。
   - ⚠️ 这条**只约束发版**：纯文档 / 纯代码推送但没有新 jar 时，**不要**为了「清 jar」去动已有 Release 附件
     （会把唯一的下载入口删掉，仓库里就没有 jar 了）。
   - ⚠️ 用户口径：旧版 jar 不再提供下载；Release 的历史说明与 tag 保留，**只删 jar 附件**，
     不要为了清 jar 去删整个 Release 或 tag。
   - ⚠️ 历史 jar 仍可在 git 历史里回溯（`releases/FO-Vx.y.jar` 及更早，用 `git show <commit>:<path>`）；
     但「停止把 jar 归档进 releases/」之后的版本没有 git 内副本，删了附件就真的没有了。

7. **仓库拉取一律浅克隆**：新设备/新环境拉取 FO 仓库用 `git clone --depth=1 https://gh-proxy.com/https://github.com/jialebot6666/FO.git`（只拉最新代码，秒级完成；github.com 直连慢且常超时，历史里还保留着 100+ 个 jar 二进制，全量 clone 要很久）。日常开发增量 `git pull` 即可；确需完整历史时才 `git fetch --unshallow`。不要全量 clone。

### Release 附件清理（每次发版必做，硬性规范 6 第 ④ 步）

用 GitHub REST API（token 需要 **Contents: Read and write**）。顺序不能反：**先建好新 Release 并传完附件，再删旧的**。

```bash
# 1) 列出全部 Release（默认只返回 30 条，必须带 per_page=100；超过 100 个再翻页）
curl -s -H "Authorization: Bearer $TOKEN" \
  "https://api.github.com/repos/jialebot6666/FO/releases?per_page=100"

# 2) 对每个 tag_name != 本次版本 的 Release，列出它的附件
curl -s -H "Authorization: Bearer $TOKEN" \
  "https://api.github.com/repos/jialebot6666/FO/releases/{release_id}/assets"

# 3) 逐个删除 .jar 附件（只删 .jar；其它附件如 .sha1 / 说明文件不要动）
curl -s -X DELETE -H "Authorization: Bearer $TOKEN" \
  "https://api.github.com/repos/jialebot6666/FO/releases/assets/{asset_id}"
```

**删完必须校验**（目标输出只有一行，且 tag 是本次版本）：

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "https://api.github.com/repos/jialebot6666/FO/releases?per_page=100" \
  | python -c "import sys,json; [print(r['tag_name'],a['name']) for r in json.load(sys.stdin) for a in r['assets'] if a['name'].endswith('.jar')]"
```

判定标准：① 只有一行；② 文件名 = 本次构建产物名；③ tag = 本次版本。三条都满足才算发版完成。

Windows / PowerShell 等价写法（没有 `python` / `jq` 时用）：

```powershell
$H = @{ Authorization = "Bearer $env:GH_TOKEN"; "User-Agent" = "fo-release" }
$rels = Invoke-RestMethod -Headers $H "https://api.github.com/repos/jialebot6666/FO/releases?per_page=100"
$rels | ForEach-Object { $t = $_.tag_name; $_.assets | Where-Object { $_.name -like '*.jar' } |
    ForEach-Object { "$t`t$($_.name)" } }
```

## 构建与测试

- 构建 + 测试（一条命令）：`cd fo && ./gradlew build`（JUnit 5 随 build 一起跑，交付前必须 0 failures / 0 errors）
- JDK：**25**（MC 26.3 / Loom 1.18 的硬性要求）
  - Linux 侧示例：`JAVA_HOME=/home/colorcard/.local/jdks/jdk-25.0.5.0.3 ./gradlew build`
  - Windows 侧：用带 `javac.exe` 的 JDK 25；Gradle 会按 `libs.versions.toml` 的 `jdk = "25"` 找工具链。
- ⚠️ `fo/gradle.properties` **不要写死** `org.gradle.java.home`；用系统 `JAVA_HOME` 或
  `-Porg.gradle.java.installations.paths=<JDK25路径>` 指定。
- ⚠️ 国内网络：`services.gradle.org` 可能 connection reset；Gradle Wrapper 为 **9.8.1**（Loom 1.18 要求）。
  `github.com` 的 git 传输在本机超时，clone 一律**浅克隆**：`git clone --depth=1 https://gh-proxy.com/https://github.com/jialebot6666/FO.git`
  （硬性规范 7）；提交与发版走 `api.github.com` REST API（见 `docs/scripts/gh-deliver.ps1`）。
- ⚠️ **依赖下载走国内镜像**：`repo1.maven.org` 直连实测仅约 32 KB/s，Loom 冷启动会直接 `SocketTimeoutException: Read timed out`。
  解决办法（用户级配置，**不改仓库文件**）与实测数据见 `docs/windows-build.md` 第 10 节。
- Baritone：26.3 下 FO 不再需要编译期 jar（`BaritonePathManager` 纯反射）；运行时由 26.3 的 baritone mod 提供。
- 产物：`fo/build/libs/FO-6.7p36-26.3.jar`（`FO-<mod版本>-<MC版本>.jar`，两段都从 `fo/gradle/libs.versions.toml` 的 `mod-version` 与 `minecraft` 自动拼）
- 26.3 运行时冒烟测试：`./itest/build.sh` 生成 `itest/build/libs/fo-it.jar`，按 `itest/README.md` 放进测试实例；
  看到 `[FO-IT] FO_INTEGRATION_TESTS_PASSED modules=9` 即通过。
- 交付：最终 jar 必须通过 `present_files` 交给用户

## 命名规范（硬性）

**本节是唯一权威来源**；任何窗口 / 任何 AI 接手本项目，一律按本节执行，不得沿用旧写法。

1. **版本号一律不带 `V` 前缀**。正确写法是 `6.7p36`，不是 `V6.25`。历史遗留的 `V6.25` / `FO-V6.25` 写法已废弃，新版本不得再出现。
2. **构建产物名 = `FO-<mod版本>-<MC版本>.jar`**，例：`mod-version = 6.7p36` + `minecraft = 26.3` → **`FO-6.7p36-26.3.jar`**。
   - mod 版本来自 `fo/gradle/libs.versions.toml` 的 `mod-version`；MC 版本来自同文件的 `minecraft`。
   - 两段都由 `fo/build.gradle.kts` 的 `base { version = "$modVersion-$minecraftVersion" }` 自动拼，**不要在别处写死版本串**；升 MC 版本时只改 `minecraft` 一处，产物名自动跟随。
3. **Git tag = `<mod版本>`**（如 `6.25`），不再用 `V6.25` 或 `FO-V6.25`。
4. **GitHub Release 标题 = `FO <mod版本>-<MC版本>`**（如 `FO 6.7p36-26.3`），**附件名必须与构建产物同名**（`FO-6.7p36-26.3.jar`）。
5. 插件名统一大写 **FO**；mod 显示名（`fabric.mod.json` 的 `name`）保持 `FO`。
6. `fabric.mod.json` 的 `version` 用**纯 mod 版本**（如 `6.25`），**不带 MC 后缀** —— 带 `-26.3` 会被 Fabric 的 semver 解析成 6.7p36 的预发布版，影响依赖判定。该字段由 `fo/build.gradle.kts` 的 `processResources` 注入。
7. mod id（`fabric.mod.json` 的 `id`）按 Fabric 硬性规范保持小写 `fo`；Java 包名 `com.fo.addon` 保持小写（Java 惯例）。

示例对照表：

| 项目 | 旧（已废弃） | 新（唯一正确） |
| --- | --- | --- |
| mod-version | `V6.25` | `6.25` |
| 构建产物 | `FO-V6.25.jar` | `FO-6.7p36-26.3.jar` |
| Git tag | `V6.25` | `6.25` |
| Release 标题 | `FO V6.25` | `FO 6.7p36-26.3` |
| Release 附件 | `FO-V6.25.jar` | `FO-6.7p36-26.3.jar` |

## 详细执行流程

涉及 FO 模块功能开发、Bug 修复、参数调整、测试与交付时，**必须加载 `.github/skills/fo-development/SKILL.md`**，严格按其工作流执行。
