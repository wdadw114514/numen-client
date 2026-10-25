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
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetPlayerInventoryPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;

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

    public BotConnection(String host, int port, String username) {
        this.host = host;
        this.port = port;
        this.username = username;
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
                0, menuStateId, slot, actionType, action,
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
                } else if (packet instanceof ClientboundSetPlayerInventoryPacket slot) {
                    inventory.put(slot.getSlot(), toMc(slot.getContents()));
                } else if (packet instanceof ClientboundContainerSetContentPacket content) {
                    if (content.getContainerId() == 0) {
                        menuStateId = content.getStateId();
                        applyMenuContent(content.getItems());
                    }
                } else if (packet instanceof ClientboundContainerSetSlotPacket slot) {
                    if (slot.getContainerId() == 0) {
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

    /** 每个客户端刻发一次位置；服务器按它校正假人的位置。 */
    public void tick() {
        ClientNetworkSession current = session;
        if (current == null || state != State.PLAY || !positionKnown) {
            return;
        }
        current.send(new ServerboundMovePlayerPosRotPacket(onGround, false, x, y, z, yaw, pitch));
    }

    private void add(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        messages.add(message);
        while (messages.size() > MAX_MESSAGES) {
            messages.removeFirst();
        }
    }

    private static String plain(Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }
}
