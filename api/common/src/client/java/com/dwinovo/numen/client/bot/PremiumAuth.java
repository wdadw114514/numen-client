package com.dwinovo.numen.client.bot;

import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;

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

    private PremiumAuth() {}

    public static JavaAuthManager manager() {
        return manager;
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
                pending = false;
            } catch (Exception e) {
                error = e.toString();
                pending = false;
            }
        });
    }
}
