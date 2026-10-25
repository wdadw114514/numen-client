package com.dwinovo.numen.client.bot;

import com.dwinovo.numen.pathing.body.Effector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;

/**
 * 假人的两只手：左键发原版挖掘的 START/STOP 包，右键发 UseItemOn 包。服务器按它自己的规则验证距离与时间。
 */
public final class BotHands implements Effector {

    private final Player player;
    private final BotConnection connection;
    private BlockPos digging;
    private int ticks;
    private int need;
    private int sequence;

    BotHands(Player player, BotConnection connection) {
        this.player = player;
        this.connection = connection;
    }

    @Override
    public Strike dig(BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Direction face = face(hit.getDirection());
        if (!pos.equals(digging)) {
            digging = pos.immutable();
            ticks = 0;
            net.minecraft.world.level.block.state.BlockState state = player.level().getBlockState(pos);
            need = Math.max(1, com.dwinovo.numen.pathing.plan.DigTime.ticks(
                    com.dwinovo.numen.pathing.body.Snapshots.of(player), player.getMainHandItem(), state,
                    player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER), player.onGround()));
            connection.send(new ServerboundPlayerActionPacket(PlayerAction.START_DIGGING,
                    Vector3i.from(pos.getX(), pos.getY(), pos.getZ()), face, sequence++));
        }
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (++ticks >= need) {
            connection.send(new ServerboundPlayerActionPacket(PlayerAction.FINISH_DIGGING,
                    Vector3i.from(pos.getX(), pos.getY(), pos.getZ()), face, sequence++));
            digging = null;
            return new Strike.Broke(pos.immutable(), player.level().getBlockState(pos));
        }
        return Strike.SWINGING;
    }

    @Override
    public void release() {
        if (digging == null) {
            return;
        }
        connection.send(new ServerboundPlayerActionPacket(PlayerAction.CANCEL_DIGGING,
                Vector3i.from(digging.getX(), digging.getY(), digging.getZ()), Direction.UP, sequence++));
        digging = null;
        ticks = 0;
    }

    @Override
    public Use use(BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        connection.send(new ServerboundUseItemOnPacket(
                Vector3i.from(pos.getX(), pos.getY(), pos.getZ()),
                face(hit.getDirection()), Hand.MAIN_HAND,
                (float) (hit.getLocation().x - pos.getX()),
                (float) (hit.getLocation().y - pos.getY()),
                (float) (hit.getLocation().z - pos.getZ()),
                false, false, sequence++));
        return Use.NOTHING;
    }

    @Override
    public BlockPos pressing() {
        return digging;
    }

    private static Direction face(net.minecraft.core.Direction direction) {
        return Direction.VALUES[direction.get3DDataValue()];
    }
}
