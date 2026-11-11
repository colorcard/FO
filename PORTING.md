# 26.3 移植与上游更新说明

## 本仓库结构

- `port/26.3`：当前工作分支，**以上游 `jialebot6666/FO` 最新 `main`（6.7p36）为基线**移植到 MC 26.3。
- `port/26.3-6.6`：旧分支，基于 6.6 tag 的移植，仅作对照/回退。
- `main`：上游 `main` 镜像（6.7p36），只读参考。
- `upstream`：`https://github.com/jialebot6666/FO.git`，用于拉取上游后续更新。
- `itest/`：26.3 运行时冒烟测试 mod，不随正式 jar 发布。

> ⛔ 未经用户明确命令，禁止 `git push`、创建公开 fork、GitHub Release 或任何公开发布。
> 本地已安装 `.git/hooks/pre-push` 拦截推送。

## 当前移植范围

- 上游 6.7p36 的全部 9 个模块（含 `StashMover` 自动搬运）。
- 构建：MC 26.3、Fabric Loader 0.19.5+、Meteor Client 26.3、Java 25、Loom 1.18.4、Gradle 9.8.1。
- 26.1 起游戏不再混淆：不再需要 Yarn / intermediary / `mappings`；`modImplementation` 改为普通 `implementation`。
- Access Widener 已迁移为 Class Tweaker：`fo/src/main/resources/fo.classtweaker`。
- Baritone 通过反射调用，不需要编译期 jar。
- 产物：`FO-6.7p36-26.3.jar`。

## 以后更新上游

```bash
cd /home/colorcard/fomod

# 1. 拉取上游最新提交
git fetch upstream

# 2. 查看上游 main 新提交
git log --oneline main..upstream/main
git diff --stat main upstream/main -- fo/src

# 3. 在 port/26.3 上合并/挑选需要的提交
git switch port/26.3
git merge upstream/main       # 或按功能 cherry-pick
# 解决 26.3 API 差异（参考本次已移植的文件）

# 4. 构建 + 单测
cd fo
JAVA_HOME=/path/to/jdk-25 ./gradlew build
cd ..

# 5. 运行时冒烟测试
./itest/build.sh
# 按 itest/README.md 放进 26.3 测试实例，确认 FO_INTEGRATION_TESTS_PASSED modules=9

# 6. 本地提交（不要 push）
git add -A
git commit -m "port: sync upstream <版本> to 26.3"
```

## 版本号约定

- 当前产物：`FO-6.7p36-26.3.jar`（`mod-version = 6.7p36` + `minecraft = 26.3`）。
- 合并新版本后，把 `fo/gradle/libs.versions.toml` 的 `mod-version` 改成对应值，产物名会自动跟随。
- `fabric.mod.json` 的 `version` 保持纯 mod 版本（不带 `-26.3`）。
