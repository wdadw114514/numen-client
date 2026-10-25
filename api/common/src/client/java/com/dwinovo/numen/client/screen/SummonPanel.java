package com.dwinovo.numen.client.screen;

import com.dwinovo.numen.client.ui.NumenStyle;
import com.dwinovo.numen.client.ui.NumenTheme;
import com.dwinovo.numen.client.ui.mc.McDrawSurface;
import com.dwinovo.numen.client.ui.mc.Sprites;
import com.dwinovo.numen.client.ui.widget.Button;
import com.dwinovo.numen.client.ui.widget.DialogBox;
import com.dwinovo.numen.client.ui.widget.InlineAlert;
import com.dwinovo.numen.client.ui.widget.TextField;
import com.dwinovo.numen.client.wizard.JoinWizard;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * 添加客户端假人：用 Numen 自己的卡片（和召唤同伴同一套版式），不走服务端同伴。
 * 服务器地址、假人账号、密码、世界种子、皮肤链接、大厅模式都在这一张卡里。
 */
public final class SummonPanel extends ModalCard {

    /** 提交口；客户端假人版本只用来关闭卡片，不再召唤服务端同伴。 */
    public interface Host {
        void onCreate(Draft draft);

        void onCancel();

        boolean canChooseMode();

        boolean ownerCreative();
    }

    public static final class Draft {
        public String name = "";
        public String personaId;
        public String providerId;
        public String voiceId;
        public String skinId;
        public boolean creative;
    }

    private final Host host;
    private TextField nameField;
    private TextField addressField;
    private TextField passwordField;
    private TextField seedField;
    private TextField skinField;
    private TextField serverField;
    private InlineAlert alert;
    private String lastStatus = "";
    private int coverTop;

    public SummonPanel(Host host) {
        this.host = host;
        Minecraft mc = Minecraft.getInstance();
        ui.setClipboard(() -> mc.keyboardHandler.getClipboard(),
                s -> mc.keyboardHandler.setClipboard(s));
        ui.setInputFactory(com.dwinovo.numen.client.ui.mc.McTextInput.factory());
    }

    @Override
    void reset() {
    }

    @Override
    int height() {
        return heightFor(COVER_H + 5 * FIELD_H + ROW_H);
    }

    @Override
    protected void layout(int top) {
        title("添加客户端假人");
        coverTop = top;
        nameField = field(coverRight(), coverFieldY(top), coverRightW(), "假人用户名",
                new TextField(JoinWizard.username(), JoinWizard::username)
                        .placeholder("3-16 位字母/数字/_"));
        int ry = top + COVER_H;

        addressField = field(x + DialogBox.PAD_X, ry, w - DialogBox.PAD_X * 2, "服务器地址",
                new TextField(JoinWizard.serverAddress(), JoinWizard::serverAddress)
                        .placeholder("play.example.com 或 ip:port"));
        ry += FIELD_H;
        passwordField = field(x + DialogBox.PAD_X, ry, w - DialogBox.PAD_X * 2, "服务器密码",
                new TextField(JoinWizard.password(), JoinWizard::password).masked(true));
        ry += FIELD_H;
        seedField = field(x + DialogBox.PAD_X, ry, w - DialogBox.PAD_X * 2, "世界种子（找结构/找矿物用）",
                new TextField(JoinWizard.seed(), JoinWizard::seed).placeholder("可留空"));
        ry += FIELD_H;
        skinField = field(x + DialogBox.PAD_X, ry, w - DialogBox.PAD_X * 2, "皮肤链接（可选）",
                new TextField(JoinWizard.skinUrl(), JoinWizard::skinUrl)
                        .placeholder("SkinsRestorer 服务器会自动发 /skin url"));
        ry += FIELD_H;

        select(ry, "大厅模式", List.of("关", "开"), JoinWizard.lobbyEnabled() ? 1 : 0,
                i -> JoinWizard.lobbyEnabled(i == 1));
        ry += ROW_H;
        serverField = field(x + DialogBox.PAD_X, ry, w - DialogBox.PAD_X * 2, "子服务器名（大厅模式用，可选）",
                new TextField(JoinWizard.serverName(), JoinWizard::serverName)
                        .placeholder("例如 survival"));
        ry += FIELD_H;

        alert = ui.add(new InlineAlert());
        alert.setBounds(x + DialogBox.PAD_X, y + 2, w - DialogBox.PAD_X * 2, DialogBox.TITLE_H);

        buttons("取消", host::onCancel, "连接", this::submit);
        ui.requestFocus(nameField);
    }

    /** 服务端召唤流程的旧调用口（客户端假人不用），保留以免屏幕代码断掉。 */
    public void setBusy(String message) {
        if (alert != null) {
            alert.show(InlineAlert.Severity.INFO, message);
        }
    }

    private void submit() {
        JoinWizard.username(nameField.value());
        JoinWizard.serverAddress(addressField.value());
        JoinWizard.password(passwordField.value());
        JoinWizard.seed(seedField.value());
        JoinWizard.skinUrl(skinField.value());
        JoinWizard.serverName(serverField.value());
        JoinWizard.connect();
        lastStatus = JoinWizard.status();
        alert.show(InlineAlert.Severity.INFO, lastStatus);
    }

    @Override
    protected void paint(McDrawSurface s, NumenTheme.Colors c, int mouseX, int mouseY, long nowMs, float alpha) {
        JoinWizard.poll();
        String status = JoinWizard.status();
        if (alert != null && !status.equals(lastStatus)) {
            lastStatus = status;
            if (!status.isBlank()) {
                alert.show(InlineAlert.Severity.INFO, status);
            }
        }
        int px = photoX();
        int py = photoY(coverTop);
        NumenStyle.box(s, px, py, PHOTO, PHOTO, c.sectionBg(), c.inputBorder());
        int size = Sprites.SIZE * 2;
        icon(s.graphics(), Sprites.USER, px + (PHOTO - size) / 2, py + (PHOTO - size) / 2, size,
                c.textSecondary(), alpha);
    }

}
