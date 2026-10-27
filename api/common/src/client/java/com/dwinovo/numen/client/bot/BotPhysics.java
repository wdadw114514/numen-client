package com.dwinovo.numen.client.bot;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * 移植自 PrismarineJS/prismarine-physics 的玩家移动（index.js）。
 * 只负责假人的位置/速度积分，方块碰撞查询走客户端世界。
 */
public final class BotPhysics {
    public static final double GRAVITY = 0.08;
    public static final double AIR_DRAG = 1.0 - 0.02;
    public static final double PLAYER_SPEED = 0.1;
    public static final double SPRINT_SPEED = 0.3;
    public static final double SNEAK_SPEED = 0.3;
    public static final double STEP_HEIGHT = 0.6;
    public static final double NEGLIGIBLE_VELOCITY = 0.003;
    public static final double DEFAULT_SLIPPERINESS = 0.6;
    public static final double WATER_INERTIA = 0.8;
    public static final double LAVA_INERTIA = 0.5;
    public static final double LIQUID_ACCELERATION = 0.02;
    public static final double AIRBORNE_INERTIA = 0.91;
    public static final double AIRBORNE_ACCELERATION = 0.02;
    public static final double OUT_OF_LIQUID_IMPULSE = 0.3;
    public static final double FLUID_JUMP_THRESHOLD = 0.4;
    public static final int AUTOJUMP_COOLDOWN = 10;
    public static final double WATER_GRAVITY = 0.02;
    public static final double LAVA_GRAVITY = 0.02;
    public static final double HALF_WIDTH = 0.3;
    public static final double HEIGHT = 1.8;

    public double x;
    public double y;
    public double z;
    public double vx;
    public double vy;
    public double vz;
    public float yaw;
    public float pitch;
    public boolean onGround;
    public boolean isInWater;
    public boolean isInLava;
    public boolean isInWeb;
    public boolean horizontalCollision;
    public boolean verticalCollision;
    public boolean sprinting;
    public boolean sneaking;
    public boolean jumping;
    public boolean flying;
    public double flyingSpeed = 0.05;
    public int jumpTicks;
    public double lavaHeight;
    public boolean jumpQueued;

    private AABB playerBB() {
        return new AABB(x - HALF_WIDTH, y, z - HALF_WIDTH, x + HALF_WIDTH, y + HEIGHT, z + HALF_WIDTH);
    }

    private static AABB offset(AABB bb, double dx, double dy, double dz) {
        return bb.move(dx, dy, dz);
    }

    private static AABB extend(AABB bb, double dx, double dy, double dz) {
        double minX = dx < 0 ? bb.minX + dx : bb.minX;
        double maxX = dx < 0 ? bb.maxX : bb.maxX + dx;
        double minY = dy < 0 ? bb.minY + dy : bb.minY;
        double maxY = dy < 0 ? bb.maxY : bb.maxY + dy;
        double minZ = dz < 0 ? bb.minZ + dz : bb.minZ;
        double maxZ = dz < 0 ? bb.maxZ : bb.maxZ + dz;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static AABB contract(AABB bb, double x, double y, double z) {
        return new AABB(bb.minX + x, bb.minY + y, bb.minZ + z, bb.maxX - x, bb.maxY - y, bb.maxZ - z);
    }

    private static double computeOffsetX(AABB block, AABB other, double offsetX) {
        if (other.maxY > block.minY && other.minY < block.maxY && other.maxZ > block.minZ && other.minZ < block.maxZ) {
            if (offsetX > 0 && other.maxX <= block.minX) offsetX = Math.min(block.minX - other.maxX, offsetX);
            else if (offsetX < 0 && other.minX >= block.maxX) offsetX = Math.max(block.maxX - other.minX, offsetX);
        }
        return offsetX;
    }

    private static double computeOffsetY(AABB block, AABB other, double offsetY) {
        if (other.maxX > block.minX && other.minX < block.maxX && other.maxZ > block.minZ && other.minZ < block.maxZ) {
            if (offsetY > 0 && other.maxY <= block.minY) offsetY = Math.min(block.minY - other.maxY, offsetY);
            else if (offsetY < 0 && other.minY >= block.maxY) offsetY = Math.max(block.maxY - other.minY, offsetY);
        }
        return offsetY;
    }

    private static double computeOffsetZ(AABB block, AABB other, double offsetZ) {
        if (other.maxX > block.minX && other.minX < block.maxX && other.maxY > block.minY && other.minY < block.maxY) {
            if (offsetZ > 0 && other.maxZ <= block.minZ) offsetZ = Math.min(block.minZ - other.maxZ, offsetZ);
            else if (offsetZ < 0 && other.minZ >= block.maxZ) offsetZ = Math.max(block.maxZ - other.minZ, offsetZ);
        }
        return offsetZ;
    }

    private static List<AABB> surroundingBBs(ClientLevel level, AABB query) {
        List<AABB> out = new ArrayList<>();
        int minY = (int) Math.floor(query.minY) - 1;
        int maxY = (int) Math.floor(query.maxY);
        int minZ = (int) Math.floor(query.minZ);
        int maxZ = (int) Math.floor(query.maxZ);
        int minX = (int) Math.floor(query.minX);
        int maxX = (int) Math.floor(query.maxX);
        for (int by = minY; by <= maxY; by++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                for (int bx = minX; bx <= maxX; bx++) {
                    BlockPos pos = new BlockPos(bx, by, bz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    VoxelShape shape = state.getCollisionShape(level, pos);
                    if (shape.isEmpty()) continue;
                    for (AABB box : shape.toAabbs()) {
                        out.add(box.move(bx, by, bz));
                    }
                }
            }
        }
        return out;
    }

    private static boolean doesNotCollide(ClientLevel level, AABB bb) {
        return surroundingBBs(level, bb).isEmpty();
    }

    private static double slipperiness(BlockState state) {
        if (state.is(Blocks.SLIME_BLOCK)) return 0.8;
        if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.FROSTED_ICE)) return 0.98;
        if (state.is(Blocks.BLUE_ICE)) return 0.989;
        return DEFAULT_SLIPPERINESS;
    }

    private static boolean isWater(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER) || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT) || state.is(Blocks.BUBBLE_COLUMN);
    }

    private static boolean isLava(BlockState state) {
        return state.getFluidState().is(FluidTags.LAVA);
    }

    private static double liquidHeightPercent(BlockState state) {
        if (state.isAir()) return 0.0;
        return (state.getFluidState().getAmount() + 1) / 9.0;
    }

    private static double[] scanFluid(ClientLevel level, AABB bb, boolean water) {
        double height = 0;
        boolean found = false;
        for (int by = (int) Math.floor(bb.minY); by <= (int) Math.floor(bb.maxY); by++) {
            for (int bz = (int) Math.floor(bb.minZ); bz <= (int) Math.floor(bb.maxZ); bz++) {
                for (int bx = (int) Math.floor(bb.minX); bx <= (int) Math.floor(bb.maxX); bx++) {
                    BlockPos pos = new BlockPos(bx, by, bz);
                    BlockState state = level.getBlockState(pos);
                    if (!(water ? isWater(state) : isLava(state))) continue;
                    double levelY = by + 1 - liquidHeightPercent(state);
                    if (levelY >= bb.minY) {
                        found = true;
                        height = Math.max(height, levelY - bb.minY);
                    }
                }
            }
        }
        return new double[]{found ? 1 : 0, height};
    }

    private boolean isOnLadder(ClientLevel level) {
        BlockPos pos = BlockPos.containing(x, y, z);
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.LADDER) || state.is(Blocks.VINE) || state.is(Blocks.SCAFFOLDING);
    }

    private void applyHeading(double strafe, double forward, double multiplier) {
        double speed = Math.sqrt(strafe * strafe + forward * forward);
        if (speed < 0.01) return;
        speed = multiplier / Math.max(speed, 1);
        strafe *= speed;
        forward *= speed;
        double a = Math.PI - Math.toRadians(yaw);
        double sin = Math.sin(a);
        double cos = Math.cos(a);
        vx += -(strafe * cos + forward * sin);
        vz += forward * cos - strafe * sin;
    }

    private void moveEntity(ClientLevel level, double dx, double dy, double dz) {
        if (isInWeb) {
            dx *= 0.25;
            dy *= 0.05;
            dz *= 0.25;
            vx = 0;
            vy = 0;
            vz = 0;
            isInWeb = false;
        }

        double oldVelX = dx;
        double oldVelY = dy;
        double oldVelZ = dz;

        if (sneaking && onGround) {
            double step = 0.05;
            for (; dx != 0 && surroundingBBs(level, offset(playerBB(), dx, 0, 0)).isEmpty(); oldVelX = dx) {
                if (dx < step && dx >= -step) dx = 0;
                else if (dx > 0) dx -= step;
                else dx += step;
            }
            for (; dz != 0 && surroundingBBs(level, offset(playerBB(), 0, 0, dz)).isEmpty(); oldVelZ = dz) {
                if (dz < step && dz >= -step) dz = 0;
                else if (dz > 0) dz -= step;
                else dz += step;
            }
            while (dx != 0 && dz != 0 && surroundingBBs(level, offset(playerBB(), dx, 0, dz)).isEmpty()) {
                if (dx < step && dx >= -step) dx = 0;
                else if (dx > 0) dx -= step;
                else dx += step;
                if (dz < step && dz >= -step) dz = 0;
                else if (dz > 0) dz -= step;
                else dz += step;
                oldVelX = dx;
                oldVelZ = dz;
            }
        }

        AABB bb = playerBB();
        AABB query = extend(bb, dx, dy, dz);
        List<AABB> boxes = surroundingBBs(level, query);
        AABB oldBB = bb;

        for (AABB box : boxes) dy = computeOffsetY(box, bb, dy);
        bb = offset(bb, 0, dy, 0);
        for (AABB box : boxes) dx = computeOffsetX(box, bb, dx);
        bb = offset(bb, dx, 0, 0);
        for (AABB box : boxes) dz = computeOffsetZ(box, bb, dz);
        bb = offset(bb, 0, 0, dz);

        if (STEP_HEIGHT > 0 && (onGround || (dy != oldVelY && oldVelY < 0)) && (dx != oldVelX || dz != oldVelZ)) {
            double oldVelXCol = dx;
            double oldVelYCol = dy;
            double oldVelZCol = dz;
            AABB oldBBCol = bb;

            dy = STEP_HEIGHT;
            AABB stepQuery = extend(oldBB, oldVelX, dy, oldVelZ);
            List<AABB> stepBoxes = surroundingBBs(level, stepQuery);
            AABB bb1 = oldBB;
            AABB bb2 = oldBB;
            AABB bbXZ = extend(bb1, oldVelX, 0, oldVelZ);

            double dy1 = dy;
            double dy2 = dy;
            for (AABB box : stepBoxes) {
                dy1 = computeOffsetY(box, bbXZ, dy1);
                dy2 = computeOffsetY(box, bb2, dy2);
            }
            bb1 = offset(bb1, 0, dy1, 0);
            bb2 = offset(bb2, 0, dy2, 0);

            double dx1 = oldVelX;
            double dx2 = oldVelX;
            for (AABB box : stepBoxes) {
                dx1 = computeOffsetX(box, bb1, dx1);
                dx2 = computeOffsetX(box, bb2, dx2);
            }
            bb1 = offset(bb1, dx1, 0, 0);
            bb2 = offset(bb2, dx2, 0, 0);

            double dz1 = oldVelZ;
            double dz2 = oldVelZ;
            for (AABB box : stepBoxes) {
                dz1 = computeOffsetZ(box, bb1, dz1);
                dz2 = computeOffsetZ(box, bb2, dz2);
            }
            bb1 = offset(bb1, 0, 0, dz1);
            bb2 = offset(bb2, 0, 0, dz2);

            double norm1 = dx1 * dx1 + dz1 * dz1;
            double norm2 = dx2 * dx2 + dz2 * dz2;
            if (norm1 > norm2) {
                dx = dx1;
                dy = -dy1;
                dz = dz1;
                bb = bb1;
            } else {
                dx = dx2;
                dy = -dy2;
                dz = dz2;
                bb = bb2;
            }
            for (AABB box : stepBoxes) dy = computeOffsetY(box, bb, dy);
            bb = offset(bb, 0, dy, 0);
            if (oldVelXCol * oldVelXCol + oldVelZCol * oldVelZCol >= dx * dx + dz * dz) {
                dx = oldVelXCol;
                dy = oldVelYCol;
                dz = oldVelZCol;
                bb = oldBBCol;
            }
        }

        x = bb.minX + HALF_WIDTH;
        y = bb.minY;
        z = bb.minZ + HALF_WIDTH;
        horizontalCollision = dx != oldVelX || dz != oldVelZ;
        verticalCollision = dy != oldVelY;
        onGround = verticalCollision && oldVelY < 0;

        BlockPos feet = BlockPos.containing(x, y - 0.2, z);
        BlockState feetState = level.getBlockState(feet);
        if (dx != oldVelX) vx = 0;
        if (dz != oldVelZ) vz = 0;
        if (dy != oldVelY) {
            if (feetState.is(Blocks.SLIME_BLOCK) && !sneaking) vy = -vy;
            else vy = 0;
        }

        AABB contracted = contract(bb, 0.001, 0.001, 0.001);
        for (int by = (int) Math.floor(contracted.minY); by <= (int) Math.floor(contracted.maxY); by++) {
            for (int bz = (int) Math.floor(contracted.minZ); bz <= (int) Math.floor(contracted.maxZ); bz++) {
                for (int bx = (int) Math.floor(contracted.minX); bx <= (int) Math.floor(contracted.maxX); bx++) {
                    BlockState state = level.getBlockState(new BlockPos(bx, by, bz));
                    if (state.is(Blocks.SOUL_SAND)) {
                        vx *= 0.4;
                        vz *= 0.4;
                    } else if (state.is(Blocks.HONEY_BLOCK)) {
                        vx *= 0.4;
                        vz *= 0.4;
                    }
                    if (state.is(Blocks.COBWEB)) isInWeb = true;
                }
            }
        }
    }

    private void moveEntityWithHeading(ClientLevel level, double strafe, double forward) {
        double gravityMultiplier = (vy <= 0 && !flying) ? 1.0 : 1.0;
        if (!flying && (isInWater || isInLava)) {
            double lastY = y;
            double acceleration = LIQUID_ACCELERATION;
            double inertia = isInWater ? WATER_INERTIA : LAVA_INERTIA;
            double horizontalInertia = inertia;

            boolean isFalling = vy <= 0;
            applyHeading(strafe, forward, acceleration);
            moveEntity(level, vx, vy, vz);
            double[] fluid = scanFluid(level, contract(playerBB(), 0.001, 0.001, 0.001), isInWater);
            if (fluid[0] > 0) {
                double gravity = GRAVITY * gravityMultiplier;
                double[] fallingAdjusted = new double[]{isFalling && Math.abs(vy - 0.005) >= 0.003 && Math.abs(vy - gravity / 16) < 0.003 ? -0.003 : vy - gravity / 16};
                if (isInWater) {
                    vx *= horizontalInertia;
                    vy *= WATER_INERTIA;
                    vz *= horizontalInertia;
                    vy = fallingAdjusted[0];
                } else if (lavaHeight <= FLUID_JUMP_THRESHOLD) {
                    vx *= LAVA_INERTIA;
                    vy *= 0.8;
                    vz *= LAVA_INERTIA;
                    vy = fallingAdjusted[0] - gravity / 4;
                } else {
                    vx *= LAVA_INERTIA;
                    vy *= LAVA_INERTIA;
                    vz *= LAVA_INERTIA;
                    vy -= gravity / 4;
                }
            } else {
                vy *= inertia;
                vy -= (isInWater ? WATER_GRAVITY : LAVA_GRAVITY) * gravityMultiplier;
                vx *= horizontalInertia;
                vz *= horizontalInertia;
            }

            if (horizontalCollision && doesNotCollide(level, offset(playerBB(), vx, vy + 0.6 - y + lastY, vz))) {
                vy = OUT_OF_LIQUID_IMPULSE;
            }
        } else {
            double acceleration;
            double inertia;
            BlockPos underPos = BlockPos.containing(x, y - 1, z);
            BlockState blockUnder = level.getBlockState(underPos);
            if (onGround && !blockUnder.isAir()) {
                double attributeSpeed = PLAYER_SPEED;
                if (sprinting) attributeSpeed += SPRINT_SPEED;
                inertia = slipperiness(blockUnder) * 0.91;
                acceleration = attributeSpeed * (0.1627714 / (inertia * inertia * inertia));
                if (acceleration < 0) acceleration = 0;
            } else if (flying) {
                acceleration = sprinting ? flyingSpeed * 2 : flyingSpeed;
                inertia = AIRBORNE_INERTIA;
            } else {
                acceleration = AIRBORNE_ACCELERATION;
                inertia = AIRBORNE_INERTIA;
                if (sprinting) acceleration += AIRBORNE_ACCELERATION * 0.3;
            }

            applyHeading(strafe, forward, acceleration);

            if (!flying && isOnLadder(level)) {
                vx = clamp(-0.15, vx, 0.15);
                vz = clamp(-0.15, vz, 0.15);
                vy = Math.max(vy, sneaking ? 0 : -0.15);
            }

            moveEntity(level, vx, vy, vz);

            if (!flying && isOnLadder(level) && (horizontalCollision || jumping)) {
                vy = 0.2;
            }

            if (flying) {
                vy *= 0.6;
            } else {
                vy -= GRAVITY * gravityMultiplier;
                vy *= AIR_DRAG;
            }
            vx *= inertia;
            vz *= inertia;
        }
    }

    private static double clamp(double min, double value, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    public void simulate(ClientLevel level, float yaw, float pitch, boolean forward, boolean back,
                         boolean left, boolean right, boolean jump, boolean sneak, boolean sprint) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.sneaking = sneak;
        this.sprinting = sprint;
        this.jumping = jump;

        AABB fluidBB = contract(playerBB(), 0.001, 0.001, 0.001);
        double[] water = scanFluid(level, fluidBB, true);
        isInWater = water[0] > 0;
        double[] lava = scanFluid(level, fluidBB, false);
        isInLava = lava[0] > 0;
        lavaHeight = lava[1];

        if (flying) {
            double vertical = (jump ? 1 : 0) - (sneak ? 1 : 0);
            vy += vertical * flyingSpeed * 3;
        }

        if (Math.abs(vx) < NEGLIGIBLE_VELOCITY) vx = 0;
        if (Math.abs(vy) < NEGLIGIBLE_VELOCITY) vy = 0;
        if (Math.abs(vz) < NEGLIGIBLE_VELOCITY) vz = 0;

        if (!flying && (jump || jumpQueued)) {
            if (jumpTicks > 0) jumpTicks--;
            if (isInWater || isInLava) {
                vy += 0.04;
            } else if (onGround && jumpTicks == 0) {
                BlockPos below = BlockPos.containing(x, y - 0.5, z);
                BlockState belowState = level.getBlockState(below);
                vy = 0.42 * (belowState.is(Blocks.HONEY_BLOCK) ? 0.4 : 1.0);
                if (sprinting) {
                    double a = Math.PI - Math.toRadians(yaw);
                    vx -= Math.sin(a) * 0.2;
                    vz += Math.cos(a) * 0.2;
                }
                jumpTicks = AUTOJUMP_COOLDOWN;
            }
        } else {
            jumpTicks = 0;
        }
        jumpQueued = false;

        double strafe = (right ? 1 : 0) - (left ? 1 : 0);
        double forwardInput = (forward ? 1 : 0) - (back ? 1 : 0);
        strafe *= 0.98;
        forwardInput *= 0.98;
        if (sneak) {
            strafe *= SNEAK_SPEED;
            forwardInput *= SNEAK_SPEED;
        }

        moveEntityWithHeading(level, strafe, forwardInput);
    }
}
