package com.dwinovo.numen.client.screen;

import com.dwinovo.numen.agent.llm.ProviderLibrary;
import com.dwinovo.numen.client.skin.SkinLibrary;
import com.dwinovo.numen.client.ui.NumenStyle;
import com.dwinovo.numen.client.ui.NumenTheme;
import com.dwinovo.numen.client.ui.mc.McDrawSurface;
import com.dwinovo.numen.client.ui.mc.Sprites;
import com.dwinovo.numen.client.ui.widget.Button;
import com.dwinovo.numen.client.ui.widget.DialogBox;
import com.dwinovo.numen.client.ui.widget.InlineAlert;
import com.dwinovo.numen.client.ui.widget.TextField;
import com.dwinovo.numen.client.voice.VoiceLibrary;
import com.dwinovo.numen.client.wizard.JoinWizard;
import com.dwinovo.numen.persona.PersonaLibrary;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * 添加客户端假人：用 Numen 自己的卡片，分三步。
 * <ol>
 *   <li>服务器：地址、账号、密码、种子、皮肤链接、大厅；</li>
 *   <li>同伴选项：人设、模型配置、声线、皮肤库（原来的选项，模式已删）；</li>
 *   <li>连接：正版登录、连接。</li>
 * </ol>
 * 每步只放少量字段，卡片不会挤成一团。
 */
public final class SummonPanel extends ModalCard {

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

    private static final String PERSONA_NONE = "__default__";
    private static final String VOICE_NONE = "__none__";
    private static final String SKIN_DEFAULT = "__default__";
    private static final String SKIN_CUSTOM = "__custom__";

    private final Host host;
    private final Draft draft = new Draft();
    private int step;
    private int loginMode;

    private TextField nameField;
    private TextField addressField;
    private TextField passwordField;
    private TextField seedField;
    private TextField skinField;
    private TextField serverField;
    private InlineAlert alert;
    private String lastStatus = "";
    private int coverTop;

    private List<String> personaIds = List.of();
    private List<String> providerIds = List.of();
    private List<String> voiceIds = List.of();
    private List<String> skinIds = List.of();

    public SummonPanel(Host host) {
        this.host = host;
        Minecraft mc = Minecraft.getInstance();
        ui.setClipboard(() -> mc.keyboardHandler.getClipboard(),
                s -> mc.keyboardHandler.setClipboard(s));
        ui.setInputFactory(com.dwinovo.numen.client.ui.mc.McTextInput.factory());
    }

    @Override
    void reset() {
        step = 0;
    }

    @Override
    int height() {
        return heightFor(COVER_H + 6 * FIELD_H + ROW_H);
    }

    @Override
    protected void layout(int top) {
        coverTop = top;
        switch (step) {
            case 0 -> layoutServer(top);
            case 1 -> layoutCompanion(top);
            default -> layoutConnect(top);
        }
    }

    private void layoutServer(int top) {
        title("添加客户端假人 · 1/3 服务器");
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
        seedField = field(x + DialogBox.PAD_X, ry, w - DialogBox.PAD_X * 2, "世界种子（找结构/找矿物用，可留空）",
                new TextField(JoinWizard.seed(), JoinWizard::seed).placeholder("可留空"));
        ry += FIELD_H;

        select(ry, "大厅模式", List.of("关", "开"), JoinWizard.lobbyEnabled() ? 1 : 0,
                i -> JoinWizard.lobbyEnabled(i == 1));
        ry += ROW_H;
        serverField = field(x + DialogBox.PAD_X, ry, w - DialogBox.PAD_X * 2, "子服务器名（大厅模式用，可留空）",
                new TextField(JoinWizard.serverName(), JoinWizard::serverName).placeholder("例如 survival"));
        ry += FIELD_H;

        alert = ui.add(new InlineAlert());
        alert.setBounds(x + DialogBox.PAD_X, y + 2, w - DialogBox.PAD_X * 2, DialogBox.TITLE_H);

        buttons("取消", host::onCancel, "下一步", this::next);
        ui.requestFocus(nameField);
    }

    private void layoutCompanion(int top) {
        title("添加客户端假人 · 2/3 同伴选项");
        int ry = top;
        PersonaLibrary.instance().reload();

        List<String> personaNames = new ArrayList<>();
        List<String> pIds = new ArrayList<>();
        pIds.add(PERSONA_NONE);
        personaNames.add("不配置");
        for (PersonaLibrary.Persona p : PersonaLibrary.instance().list()) {
            pIds.add(p.id());
            personaNames.add(p.name());
        }
        personaIds = pIds;
        select(ry, "人设", personaNames,
                Math.max(0, personaIds.indexOf(draft.personaId == null ? PERSONA_NONE : draft.personaId)),
                i -> {
                    String id = personaIds.get(i);
                    draft.personaId = PERSONA_NONE.equals(id) ? null : id;
                });
        ry += ROW_H;

        List<String> provNames = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (var e : ProviderLibrary.instance().list()) {
            ids.add(e.id());
            provNames.add(e.name());
        }
        providerIds = ids;
        if (!ids.isEmpty() && draft.providerId == null) draft.providerId = ids.get(0);
        select(ry, "模型配置", provNames, Math.max(0, providerIds.indexOf(draft.providerId)),
                i -> draft.providerId = providerIds.get(i));
        ry += ROW_H;

        List<String> voiceNames = new ArrayList<>();
        List<String> vIds = new ArrayList<>();
        vIds.add(VOICE_NONE);
        voiceNames.add("不配置");
        for (var e : VoiceLibrary.instance().list()) {
            vIds.add(e.id());
            voiceNames.add(e.name());
        }
        voiceIds = vIds;
        select(ry, "声线", voiceNames,
                Math.max(0, voiceIds.indexOf(draft.voiceId == null ? VOICE_NONE : draft.voiceId)),
                i -> {
                    String id = voiceIds.get(i);
                    draft.voiceId = VOICE_NONE.equals(id) ? null : id;
                });
        ry += ROW_H;

        List<String> skinNames = new ArrayList<>();
        List<String> sIds = new ArrayList<>();
        sIds.add(SKIN_DEFAULT);
        skinNames.add("默认（按名字）");
        sIds.add(SKIN_CUSTOM);
        skinNames.add("自定义（填下载链接）");
        for (var e : SkinLibrary.instance().list()) {
            if (e.signed()) {
                sIds.add(e.id());
                skinNames.add(e.name());
            }
        }
        skinIds = sIds;
        select(ry, "皮肤", skinNames,
                Math.max(0, skinIds.indexOf(draft.skinId == null ? SKIN_DEFAULT : draft.skinId)),
                i -> {
                    draft.skinId = skinIds.get(i);
                    if (SKIN_CUSTOM.equals(draft.skinId)) {
                        rebuild();
                    }
                });
        if (SKIN_CUSTOM.equals(draft.skinId)) {
            ry += ROW_H;
            skinField = field(x + DialogBox.PAD_X, ry, w - DialogBox.PAD_X * 2,
                    "皮肤下载链接（png 直链）",
                    new TextField(JoinWizard.skinUrl(), JoinWizard::skinUrl)
                            .placeholder("https://.../skin.png"));
        }

        alert = ui.add(new InlineAlert());
        alert.setBounds(x + DialogBox.PAD_X, y + 2, w - DialogBox.PAD_X * 2, DialogBox.TITLE_H);
        buttons("上一步", this::previous, "下一步", this::next);
    }

    private void layoutConnect(int top) {
        title("添加客户端假人 · 3/3 连接");
        int ry = top;
        select(ry, "登录方式", List.of("离线登录（默认）", "LittleSkin", "正版 Microsoft"), loginMode,
                i -> {
                    loginMode = i;
                    rebuild();
                });
        ry += ROW_H;

        switch (loginMode) {
            case 0 -> {
                nameField = field(x + DialogBox.PAD_X, ry, w - DialogBox.PAD_X * 2, "玩家名称",
                        new TextField(JoinWizard.username(), JoinWizard::username)
                                .placeholder("离线账号名"));
                ry += FIELD_H;
            }
            case 1 -> {
                TextField lsEmail = field(x + DialogBox.PAD_X, ry, w - DialogBox.PAD_X * 2,
                        "LittleSkin 邮箱", new TextField("", v -> { }).placeholder("邮箱"));
                ry += FIELD_H;
                TextField lsPassword = field(x + DialogBox.PAD_X, ry, w - DialogBox.PAD_X * 2,
                        "LittleSkin 密码", new TextField("", v -> { }).masked(true));
                ry += FIELD_H;
                Button littleSkin = ui.add(new Button(
                        com.dwinovo.numen.client.bot.LittleSkinAuth.pending
                                ? "LittleSkin 登录中…" : "LittleSkin 登录",
                        Button.Style.LINK, () -> com.dwinovo.numen.client.bot.LittleSkinAuth.login(
                                lsEmail.value(), lsPassword.value())));
                littleSkin.setBounds(x + DialogBox.PAD_X, ry, w - DialogBox.PAD_X * 2, NumenStyle.CONTROL_H);
                ry += ROW_H + 4;
            }
            default -> {
                Button premium = ui.add(new Button(
                        com.dwinovo.numen.client.bot.PremiumAuth.manager() != null
                                ? "正版已登录" : "正版登录（设备码）",
                        Button.Style.LINK, com.dwinovo.numen.client.bot.PremiumAuth::login));
                premium.setBounds(x + DialogBox.PAD_X, ry, w - DialogBox.PAD_X * 2, NumenStyle.CONTROL_H);
                ry += ROW_H + 4;
            }
        }

        alert = ui.add(new InlineAlert());
        alert.setBounds(x + DialogBox.PAD_X, ry, w - DialogBox.PAD_X * 2, 28);
        buttons("上一步", this::previous, "连接", this::submit);
    }

    private void next() {
        capture();
        if (step < 2) {
            step++;
            rebuild();
        }
    }

    private void previous() {
        if (step > 0) {
            step--;
            rebuild();
        }
    }

    private void rebuild() {
        ui.clear();
        layout(y + DialogBox.TITLE_H);
    }

    private void capture() {
        if (nameField != null) JoinWizard.username(nameField.value());
        if (addressField != null) JoinWizard.serverAddress(addressField.value());
        if (passwordField != null) JoinWizard.password(passwordField.value());
        if (seedField != null) JoinWizard.seed(seedField.value());
        if (skinField != null) JoinWizard.skinUrl(skinField.value());
        if (serverField != null) JoinWizard.serverName(serverField.value());
    }

    public void setBusy(String message) {
        if (alert != null) {
            alert.show(InlineAlert.Severity.INFO, message);
        }
    }

    private void submit() {
        capture();
        if (loginMode == 0) {
            com.dwinovo.numen.client.bot.BotConnection.clearAuth();
        }
        JoinWizard.connect();
        lastStatus = JoinWizard.status();
        if (alert != null) alert.show(InlineAlert.Severity.INFO, lastStatus);
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
        if (step == 0) {
            int px = photoX();
            int py = photoY(coverTop);
            NumenStyle.box(s, px, py, PHOTO, PHOTO, c.sectionBg(), c.inputBorder());
            int size = Sprites.SIZE * 2;
            icon(s.graphics(), Sprites.USER, px + (PHOTO - size) / 2, py + (PHOTO - size) / 2, size,
                    c.textSecondary(), alpha);
        }
    }
}
