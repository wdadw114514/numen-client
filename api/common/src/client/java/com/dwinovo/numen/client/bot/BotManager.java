package com.dwinovo.numen.client.bot;

/**
 * 当前这一只客户端假人的连接与身体。同一时间只留一只；重新连接会先断开旧的。
 */
public final class BotManager {

    private static volatile BotConnection bot;
    private static volatile BotBody body;

    private BotManager() {}

    public static BotConnection connect(String host, int port, String username) {
        return connect(host, port, username, "");
    }

    public static BotConnection connect(String host, int port, String username, String skinUrl) {
        disconnect();
        BotConnection created = new BotConnection(host, port, username, skinUrl);
        bot = created;
        created.connect();
        return created;
    }

    public static BotConnection bot() {
        return bot;
    }

    /** 假人的身体；连接还没进入 PLAY 或世界还没加载时 null（下一刻会再建）。 */
    public static BotBody body() {
        BotConnection current = bot;
        if (current == null) {
            body = null;
            return null;
        }
        BotBody existing = body;
        if (existing != null && existing.connection() == current) {
            return existing;
        }
        BotBody created = BotBody.create(current);
        body = created;
        if (created != null) {
            com.dwinovo.numen.client.agent.AgentLoopRegistry.getOrCreate(created.getUUID());
            // 步骤 2 选的人设/模型/声线落到这只假人身上
            var binding = com.dwinovo.numen.client.agent.CompanionHome.binding(created.getUUID())
                    .withProvider(com.dwinovo.numen.client.wizard.JoinWizard.providerId())
                    .withPersona(com.dwinovo.numen.client.wizard.JoinWizard.personaId())
                    .withVoice(com.dwinovo.numen.client.wizard.JoinWizard.voiceId());
            com.dwinovo.numen.client.agent.CompanionHome.bind(created.getUUID(), binding);
            // 把假人放进 Numen 的名册，聊天左栏/名册才看得到她
            com.dwinovo.numen.client.agent.NumenRoster.instance().replaceAll("client",
                    java.util.List.of(new com.dwinovo.numen.client.agent.NumenRoster.Entry(
                            created.getUUID(), created.getName().getString())));
        }
        return created;
    }

    public static boolean playing() {
        BotConnection current = bot;
        return current != null && current.state() == BotConnection.State.PLAY;
    }

    public static void disconnect() {
        BotConnection current = bot;
        bot = null;
        body = null;
        if (current != null) {
            current.disconnect();
        }
        com.dwinovo.numen.client.agent.NumenRoster.instance().clear();
    }

    public static void tick() {
        BotConnection current = bot;
        if (current == null) {
            return;
        }
        BotBody currentBody = body();
        if (currentBody != null) {
            currentBody.tick();
        }
    }
}
