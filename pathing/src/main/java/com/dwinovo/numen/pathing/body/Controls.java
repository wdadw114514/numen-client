package com.dwinovo.numen.pathing.body;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 涓€鍏锋湇鍔＄鍋囩帺瀹剁殑閿洏,涓€鍏疯韩浣撲竴鍓?{@link Body#controls})銆傞敭鎸変笅灏变竴鐩存寜鐫€,鐩村埌鏉惧紑;姣忓埢鐢?{@link Physics#step}
 * 鍦ㄨ韩浣撶殑鐗╃悊姝ヨ繘涔嬪墠钀戒竴娆?鐓у師鐗堝鎴风鎶婃寜閿彉鎴愯韩浣撹緭鍏ョ殑閭ｄ竴濂?{@code KeyboardInput.tick} 涓? * {@code LocalPlayer.aiStep})钀藉埌韬綋涓娾€斺€斿亣鐜╁娌℃湁瀹㈡埛绔?鏈嶅姟绔己鐨勫氨鏄繖涓€娈?
 * <ul>
 *   <li>鍓嶅悗宸﹀彸鏄暟瀛楅敭:{@code zza}銆亄@code xxa} 鍙彇 -1銆?銆?,鐩稿韬綋姝ゅ埢鐨勬湞鍚?韫茬潃鎴栫埇鐫€涔樻綔琛岄€熷害灞炴€?
 *       鐢ㄧ潃鐗╁搧涔?0.2;</li>
 *   <li>璺虫槸鎸変綇:鍘熺増鏈嶅姟绔殑 {@code aiStep} 鎸変綇璺虫椂鑷繁鍒嗗湴闈㈣捣璺炽€佹按閲屼笂娴€佹攢鐖笂鐖?</li>
 *   <li>娼滆浜ょ粰鏈嶅姟绔殑濮垮娍涓?涓嶈蛋鍑鸿竟娌?;鍦ㄦ按閲屾寜鐫€娼滆寰€涓嬫矇(鍘熺増瀹㈡埛绔殑 {@code goDownInWater});</li>
 *   <li>鐤捐窇鐓у師鐗堝鎴风鐨勬潯浠跺紑濮嬩笌涓:鍓嶅啿澶熷ぇ銆佸悆寰楀楗?鎴栬兘椋?銆佹病鐢ㄧ墿鍝併€佹病澶辨槑;鎾炲銆佹澗寮€鍓嶈繘銆侀タ浜嗐€佸湪姘撮潰
 *       涓婂氨鍋溿€傚敮涓€鐨勪笉鍚屾槸鏉惧紑鐤捐窇閿氨鍋滆窇鈥斺€旈敭鐩樹笂瑕佸仠璺戝緱鏉句竴涓嬪墠杩涢敭,杩欓噷鎶婁袱浠朵簨鍚堟垚涓€涓敭;</li>
 *   <li>韬綋鍗″湪鏂瑰潡閲屾椂,鐓у師鐗堝鎴风姣忓埢寰€鏈€杩戠殑绌哄鎺ㄤ竴涓?{@code LocalPlayer.moveTowardsClosestSpace})銆?/li>
 * </ul>
 * 瑙嗚涓嶅湪杩欓噷:鏈濆悜鐢辨墽琛屽眰鐨勭瀯鍑嗙洿鎺ヨ浆銆? */
public final class Controls {

    /** 閿€?*/
    public enum Key {
        FORWARD, BACK, LEFT, RIGHT, JUMP, SNEAK, SPRINT
    }

    /** 鍘熺増瀹㈡埛绔垽"鍓嶅啿澶熷ぇ鎵嶈兘寮€璺?鐨勯棬妲涖€?*/
    private static final double SPRINT_IMPULSE = 0.8;
    /** 鐢ㄧ潃鐗╁搧鏃剁殑璧伴€?{@code LocalPlayer.aiStep})銆?*/
    private static final float USING_ITEM_SLOWDOWN = 0.2F;
    /** 鎸夌潃娼滆鏃舵瘡鍒诲線涓嬫矇澶氬皯(鍘熺増 {@code LivingEntity.goDownInWater})銆?*/
    private static final double SINK = -0.04;
    /** 鍗″湪鏂瑰潡閲屾椂姣忓埢寰€澶栨帹鐨勯€熷害(鍘熺増 {@code LocalPlayer.moveTowardsClosestSpace})銆?*/
    private static final double PUSH_OUT = 0.1;

    private final EnumSet<Key> held = EnumSet.noneOf(Key.class);

    public void press(Key key) {
        held.add(key);
    }

    public void release(Key key) {
        held.remove(key);
    }

    public void set(Key key, boolean down) {
        if (down) {
            held.add(key);
        } else {
            held.remove(key);
        }
    }

    public boolean held(Key key) {
        return held.contains(key);
    }

    /** 鏉惧紑鎵€鏈夐敭銆?*/
    public void releaseAll() {
        held.clear();
    }

    /** 鍋滀笅鑴氭:鏉惧紑鍓嶅悗宸﹀彸銆佽烦涓庣柧璺?娼滆鐓ф棫鎸夌潃(韫插湪杈规部涓婂共娲绘椂鍋滀笅涓嶇珯璧锋潵)銆?*/
    public void stop() {
        held.removeAll(MOVING);
    }

    private static final EnumSet<Key> MOVING = EnumSet.of(Key.FORWARD, Key.BACK, Key.LEFT, Key.RIGHT, Key.JUMP,
            Key.SPRINT);

    /** 鎶婃寜鐫€鐨勯敭钀藉埌韬綋涓娿€傚彧鐢?{@link Physics#step} 璋?姣忓埢涓€娆°€?*/
    public void apply(Player body) {
        pushOutOfBlocks(body);
        float forward = impulse(Key.FORWARD, Key.BACK);
        float left = impulse(Key.LEFT, Key.RIGHT);
        if (body.isCrouching() || body.isVisuallyCrawling()) {
            float slow = (float) body.getAttributeValue(Attributes.SNEAKING_SPEED);
            forward *= slow;
            left *= slow;
        }
        if (body.isUsingItem() && !body.isPassenger()) {
            forward *= USING_ITEM_SLOWDOWN;
            left *= USING_ITEM_SLOWDOWN;
        }
        body.zza = forward;
        body.xxa = left;
        body.setJumping(held(Key.JUMP));
        body.setShiftKeyDown(held(Key.SNEAK));
        sprint(body, forward);
        if (body.isInWater() && held(Key.SNEAK) && body.isAffectedByFluids()) {
            body.setDeltaMovement(body.getDeltaMovement().add(0, SINK, 0));
        }
    }

    private float impulse(Key positive, Key negative) {
        boolean p = held(positive);
        boolean n = held(negative);
        return p == n ? 0 : p ? 1 : -1;
    }

    /** 鍘熺増瀹㈡埛绔殑寮€璺戜笌鍋滆窇銆?*/
    private void sprint(Player body, float forward) {
        boolean forwardImpulse = forward > 1.0E-5F;
        boolean fed = body.getFoodData().getFoodLevel() > 6 || body.getAbilities().mayfly;
        if (!body.isSprinting()) {
            boolean impulse = body.isUnderWater() ? forwardImpulse : forward >= SPRINT_IMPULSE;
            boolean surface = body.isInWater() && !body.isUnderWater();
            if (held(Key.SPRINT) && impulse && fed && !body.isUsingItem() && !body.hasEffect(MobEffects.BLINDNESS)
                    && !body.isPassenger() && !body.isFallFlying() && !surface) {
                body.setSprinting(true);
            }
            return;
        }
        boolean spent = !forwardImpulse || !fed;
        boolean stop = body.isSwimming()
                ? !body.onGround() && !held(Key.SNEAK) && spent || !body.isInWater()
                : spent || body.horizontalCollision && !body.minorHorizontalCollision
                        || body.isInWater() && !body.isUnderWater();
        if (stop || !held(Key.SPRINT)) {
            body.setSprinting(false);
        }
    }

    /** 鍘熺増瀹㈡埛绔?韬綋鑴氬簳鐨勫洓涓鍚勭湅涓€娆?闄峰湪浼氱獟鎭殑鏂瑰潡閲屽氨鏈濇渶杩戠殑绌哄鎺ㄣ€?*/
    private static void pushOutOfBlocks(Player body) {
        if (body.noPhysics) {
            return;
        }
        double reach = body.getBbWidth() * 0.35;
        pushOut(body, body.getX() - reach, body.getZ() + reach);
        pushOut(body, body.getX() - reach, body.getZ() - reach);
        pushOut(body, body.getX() + reach, body.getZ() - reach);
        pushOut(body, body.getX() + reach, body.getZ() + reach);
    }

    private static final Direction[] SIDES = {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};

    private static void pushOut(Player body, double x, double z) {
        BlockPos cell = BlockPos.containing(x, body.getY(), z);
        if (!suffocatesAt(body, cell)) {
            return;
        }
        double inX = x - cell.getX();
        double inZ = z - cell.getZ();
        Direction best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction side : SIDES) {
            double along = side.getAxis().choose(inX, 0.0, inZ);
            double distance = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 - along : along;
            if (distance < bestDistance && !suffocatesAt(body, cell.relative(side))) {
                bestDistance = distance;
                best = side;
            }
        }
        if (best != null) {
            Vec3 motion = body.getDeltaMovement();
            body.setDeltaMovement(best.getAxis() == Direction.Axis.X
                    ? new Vec3(PUSH_OUT * best.getStepX(), motion.y, motion.z)
                    : new Vec3(motion.x, motion.y, PUSH_OUT * best.getStepZ()));
        }
    }

    private static boolean suffocatesAt(Player body, BlockPos cell) {
        AABB box = body.getBoundingBox();
        AABB column = new AABB(cell.getX(), box.minY, cell.getZ(), cell.getX() + 1.0, box.maxY, cell.getZ() + 1.0)
                .deflate(1.0E-7);
        return body.level().collidesWithSuffocatingBlock(body, column);
    }
}

