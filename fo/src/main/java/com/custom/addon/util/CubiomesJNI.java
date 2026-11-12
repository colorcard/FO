package com.custom.addon.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * CubiomesJNI 原生库桥（黑盒域，包名必须保持 com.custom.addon.util ——
 * 原生库导出的 JNI 符号是 Java_com_custom_addon_util_CubiomesJNI_*，改包名会链接失败）。
 * 从 jar 内 /natives/{windows,linux-x86_64,linux-arm64,macos}/ 提取原生库到临时目录后加载；
 * 找不到时回退本地文件/系统库路径。三平台原生库均由 xpple/cubiomes（MC 26.3）构建。
 */
public class CubiomesJNI {
    private static boolean loaded;
    private static Path tempDir;

    public static boolean isLibraryLoaded() {
        return loaded;
    }

    static {
        loaded = false;
        try {
            loadFromJarOrSystem();
            loaded = true;
            System.out.println("[CubiomesJNI] Library loaded successfully");
        } catch (Throwable e) {
            // UnsatisfiedLinkError（例如把 Windows DLL 拿到 Linux 上加载）也是 Error，必须一并兜住，
            // 否则会变成 ExceptionInInitializerError 把整个古城搜索线程带崩。
            System.err.println("[CubiomesJNI] Failed to load library: " + e);
            loaded = false;
        }
    }

    private static void loadFromJarOrSystem() throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            loadWindowsFromJarOrSystem();
        } else if (os.contains("mac")) {
            loadMacFromJarOrSystem();
        } else if (os.contains("linux")) {
            String arch = System.getProperty("os.arch", "").toLowerCase();
            String dir = (arch.contains("aarch64") || arch.contains("arm64"))
                ? "linux-arm64" : "linux-x86_64";
            loadLinuxFromJarOrSystem(dir);
        } else {
            try {
                System.loadLibrary("CubiomesJNI");
                System.out.println("[CubiomesJNI] Loaded from system library path");
            } catch (Throwable t) {
                throw new IOException("当前平台没有随包提供的 CubiomesJNI 原生库: " + os, t);
            }
        }
    }

    private static void loadLinuxFromJarOrSystem(String dir) throws IOException {
        String base = "/natives/" + dir + "/";
        if (CubiomesJNI.class.getResource(base + "libCubiomesJNI.so") == null) {
            try {
                System.loadLibrary("CubiomesJNI");
                System.out.println("[CubiomesJNI] Loaded from system library path");
                return;
            } catch (Throwable t) {
                throw new IOException("Cannot find libCubiomesJNI.so in JAR nor system library path", t);
            }
        }

        tempDir = Files.createTempDirectory("cubiomes");
        tempDir.toFile().deleteOnExit();

        // libCubiomesJNI.so 通过 RUNPATH=$ORIGIN 依赖同目录的 libcubiomes.so，先加载依赖。
        Path depPath = tempDir.resolve("libcubiomes.so");
        copyResource(base + "libcubiomes.so", depPath);
        System.load(depPath.toString());
        System.out.println("[CubiomesJNI] Loaded dependency: " + depPath);

        Path jniPath = tempDir.resolve("libCubiomesJNI.so");
        copyResource(base + "libCubiomesJNI.so", jniPath);
        System.load(jniPath.toString());
        System.out.println("[CubiomesJNI] Loaded from JAR: " + jniPath);
    }

    private static void loadMacFromJarOrSystem() throws IOException {
        String base = "/natives/macos/";
        String dylibName = "libCubiomesJNI.dylib";
        if (CubiomesJNI.class.getResource(base + dylibName) == null) {
            try {
                System.loadLibrary("CubiomesJNI");
                System.out.println("[CubiomesJNI] Loaded from system library path");
                return;
            } catch (Throwable t) {
                throw new IOException("Cannot find libCubiomesJNI.dylib in JAR nor system library path", t);
            }
        }

        tempDir = Files.createTempDirectory("cubiomes");
        tempDir.toFile().deleteOnExit();

        Path jniPath = tempDir.resolve(dylibName);
        copyResource(base + dylibName, jniPath);
        System.load(jniPath.toString());
        System.out.println("[CubiomesJNI] Loaded from JAR: " + jniPath);
    }

    private static void loadWindowsFromJarOrSystem() throws IOException {
        String dllName = "CubiomesJNI.dll";

        if (CubiomesJNI.class.getResource("/natives/windows/" + dllName) == null
            && CubiomesJNI.class.getResource("/" + dllName) == null) {
            String[] candidates = { "./CubiomesJNI", "../CubiomesJNI", "../../CubiomesJNI" };
            for (String cand : candidates) {
                try {
                    System.load(cand);
                    System.out.println("[CubiomesJNI] Loaded from: " + cand);
                    return;
                } catch (UnsatisfiedLinkError e) {
                    // continue
                }
            }
            try {
                System.loadLibrary("CubiomesJNI");
                System.out.println("[CubiomesJNI] Loaded from system library path");
                return;
            } catch (UnsatisfiedLinkError e) {
                throw new IOException("Cannot find CubiomesJNI in JAR nor system library path", e);
            }
        }

        tempDir = Files.createTempDirectory("cubiomes");
        tempDir.toFile().deleteOnExit();

        // 新版 Windows DLL 默认静态链接 cubiomes；若仍有旧版依赖 DLL，则先加载。
        if (CubiomesJNI.class.getResource("/natives/windows/cubiomes.dll") != null) {
            Path depPath = tempDir.resolve("cubiomes.dll");
            copyResource("/natives/windows/cubiomes.dll", depPath);
            System.load(depPath.toString());
            System.out.println("[CubiomesJNI] Loaded dependency: " + depPath);
        }

        Path jniPath = tempDir.resolve(dllName);
        copyResource("/natives/windows/" + dllName, jniPath);
        System.load(jniPath.toString());
        System.out.println("[CubiomesJNI] Loaded from JAR: " + jniPath);
    }

    private static void copyResource(String resource, Path target) throws IOException {
        try (InputStream in = CubiomesJNI.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Cannot find native resource: " + resource);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            target.toFile().deleteOnExit();
        }
    }

    // ---- native 接口（方法名与 DLL 导出一致，不可改名） ----
    public native String getBiomeName(int biomeId);

    public native boolean isViableAncientCity(long generator, int blockX, int blockZ);

    public native boolean isAncientCityAt(long generator, int blockX, int blockZ);

    public native int getBiomeAt(long generator, int blockX, int blockY, int blockZ);

    public native long initGenerator(int versionId, long seed);

    public native void freeGenerator(long generator);

    public native long findAncientCity(long generator, int regionX, int regionZ);

    /** 在给定 region（24×24 区块）内查找古城，返回 int[]{chunkX, chunkZ}；未找到返回 null。 */
    public int[] findCityChunks(long generator, int regionX, int regionZ) {
        long packed = findAncientCity(generator, regionX, regionZ);
        if (packed == 0L) {
            return null;
        }
        int chunkX = (int) (packed >> 32);
        int chunkZ = (int) (packed & 0xFFFFFFFFL);
        return new int[]{chunkX >> 4, chunkZ >> 4};
    }
}
