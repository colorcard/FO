package com.fo.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/**
 * 旋转管理器 - 统一管理模块的视角旋转（移植自 meteor-miku util.RotationManager）。
 * 基于优先级处理旋转冲突，发送完整位置包更新服务器朝向并同步客户端视角。
 */
public class RotationManager {
    Minecraft mc = Minecraft.getInstance();

    private static volatile RotationManager instance;

    public Rotation currentRotation = null;
    Timer timer = new Timer();

    private RotationManager() {
        MeteorClient.EVENT_BUS.subscribe(this);
        mc = Minecraft.getInstance();
    }

    public static RotationManager getInstance() {
        if (instance == null) {
            synchronized (RotationManager.class) {
                if (instance == null) {
                    instance = new RotationManager();
                }
            }
        }
        return instance;
    }

    /**
     * 注册旋转请求：接受后发送完整位置包并同步摄像机视角。
     */
    public boolean register(Rotation rotation) {
        this.currentRotation = rotation;
        this.timer.reset();
        mc.player
            .connection
            .send(
                new ServerboundMovePlayerPacket.PosRot(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    rotation.getYaw(),
                    rotation.getPitch(),
                    mc.player.onGround(),
                    mc.player.horizontalCollision
                )
            );
        Rotations.setCamRotation(rotation.getYaw(), rotation.getPitch());
        return true;
    }

    /** 同步并重置旋转状态：发送玩家真实朝向并清除当前旋转 */
    public void sync() {
        mc.player
            .connection
            .send(
                new ServerboundMovePlayerPacket.PosRot(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    mc.player.getYRot(),
                    mc.player.getXRot(),
                    mc.player.onGround(),
                    mc.player.horizontalCollision
                )
            );
        this.currentRotation = null;
    }
}
