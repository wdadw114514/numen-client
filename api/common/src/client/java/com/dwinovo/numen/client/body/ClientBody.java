package com.dwinovo.numen.client.body;

import com.dwinovo.numen.pathing.body.BodyView;
import com.dwinovo.numen.pathing.body.Controls;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client side body: the companion drives the local player instead of a fake one.
 * Movement/breaking will be implemented by dedicated client effectors; for now
 * this exposes everything the query side of the API needs.
 */
public final class ClientBody implements BodyView {

    private static ClientBody cached;

    private final LocalPlayer player;
    private final Map<Class<?>, Object> state = new HashMap<>();

    private ClientBody(LocalPlayer player) {
        this.player = player;
    }

    public static ClientBody local() {
        LocalPlayer current = Minecraft.getInstance().player;
        if (current == null) {
            cached = null;
            return null;
        }
        if (cached == null || cached.player != current) cached = new ClientBody(current);
        return cached;
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
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T state(Class<T> type, Supplier<T> init) {
        return (T) state.computeIfAbsent(type, key -> init.get());
    }

    @Override
    public UUID getOwnerUuid() {
        return player.getUUID();
    }
}
