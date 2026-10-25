package com.dwinovo.numen.client.wizard;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Four step wizard for hooking the assistant onto the local player and getting past server gates. */
public class JoinWizardScreen extends Screen {

    private EditBox passwordBox;
    private EditBox seedBox;

    public JoinWizardScreen() {
        super(Component.literal("加入服务器"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - 100;
        int y = this.height / 4 + 30;

        switch (JoinWizard.step()) {
            case INTRO -> {
                this.seedBox = new EditBox(this.font, left, y, 200, 20, Component.literal("世界种子"));
                this.seedBox.setMaxLength(32);
                this.seedBox.setValue(JoinWizard.seed());
                this.seedBox.setResponder(JoinWizard::seed);
                this.addRenderableWidget(this.seedBox);
                this.addRenderableWidget(Button.builder(Component.literal("开始"), button -> {
                    JoinWizard.seed(this.seedBox.getValue());
                    JoinWizard.step(JoinWizard.Step.ACCOUNT);
                    this.rebuildWidgets();
                }).bounds(left, y + 26, 200, 20).build());
            }
            case ACCOUNT -> {
                this.passwordBox = new EditBox(this.font, left, y, 200, 20, Component.literal("密码"));
                this.passwordBox.setMaxLength(32);
                this.passwordBox.setValue(JoinWizard.password());
                this.passwordBox.setResponder(JoinWizard::password);
                this.addRenderableWidget(this.passwordBox);
                this.addRenderableWidget(Button.builder(Component.literal("登录"), button -> {
                    JoinWizard.login();
                    this.rebuildWidgets();
                }).bounds(left, y + 26, 98, 20).build());
                this.addRenderableWidget(Button.builder(Component.literal("注册"), button -> {
                    JoinWizard.register();
                    this.rebuildWidgets();
                }).bounds(left + 102, y + 26, 98, 20).build());
                this.addRenderableWidget(Button.builder(Component.literal("跳过"), button -> {
                    JoinWizard.step(JoinWizard.Step.BIND);
                    this.rebuildWidgets();
                }).bounds(left, y + 52, 200, 20).build());
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
                this.addRenderableWidget(Button.builder(Component.literal("开始正版验证"), button -> {
                    JoinWizard.status("退出到主菜单 →「账户」登录正版账号，然后重新进服");
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, cx, 24, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal(stepTitle()), cx, 40, 0xA0A0A0);

        switch (JoinWizard.step()) {
            case INTRO -> {
                graphics.drawCenteredString(this.font, Component.literal("这一步把 AI 助手接到你当前的角色上。"),
                    cx, this.height / 4 - 6, 0xD0D0D0);
                graphics.drawCenteredString(this.font, Component.literal("不会生成假人，服务器看不到额外的玩家。"),
                    cx, this.height / 4 + 8, 0xD0D0D0);
                graphics.drawCenteredString(this.font, Component.literal("填当前服务器的世界种子（找结构/找矿物用）"),
                    cx, this.height / 4 + 22, 0x80C080);
            }
            case ACCOUNT -> graphics.drawCenteredString(this.font,
                Component.literal("输入登录密码；没注册过就点「注册」。"),
                cx, this.height / 4 - 6, 0xD0D0D0);
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
                graphics.drawCenteredString(this.font, Component.literal("换成正版账号重进即可；检测错了就点下面第二个按钮。"),
                    cx, this.height / 4 + 8, 0xD0D0D0);
            }
        }

        String status = JoinWizard.status();
        if (!status.isBlank()) {
            graphics.drawCenteredString(this.font, Component.literal(status), cx, this.height - 40, 0x80C080);
        }
    }

    private String stepTitle() {
        return switch (JoinWizard.step()) {
            case INTRO -> "第 1 步 / 4 · 开始";
            case ACCOUNT -> "第 2 步 / 4 · 登录 / 注册";
            case BIND -> "第 3 步 / 4 · 绑定 QQ";
            case PREMIUM -> "第 4 步 / 4 · 正版验证";
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
