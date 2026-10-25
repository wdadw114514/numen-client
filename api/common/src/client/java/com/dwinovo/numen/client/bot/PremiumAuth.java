package com.dwinovo.numen.client.bot;

import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * 正版（Microsoft）登录：设备码流程。后台拿 token，界面显示 code 和网址，用户在浏览器里确认。
 */
public final class PremiumAuth {

    public static volatile String userCode = "";
    public static volatile String verificationUri = "";
    public static volatile boolean pending;
    public static volatile String error = "";
    private static volatile JavaAuthManager manager;
    private static Path file;

    private PremiumAuth() {}

    public static JavaAuthManager manager() {
        return manager;
    }

    /** 启动时读回上次的正版会话；没有或读不出就保持未登录。 */
    public static void init(Path configDir) {
        file = configDir.resolve("premium.json");
        try {
            if (!Files.exists(file)) {
                return;
            }
            var json = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            manager = JavaAuthManager.fromJson(MinecraftAuth.createHttpClient("Numen/1.0"), json);
            BotConnection.setPremium(manager);
        } catch (Exception e) {
            error = "读取正版会话失败: " + e;
        }
    }

    private static void save() {
        if (file == null || manager == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, JavaAuthManager.toJson(manager).toString());
        } catch (Exception e) {
            error = "保存正版会话失败: " + e;
        }
    }

    public static void login() {
        if (pending) {
            return;
        }
        pending = true;
        error = "";
        userCode = "";
        verificationUri = "";
        CompletableFuture.runAsync(() -> {
            try {
                JavaAuthManager auth = JavaAuthManager.create(MinecraftAuth.createHttpClient("Numen/1.0"))
                        .login((net.lenni0451.commons.httpclient.HttpClient httpClient,
                                net.raphimc.minecraftauth.msa.model.MsaApplicationConfig config,
                                java.util.function.Consumer<MsaDeviceCode> callback) ->
                                new DeviceCodeMsaAuthService(httpClient, config, callback),
                                (MsaDeviceCode code) -> {
                                    userCode = code.getUserCode();
                                    verificationUri = code.getVerificationUri();
                                });
                manager = auth;
                BotConnection.setPremium(auth);
                save();
                pending = false;
            } catch (Exception e) {
                error = e.toString();
                pending = false;
            }
        });
    }
}
