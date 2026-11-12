/*
 * JNI bridge for the xpple/cubiomes fork (MC 26.3 support).
 *
 * The original Windows DLL (com.custom.addon.util.CubiomesJNI) exposed these
 * seven methods; this file keeps the same ABI and semantics so the Java side
 * does not need to change. Built for Linux x86_64 by build-linux.sh.
 *
 * cubiomes is MIT licensed, Copyright (c) 2020 Cubitect and contributors.
 */
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

#include "generator.h"
#include "finders.h"

/* Match the old Windows wrapper's version-id mapping where it matters, and
 * use the xpple fork's 26.x enum values for the current game. */
static int map_version(jint version_id)
{
    switch (version_id)
    {
        case 19: return MC_1_19_2;
        case 20: return MC_1_19_4;
        case 21: return MC_1_20_6;
        case 22: return MC_1_21_WD;
        case 23: return MC_1_21_11; /* FO "1.21.x / <26.1" setting */
        case 24: return MC_26_3;    /* FO ">=26.1" setting */
        default: return MC_26_3;
    }
}

static Generator *as_generator(jlong handle)
{
    return (Generator *)(intptr_t)handle;
}

JNIEXPORT jlong JNICALL
Java_com_custom_addon_util_CubiomesJNI_initGenerator
  (JNIEnv *env, jobject self, jint version_id, jlong seed)
{
    (void)env; (void)self;
    Generator *g = (Generator *)malloc(sizeof(Generator));
    if (g == NULL) return 0;

    setupGenerator(g, map_version(version_id), 0);
    applySeed(g, DIM_OVERWORLD, (uint64_t)seed);
    return (jlong)(intptr_t)g;
}

JNIEXPORT void JNICALL
Java_com_custom_addon_util_CubiomesJNI_freeGenerator
  (JNIEnv *env, jobject self, jlong handle)
{
    (void)env; (void)self;
    free(as_generator(handle));
}

JNIEXPORT jint JNICALL
Java_com_custom_addon_util_CubiomesJNI_getBiomeAt
  (JNIEnv *env, jobject self, jlong handle, jint x, jint y, jint z)
{
    (void)env; (void)self;
    Generator *g = as_generator(handle);
    if (g == NULL) return -1;
    /* The Windows wrapper used scale=4. Keep the same call. */
    return getBiomeAt(g, 4, x, y, z);
}

JNIEXPORT jstring JNICALL
Java_com_custom_addon_util_CubiomesJNI_getBiomeName
  (JNIEnv *env, jobject self, jint id)
{
    (void)self;
    const char *name;
    switch (id)
    {
        case 0:   name = "ocean"; break;
        case 1:   name = "plains"; break;
        case 4:   name = "forest"; break;
        case 183: name = "deep_dark"; break;
        case 184: name = "mangrove_swamp"; break;
        case 185: name = "cherry_grove"; break;
        case 186: name = "pale_garden"; break;
        default:  name = "unknown"; break;
    }
    return (*env)->NewStringUTF(env, name);
}

JNIEXPORT jboolean JNICALL
Java_com_custom_addon_util_CubiomesJNI_isViableAncientCity
  (JNIEnv *env, jobject self, jlong handle, jint block_x, jint block_z)
{
    (void)env; (void)self;
    Generator *g = as_generator(handle);
    if (g == NULL) return JNI_FALSE;
    return isViableStructurePos(Ancient_City, g, block_x, block_z, 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_custom_addon_util_CubiomesJNI_isAncientCityAt
  (JNIEnv *env, jobject self, jlong handle, jint block_x, jint block_z)
{
    (void)env; (void)self;
    Generator *g = as_generator(handle);
    if (g == NULL) return JNI_FALSE;

    /* Mirrors the old Windows wrapper: region = floorDiv(block, 24), then
     * compare the chunk part of getStructurePos with the requested chunk. */
    int rx = block_x >= 0 ? block_x / 24 : -(((-block_x) + 23) / 24);
    int rz = block_z >= 0 ? block_z / 24 : -(((-block_z) + 23) / 24);
    Pos pos;
    if (!getStructurePos(Ancient_City, g->mc, g->seed, rx, rz, &pos))
        return JNI_FALSE;
    return ((pos.x >> 4) == (block_x >> 4) && (pos.z >> 4) == (block_z >> 4))
        ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_custom_addon_util_CubiomesJNI_findAncientCity
  (JNIEnv *env, jobject self, jlong handle, jint region_x, jint region_z)
{
    (void)env; (void)self;
    Generator *g = as_generator(handle);
    if (g == NULL) return 0;

    Pos pos;
    if (!getStructurePos(Ancient_City, g->mc, g->seed, region_x, region_z, &pos))
        return 0;

    return (jlong)(((uint64_t)(uint32_t)pos.x << 32) | (uint32_t)pos.z);
}
