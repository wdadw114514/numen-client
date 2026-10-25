package com.dwinovo.numen.client.wizard;

import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * State for the "join a server" wizard. Steps:
 * <ol>
 *   <li>intro: explain what will happen;</li>
 *   <li>account: /login or /register with the password the player types;</li>
 *   <li>bind: watch chat for the verification code, player confirms with the "verified" button;</li>
 *   <li>premium: warn about online-mode servers and offer a fallback.</li>
 * </ol>
 * Everything runs on the local player: no fake player is spawned.
 */
public final class JoinWizard {

    public enum Step {
        INTRO, ACCOUNT, BIND, PREMIUM
    }

    private static final Pattern CODE = Pattern.compile(
            "(?:绑定|验证码|验证码为|code)[^A-Za-z0-9]{0,10}([A-Za-z0-9]{4,12})",
            Pattern.CASE_INSENSITIVE);

    private static final Deque<String> RECENT = new ArrayDeque<>();

    private static Step step = Step.INTRO;
    private static String password = "";
    private static String code = "";
    private static String status = "";

    private JoinWizard() {
    }

    public static Step step() {
        return step;
    }

    public static void step(Step value) {
        step = value;
    }

    public static String password() {
        return password;
    }

    public static void password(String value) {
        password = value == null ? "" : value;
    }

    public static String code() {
        return code;
    }

    public static String status() {
        return status;
    }

    public static void status(String value) {
        status = value == null ? "" : value;
    }

    public static List<String> recent() {
        return List.copyOf(RECENT);
    }

    /** Feed every server chat/system message here; the code is picked up automatically. */
    public static void onMessage(String text) {
        if (text == null || text.isBlank()) return;
        RECENT.addLast(text);
        while (RECENT.size() > 8) RECENT.removeFirst();

        Matcher matcher = CODE.matcher(text);
        if (matcher.find()) {
            code = matcher.group(1);
            status = "抓到验证码: " + code;
        }
        if (text.contains("登录成功") || text.contains("注册成功") || text.contains("已登录")) {
            status = text;
        }
    }

    public static void sendCommand(String command) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            status = "还没连接服务器";
            return;
        }
        minecraft.getConnection().sendChatCommand(command);
        status = "已发送 /" + command;
    }

    public static void login() {
        if (password.isBlank()) {
            status = "先输入密码";
            return;
        }
        sendCommand("login " + password);
        step = Step.BIND;
    }

    public static void register() {
        if (password.isBlank()) {
            status = "先输入密码";
            return;
        }
        sendCommand("register " + password + " " + password);
        step = Step.BIND;
    }

    public static void verified() {
        step = Step.PREMIUM;
        status = "已标记验证完成";
    }

    public static void reset() {
        step = Step.INTRO;
        password = "";
        code = "";
        status = "";
        RECENT.clear();
    }
}
