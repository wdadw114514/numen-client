package com.dwinovo.numen.client.bot;

import com.dwinovo.numen.pathing.body.BodyAction;
import com.dwinovo.numen.pathing.body.BodyView;
import com.dwinovo.numen.pathing.body.Controls;
import com.dwinovo.numen.pathing.body.Effector;
import com.dwinovo.numen.pathing.body.Hotbar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
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

    private final BotPlayer player;
    private final BotConnection connection;
    private final Controls controls = new Controls();
    private final Map<Class<?>, Object> state = new HashMap<>();
    private final BotHands hands;
    private final UUID owner;

    private BotBody(BotPlayer player, BotConnection connection, UUID owner) {
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
        UUID uuid;
        var premium = com.dwinovo.numen.client.bot.PremiumAuth.manager();
        if (premium != null) {
            var profile = premium.getMinecraftProfile().getCached();
            uuid = profile != null ? profile.getId()
                    : UUID.nameUUIDFromBytes(("OfflinePlayer:" + connection.host() + ":" + connection.username())
                    .getBytes(StandardCharsets.UTF_8));
        } else {
            uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + connection.host() + ":" + connection.username())
                    .getBytes(StandardCharsets.UTF_8));
        }
        BotPlayer shadow = new BotPlayer(level, new com.mojang.authlib.GameProfile(uuid, connection.username()));
        shadow.setPos(connection.x(), connection.y(), connection.z());
        shadow.setYRot(connection.yaw());
        shadow.setXRot(connection.pitch());
        shadow.setId(-2_000_000 - Math.abs(uuid.hashCode() % 100_000));
        level.addFreshEntity(shadow);
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
        BotConnection.OpenMenu open = connection.takePendingMenu();
        if (open != null) {
            switch (open.type()) {
                case CRAFTING -> {
                    player.containerMenu = new net.minecraft.world.inventory.CraftingMenu(open.id(),
                            player.getInventory());
                    connection.setMenuId(open.id());
                }
                case GENERIC_9X1 -> openChest(open.id(), 1);
                case GENERIC_9X2 -> openChest(open.id(), 2);
                case GENERIC_9X3 -> openChest(open.id(), 3);
                case GENERIC_9X4 -> openChest(open.id(), 4);
                case GENERIC_9X5 -> openChest(open.id(), 5);
                case GENERIC_9X6 -> openChest(open.id(), 6);
                case SHULKER_BOX -> openShulker(open.id());
                case HOPPER -> openHopper(open.id());
                case FURNACE, BLAST_FURNACE, SMOKER -> openFurnace(open.id(), open.type());
                case ANVIL -> openAccess(open.id(), "anvil");
                case ENCHANTMENT -> openAccess(open.id(), "enchantment");
                case GRINDSTONE -> openAccess(open.id(), "grindstone");
                case LOOM -> openAccess(open.id(), "loom");
                case SMITHING -> openAccess(open.id(), "smithing");
                case CARTOGRAPHY -> openAccess(open.id(), "cartography");
                case STONECUTTER -> {
                    player.containerMenu = new net.minecraft.world.inventory.StonecutterMenu(open.id(),
                            player.getInventory());
                    connection.setMenuId(open.id());
                }
                case BREWING_STAND -> openBrewing(open.id());
                case BEACON -> openBeacon(open.id());
                case GENERIC_3X3 -> {
                    player.containerMenu = new net.minecraft.world.inventory.DispenserMenu(open.id(),
                            player.getInventory(), new net.minecraft.world.SimpleContainer(9));
                    connection.setMenuId(open.id());
                }
                case LECTERN -> {
                    player.containerMenu = new net.minecraft.world.inventory.LecternMenu(open.id());
                    connection.setMenuId(open.id());
                }
                default -> {
                }
            }
        }
        if (connection.takePendingClose()) {
            player.containerMenu = player.inventoryMenu;
            connection.setMenuId(0);
        }
        net.minecraft.world.inventory.AbstractContainerMenu menu = player.containerMenu;
        for (Map.Entry<Integer, net.minecraft.world.item.ItemStack> entry : connection.menuItems().entrySet()) {
            if (entry.getKey() >= 0 && entry.getKey() < menu.slots.size()) {
                var slot = menu.getSlot(entry.getKey());
                slot.container.setItem(slot.getContainerSlot(), entry.getValue());
            }
        }
        if (connection.health() < player.getHealth()) {
            player.hurtTime = 10;
            player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.PLAYER_HURT, net.minecraft.sounds.SoundSource.PLAYERS,
                    0.5F, 1.0F, false);
        }
        player.setHealth(connection.health());
        player.getFoodData().setFoodLevel(connection.food());
        BotConnection.Teleport teleport = connection.takeTeleport();
        if (teleport != null) {
            player.setPos(teleport.x(), teleport.y(), teleport.z());
            player.setYRot(teleport.yaw());
            player.setXRot(teleport.pitch());
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            connection.resetPositionTracking(teleport.x(), teleport.y(), teleport.z(),
                    teleport.yaw(), teleport.pitch());
        }
        if (!connection.dimensionMatches(player.level())) {
            return;
        }
        controls.apply(player);
        // 物理由客户端世界刻驱动；这里按原版 sendPosition 的规则发包
        connection.sendPosition(player);
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

    private void openAccess(int id, String kind) {
        var access = net.minecraft.world.inventory.ContainerLevelAccess.NULL;
        player.containerMenu = switch (kind) {
            case "anvil" -> new net.minecraft.world.inventory.AnvilMenu(id, player.getInventory(), access);
            case "enchantment" -> new net.minecraft.world.inventory.EnchantmentMenu(id, player.getInventory(), access);
            case "grindstone" -> new net.minecraft.world.inventory.GrindstoneMenu(id, player.getInventory(), access);
            case "loom" -> new net.minecraft.world.inventory.LoomMenu(id, player.getInventory(), access);
            case "smithing" -> new net.minecraft.world.inventory.SmithingMenu(id, player.getInventory(), access);
            default -> new net.minecraft.world.inventory.CartographyTableMenu(id, player.getInventory(), access);
        };
        connection.setMenuId(id);
    }

    private void openBrewing(int id) {
        player.containerMenu = new net.minecraft.world.inventory.BrewingStandMenu(id, player.getInventory(),
                new net.minecraft.world.SimpleContainer(5),
                new net.minecraft.world.inventory.SimpleContainerData(2));
        connection.setMenuId(id);
    }

    private void openBeacon(int id) {
        player.containerMenu = new net.minecraft.world.inventory.BeaconMenu(id,
                new net.minecraft.world.SimpleContainer(1),
                new net.minecraft.world.inventory.SimpleContainerData(3),
                net.minecraft.world.inventory.ContainerLevelAccess.NULL);
        connection.setMenuId(id);
    }

    private void openShulker(int id) {
        player.containerMenu = new net.minecraft.world.inventory.ShulkerBoxMenu(id, player.getInventory(),
                new net.minecraft.world.SimpleContainer(27));
        connection.setMenuId(id);
    }

    private void openHopper(int id) {
        player.containerMenu = new net.minecraft.world.inventory.HopperMenu(id, player.getInventory(),
                new net.minecraft.world.SimpleContainer(5));
        connection.setMenuId(id);
    }

    private void openFurnace(int id,
                             org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType type) {
        net.minecraft.world.SimpleContainer container = new net.minecraft.world.SimpleContainer(3);
        net.minecraft.world.inventory.SimpleContainerData data = new net.minecraft.world.inventory.SimpleContainerData(4);
        player.containerMenu = switch (type) {
            case BLAST_FURNACE -> new net.minecraft.world.inventory.BlastFurnaceMenu(id, player.getInventory(),
                    container, data);
            case SMOKER -> new net.minecraft.world.inventory.SmokerMenu(id, player.getInventory(), container, data);
            default -> new net.minecraft.world.inventory.FurnaceMenu(id, player.getInventory(), container, data);
        };
        connection.setMenuId(id);
    }

    private void openChest(int id, int rows) {
        net.minecraft.world.SimpleContainer container = new net.minecraft.world.SimpleContainer(rows * 9);
        player.containerMenu = new net.minecraft.world.inventory.ChestMenu(
                net.minecraft.world.inventory.MenuType.GENERIC_9x3, id, player.getInventory(), container, rows);
        connection.setMenuId(id);
    }

    @Override
    public <T extends net.minecraft.world.entity.Entity> java.util.List<T> nearbyEntities(double radius,
            Class<T> type, java.util.function.Predicate<? super T> filter) {
        double radiusSqr = radius * radius;
        java.util.List<T> out = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.Entity entity : connection.entities().values()) {
            if (type.isInstance(entity) && entity != player
                    && player.distanceToSqr(entity) <= radiusSqr && filter.test(type.cast(entity))) {
                out.add(type.cast(entity));
            }
        }
        return out;
    }

    @Override
    public void clickSlot(int slot, int button, net.minecraft.world.inventory.ClickType type) {
        net.minecraft.world.inventory.AbstractContainerMenu menu = player.containerMenu;
        int size = menu.slots.size();
        net.minecraft.world.item.ItemStack[] before = new net.minecraft.world.item.ItemStack[size];
        for (int i = 0; i < size; i++) {
            before[i] = menu.getSlot(i).getItem().copy();
        }
        net.minecraft.world.item.ItemStack carriedBefore = menu.getCarried().copy();
        menu.clicked(slot, button, type, player);
        java.util.Map<Integer, org.geysermc.mcprotocollib.protocol.data.game.item.HashedStack> changed =
                new java.util.HashMap<>();
        for (int i = 0; i < size; i++) {
            net.minecraft.world.item.ItemStack now = menu.getSlot(i).getItem();
            if (!net.minecraft.world.item.ItemStack.matches(before[i], now)) {
                changed.put(i, BotConnection.toHashedStack(now));
            }
        }
        for (int i = 0; i < size; i++) {
            var slotView = menu.getSlot(i);
            slotView.container.setItem(slotView.getContainerSlot(), before[i]);
        }
        menu.setCarried(carriedBefore);
        connection.clickSlot(slot, button, type, carriedBefore, changed);
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
