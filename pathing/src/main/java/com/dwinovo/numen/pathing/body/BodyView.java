package com.dwinovo.numen.pathing.body;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The body a companion drives. On a dedicated server this is the fake
 * {@code NumenPlayer}; on a client it is a thin wrapper around the local player.
 *
 * <p>Everything the agent/API layer needs from a body is declared here, so the
 * {@code @Fn} functions and tasks can be written once and run against either
 * implementation. Implementations that already inherit the vanilla methods
 * ({@code Player}/{@code LivingEntity}) only need to provide {@link #player()}
 * and the companion specific bits.
 */
public interface BodyView {

    /** The vanilla entity behind this body. */
    Player player();

    Level level();

    Controls controls();

    /** 这具身体的两只手:服务端是原版挖掘/交互循环,客户端是协议发包。 */
    default Effector hands() {
        throw new UnsupportedOperationException("this body has no hands");
    }

    /** 把主背包第 {@code slot} 格拿到主手;客户端身体由协议连接实现。 */
    default java.util.Optional<BodyAction> hold(int slot) {
        return java.util.Optional.empty();
    }

    /** 右键 {@code pos} 这一格(原版 {@code useItemOn} 那条路);客户端身体由协议连接实现。 */
    default boolean useItemOn(BlockPos pos, InteractionHand hand) {
        if (player() instanceof ServerPlayer serverPlayer) {
            net.minecraft.world.InteractionResult result = serverPlayer.gameMode.useItemOn(serverPlayer,
                    serverPlayer.level(), serverPlayer.getItemInHand(hand), hand,
                    new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos),
                            net.minecraft.core.Direction.UP, pos, false));
            if (result.consumesAction()) {
                serverPlayer.swing(hand);
            }
            return result.consumesAction();
        }
        return false;
    }

    /** 让一只手拿着 {@code item};客户端身体由协议连接实现。 */
    default Hotbar.Grip grip(Item item) {
        return new Hotbar.Grip(InteractionHand.MAIN_HAND, null);
    }

    <T> T state(Class<T> type, Supplier<T> init);

    UUID getOwnerUuid();

    default boolean isOwnedByPlayer(UUID playerUuid) {
        return playerUuid != null && playerUuid.equals(getOwnerUuid());
    }

    /** 转头看 {@code point}；客户端身体由协议连接同步朝向。 */
    default void lookAt(Vec3 point) {
        if (player() instanceof ServerPlayer serverPlayer) {
            serverPlayer.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, point);
        }
    }

    /** 服务端身体有 server；客户端身体没有，返回 null。 */
    default MinecraftServer server() {
        return level() instanceof ServerLevel serverLevel ? serverLevel.getServer() : null;
    }

    /** 主人的在线玩家实体；客户端身体没有这个概念，返回 null。 */
    default ServerPlayer resolveOwnerPlayer() {
        return null;
    }

    default Component getName() {
        return player().getName();
    }

    /** 一件在她身上用坏的装备:是什么、坏在哪个装备位。 */
    record BrokenGear(Item item, EquipmentSlot slot) {}

    /** 用坏的装备流水读数；客户端身体不追踪，恒为 0。 */
    default long brokenGearMark() {
        return 0L;
    }

    /** 读数 {@code mark} 以来用坏的装备；客户端身体不追踪，恒为空。 */
    default List<BrokenGear> brokenGearSince(long mark) {
        return List.of();
    }

    default boolean isRemoved() {
        return player().isRemoved();
    }

    /** 身体闲下来时解除全部被按住的本能；客户端身体没有这一层。 */
    default void resumeAllReflexes() {
    }

    default Vec3 position() {
        return player().position();
    }

    default BlockPos blockPosition() {
        return player().blockPosition();
    }

    default Vec3 getEyePosition() {
        return player().getEyePosition();
    }

    default double getX() {
        return player().getX();
    }

    default double getY() {
        return player().getY();
    }

    default double getZ() {
        return player().getZ();
    }

    default UUID getUUID() {
        return player().getUUID();
    }

    default int getId() {
        return player().getId();
    }

    default Inventory getInventory() {
        return player().getInventory();
    }

    default ItemStack getMainHandItem() {
        return player().getMainHandItem();
    }

    default ItemStack getOffhandItem() {
        return player().getOffhandItem();
    }

    default boolean onGround() {
        return player().onGround();
    }

    default boolean isUsingItem() {
        return player().isUsingItem();
    }

    default int getAirSupply() {
        return player().getAirSupply();
    }

    default int getMaxAirSupply() {
        return player().getMaxAirSupply();
    }

    default boolean isInWater() {
        return player().isInWater();
    }

    default boolean isInLava() {
        return player().isInLava();
    }

    default void swing(InteractionHand hand) {
        player().swing(hand);
    }

    default Vec3 getDeltaMovement() {
        return player().getDeltaMovement();
    }

    default FoodData getFoodData() {
        return player().getFoodData();
    }

    default AABB getBoundingBox() {
        return player().getBoundingBox();
    }

    default Entity getVehicle() {
        return player().getVehicle();
    }

    default float getYRot() {
        return player().getYRot();
    }

    default float getXRot() {
        return player().getXRot();
    }

    default float getHealth() {
        return player().getHealth();
    }

    default float getMaxHealth() {
        return player().getMaxHealth();
    }

    default double blockInteractionRange() {
        return player().blockInteractionRange();
    }

    default boolean isWithinEntityInteractionRange(Entity entity, double extra) {
        return player().isWithinEntityInteractionRange(entity, extra);
    }

    default String bodyName() {
        return player().getName().getString();
    }
}
