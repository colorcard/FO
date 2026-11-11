package com.fo.addon.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 交互转头工具（移植自 Ying / SlimefunHelper 的统一转头模式，三家写法一致）：
 * 1. 记录原视角（SlimefunHelper InteractionTasks 模式）
 * 2. atan2 计算目标角度并 setYaw/setPitch 对准目标（全项目同一公式）
 * 3. 显式发送 LookAndOnGround 视角包，让服务端在处理交互包前先看到新视角（Ying 模式）
 * 4. 执行交互
 * 5. 恢复原视角（SlimefunHelper 模式：避免视角被永久劫持，与移动/挖沙模块打架）
 */
public final class InteractionUtils {

    private static final Minecraft mc = Minecraft.getInstance();

    private InteractionUtils() {
    }

    /** 转头对准方块指定面的面中心并安全交互，交互后恢复原视角 */
    public static void interactBlockSafely(BlockPos pos, Direction face) {
        if (mc.player == null || mc.level == null) return;

        // 1. 记录原视角
        float oldPitch = mc.player.getXRot();
        float oldYaw = mc.player.getYRot();

        // 2. 对准"面中心"（V4.16）：视线与交互点一致，反作弊按视线回溯能命中该面；
        //    面由调用方按六面检测选定（不再固定盒子中心 + UP，顶面被盖/站位刁钻也能开）
        double cx = pos.getX() + 0.5 + face.getStepX() * 0.5;
        double cy = pos.getY() + 0.5 + face.getStepY() * 0.5;
        double cz = pos.getZ() + 0.5 + face.getStepZ() * 0.5;
        float[] fp = FacingLogic.yawPitchTo(
            cx, cy, cz,
            mc.player.getX(), mc.player.getY() + mc.player.getEyeHeight(), mc.player.getZ());
        mc.player.setYRot(fp[0]);
        mc.player.setXRot(fp[1]);

        // 3. 显式发送视角包（先发视角包，服务器处理交互包时才能看到新视角）
        mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(
            fp[0], fp[1], mc.player.onGround(), mc.player.horizontalCollision));

        // 4. 交互（命中点 = 面中心）
        BlockHitResult hit = new BlockHitResult(new Vec3(cx, cy, cz), face, pos, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);

        // 5. 恢复原视角
        mc.player.setYRot(oldYaw);
        mc.player.setXRot(oldPitch);
    }
}
