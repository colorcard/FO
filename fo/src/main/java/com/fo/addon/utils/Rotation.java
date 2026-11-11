package com.fo.addon.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** 旋转请求对象（移植自 meteor-miku util.Rotation） */
public class Rotation {
    float yaw;
    float pitch;
    int priority = 0;
    int waitTime = 100;

    public Rotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Rotation(Vec3 vec3d) {
        Minecraft mc = Minecraft.getInstance();

        float[] angle = this.getRotation(mc.player.position().add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0), vec3d);
        this.yaw = angle[0];
        this.pitch = angle[1];
    }

    public float getYaw() {
        return this.yaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public int getPriority() {
        return this.priority;
    }

    public Rotation setPriority(int priority) {
        this.priority = priority;
        return this;
    }

    public int getWaitTime() {
        return this.waitTime;
    }

    public Rotation setWaitTime(int waitTime) {
        this.waitTime = waitTime;
        return this;
    }

    private float[] getRotation(Vec3 eyesPos, Vec3 vec) {
        double diffX = vec.x - eyesPos.x;
        double diffY = vec.y - eyesPos.y;
        double diffZ = vec.z - eyesPos.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, diffXZ)));
        return new float[]{Mth.wrapDegrees(yaw), Mth.wrapDegrees(pitch)};
    }
}
