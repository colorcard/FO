# 26.3 移植与上游更新说明

## 本仓库结构

- `port/26.3`：当前工作分支，基于上游 **6.6** tag 移植到 **MC 26.3**，对应你提供的 `FO-6.6-1.21.11.jar`。
- `main`：上游 `jialebot6666/FO` 的 `main` 镜像（当前为 **6.7p36**，只读参考，未移植）。
- `upstream`：`https://github.com/jialebot6666/FO.git`，用于拉取上游后续更新。
- `itest/`：26.3 运行时冒烟测试 mod，不随正式 jar 发布。

> ⛔ 未经用户明确命令，禁止 `git push`、创建公开 fork、GitHub Release 或任何公开发布。
> 本地已安装 `.git/hooks/pre-push` 拦截推送。

## 为什么先按 6.6 而不是最新 main

你提供的 jar 是 `FO-6.6-1.21.11.jar`，本移植以 tag **6.6** 为基线，保证功能与 jar 对齐。上游 `main` 当前是 **6.7p36**，6.6 → 6.7p36 的主要增量是：

- 新增 `modules/StashMover.java`（约 5300 行）及 `Stash*` / `PeerMessage*` 等工具与测试；
- `FOKillAura`、`AncientChestStore`、`FOAncientCitySearch`、`BaritonePathManager` 的小幅改动；
- 合计约 8k 行 diff。

这部分是 6.6 之后的新功能，不属于“把 6.6 jar 移植到 26.3”的验收范围，所以当前交付保持 6.6 基线。需要升级到最新功能时，按下面流程做即可。

## 以后更新上游

```bash
cd /home/colorcard/fomod

# 1. 拉取上游最新提交（浅克隆也能增量 fetch）
git fetch upstream

# 2. 看看 6.6 -> 最新 main 改了什么
git log --oneline 6.6..upstream/main -- fo/src
git diff --stat 6.6 upstream/main -- fo/src

# 3. 在 port/26.3 上合并/挑选需要的提交
git switch port/26.3
git merge upstream/main       # 或按功能 cherry-pick
# 解决 26.3 API 差异（参考本次已移植的 17 个文件）

# 4. 构建 + 单测
cd fo
JAVA_HOME=/path/to/jdk-25 ./gradlew build
cd ..

# 5. 运行时冒烟测试
./itest/build.sh
# 按 itest/README.md 放进 26.3 测试实例，确认 FO_INTEGRATION_TESTS_PASSED

# 6. 本地提交（不要 push）
git add -A
git commit -m "port: sync upstream <版本> to 26.3"
```

## 版本号约定

- 当前产物：`FO-6.6-26.3.jar`（`mod-version = 6.6` + `minecraft = 26.3`）。
- 如果合并 6.7p36，把 `fo/gradle/libs.versions.toml` 的 `mod-version` 改成对应版本，产物名会自动变成 `FO-<版本>-26.3.jar`。
- `fabric.mod.json` 的 `version` 保持纯 mod 版本（不带 `-26.3`）。
