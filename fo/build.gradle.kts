plugins {
    alias(libs.plugins.fabric.loom)
}

val archivesBaseName = providers.gradleProperty("archives_base_name").get()
val mavenGroup = providers.gradleProperty("maven_group").get()

// 命名准则（见 AGENTS.md「命名规范」）：
//   构建产物名 = FO-<mod版本>-<MC版本>.jar，例如 FO-6.6-26.3.jar
//   mod 版本号不带 V 前缀；MC 版本随 libs.versions.toml 的 minecraft 自动跟随，升 MC 不用改两处。
val modVersion = libs.versions.mod.version.get()
val minecraftVersion = libs.versions.minecraft.get()

base {
    archivesName = archivesBaseName
    version = "$modVersion-$minecraftVersion"
    group = mavenGroup
}

loom {
    accessWidenerPath = file("src/main/resources/fo.classtweaker")
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    maven {
        name = "seedfinding"
        url = uri("https://maven.seedfinding.com/")
    }
    mavenCentral()
}

// Configuration that holds jars to include in the final jar
val extraLibs: Configuration by configurations.creating

dependencies {
    // Fabric / Minecraft 26.3
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    // 26.3 的 ServerLevel.overworld() 签名涉及 Fabric 资源 API；运行环境也已安装 Fabric API。
    implementation("net.fabricmc.fabric-api:fabric-api:0.162.0+26.3")

    // Meteor（26.3 的 Meteor 直接使用官方名字，无需 remap）
    implementation(libs.meteor.client)

    // 26.3 的 Baritone 通过反射调用（BaritonePathManager），无需编译期依赖。

    // Seedfinding (世界种子/结构定位计算库，ElytraCollector 依赖)
    extraLibs("com.seedfinding:mc_biome:1.171.1") { isTransitive = false }
    extraLibs("com.seedfinding:mc_core:1.210.0") { isTransitive = false }
    extraLibs("com.seedfinding:mc_feature:1.171.10") { isTransitive = false }
    extraLibs("com.seedfinding:mc_math:1.171.0") { isTransitive = false }
    extraLibs("com.seedfinding:mc_noise:1.171.1") { isTransitive = false }
    extraLibs("com.seedfinding:mc_seed:1.171.2") { isTransitive = false }
    extraLibs("com.seedfinding:mc_terrain:1.171.1") { isTransitive = false }

    // 测试
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    configurations.implementation.get().extendsFrom(extraLibs)
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get().toInt()))
    }
}

fun toMinecraftCompat(version: String): String {
    val stable = Regex("""^(\d{2})\.([1-9]\d*)(?:\.(\d+))?$""")

    stable.matchEntire(version)?.let {
        val (year, drop, _) = it.destructured
        return "~$year.$drop"
    }

    val pre = Regex("""^(\d{2})\.([1-9]\d*)-pre[-.](\d+)$""")
    pre.matchEntire(version)?.let {
        return version.replace("-pre-", "-pre.")
    }

    val rc = Regex("""^(\d{2})\.([1-9]\d*)-rc[-.](\d+)$""")
    rc.matchEntire(version)?.let {
        return version.replace("-rc-", "-rc.")
    }

    return version
}

tasks {
    processResources {
        // fabric.mod.json 里的版本号用纯 mod 版本（不带 MC 后缀）：
        // 带 "-26.3" 会被 Fabric 的 semver 解析成 6.6 的预发布版，影响依赖判定，没必要。
        val propertyMap = mapOf(
            "version" to modVersion,
            "minecraft_version" to toMinecraftCompat(minecraftVersion),
            "jdk_version" to libs.versions.jdk.get(),
            "loader_version" to libs.versions.fabric.loader.get(),
        )

        inputs.properties(propertyMap)
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(
            listOf(
                "-Xlint:deprecation",
                "-Xlint:unchecked"
            )
        )
    }
}

tasks.jar {
    inputs.property("archivesName", archivesBaseName)

    // 打入 seedfinding 库 (ElytraCollector 运行时需要)
    from(extraLibs.map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/**", "module-info.class")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from("LICENSE") {
        rename { "${it}_$archivesBaseName" }
    }
}

// 供集成测试 mod 编译使用：输出主源码集编译类路径。
tasks.register("printCompileClasspath") {
    doLast {
        println(sourceSets["main"].compileClasspath.asPath)
    }
}
