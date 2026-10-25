package com.dwinovo.numen.client.bot;

import com.dwinovo.numen.pathing.body.BodyAction;
import com.dwinovo.numen.pathing.body.BodyView;
import com.dwinovo.numen.pathing.body.Controls;
import com.dwinovo.numen.pathing.body.Effector;
import com.dwinovo.numen.pathing.body.Hotbar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 客户端假人的身体：一只挂在本机客户端世界里的影子玩家，物理由客户端刻驱动，动作经独立协议连接发给服务器。
 * AI 的任务层拿到的就是它，而不是本地玩家的角色。
 */
public final class BotBody implements BodyView {

    private final RemotePlayer player;
    private final BotConnection connection;
    private final Controls controls = new Controls();
    private final Map<Class<?>, Object> state = new HashMap<>();
    private final BotHands hands;
    private final UUID owner;

    private BotBody(RemotePlayer player, BotConnection connection, UUID owner) {
        this.player = player;
        this.connection = connection;
        this.owner = owner;
        this.hands = new BotHands(player, connection);
    }

    /** 连接进入 PLAY 之后建一具身体；世界还没加载或连接不在 PLAY 时 null。 */
    public static BotBody create(BotConnection connection) {
        if (connection == null || connection.state() != BotConnection.State.PLAY) {
            return null;
        }
        ClientLevel level = Minecraft.getInstance().level;
        Player local = Minecraft.getInstance().player;
        if (level == null || local == null) {
            return null;
        }
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + connection.username())
                .getBytes(StandardCharsets.UTF_8));
        RemotePlayer shadow = new RemotePlayer(level, new com.mojang.authlib.GameProfile(uuid, connection.username()));
        shadow.setPos(connection.x(), connection.y(), connection.z());
        shadow.setYRot(connection.yaw());
        shadow.setXRot(connection.pitch());
        return new BotBody(shadow, connection, local.getUUID());
    }

    public BotConnection connection() {
        return connection;
    }

    /** 每客户端刻一次：落下键盘输入、跑一遍物理、把位置同步给服务器。 */
    public void tick() {
        if (connection.state() != BotConnection.State.PLAY) {
            return;
        }
        for (Map.Entry<Integer, net.minecraft.world.item.ItemStack> entry : connection.inventory().entrySet()) {
            player.getInventory().setItem(entry.getKey(), entry.getValue());
        }
        controls.apply(player);
        player.tick();
        connection.moveTo(player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), player.onGround());
    }

    @Override
    public Player player() {
        return player;
    }

    @Override
    public Level level() {
        return player.level();
    }

    @Override
    public Controls controls() {
        return controls;
    }

    @Override
    public Effector hands() {
        return hands;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T state(Class<T> type, Supplier<T> init) {
        return (T) state.computeIfAbsent(type, key -> init.get());
    }

    @Override
    public UUID getOwnerUuid() {
        return owner;
    }

    @Override
    public Optional<BodyAction> hold(int slot) {
        if (slot >= 0 && slot < 9) {
            connection.send(new org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player
                    .ServerboundSetCarriedItemPacket(slot));
        }
        return Optional.empty();
    }

    @Override
    public boolean useItemOn(BlockPos pos, InteractionHand hand) {
        hands.use(new BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
        return true;
    }

    @Override
    public void lookAt(Vec3 point) {
        Vec3 eye = player.getEyePosition();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
        player.setXRot(pitch);
    }
}
