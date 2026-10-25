package com.dwinovo.numen.client.bot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * LittleSkin 外置登录（Yggdrasil）：邮箱/密码换 accessToken 与角色 UUID。
 * 服务器必须开了外置登录（authlib-injector），否则连不上。
 */
public final class LittleSkinAuth {

    private static final String API = "https://littleskin.cn/api/yggdrasil";

    public static volatile boolean pending;
    public static volatile String error = "";
    public static volatile String playerName = "";
    public static volatile String playerUuid = "";

    private LittleSkinAuth() {}

    /** LittleSkin 的角色皮肤直链（textures property 里的 SKIN.url）；没有返回 null。 */
    private static String fetchSkinUrl(HttpClient http, String rawId) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            API + "/sessionserver/session/minecraft/profile/" + rawId))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            for (var element : json.getAsJsonArray("properties")) {
                JsonObject property = element.getAsJsonObject();
                if (!"textures".equals(property.get("name").getAsString())) {
                    continue;
                }
                String value = property.get("value").getAsString();
                String decoded = new String(java.util.Base64.getDecoder().decode(value),
                        StandardCharsets.UTF_8);
                JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject()
                        .getAsJsonObject("textures");
                if (textures != null && textures.has("SKIN")) {
                    return textures.getAsJsonObject("SKIN").get("url").getAsString();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static void login(String email, String password) {
        if (pending) {
            return;
        }
        pending = true;
        error = "";
        CompletableFuture.runAsync(() -> {
            try {
                HttpClient http = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
                JsonObject body = new JsonObject();
                body.addProperty("username", email);
                body.addProperty("password", password);
                body.addProperty("clientToken", UUID.randomUUID().toString().replace("-", ""));
                JsonObject agent = new JsonObject();
                agent.addProperty("name", "Minecraft");
                agent.addProperty("version", 1);
                body.add("agent", agent);
                HttpRequest request = HttpRequest.newBuilder(URI.create(API + "/authserver/authenticate"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(20))
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IOException("LittleSkin HTTP " + response.statusCode() + ": " + response.body());
                }
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                String token = json.get("accessToken").getAsString();
                JsonObject profile = json.getAsJsonObject("selectedProfile");
                String rawId = profile.get("id").getAsString();
                String name = profile.get("name").getAsString();
                UUID uuid = UUID.fromString(rawId.replaceFirst(
                        "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
                playerName = name;
                playerUuid = uuid.toString();
                BotConnection.setExternal(
                        new org.geysermc.mcprotocollib.auth.GameProfile(uuid, name), token);
                String skin = fetchSkinUrl(http, rawId);
                if (skin != null) {
                    BotConnection.setExternalSkinUrl(skin);
                }
                pending = false;
            } catch (Exception e) {
                error = e.toString();
                pending = false;
            }
        });
    }
}
