package com.dwinovo.numen.client.wizard;

import com.dwinovo.numen.client.bot.BotConnection;
import com.dwinovo.numen.client.bot.BotManager;
import com.dwinovo.numen.sdk.WorldSeed;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * State for the "join a server" wizard. Steps:
 * <ol>
 *   <li>intro: server address + world seed;</li>
 *   <li>account: the fake player's name and /login or /register password;</li>
 *   <li>bind: watch chat for the verification code, player confirms with the "verified" button;</li>
 *   <li>premium: warn about online-mode servers and offer a fallback.</li>
 * </ol>
 * The connection is a real protocol session ({@link BotManager}), not the local player's connection.
 */
public final class JoinWizard {

    public enum Step {
        INTRO, ACCOUNT, BIND, PREMIUM
    }

    private static final Pattern SERVER_LIST = Pattern.compile(
            "(?:servers?|服务器)[:：]\\\\s*(.+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern CODE = Pattern.compile(
            "(?:绑定|验证码|验证码为|code)[^A-Za-z0-9]{0,10}([A-Za-z0-9]{4,12})",
            Pattern.CASE_INSENSITIVE);

    private static final Deque<String> RECENT = new ArrayDeque<>();
    private static int processedMessages;

    private static Step step = Step.INTRO;
    private static String serverAddress = "";
    private static String username = "";
    private static String password = "";
    private static String seed = "";
    private static String skinUrl = "";
    private static String serverName = "";
    private static boolean lobbyEnabled;
    private static boolean serverProbeSent;
    private static final java.util.List<String> SERVERS = new java.util.ArrayList<>();
    private static String code = "";
    private static String status = "";
    private static java.nio.file.Path file;

    private JoinWizard() {}

    public static Step step() {
        return step;
    }

    public static void step(Step value) {
        step = value;
    }

    public static String serverAddress() {
        return serverAddress;
    }

    public static void serverAddress(String value) {
        serverAddress = value == null ? "" : value.strip();
    }

    public static String username() {
        return username;
    }

    public static void username(String value) {
        username = value == null ? "" : value.strip();
    }

    public static String password() {
        return password;
    }

    public static void password(String value) {
        password = value == null ? "" : value;
    }

    public static String seed() {
        return seed;
    }

    public static void seed(String value) {
        seed = value == null ? "" : value.strip();
        WorldSeed.set(seed);
    }

    public static String skinUrl() {
        return skinUrl;
    }

    public static void skinUrl(String value) {
        skinUrl = value == null ? "" : value.strip();
    }

    public static boolean lobbyEnabled() {
        return lobbyEnabled;
    }

    public static void lobbyEnabled(boolean value) {
        lobbyEnabled = value;
    }

    public static String serverName() {
        return serverName;
    }

    public static void serverName(String value) {
        serverName = value == null ? "" : value.strip();
    }

    public static java.util.List<String> servers() {
        return java.util.List.copyOf(SERVERS);
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

    /** 每客户端刻调一次：把假人连接的新消息读进向导。 */
    /** 启动时读回上次的假人配置；退出重进不用重新填。 */
    public static void init(java.nio.file.Path configDir) {
        file = configDir.resolve("bot.json");
        if (!java.nio.file.Files.exists(file)) {
            return;
        }
        try {
            var o = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file))
                    .getAsJsonObject();
            if (o.has("address")) serverAddress = o.get("address").getAsString();
            if (o.has("username")) username = o.get("username").getAsString();
            if (o.has("password")) password = o.get("password").getAsString();
            if (o.has("seed")) seed = o.get("seed").getAsString();
            if (o.has("skin")) skinUrl = o.get("skin").getAsString();
            if (o.has("lobby")) lobbyEnabled = o.get("lobby").getAsBoolean();
            if (o.has("server")) serverName = o.get("server").getAsString();
            WorldSeed.set(seed);
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        if (file == null) {
            return;
        }
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            var o = new com.google.gson.JsonObject();
            o.addProperty("address", serverAddress);
            o.addProperty("username", username);
            o.addProperty("password", password);
            o.addProperty("seed", seed);
            o.addProperty("skin", skinUrl);
            o.addProperty("lobby", lobbyEnabled);
            o.addProperty("server", serverName);
            java.nio.file.Files.writeString(file, o.toString());
        } catch (Exception ignored) {
        }
    }

    public static void poll() {
        BotConnection bot = BotManager.bot();
        if (bot == null) {
            processedMessages = 0;
            return;
        }
        List<String> all = bot.messages();
        if (all.size() < processedMessages) {
            processedMessages = 0;
        }
        for (int i = processedMessages; i < all.size(); i++) {
            onMessage(all.get(i));
        }
        processedMessages = all.size();

        if (lobbyEnabled && !serverProbeSent && bot.state() == BotConnection.State.PLAY) {
            serverProbeSent = true;
            bot.sendCommand("server");
        }
        switch (bot.state()) {
            case CONNECTING -> status = "正在连接服务器…";
            case PLAY -> {
                if (status.isBlank() || status.startsWith("正在连接")) {
                    status = "假人已进入服务器";
                }
            }
            case DISCONNECTED -> status = "连接断开: " + bot.lastError();
            default -> {
            }
        }
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
        java.util.regex.Matcher list = SERVER_LIST.matcher(text);
        if (list.find()) {
            for (String part : list.group(1).split("[,，\\\\s]+")) {
                String name = part.strip();
                if (!name.isEmpty() && !name.startsWith("<") && !SERVERS.contains(name)) {
                    SERVERS.add(name);
                }
            }
        }
    }

    /** 用独立账号连服务器（离线模式）。 */
    public static void connect() {
        if (serverAddress.isBlank()) {
            var current = Minecraft.getInstance().getCurrentServer();
            if (current != null) {
                serverAddress = current.ip;
            }
        }
        if (serverAddress.isBlank()) {
            status = "先填服务器地址";
            return;
        }
        if (username.isBlank()) {
            status = "先填假人用户名";
            return;
        }
        String host = serverAddress;
        int port = 25565;
        int colon = host.lastIndexOf(':');
        if (colon > 0 && colon < host.length() - 1) {
            try {
                port = Integer.parseInt(host.substring(colon + 1));
                host = host.substring(0, colon);
            } catch (NumberFormatException ignored) {
                status = "端口不是数字";
                return;
            }
        }
        processedMessages = 0;
        RECENT.clear();
        BotManager.connect(host, port, username, skinUrl);
        save();
        status = "正在连接 " + host + ":" + port + " …";
    }

    public static void sendCommand(String command) {
        BotConnection bot = BotManager.bot();
        if (bot == null || bot.state() != BotConnection.State.PLAY) {
            status = "假人还没连上服务器";
            return;
        }
        bot.sendCommand(command);
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

    public static void enterServer(String name) {
        String target = name == null ? "" : name.strip();
        if (target.isEmpty()) {
            status = "先填子服务器名";
            return;
        }
        sendCommand("server " + target);
        status = "已发送 /server " + target;
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
        processedMessages = 0;
        serverProbeSent = false;
        SERVERS.clear();
        BotManager.disconnect();
    }
}
