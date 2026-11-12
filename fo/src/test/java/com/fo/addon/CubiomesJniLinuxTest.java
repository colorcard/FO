package com.fo.addon;

import com.custom.addon.util.CubiomesJNI;
import com.fo.addon.utils.AncientCityLocatorLogic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Linux x86_64 cubiomes JNI 冒烟测试。
 *
 * <p>原生库由 xpple/cubiomes（支持 MC 26.3）构建；这里验证 26.3 生成器能初始化、
 * 能对已知古城做群系验证，并能按区域找到城市。种子/坐标来自
 * {@code AncientCityGoldenTest} 的同一世界种子。
 */
@EnabledOnOs(OS.LINUX)
class CubiomesJniLinuxTest {
    private static final long SEED = -7346913998703726680L;

    @Test
    void loadsNativeLibrary() {
        assertTrue(CubiomesJNI.isLibraryLoaded(), "Linux libCubiomesJNI.so 应能从 jar 内加载");
    }

    @Test
    void validatesKnownAncientCityFor263() {
        CubiomesJNI jni = new CubiomesJNI();
        long g = jni.initGenerator(24, SEED); // 24 = FO 的 >=26.1 档，映射 MC_26_3
        assertNotEquals(0L, g, "initGenerator 应返回非空 Generator");
        try {
            // xpple/cubiomes 在 26.3 下 region(0,-29) 的古城原点；同种子已知可生成。
            // 模块实际探针是 city.blockX()+8，故这里也验证 +8 探针。
            assertTrue(jni.isViableAncientCity(g, 152, -10904));
            assertEquals("deep_dark", jni.getBiomeName(183));
        } finally {
            jni.freeGenerator(g);
        }
    }

    @Test
    void pureJavaLocatorMatchesXppleFor263City() {
        var cities = AncientCityLocatorLogic.findNearby(SEED, 0, 0, 1000);
        assertTrue(cities.stream().anyMatch(c -> c.blockX() == 144 && c.blockZ() == -10912),
            "纯 Java 定位应包含 xpple/cubiomes 26.3 的 region(0,-29) 城市 144,-10912");
    }

    @Test
    void findsAncientCityInRegion() {
        CubiomesJNI jni = new CubiomesJNI();
        long g = jni.initGenerator(24, SEED);
        assertNotEquals(0L, g);
        try {
            int[] city = jni.findCityChunks(g, 0, -29);
            assertNotNull(city, "region(0,-29) 应有古城");
            assertArrayEquals(new int[]{9, -682}, city);
        } finally {
            jni.freeGenerator(g);
        }
    }
}
