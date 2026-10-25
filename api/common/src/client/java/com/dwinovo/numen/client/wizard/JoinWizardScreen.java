package com.dwinovo.numen.client.wizard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

/** Four step wizard for the client-side fake player: address, account, QQ bind, premium check. */
public class JoinWizardScreen extends Screen {

    private EditBox addressBox;
    private EditBox usernameBox;
    private EditBox passwordBox;
    private EditBox seedBox;
    private EditBox skinBox;
    private EditBox serverBox;

    public JoinWizardScreen() {
        super(Component.literal("加入服务器"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - 100;
        int y = this.height / 4 - 10;

        switch (JoinWizard.step()) {
            case INTRO -> {
                if (JoinWizard.serverAddress().isBlank()) {
                    ServerData server = Minecraft.getInstance().getCurrentServer();
                    if (server != null) {
                        JoinWizard.serverAddress(server.ip);
                    }
                }
                this.addressBox = new EditBox(this.font, left, y, 200, 20, Component.literal("服务器地址"));
                this.addressBox.setMaxLength(128);
                this.addressBox.setValue(JoinWizard.serverAddress());
                this.addressBox.setResponder(JoinWizard::serverAddress);
                this.addRenderableWidget(this.addressBox);

                this.seedBox = new EditBox(this.font, left, y + 26, 200, 20, Component.literal("世界种子"));
                this.seedBox.setMaxLength(32);
                this.seedBox.setValue(JoinWizard.seed());
                this.seedBox.setResponder(JoinWizard::seed);
                this.addRenderableWidget(this.seedBox);

                this.skinBox = new EditBox(this.font, left, y + 52, 200, 20, Component.literal("自定义皮肤链接"));
                this.skinBox.setMaxLength(256);
                this.skinBox.setValue(JoinWizard.skinUrl());
                this.skinBox.setResponder(JoinWizard::skinUrl);
                this.addRenderableWidget(this.skinBox);

                this.addRenderableWidget(Button.builder(
                        Component.literal("大厅模式: " + (JoinWizard.lobbyEnabled() ? "开" : "关")), button -> {
                            JoinWizard.lobbyEnabled(!JoinWizard.lobbyEnabled());
                            this.rebuildWidgets();
                        }).bounds(left, y + 78, 200, 20).build());

                this.addRenderableWidget(Button.builder(Component.literal("开始"), button -> {
                    JoinWizard.serverAddress(this.addressBox.getValue());
                    JoinWizard.seed(this.seedBox.getValue());
                    JoinWizard.skinUrl(this.skinBox.getValue());
                    JoinWizard.step(JoinWizard.Step.ACCOUNT);
                    this.rebuildWidgets();
                }).bounds(left, y + 104, 200, 20).build());
            }
            case ACCOUNT -> {
                this.usernameBox = new EditBox(this.font, left, y, 200, 20, Component.literal("假人用户名"));
                this.usernameBox.setMaxLength(16);
                this.usernameBox.setValue(JoinWizard.username());
                this.usernameBox.setResponder(JoinWizard::username);
                this.addRenderableWidget(this.usernameBox);

                this.passwordBox = new EditBox(this.font, left, y + 26, 200, 20, Component.literal("密码"));
                this.passwordBox.setMaxLength(32);
                this.passwordBox.setValue(JoinWizard.password());
                this.passwordBox.setResponder(JoinWizard::password);
                this.addRenderableWidget(this.passwordBox);

                this.addRenderableWidget(Button.builder(Component.literal("连接并登录"), button -> {
                    JoinWizard.username(this.usernameBox.getValue());
                    JoinWizard.password(this.passwordBox.getValue());
                    if (JoinWizard.username().isBlank()) {
                        JoinWizard.status("先填假人用户名");
                    } else if (com.dwinovo.numen.client.bot.BotManager.bot() == null
                            || com.dwinovo.numen.client.bot.BotManager.bot().state()
                                    != com.dwinovo.numen.client.bot.BotConnection.State.PLAY) {
                        JoinWizard.connect();
                    } else {
                        JoinWizard.login();
                    }
                    this.rebuildWidgets();
                }).bounds(left, y + 52, 98, 20).build());
                this.addRenderableWidget(Button.builder(Component.literal("注册"), button -> {
                    JoinWizard.username(this.usernameBox.getValue());
                    JoinWizard.password(this.passwordBox.getValue());
                    if (com.dwinovo.numen.client.bot.BotManager.bot() == null
                            || com.dwinovo.numen.client.bot.BotManager.bot().state()
                                    != com.dwinovo.numen.client.bot.BotConnection.State.PLAY) {
                        JoinWizard.connect();
                    } else {
                        JoinWizard.register();
                    }
                    this.rebuildWidgets();
                }).bounds(left + 102, y + 52, 98, 20).build());
                this.addRenderableWidget(Button.builder(Component.literal("跳过"), button -> {
                    JoinWizard.step(JoinWizard.Step.BIND);
                    this.rebuildWidgets();
                }).bounds(left, y + 78, 200, 20).build());
                this.serverBox = new EditBox(this.font, left, y + 104, 200, 20, Component.literal("子服务器名"));
                this.serverBox.setMaxLength(64);
                this.serverBox.setValue(JoinWizard.serverName());
                this.serverBox.setResponder(JoinWizard::serverName);
                this.addRenderableWidget(this.serverBox);
                this.addRenderableWidget(Button.builder(Component.literal("进入子服务器"), button -> {
                    JoinWizard.serverName(this.serverBox.getValue());
                    JoinWizard.enterServer(this.serverBox.getValue());
                    this.rebuildWidgets();
                }).bounds(left, y + 130, 200, 20).build());
            }
            case BIND -> {
                this.addRenderableWidget(Button.builder(Component.literal("已验证"), button -> {
                    JoinWizard.verified();
                    this.rebuildWidgets();
                }).bounds(left, y, 200, 20).build());
                this.addRenderableWidget(Button.builder(Component.literal("跳过"), button -> {
                    JoinWizard.step(JoinWizard.Step.PREMIUM);
                    this.rebuildWidgets();
                }).bounds(left, y + 26, 200, 20).build());
            }
            case PREMIUM -> {
                this.addRenderableWidget(Button.builder(
                        Component.literal(com.dwinovo.numen.client.bot.PremiumAuth.pending
                                ? "正版登录中…" : "开始正版登录"), button -> {
                            com.dwinovo.numen.client.bot.PremiumAuth.login();
                            this.rebuildWidgets();
                        }).bounds(left, y, 200, 20).build());
                this.addRenderableWidget(Button.builder(Component.literal("检测错了？点我直接继续"), button -> {
                    JoinWizard.status("已跳过正版验证");
                    this.rebuildWidgets();
                }).bounds(left, y + 26, 200, 20).build());
                this.addRenderableWidget(Button.builder(Component.literal("完成"), button -> this.onClose())
                    .bounds(left, y + 52, 200, 20).build());
            }
        }
    }

    @Override
    public void tick() {
        JoinWizard.poll();
        super.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, cx, 24, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal(stepTitle()), cx, 40, 0xA0A0A0);

        switch (JoinWizard.step()) {
            case INTRO -> {
                graphics.drawCenteredString(this.font, Component.literal("这一步用独立账号把假人接进服务器。"),
                    cx, this.height / 4 - 18, 0xD0D0D0);
                graphics.drawCenteredString(this.font, Component.literal("服务器看不到你的本地角色，只看到这只假人。"),
                    cx, this.height / 4 - 4, 0xD0D0D0);
                graphics.drawCenteredString(this.font, Component.literal("填服务器地址、世界种子，以及可选的皮肤链接"),
                    cx, this.height / 4 + 10, 0x80C080);
            }
            case ACCOUNT -> {
                graphics.drawCenteredString(this.font, Component.literal("假人用户名与服务器密码；没注册过就点「注册」。"),
                    cx, this.height / 4 - 6, 0xD0D0D0);
            }
            case BIND -> {
                graphics.drawCenteredString(this.font, Component.literal("按服务器提示完成 QQ 绑定，验证码会自动抓取。"),
                    cx, this.height / 4 - 6, 0xD0D0D0);
                String code = JoinWizard.code();
                graphics.drawCenteredString(this.font,
                    Component.literal(code.isBlank() ? "验证码: 还没抓到" : "验证码: " + code),
                    cx, this.height / 4 + 10, code.isBlank() ? 0x888888 : 0xFFD060);
                int lineY = this.height - 130;
                for (String line : JoinWizard.recent()) {
                    String shown = line.length() > 80 ? line.substring(0, 80) + "…" : line;
                    graphics.drawString(this.font, Component.literal(shown), 20, lineY, 0x888888);
                    lineY += 10;
                }
            }
            case PREMIUM -> {
                graphics.drawCenteredString(this.font, Component.literal("如果进服被踢并提示正版验证，说明服务器开了正版模式。"),
                    cx, this.height / 4 - 6, 0xD0D0D0);
                graphics.drawCenteredString(this.font, Component.literal("点「开始正版登录」，按提示在浏览器里输入设备码。"),
                    cx, this.height / 4 + 8, 0xD0D0D0);
                if (com.dwinovo.numen.client.bot.PremiumAuth.pending) {
                    String code = com.dwinovo.numen.client.bot.PremiumAuth.userCode;
                    if (!code.isBlank()) {
                        graphics.drawCenteredString(this.font,
                                Component.literal("设备码: " + code + "  →  " + com.dwinovo.numen.client.bot.PremiumAuth.verificationUri),
                                cx, this.height / 4 + 26, 0xFFD060);
                    }
                } else if (!com.dwinovo.numen.client.bot.PremiumAuth.error.isBlank()) {
                    graphics.drawCenteredString(this.font,
                            Component.literal("登录失败: " + com.dwinovo.numen.client.bot.PremiumAuth.error),
                            cx, this.height / 4 + 26, 0xFF8080);
                } else if (com.dwinovo.numen.client.bot.PremiumAuth.manager() != null) {
                    graphics.drawCenteredString(this.font, Component.literal("正版登录成功，重新连接即可。"),
                            cx, this.height / 4 + 26, 0x80C080);
                }
            }
        }

        String status = JoinWizard.status();
        if (!status.isBlank()) {
            graphics.drawCenteredString(this.font, Component.literal(status), cx, this.height - 40, 0x80C080);
        }
    }

    private String stepTitle() {
        return switch (JoinWizard.step()) {
            case INTRO -> "第 1 步 / 4 · 服务器与种子";
            case ACCOUNT -> "第 2 步 / 4 · 假人登录 / 注册";
            case BIND -> "第 3 步 / 4 · 绑定 QQ";
            case PREMIUM -> "第 4 步 / 4 · 正版验证";
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
