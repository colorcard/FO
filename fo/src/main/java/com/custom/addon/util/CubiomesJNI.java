package com.custom.addon.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * CubiomesJNI 原生库桥（黑盒域，包名必须保持 com.custom.addon.util ——
 * 原生库导出的 JNI 符号是 Java_com_custom_addon_util_CubiomesJNI_*，改包名会链接失败）。
 * 从 jar 内 /natives/windows/（Windows）或 /natives/linux-x86_64/（Linux）提取原生库到临时目录后加载；
 * 找不到时回退本地文件/系统库路径。Linux 原生库由 xpple/cubiomes 构建。
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
        } else if (os.contains("linux")) {
            loadLinuxFromJarOrSystem();
        } else {
            // 其它平台（macOS 等）暂未随包提供原生库，允许系统库兜底。
            try {
                System.loadLibrary("CubiomesJNI");
                System.out.println("[CubiomesJNI] Loaded from system library path");
            } catch (Throwable t) {
                throw new IOException("当前平台没有随包提供的 CubiomesJNI 原生库: " + os, t);
            }
        }
    }

    private static void loadLinuxFromJarOrSystem() throws IOException {
        String base = "/natives/linux-x86_64/";
        if (CubiomesJNI.class.getResource(base + "libCubiomesJNI.so") == null) {
            // 开发环境/系统安装：允许从 java.library.path 或 LD_LIBRARY_PATH 加载。
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

        Path depPath = tempDir.resolve("cubiomes.dll");
        copyResource("/natives/windows/cubiomes.dll", depPath);
        System.load(depPath.toString());
        System.out.println("[CubiomesJNI] Loaded dependency: " + depPath);

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
