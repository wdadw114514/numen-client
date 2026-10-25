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
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;

import java.util.BitSet;
import java.util.List;
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
