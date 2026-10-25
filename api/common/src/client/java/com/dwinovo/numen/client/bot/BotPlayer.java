package com.dwinovo.numen.client.bot;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * 客户端假人的影子实体：不是 {@link net.minecraft.client.player.RemotePlayer}（那个 {@code noPhysics=true}，
 * 只做插值），而是一个真正跑原版 {@code Player.aiStep}/{@code travel} 物理的客户端玩家。
 * 输入由 {@link com.dwinovo.numen.pathing.body.Controls} 落到 {@code zza/xxa/jumping}，
 * 位置包按原版 {@code LocalPlayer.sendPosition} 的规则发。
 */
public class BotPlayer extends AbstractClientPlayer {

    public BotPlayer(ClientLevel level, GameProfile profile) {
        super(level, profile);
    }
}
