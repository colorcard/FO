package com.fo.addon.utils;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.phys.Vec3;

/**
 * GrimV3 方块破坏 sequence 补偿管理器。
 *
 * 核心原理（移植自 slimefun-helper FakeBlockManager）:
 *  GrimV3 用 sequence number 跟踪客户端方块破坏预测。当我们想"假装正在挖"
 *  某个准星没对着的方块时，发 STOP_DESTROY_BLOCK 包并带正确 sequence + 方向，
 *  服务器 ACK 后认为预测合法。
 */
public final class FakeBlockManager {
    private static final Minecraft mc = Minecraft.getInstance();

    /** sequence -> 假装在挖的方块 */
    public static final Int2ObjectOpenHashMap<BlockPos> fakeMining = new Int2ObjectOpenHashMap<>(8);

    private FakeBlockManager() {}

    /**
     * 对指定方块发一次"假破坏补偿"包。
     */
    public static void addFakeCompensate(BlockPos pos) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;

        Direction dir = Direction.getApproximateNearest(mc.player.getEyePosition().subtract(Vec3.atCenterOf(pos)));
        int seq = nextSequence();
        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, dir, seq));
        fakeMining.put(seq, pos);
    }

    /** 收到服务器 ACK（PlayerActionResponseS2CPacket）后调用，清理已确认的 sequence */
    public static void onAck(int serverSequence) {
        if (fakeMining.isEmpty()) return;
        var it = fakeMining.int2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getIntKey() <= serverSequence) it.remove();
        }
    }

    private static int nextSequence() {
        var re = mc.level.getBlockStatePredictionHandler().startPredicting();
        int seq = re.currentSequence();
        re.close();
        return seq;
    }
}
