package com.dwinovo.numen.permission;

import com.dwinovo.numen.pathing.body.BodyView;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.UUID;

/**
 * 权限层的唯一入口。全仓凡会改世界的地方——挖掘落点、放置落点、攻击落点、成本模型、
 * 选目标——只问这里;能不能挖/放/打没有第二个出处。
 *
 * <p>两种问法:{@link #judge(BodyView, Action)} 在主线程对活世界问一个动作;
 * {@link #gateFor} 在主线程取一份快照,交给搜索线程逐格问(见 {@link Gate#judge})。
 * 模型不是权限的执行者:它没有任何写入口,看到的只是普通的工具结果和回执。
 */
public final class Permission {

    private Permission() {}

    /**
     * 主线程:取这只同伴此刻的裁决快照——模式、主人层与出厂层规则、所在维度的放置记录、主人答应下来的任务期授权。
     * 快照不可变,任何线程可读。
     */
    public static Gate gateFor(BodyView companion) {
        PlacedBlocks placed = companion.level() instanceof ServerLevel level
                ? PlacedBlocks.of(level) : new PlacedBlocks();
        return new Gate(companion.getUUID(), modeOf(companion), ownerRules(companion), RuleSet.factory(),
                placed, ConsentDesk.of(companion).granted());
    }

    /** 主线程:对活世界裁决一个动作。 */
    public static Verdict judge(BodyView companion, Action action) {
        Gate gate = gateFor(companion);
        return companion.level() instanceof ServerLevel level
                ? gate.judgeLive(action, level)
                : gate.judge(action, companion.level());
    }

    /** 这只同伴的模式;客户端身体没有主人存档,按 {@link Mode#BYPASS}。 */
    public static Mode modeOf(BodyView companion) {
        MinecraftServer server = companion.server();
        UUID owner = companion.getOwnerUuid();
        if (server == null || owner == null) {
            return Mode.BYPASS;
        }
        return PermissionStore.of(server, owner).modeOf(companion.getUUID());
    }

    /** 主人给这只同伴设模式(面板与命令的落点);客户端身体没有主人存档,不存。 */
    public static void setMode(BodyView companion, Mode mode) {
        MinecraftServer server = companion.server();
        UUID owner = companion.getOwnerUuid();
        if (server == null || owner == null) {
            return;
        }
        PermissionStore.of(server, owner).setMode(companion.getUUID(), mode);
    }

    /** 这只同伴的主人写的那一层规则;客户端身体没有主人存档,是空层。 */
    private static RuleSet ownerRules(BodyView companion) {
        MinecraftServer server = companion.server();
        UUID owner = companion.getOwnerUuid();
        if (server == null || owner == null) {
            return RuleSet.EMPTY;
        }
        return PermissionStore.of(server, owner).rules();
    }
}
