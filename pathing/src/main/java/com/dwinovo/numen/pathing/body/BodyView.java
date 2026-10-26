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

    /** 右键一个拾取结果(原版 {@code useItemOn} 那条路);客户端身体由协议连接实现。 */
    default net.minecraft.world.InteractionResult useItemOn(
            net.minecraft.world.phys.BlockHitResult hit, InteractionHand hand) {
        if (player() instanceof ServerPlayer serverPlayer) {
            return serverPlayer.gameMode.useItemOn(serverPlayer, serverPlayer.level(),
                    serverPlayer.getItemInHand(hand), hand, hit);
        }
        return net.minecraft.world.InteractionResult.PASS;
    }

    /** 对着空气用一次手上的东西;客户端身体由协议连接实现。 */
    default net.minecraft.world.InteractionResult useItem(InteractionHand hand) {
        if (player() instanceof ServerPlayer serverPlayer) {
            return serverPlayer.gameMode.useItem(serverPlayer, serverPlayer.level(),
                    serverPlayer.getItemInHand(hand), hand);
        }
        return net.minecraft.world.InteractionResult.PASS;
    }

    default void releaseUsingItem() {
        player().releaseUsingItem();
    }

    default float getAttackStrengthScale(float partialTick) {
        return player().getAttackStrengthScale(partialTick);
    }

    default void attack(Entity target) {
        player().attack(target);
    }

    default net.minecraft.world.inventory.AbstractContainerMenu containerMenu() {
        return player().containerMenu;
    }

    default net.minecraft.world.inventory.AbstractContainerMenu inventoryMenu() {
        return player().inventoryMenu;
    }

    /** 点一下物品栏菜单的槽位：服务端身体走原版菜单，客户端假人发点击包。 */
    default void clickSlot(int slot, int button, net.minecraft.world.inventory.ClickType type) {
        player().containerMenu.clicked(slot, button, type, player());
    }

    default void closeContainer() {
    }

    default int getBlockX() {
        return player().getBlockX();
    }

    default int getBlockY() {
        return player().getBlockY();
    }

    default int getBlockZ() {
        return player().getBlockZ();
    }

    default net.minecraft.world.level.ChunkPos chunkPosition() {
        return player().chunkPosition();
    }

    default boolean hasLineOfSight(Entity entity) {
        return player().hasLineOfSight(entity);
    }

    /** 以她的身份发一条服务器命令；客户端假人走协议连接。 */
    default void sendCommand(String command) {
        if (player() instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.sendCommand(command);
        }
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

    default void setYRot(float value) {
        player().setYRot(value);
    }

    default void setYHeadRot(float value) {
        player().setYHeadRot(value);
    }

    default void setYBodyRot(float value) {
        player().setYBodyRot(value);
    }

    default void setXRot(float value) {
        player().setXRot(value);
    }

    default double entityInteractionRange() {
        return player().entityInteractionRange();
    }

    default Vec3 getViewVector(float partialTick) {
        return player().getViewVector(partialTick);
    }

    default Vec3 calculateViewVector(float pitch, float yaw) {
        return player().calculateViewVector(pitch, yaw);
    }

    default net.minecraft.world.phys.HitResult pick(double range, float partialTick, boolean hitFluids) {
        return player().pick(range, partialTick, hitFluids);
    }

    default boolean isPassenger() {
        return player().isPassenger();
    }

    default boolean isAlive() {
        return player().isAlive();
    }

    default boolean isSleeping() {
        return player().isSleeping();
    }

    default double distanceToSqr(double x, double y, double z) {
        return player().distanceToSqr(x, y, z);
    }

    default double distanceToSqr(Entity entity) {
        return player().distanceToSqr(entity);
    }

    default ItemStack getItemInHand(InteractionHand hand) {
        return player().getItemInHand(hand);
    }

    default void setItemInHand(InteractionHand hand, ItemStack stack) {
        player().setItemInHand(hand, stack);
    }

    default ItemStack getItemBySlot(EquipmentSlot slot) {
        return player().getItemBySlot(slot);
    }

    default ItemStack getUseItem() {
        return player().getUseItem();
    }

    default int getUseItemRemainingTicks() {
        return player().getUseItemRemainingTicks();
    }

    default boolean isCrouching() {
        return player().isCrouching();
    }

    default boolean isShiftKeyDown() {
        return player().isShiftKeyDown();
    }

    default void setShiftKeyDown(boolean value) {
        player().setShiftKeyDown(value);
    }

    default boolean isSprinting() {
        return player().isSprinting();
    }

    default void setSprinting(boolean value) {
        player().setSprinting(value);
    }

    default net.minecraft.world.entity.player.Abilities getAbilities() {
        return player().getAbilities();
    }

    default boolean canBreatheUnderwater() {
        return player().canBreatheUnderwater();
    }

    default double getAttributeValue(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        return player().getAttributeValue(attribute);
    }

    default net.minecraft.world.effect.MobEffectInstance getEffect(
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        return player().getEffect(effect);
    }

    default boolean hasEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        return player().hasEffect(effect);
    }

    default net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return player().getDimensions(pose);
    }

    default float maxUpStep() {
        return player().maxUpStep();
    }

    default double getGravity() {
        return player().getGravity();
    }

    default boolean isCreative() {
        return player().isCreative();
    }

    default boolean isSpectator() {
        return player().isSpectator();
    }

    default boolean isFallFlying() {
        return player().isFallFlying();
    }

    default boolean isSwimming() {
        return player().isSwimming();
    }

    default boolean isVisuallyCrawling() {
        return player().isVisuallyCrawling();
    }

    default boolean isUnderWater() {
        return player().isUnderWater();
    }

    default boolean isAffectedByFluids() {
        return player().isAffectedByFluids();
    }

    default boolean hasInfiniteMaterials() {
        return player().hasInfiniteMaterials();
    }

    default net.minecraft.world.item.ItemCooldowns getCooldowns() {
        return player().getCooldowns();
    }

    default void startUsingItem(InteractionHand hand) {
        player().startUsingItem(hand);
    }

    default InteractionHand getUsedItemHand() {
        return player().getUsedItemHand();
    }

    default void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        player().setItemSlot(slot, stack);
    }

    default int tickCount() {
        return player().tickCount;
    }

    default net.minecraft.world.entity.LivingEntity getLastHurtByMob() {
        return player().getLastHurtByMob();
    }

    default void stopUsingItem() {
        player().stopUsingItem();
    }

    default ItemStack getProjectile(ItemStack weapon) {
        return player().getProjectile(weapon);
    }

    /** 半径内的实体（按距离），不含自己；客户端假人用自己的实体表。 */
    default <T extends Entity> java.util.List<T> nearbyEntities(double radius, Class<T> type,
                                                                java.util.function.Predicate<? super T> filter) {
        double radiusSqr = radius * radius;
        return player().level().getEntitiesOfClass(type, player().getBoundingBox().inflate(radius),
                e -> e != player() && player().distanceToSqr(e) <= radiusSqr && filter.test(e));
    }

    default boolean isWithinBlockInteractionRange(BlockPos pos, double extra) {
        return player().isWithinBlockInteractionRange(pos, extra);
    }

    /** 本能是否被按住；客户端身体没有本能层，恒为 false。 */
    default boolean reflexPaused(String reflexId) {
        return false;
    }

    default boolean isEyeInFluid(net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> fluid) {
        return player().isEyeInFluid(fluid);
    }

    default boolean onClimbable() {
        return player().onClimbable();
    }

    default EquipmentSlot getEquipmentSlotForItem(ItemStack stack) {
        return player().getEquipmentSlotForItem(stack);
    }

    default net.minecraft.world.entity.item.ItemEntity drop(ItemStack stack, boolean throwRandomly) {
        return player().drop(stack, throwRandomly);
    }

    default net.minecraft.world.entity.projectile.FishingHook fishing() {
        return player().fishing;
    }

    default net.minecraft.util.RandomSource getRandom() {
        return player().getRandom();
    }

    default Vec3 getLookAngle() {
        return player().getLookAngle();
    }

    default net.minecraft.core.Direction getDirection() {
        return player().getDirection();
    }

    default void stopRiding() {
        player().stopRiding();
    }

    default com.mojang.datafixers.util.Either<net.minecraft.world.entity.player.Player.BedSleepingProblem,
            net.minecraft.util.Unit> startSleepInBed(BlockPos pos) {
        return player().startSleepInBed(pos);
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

    /** 按住一条本能；客户端身体没有这一层。 */
    default void pauseReflex(String reflexId) {
    }

    default boolean isDeadOrDying() {
        return player().isDeadOrDying();
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
