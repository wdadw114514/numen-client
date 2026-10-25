package com.dwinovo.numen.core.tools.web;

import com.dwinovo.numen.agent.script.ApiError;
import com.dwinovo.numen.agent.script.ErrorKind;
import com.dwinovo.numen.api.NumenApi;
import com.dwinovo.numen.sdk.Doc;
import com.dwinovo.numen.sdk.Example;
import com.dwinovo.numen.sdk.Fn;
import com.dwinovo.numen.sdk.Note;
import com.dwinovo.numen.sdk.Omitted;
import com.dwinovo.numen.sdk.Pending;
import com.dwinovo.numen.sdk.SeeAlso;
import com.dwinovo.numen.sdk.ServerCall;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * {@code numen.web}:联网搜索与网页抓取。搜索走本机网关的 {@code /v1/search}（OpenAI 兼容网关，
 * 监听 127.0.0.1:8080），抓取直接 GET 目标页并去掉 HTML 标签。两件事都在后台线程上做，
 * 不占身体、不卡服务端刻。
 */
public final class WebApi {

    private static final String GATEWAY = System.getProperty("numen.gateway", "http://127.0.0.1:8080");
    private static final String KEY = System.getProperty("numen.gateway.key", "sk-1GmFRYqhcW4I3itmpJfDGr5-");
    private static final int MAX_FETCH_CHARS = 8000;
    private static final Pattern SCRIPT = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern TAG = Pattern.compile("(?s)<[^>]+>");
    private static final Pattern SPACE = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern LINES = Pattern.compile("\\n{3,}");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private WebApi() {}

    public static void install(NumenApi numen) {
        numen.api("web", "Searching the web and reading pages.", WebApi.class);
    }

    /** 搜什么、要几条。 */
    public record Search(@Doc("What to search for, as you would type it into a search box.") String query,
                         @Doc("How many results to return (1-10).") @Omitted("5")
                         Optional<Integer> count) {}

    @Fn("Search the web; returns titles, URLs and snippets.")
    @Example("numen.web.search(\"Minecraft 1.21 stronghold mechanics\")")
    @Note("The search runs on the local gateway, not in the game. It returns links and snippets only; "
            + "call numen.web.fetch on a result URL to read the page itself.")
    public static Pending<String> search(ServerCall call, Search args) {
        Pending<String> out = Pending.create();
        CompletableFuture.runAsync(() -> {
            try {
                out.complete(searchNow(args.query(), args.count().orElse(5)));
            } catch (Exception e) {
                out.fail(new ApiError(ErrorKind.FAILED, "web search failed: " + e.getMessage(), null));
            }
        });
        return out;
    }

    /** 抓哪一页。 */
    public record Fetch(@Doc("A full http(s) URL, usually one returned by numen.web.search.") String url) {}

    @Fn("Fetch a web page and return its readable text.")
    @Example("numen.web.fetch(\"https://zh.minecraft.wiki/w/%E5%BC%BA%E5%A0%A1\")")
    @Note("HTML is stripped; scripts and styles are dropped. Very long pages are cut to about "
            + MAX_FETCH_CHARS + " characters.")
    public static Pending<String> fetch(ServerCall call, Fetch args) {
        Pending<String> out = Pending.create();
        CompletableFuture.runAsync(() -> {
            try {
                out.complete(fetchNow(args.url()));
            } catch (Exception e) {
                out.fail(new ApiError(ErrorKind.FAILED, "web fetch failed: " + e.getMessage(), null));
            }
        });
        return out;
    }

    private static String searchNow(String query, int count) throws IOException, InterruptedException {
        String text = query == null ? "" : query.strip();
        if (text.isEmpty()) {
            throw new IOException("query is empty");
        }
        JsonObject body = new JsonObject();
        body.addProperty("query", text);
        HttpRequest request = HttpRequest.newBuilder(URI.create(GATEWAY + "/v1/search"))
                .header("Authorization", "Bearer " + KEY)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("gateway HTTP " + response.statusCode());
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray results = json.has("results") ? json.getAsJsonArray("results") : new JsonArray();
        if (results.isEmpty()) {
            return "没有搜到 \"" + text + "\" 的结果。";
        }
        int limit = Math.max(1, Math.min(10, count));
        StringBuilder out = new StringBuilder("搜索 \"").append(text).append("\" 的结果:");
        for (JsonElement element : results) {
            if (limit-- <= 0) {
                break;
            }
            JsonObject row = element.getAsJsonObject();
            out.append("\n- ").append(str(row, "title")).append('\n').append("  ").append(str(row, "url"));
            String snippet = str(row, "snippet");
            if (!snippet.isBlank()) {
                out.append('\n').append("  ").append(shorten(snippet, 300));
            }
        }
        return out.toString();
    }

    private static String fetchNow(String url) throws IOException, InterruptedException {
        String text = url == null ? "" : url.strip();
        if (!text.startsWith("http://") && !text.startsWith("https://")) {
            throw new IOException("not an http(s) URL: " + text);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(text))
                .header("User-Agent", "Mozilla/5.0 (compatible; numen/1.0)")
                .header("Accept", "text/html,application/xhtml+xml,text/plain")
                .timeout(Duration.ofSeconds(25))
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode());
        }
        String body = SCRIPT.matcher(response.body()).replaceAll(" ");
        body = TAG.matcher(body).replaceAll(" ");
        body = body.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ");
        body = SPACE.matcher(body).replaceAll(" ");
        body = body.replaceAll("(?m)^ +| +$", "");
        body = LINES.matcher(body).replaceAll("\n\n").strip();
        return shorten(body, MAX_FETCH_CHARS);
    }

    private static String str(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 3)) + "...";
    }
}
