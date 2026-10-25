package com.dwinovo.numen.client.bot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.network.session.ClientNetworkSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerCombatKillPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerClosePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetPlayerInventoryPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerStatusOnlyPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 一个独立账号的协议连接（离线登录）。它是"客户端假人"的身体底座：不借用本地玩家的连接，
 * 服务器看到的是一个真正登录进来的玩家。聊天、命令、位置都在这里收发。
 */
public final class BotConnection {

    public enum State { IDLE, CONNECTING, PLAY, DISCONNECTED }

    private static final int MAX_MESSAGES = 200;

    private final String host;
    private final int port;
    private final String username;
    private final String skinUrl;

    private volatile State state = State.IDLE;
    private volatile String lastError = "";
    private final List<String> messages = new CopyOnWriteArrayList<>();
    /** 服务器同步来的玩家背包：Inventory 槽位 → 物品。 */
    private final Map<Integer, net.minecraft.world.item.ItemStack> inventory = new ConcurrentHashMap<>();
    /** 玩家物品栏菜单的原始槽位（0-45）：合成格、盔甲、背包、快捷栏、副手。 */
    private final Map<Integer, net.minecraft.world.item.ItemStack> menuItems = new ConcurrentHashMap<>();
    private volatile int menuStateId;
    private volatile ClientNetworkSession session;
    private volatile int entityId = -1;
    private volatile double x;
    private volatile double y;
    private volatile double z;
    private volatile float yaw;
    private volatile float pitch;
    private volatile boolean onGround;
    private volatile boolean positionKnown;
    private double lastX;
    private double lastY;
    private double lastZ;
    private float lastYaw;
    private float lastPitch;
    private boolean lastOnGround;
    private boolean lastHorizontalCollision;
    private boolean wasSprinting;
    private byte lastInputFlags = -1;
    private int positionReminder;
    private volatile boolean skinProbeSent;
    private volatile boolean skinApplied;
    private volatile boolean skinPluginDetected;
    /** 服务器打开/关闭菜单的待处理事件；客户端主线程取走。 */
    public record OpenMenu(int id, org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType type) {}
    private volatile OpenMenu pendingMenu;
    private volatile boolean pendingClose;
    private volatile int currentMenuId = 0;
    private volatile float healthValue = 20.0F;
    private volatile int foodValue = 20;

    public BotConnection(String host, int port, String username) {
        this(host, port, username, "");
    }

    public BotConnection(String host, int port, String username, String skinUrl) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.skinUrl = skinUrl == null ? "" : skinUrl.strip();
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String username() {
        return username;
    }

    public boolean skinPluginDetected() {
        return skinPluginDetected;
    }

    public String skinUrl() {
        return skinUrl;
    }

    public State state() {
        return state;
    }

    public String lastError() {
        return lastError;
    }

    public int entityId() {
        return entityId;
    }

    public boolean positionKnown() {
        return positionKnown;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public boolean onGround() {
        return onGround;
    }

    public void moveTo(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
        this.positionKnown = true;
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
        this.lastYaw = yaw;
        this.lastPitch = pitch;
        this.lastOnGround = onGround;
        this.lastHorizontalCollision = false;
        this.positionReminder = 0;
    }

    public List<String> messages() {
        return List.copyOf(messages);
    }

    public Map<Integer, net.minecraft.world.item.ItemStack> inventory() {
        return inventory;
    }

    public Map<Integer, net.minecraft.world.item.ItemStack> menuItems() {
        return menuItems;
    }

    public OpenMenu takePendingMenu() {
        OpenMenu menu = pendingMenu;
        pendingMenu = null;
        return menu;
    }

    public boolean takePendingClose() {
        boolean close = pendingClose;
        pendingClose = false;
        return close;
    }

    public int currentMenuId() {
        return currentMenuId;
    }

    public float health() {
        return healthValue;
    }

    public int food() {
        return foodValue;
    }

    public void setMenuId(int id) {
        currentMenuId = id;
        menuItems.clear();
    }

    /** 发一次物品栏点击（合成、搬运都走这里）。 */
    public void clickSlot(int slot, int button, net.minecraft.world.inventory.ClickType type) {
        org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType actionType;
        org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerAction action;
        switch (type) {
            case PICKUP -> {
                actionType = org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType.CLICK_ITEM;
                action = button == 0
                        ? org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction.LEFT_CLICK
                        : org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction.RIGHT_CLICK;
            }
            case QUICK_MOVE -> {
                actionType = org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType.SHIFT_CLICK_ITEM;
                action = org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction.LEFT_CLICK;
            }
            default -> {
                return;
            }
        }
        send(new org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket(
                currentMenuId, menuStateId, slot, actionType, action,
                new org.geysermc.mcprotocollib.protocol.data.game.item.HashedStack(0, 0, java.util.Map.of(),
                        java.util.Set.of()), java.util.Map.of()));
    }

    private void applyMenuContent(org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack[] items) {
        for (int i = 0; i < items.length; i++) {
            applyMenuSlot(i, items[i]);
        }
    }

    /** 玩家物品栏菜单：5-8 甲、9-35 主背包、36-44 快捷栏、45 副手。 */
    private void applyMenuSlot(int slot, org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack item) {
        net.minecraft.world.item.ItemStack stack = toMc(item);
        if (slot >= 0 && slot <= 45) {
            menuItems.put(slot, stack);
        }
        if (slot >= 5 && slot <= 8) {
            inventory.put(39 - (slot - 5), stack);
        } else if (slot >= 9 && slot <= 35) {
            inventory.put(slot, stack);
        } else if (slot >= 36 && slot <= 44) {
            inventory.put(slot - 36, stack);
        } else if (slot == 45) {
            inventory.put(40, stack);
        }
    }

    private static net.minecraft.world.item.ItemStack toMc(
            org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack stack) {
        if (stack == null || stack.getAmount() <= 0) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(stack.getId());
        return item == null ? net.minecraft.world.item.ItemStack.EMPTY
                : new net.minecraft.world.item.ItemStack(item, stack.getAmount());
    }

    public void clearMessages() {
        messages.clear();
    }

    public synchronized void connect() {
        if (state == State.CONNECTING || state == State.PLAY) {
            return;
        }
        state = State.CONNECTING;
        lastError = "";
        messages.clear();
        positionKnown = false;
        MinecraftProtocol protocol = new MinecraftProtocol(username);
        ClientNetworkSession created = ClientNetworkSessionFactory.factory()
                .setAddress(host, port)
                .setProtocol(protocol)
                .create();
        created.addListener(new SessionAdapter() {
            @Override
            public void packetReceived(Session session, Packet packet) {
                if (packet instanceof ClientboundLoginPacket login) {
                    entityId = login.getEntityId();
                    state = State.PLAY;
                    if (!skinProbeSent) {
                        skinProbeSent = true;
                        sendCommand("skin");
                    }
                } else if (packet instanceof ClientboundPlayerPositionPacket position) {
                    x = position.getPosition().getX();
                    y = position.getPosition().getY();
                    z = position.getPosition().getZ();
                    yaw = position.getYRot();
                    pitch = position.getXRot();
                    positionKnown = true;
                    session.send(new ServerboundAcceptTeleportationPacket(position.getId()));
                } else if (packet instanceof ClientboundSystemChatPacket chat) {
                    add(plain(chat.getContent()));
                } else if (packet instanceof ClientboundPlayerChatPacket chat) {
                    Component content = chat.getUnsignedContent();
                    add(content != null ? plain(content) : chat.getContent());
                } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
                    applyChunk(chunk);
                } else if (packet instanceof ClientboundPlayerCombatKillPacket) {
                    add("假人死亡，自动重生");
                    send(new ServerboundClientCommandPacket(
                            org.geysermc.mcprotocollib.protocol.data.game.ClientCommand.RESPAWN));
                } else if (packet instanceof ClientboundRespawnPacket) {
                    positionKnown = false;
                } else if (packet instanceof ClientboundSetHealthPacket health) {
                    healthValue = health.getHealth();
                    foodValue = health.getFood();
                } else if (packet instanceof ClientboundOpenScreenPacket open) {
                    pendingMenu = new OpenMenu(open.getContainerId(), open.getType());
                    menuItems.clear();
                } else if (packet instanceof ClientboundContainerClosePacket close) {
                    pendingClose = true;
                    menuItems.clear();
                } else if (packet instanceof ClientboundSetPlayerInventoryPacket slot) {
                    inventory.put(slot.getSlot(), toMc(slot.getContents()));
                } else if (packet instanceof ClientboundContainerSetContentPacket content) {
                    if (content.getContainerId() == currentMenuId) {
                        menuStateId = content.getStateId();
                        applyMenuContent(content.getItems());
                    }
                } else if (packet instanceof ClientboundContainerSetSlotPacket slot) {
                    if (slot.getContainerId() == currentMenuId) {
                        applyMenuSlot(slot.getSlot(), slot.getItem());
                    }
                } else if (packet instanceof ClientboundDisconnectPacket disconnect) {
                    add(plain(disconnect.getReason()));
                }
            }

            @Override
            public void disconnected(DisconnectedEvent event) {
                state = State.DISCONNECTED;
                lastError = plain(event.getReason());
            }
        });
        session = created;
        created.connect(false);
    }

    public synchronized void disconnect() {
        ClientNetworkSession current = session;
        session = null;
        state = State.IDLE;
        if (current != null) {
            current.disconnect("closed");
        }
    }

    /** 直接发一个协议包（移动、挖掘、右键都由身体层走这里）。 */
    public void send(Packet packet) {
        ClientNetworkSession current = session;
        if (current != null && state == State.PLAY) {
            current.send(packet);
        }
    }

    public void sendCommand(String command) {
        ClientNetworkSession current = session;
        if (current != null && state == State.PLAY && command != null && !command.isBlank()) {
            current.send(new ServerboundChatCommandPacket(command.strip().replaceFirst("^/", "")));
        }
    }

    public void sendChat(String message) {
        ClientNetworkSession current = session;
        if (current != null && state == State.PLAY && message != null && !message.isBlank()) {
            current.send(new ServerboundChatPacket(message, System.currentTimeMillis(), 0L, null, 0,
                    new BitSet(20), 0));
        }
    }

    /** 原版 LocalPlayer.sendPosition 的同一套规则：位移/朝向/落地状态各发各的包，输入变了再发输入包。 */
    public void sendPosition(net.minecraft.world.entity.player.Player player) {
        ClientNetworkSession current = session;
        if (current == null || state != State.PLAY || !positionKnown) {
            return;
        }
        boolean sprinting = player.isSprinting();
        if (sprinting != wasSprinting) {
            current.send(new ServerboundPlayerCommandPacket(entityId,
                    sprinting ? org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState.START_SPRINTING
                            : org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState.STOP_SPRINTING));
            wasSprinting = sprinting;
        }

        double dx = player.getX() - lastX;
        double dy = player.getY() - lastY;
        double dz = player.getZ() - lastZ;
        float dyaw = player.getYRot() - lastYaw;
        float dpitch = player.getXRot() - lastPitch;
        positionReminder++;
        boolean moved = dx * dx + dy * dy + dz * dz > 2.0E-4 || positionReminder >= 20;
        boolean rotated = dyaw != 0.0F || dpitch != 0.0F;
        boolean grounded = player.onGround();
        boolean collision = player.horizontalCollision;

        if (moved && rotated) {
            current.send(new ServerboundMovePlayerPosRotPacket(grounded, collision,
                    player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
        } else if (moved) {
            current.send(new ServerboundMovePlayerPosPacket(grounded, collision,
                    player.getX(), player.getY(), player.getZ()));
        } else if (rotated) {
            current.send(new ServerboundMovePlayerRotPacket(grounded, collision,
                    player.getYRot(), player.getXRot()));
        } else if (lastOnGround != grounded || lastHorizontalCollision != collision) {
            current.send(new ServerboundMovePlayerStatusOnlyPacket(grounded, collision));
        }
        if (moved) {
            lastX = player.getX();
            lastY = player.getY();
            lastZ = player.getZ();
            positionReminder = 0;
        }
        if (rotated) {
            lastYaw = player.getYRot();
            lastPitch = player.getXRot();
        }
        lastOnGround = grounded;
        lastHorizontalCollision = collision;

        byte flags = 0;
        if (player.zza > 0.0F) flags |= 0x01;
        if (player.zza < 0.0F) flags |= 0x02;
        if (player.xxa > 0.0F) flags |= 0x04;
        if (player.xxa < 0.0F) flags |= 0x08;
        if (player instanceof BotPlayer bot && bot.jumpingInput()) flags |= 0x10;
        if (player.isShiftKeyDown()) flags |= 0x20;
        if (sprinting) flags |= 0x40;
        if (flags != lastInputFlags) {
            current.send(new ServerboundPlayerInputPacket(
                    (flags & 0x01) != 0, (flags & 0x02) != 0, (flags & 0x04) != 0, (flags & 0x08) != 0,
                    (flags & 0x10) != 0, (flags & 0x20) != 0, (flags & 0x40) != 0));
            lastInputFlags = flags;
        }
    }

    private void add(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        messages.add(message);
        while (messages.size() > MAX_MESSAGES) {
            messages.removeFirst();
        }
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("skinsrestorer") || lower.contains("skin url") || lower.contains("skin set")) {
            skinPluginDetected = true;
            if (!skinUrl.isBlank() && !skinApplied) {
                skinApplied = true;
                sendCommand("skin url " + skinUrl);
            }
        }
    }

    /** 假人自己连接收到的区块写进本地世界（同一个服务器世界，数据一致）；按设置里的视距过滤。 */
    private void applyChunk(ClientboundLevelChunkWithLightPacket packet) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        minecraft.execute(() -> {
            net.minecraft.client.multiplayer.ClientLevel level = minecraft.level;
            if (level == null) {
                return;
            }
            int view = com.dwinovo.numen.client.data.ClientPrefs.botViewDistance();
            int centerX = net.minecraft.core.SectionPos.blockToSectionCoord(positionKnown
                    ? (int) x : (minecraft.player != null ? minecraft.player.getBlockX() : 0));
            int centerZ = net.minecraft.core.SectionPos.blockToSectionCoord(positionKnown
                    ? (int) z : (minecraft.player != null ? minecraft.player.getBlockZ() : 0));
            if (Math.abs(packet.getX() - centerX) > view || Math.abs(packet.getZ() - centerZ) > view) {
                return;
            }
            java.util.EnumMap<net.minecraft.world.level.levelgen.Heightmap.Types, long[]> heights =
                    new java.util.EnumMap<>(net.minecraft.world.level.levelgen.Heightmap.Types.class);
            packet.getHeightMaps().forEach((type, data) ->
                    heights.put(net.minecraft.world.level.levelgen.Heightmap.Types.values()[type.ordinal()], data));
            net.minecraft.network.FriendlyByteBuf buffer = new net.minecraft.network.FriendlyByteBuf(
                    io.netty.buffer.Unpooled.wrappedBuffer(packet.getChunkData()));
            level.getChunkSource().replaceWithPacketData(packet.getX(), packet.getZ(), buffer, heights,
                    output -> { });
        });
    }

    private static String plain(Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }
}
