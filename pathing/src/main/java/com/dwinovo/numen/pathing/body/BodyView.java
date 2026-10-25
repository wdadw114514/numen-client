package com.dwinovo.numen.pathing.body;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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

    <T> T state(Class<T> type, Supplier<T> init);

    UUID getOwnerUuid();

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

    default double distanceTo(Entity entity) {
        return player().distanceTo(entity);
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
