package com.dwinovo.numen.core.tools.wiki;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of the AstrBot "minecraft-wiki" plugin: queries a Minecraft Wiki (MediaWiki API),
 * cleans wikitext and exposes search / summary / section / recipe / command / mechanic lookups.
 */
public final class MinecraftWiki {
    private static final Pattern REDIRECT = Pattern.compile("#(?:redirect|重定向)\\s*\\[\\[(.*?)\\]\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADING = Pattern.compile("^(={2,6})\\s*(.*?)\\s*\\1\\s*$", Pattern.MULTILINE);
    private static final Pattern CRAFTING = Pattern.compile("\\{\\{\\s*(Crafting|合成).*?\\}\\}", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern TEMPLATE_PARAM = Pattern.compile("\\|\\s*([^=|\\n]+?)\\s*=\\s*([^|\\n]+)");
    private static final Pattern INGREDIENT_KEY = Pattern.compile("[abc]?[1-3]|[a-z]\\d|材料\\d+|input\\d*|in\\d*");
    private static final Pattern VERSION_TITLE = Pattern.compile("^(java版|基岩版|携带版|教育版|[a-z]+版指南/)\\d");
    private static final Pattern NOISE = Pattern.compile("\\{\\{|\\}\\}|\\||#REDIRECT|教程性内容|name=|oplevel=");

    private static final Map<String, String> VARIANT_KEYWORDS = Map.of(
        "下界合金", "Netherite", "钻石", "Diamond", "金", "Golden", "铁", "Iron",
        "铜", "Copper", "石", "Stone", "木", "Wooden"
    );

    private static final Map<String, String> TERM_TRANSLATIONS = buildTranslations();

    private record CacheEntry(Object value, long expiresAt) {}

    private final String baseUrl;
    private final int timeoutSeconds;
    private final long cacheTtlMillis;
    private final int maxChars;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public MinecraftWiki(String baseUrl, int timeoutSeconds, long cacheTtlSeconds, int maxChars) {
        this.baseUrl = baseUrl;
        this.timeoutSeconds = Math.max(3, timeoutSeconds);
        this.cacheTtlMillis = Math.max(0, cacheTtlSeconds) * 1000L;
        this.maxChars = Math.max(500, maxChars);
    }

    public String search(String query, int limit) throws IOException, InterruptedException {
        if (query == null || query.isBlank()) return "查询不能为空。";
        JsonObject data = request(mapOf("action", "query", "list", "search", "srsearch", query.strip(),
            "srlimit", String.valueOf(Math.max(1, Math.min(20, limit)))));
        JsonArray results = data.has("query") ? data.getAsJsonObject("query").getAsJsonArray("search") : null;
        if (results == null || results.isEmpty()) return "没有找到与 \"" + query + "\" 相关的 Wiki 页面。";
        StringBuilder builder = new StringBuilder("Wiki 搜索 \"").append(query).append("\":");
        for (JsonElement element : results) {
            JsonObject row = element.getAsJsonObject();
            builder.append("\n- ").append(text(row, "title")).append("：").append(stripHtml(text(row, "snippet")));
        }
        return builder.toString();
    }

    public String summary(String title) throws IOException, InterruptedException {
        String clean = normalizeQuery(title);
        if (clean.isEmpty()) return "页面标题不能为空。";
        String cached = cachedString(clean, "summary");
        if (cached != null) return cached;

        JsonObject page = summaryPage(clean);
        if (page == null) {
            String best = pickBestTitle(searchRows(clean, 5), clean);
            if (!best.isEmpty()) page = summaryPage(best);
        }
        if (page == null) return "没有找到页面 \"" + clean + "\" 的简介。";

        String result = "【" + text(page, "title") + "】简介:\n" + cleanExtract(text(page, "extract"), maxChars);
        store(clean, "summary", result);
        return result;
    }

    public String section(String title, String section) throws IOException, InterruptedException {
        if (title == null || title.isBlank() || section == null || section.isBlank()) return "页面标题和章节名不能为空。";
        String page = title.strip();
        String wikitext = wikitext(page);
        String redirect = resolveRedirectTitle(wikitext);
        if (!redirect.isEmpty()) {
            page = redirect;
            wikitext = wikitext(page);
        }
        JsonArray sections = sections(page);
        String chosen = chooseSectionName(sections, section);
        String content = extractSection(wikitext, chosen, maxChars);
        if (content.isEmpty()) return "页面 \"" + page + "\" 中没有找到章节 \"" + section + "\"。";
        return "【" + page + " · " + chosen + "】:\n" + content;
    }

    public String recipe(String item) throws IOException, InterruptedException {
        String name = normalizeItemName(item);
        if (name.isEmpty()) return "物品名不能为空。";

        String title = resolveItemTitle(name);
        if (title.isEmpty()) {
            List<String> candidates = List.of(name + " 合成", name, name + " 配方");
            List<JsonObject> rows = List.of();
            for (String query : candidates) {
                rows = searchRows(query, 5);
                if (!rows.isEmpty()) break;
            }
            title = pickBestItemTitle(rows, name);
        }
        if (title.isEmpty()) return "没有找到物品 \"" + name + "\" 的配方。";

        String cached = cachedString(title, "recipe");
        if (cached != null) return cached;

        String wikitext = wikitext(title);
        String redirect = resolveRedirectTitle(wikitext);
        if (!redirect.isEmpty()) {
            title = redirect;
            wikitext = wikitext(title);
        }
        String recipe = extractRecipe(wikitext, name, maxChars);
        if (recipe.isEmpty()) return "没有找到物品 \"" + name + "\" 的合成配方。";

        String result = "【" + name + "】合成（来源: " + title + "）:\n" + recipe;
        store(title, "recipe", result);
        return result;
    }

    public String command(String command) throws IOException, InterruptedException {
        String cmd = normalizeCommand(command);
        if (cmd.isEmpty()) return "命令不能为空。";

        String title = resolveCommandTitle(cmd);
        if (title.isEmpty()) {
            List<JsonObject> rows = List.of();
            for (String query : List.of(cmd + " 命令", "命令/" + cmd, cmd)) {
                rows = searchRows(query, 5);
                if (!rows.isEmpty()) break;
            }
            title = pickBestCommandTitle(rows, cmd);
        }
        if (title.isEmpty()) return "没有找到命令 \"" + cmd + "\" 的资料。";

        String wikitext = wikitext(title);
        String redirect = resolveRedirectTitle(wikitext);
        if (!redirect.isEmpty()) {
            title = redirect;
            wikitext = wikitext(title);
        }

        String syntax = extractCommandUsage(wikitext, Math.max(400, maxChars / 2));
        if (isNoisy(syntax)) syntax = fallbackCommandLines(wikitext, cmd, Math.max(400, maxChars / 2));
        String example = extractSection(wikitext, "示例", Math.max(300, maxChars / 3));
        if (example.isEmpty()) example = extractSection(wikitext, "用法", Math.max(300, maxChars / 3));
        if (isNoisy(example)) example = fallbackCommandLines(wikitext, cmd, Math.max(300, maxChars / 3));

        String description = extractMechanicDescription(wikitext, Math.max(400, maxChars / 2));
        if (description.isEmpty() || isNoisy(description)) description = summary(title);

        StringBuilder builder = new StringBuilder("【/" + cmd + "】命令（来源: ").append(title).append("）");
        if (!syntax.isEmpty()) builder.append("\n语法:\n").append(syntax);
        if (!description.isEmpty()) builder.append("\n说明:\n").append(description);
        if (!example.isEmpty()) builder.append("\n示例:\n").append(example);
        return builder.toString();
    }

    public String mechanic(String mechanic) throws IOException, InterruptedException {
        String keyword = normalizeMechanic(mechanic);
        if (keyword.isEmpty()) return "机制名不能为空。";

        String title = resolveMechanicTitle(keyword);
        if (title.isEmpty()) {
            List<String> candidates = new ArrayList<>(List.of(keyword, keyword + " 机制", "Minecraft " + keyword));
            candidates.addAll(mechanicAliases(keyword));
            List<JsonObject> rows = List.of();
            for (String query : candidates) {
                rows = searchRows(query, 5);
                if (!rows.isEmpty()) break;
            }
            title = pickBestMechanicTitle(rows, keyword);
        }
        if (title.isEmpty()) return "没有找到机制 \"" + keyword + "\" 的资料。";

        String wikitext = wikitext(title);
        String redirect = resolveRedirectTitle(wikitext);
        if (!redirect.isEmpty()) {
            title = redirect;
            wikitext = wikitext(title);
        }

        StringBuilder description = new StringBuilder();
        description.append(cleanExtract(extractText(summaryPage(title), "extract"), maxChars / 2));
        for (String name : new String[]{"机制", "原理", "行为", "用途", "生成", "掉落", "交易", "刷怪"}) {
            String content = extractSection(wikitext, name, maxChars / 2);
            if (!content.isEmpty()) {
                if (description.length() > 0) description.append("\n\n");
                description.append(content);
                break;
            }
        }
        String fallback = extractMechanicDescription(wikitext, maxChars);
        if (description.length() == 0) description.append(fallback);
        return "【" + keyword + "】机制（来源: " + title + "）:\n" + cleanupMechanicText(description.toString(), maxChars);
    }

    public String fullPage(String title) throws IOException, InterruptedException {
        if (title == null || title.isBlank()) return "页面标题不能为空。";
        String page = title.strip();
        String wikitext = wikitext(page);
        String redirect = resolveRedirectTitle(wikitext);
        if (!redirect.isEmpty()) {
            page = redirect;
            wikitext = wikitext(page);
        }
        StringBuilder builder = new StringBuilder("【").append(page).append("】整页内容:");
        JsonArray sections = sections(page);
        if (sections != null && !sections.isEmpty()) {
            builder.append("\n章节目录:");
            for (JsonElement element : sections) {
                builder.append("\n  ").append(text(element.getAsJsonObject(), "number")).append(" ")
                    .append(text(element.getAsJsonObject(), "line"));
            }
        }
        builder.append("\n\n").append(cleanWikitext(wikitext, Math.max(maxChars, maxChars * 4)));
        return builder.toString();
    }

    private JsonObject summaryPage(String title) throws IOException, InterruptedException {
        JsonObject data = request(mapOf("action", "query", "prop", "extracts", "exintro", "1", "titles", title));
        if (data.has("error") || !data.has("query")) return null;
        JsonObject pages = data.getAsJsonObject("query").getAsJsonObject("pages");
        for (Map.Entry<String, JsonElement> entry : pages.entrySet()) {
            JsonObject page = entry.getValue().getAsJsonObject();
            if (page.has("missing")) return null;
            return page;
        }
        return null;
    }

    private String wikitext(String title) throws IOException, InterruptedException {
        JsonObject data = request(mapOf("action", "parse", "page", title, "prop", "wikitext"));
        if (data.has("error") || !data.has("parse")) throw new IOException("Wiki 页面不存在: " + title);
        JsonElement wikitext = data.getAsJsonObject("parse").get("wikitext");
        if (wikitext == null || wikitext.isJsonNull()) return "";
        if (wikitext.isJsonObject()) return text(wikitext.getAsJsonObject(), "*");
        return wikitext.getAsString();
    }

    private JsonArray sections(String title) throws IOException, InterruptedException {
        JsonObject data = request(mapOf("action", "parse", "page", title, "prop", "sections"));
        if (data.has("error") || !data.has("parse")) return new JsonArray();
        JsonObject parse = data.getAsJsonObject("parse");
        return parse.has("sections") ? parse.getAsJsonArray("sections") : new JsonArray();
    }

    private List<JsonObject> searchRows(String query, int limit) throws IOException, InterruptedException {
        JsonObject data = request(mapOf("action", "query", "list", "search", "srsearch", query,
            "srlimit", String.valueOf(Math.max(1, Math.min(20, limit)))));
        if (data.has("error") || !data.has("query")) return List.of();
        JsonArray search = data.getAsJsonObject("query").getAsJsonArray("search");
        if (search == null) return List.of();
        List<JsonObject> rows = new ArrayList<>();
        for (JsonElement element : search) rows.add(element.getAsJsonObject());
        return rows;
    }

    private JsonObject request(Map<String, String> params) throws IOException, InterruptedException {
        StringBuilder url = new StringBuilder(baseUrl);
        url.append(baseUrl.contains("?") ? '&' : '?').append("format=json");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            url.append('&').append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .header("User-Agent", "numen/1.0")
            .header("Accept", "application/json")
            .GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IOException("Wiki HTTP " + response.statusCode());
        JsonElement parsed = JsonParser.parseString(response.body());
        if (!parsed.isJsonObject()) throw new IOException("Wiki 返回了非 JSON 数据。");
        return parsed.getAsJsonObject();
    }

    private String resolveItemTitle(String item) throws IOException, InterruptedException {
        for (String title : List.of(item, item.replace(" ", ""), item + "（物品）")) {
            try {
                wikitext(title);
                return title;
            } catch (IOException ignored) {
                // try next
            }
        }
        return "";
    }

    private String resolveCommandTitle(String command) throws IOException, InterruptedException {
        for (String title : List.of("命令/" + command, "命令/" + command.toLowerCase(), command)) {
            try {
                wikitext(title);
                return title;
            } catch (IOException ignored) {
                // try next
            }
        }
        return "";
    }

    private String resolveMechanicTitle(String mechanic) throws IOException, InterruptedException {
        List<String> titles = new ArrayList<>();
        titles.add(mechanic);
        titles.addAll(mechanicAliases(mechanic));
        for (String title : titles) {
            try {
                wikitext(title);
                return title;
            } catch (IOException ignored) {
                // try next
            }
        }
        return "";
    }

    private static List<String> mechanicAliases(String mechanic) {
        return switch (mechanic) {
            case "刷怪机制" -> List.of("生物生成", "生成", "刷怪");
            case "掉落机制" -> List.of("掉落", "掉落物");
            case "村民交易机制" -> List.of("交易", "村民");
            case "伤害机制" -> List.of("伤害");
            default -> List.of();
        };
    }

    private static String normalizeQuery(String title) {
        String text = title == null ? "" : title.strip();
        if (text.isEmpty()) return "";
        text = strip(text, " \t\r\n\"'“”‘’。！？?!");
        text = text.replaceFirst("^(请问|问下|想问下|我想知道|请教一下|帮我查下)\\s*", "");
        text = text.replaceFirst("(?:是什么|是啥|什么意思|介绍一下|是什么东西)$", "").strip();
        return text;
    }

    private static String normalizeItemName(String item) {
        String text = item == null ? "" : item.strip();
        if (text.isEmpty()) return "";
        text = strip(text, " \t\r\n\"'“”‘’。！？?!");
        text = text.replaceFirst("^(请问|问下|想问下|我想知道|请教一下)\\s*", "");
        for (Pattern pattern : List.of(
            Pattern.compile("^(.+?)\\s*的?\\s*(?:合成)?配方(?:是|是什么|是多少)?$"),
            Pattern.compile("^(.+?)\\s*(?:怎么|如何)(?:合成|制作)$"),
            Pattern.compile("^(.+?)\\s*(?:配方|合成)$"))) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.matches()) {
                String candidate = strip(matcher.group(1), " \t\r\n\"'“”‘’。！？?!");
                if (!candidate.isEmpty()) return candidate;
            }
        }
        return text;
    }

    private static String normalizeCommand(String command) {
        String cmd = command == null ? "" : command.strip().toLowerCase();
        cmd = cmd.replaceFirst("^(请问|问下|想问下|我想知道|请教一下)\\s*", "");
        cmd = cmd.replace("命令", "").replace("指令", "").strip();
        cmd = cmd.replaceFirst("(怎么用|如何使用|是什么|用法|语法|参数)$", "").strip();
        Matcher slash = Pattern.compile("/(\\w+)").matcher(cmd);
        if (slash.find()) return slash.group(1);
        Matcher word = Pattern.compile("\\b([a-z_][a-z0-9_]*)\\b").matcher(cmd);
        if (word.find()) return word.group(1);
        return cmd.startsWith("/") ? cmd.substring(1) : cmd;
    }

    private static String normalizeMechanic(String mechanic) {
        String text = mechanic == null ? "" : mechanic.strip();
        if (text.isEmpty()) return "";
        text = strip(text, " \t\r\n\"'“”‘’。！？?!");
        text = text.replaceFirst("^(请问|问下|想问下|我想知道|请教一下)\\s*", "");
        text = text.replaceFirst("(是什么|是啥|什么意思|介绍一下|怎么回事)$", "").strip();
        return text.replaceAll("\\s+", " ");
    }

    private static String pickBestTitle(List<JsonObject> rows, String query) {
        if (rows.isEmpty()) return "";
        List<String> tokens = new ArrayList<>();
        for (String token : query.split("\\s+")) if (!token.isEmpty()) tokens.add(token);
        JsonObject best = rows.get(0);
        int bestScore = -1;
        for (JsonObject row : rows) {
            String title = text(row, "title");
            String snippet = stripHtml(text(row, "snippet"));
            int score = 0;
            for (String token : tokens) {
                if (title.contains(token)) score += 3;
                else if (snippet.contains(token)) score += 1;
            }
            if (score > bestScore) {
                bestScore = score;
                best = row;
            }
        }
        return text(best, "title");
    }

    private static String pickBestItemTitle(List<JsonObject> rows, String item) {
        if (rows.isEmpty()) return "";
        String keyword = item.toLowerCase();
        for (JsonObject row : rows) {
            String title = text(row, "title");
            if (title.toLowerCase().contains(keyword)) return title;
        }
        for (JsonObject row : rows) {
            String title = text(row, "title");
            String lower = title.toLowerCase();
            if (!VERSION_TITLE.matcher(lower).find() && !title.contains("指南/")) return title;
        }
        return text(rows.get(0), "title");
    }

    private static String pickBestCommandTitle(List<JsonObject> rows, String command) {
        if (rows.isEmpty()) return "";
        String lower = command.toLowerCase();
        for (JsonObject row : rows) {
            String title = text(row, "title").toLowerCase();
            if (title.contains("命令/" + lower) || title.equals(lower)) return text(row, "title");
        }
        for (JsonObject row : rows) {
            String title = text(row, "title");
            if (title.toLowerCase().contains(lower) && !VERSION_TITLE.matcher(title.toLowerCase()).find()) return title;
        }
        return text(rows.get(0), "title");
    }

    private static String pickBestMechanicTitle(List<JsonObject> rows, String mechanic) {
        if (rows.isEmpty()) return "";
        String keyword = mechanic.toLowerCase();
        for (JsonObject row : rows) {
            if (text(row, "title").toLowerCase().contains(keyword)) return text(row, "title");
        }
        return text(rows.get(0), "title");
    }

    private static String chooseSectionName(JsonArray sections, String target) {
        String normalized = target.toLowerCase().replaceAll("\\s+", "");
        if (sections != null) {
            for (JsonElement element : sections) {
                String line = text(element.getAsJsonObject(), "line");
                String normalizedLine = line.toLowerCase().replaceAll("\\s+", "");
                if (normalizedLine.contains(normalized) || normalized.contains(normalizedLine)) return line;
            }
        }
        return target;
    }

    private static String cleanExtract(String text, int maxChars) {
        String cleaned = (text == null ? "" : text).replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").strip();
        return limitText(cleaned, maxChars);
    }

    private static String cleanWikitext(String text, int maxChars) {
        if (text == null || text.isEmpty()) return "";
        text = replace(text, "\\{\\{cmd\\|(?:long=\\d\\|)?(?:link=none\\|)?([^{}|]+)\\}\\}", "$1", Pattern.CASE_INSENSITIVE);
        text = replace(text, "\\{\\{cd\\|([^{}|]+)\\}\\}", "$1", Pattern.CASE_INSENSITIVE);
        text = replace(text, "\\{\\{[Cc]ollapse\\|title=.*?\\|content=(.*?)\\}\\}", "$1", Pattern.DOTALL);
        text = replace(text, "\\{\\{[^{}|]+\\|([^{}]+?)\\}\\}", "$1", 0);
        text = replace(text, "<!--.*?-->", "", Pattern.DOTALL);
        text = replace(text, "<ref[^>]*>.*?</ref>", "", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        text = replace(text, "<ref[^/]*/>", "", Pattern.CASE_INSENSITIVE);
        text = replace(text, "<[^>]+>", "", 0);
        text = stripTemplates(text);
        text = replace(text, "\\[\\[(?:[^\\]|]+\\|)?([^\\]]+)\\]\\]", "$1", 0);
        text = replace(text, "\\[https?://[^\\s\\]]+\\s+([^\\]]+)\\]", "$1", 0);
        text = replace(text, "\\[https?://[^\\s\\]]+\\]", "", 0);
        text = replace(text, "'{2,}", "", 0);
        text = replace(text, "-\\{([^{}]+)\\}-", "$1", 0);
        text = replace(text, "\\*:\\s*", "", 0);
        text = replace(text, "^\\s*=+\\s*(.*?)\\s*=+\\s*$", "$1", Pattern.MULTILINE);
        text = replace(text, "\\n{3,}", "\n\n", 0);
        text = replace(text, "[ \t]{2,}", " ", 0);
        return limitText(text.strip(), maxChars);
    }

    private static String extractSection(String wikitext, String sectionName, int maxChars) {
        if (wikitext == null || wikitext.isEmpty() || sectionName == null || sectionName.isEmpty()) return "";
        Matcher matcher = HEADING.matcher(wikitext);
        List<Integer> ends = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        List<String> heads = new ArrayList<>();
        while (matcher.find()) {
            ends.add(matcher.end());
            starts.add(matcher.start());
            heads.add(matcher.group(2));
        }
        if (ends.isEmpty()) return "";
        String target = sectionName.strip().toLowerCase();
        for (int i = 0; i < ends.size(); i++) {
            String heading = heads.get(i).replaceAll("\\s+", "").toLowerCase();
            if (heading.contains(target) || target.contains(heading)) {
                int start = ends.get(i);
                int end = i + 1 < ends.size() ? starts.get(i + 1) : wikitext.length();
                return cleanWikitext(wikitext.substring(start, end), maxChars);
            }
        }
        return "";
    }

    private static String extractCommandUsage(String wikitext, int maxChars) {
        for (String name : new String[]{"语法", "Syntax", "命令格式", "参数", "用法"}) {
            String content = extractSection(wikitext, name, maxChars);
            if (!content.isEmpty()) return content;
        }
        List<String> lines = new ArrayList<>();
        for (String line : (wikitext == null ? "" : wikitext).split("\n", -1)) {
            String striped = line.strip();
            if (striped.startsWith("/") || striped.contains("<player>") || striped.contains("<targets>")) lines.add(striped);
            if (lines.size() >= 12) break;
        }
        return cleanWikitext(String.join("\n", lines), maxChars);
    }

    private static String extractRecipe(String wikitext, String itemName, int maxChars) {
        Matcher templateMatch = CRAFTING.matcher(wikitext == null ? "" : wikitext);
        if (templateMatch.find()) {
            String template = templateMatch.group(0);
            Map<String, String> params = new LinkedHashMap<>();
            Matcher paramMatcher = TEMPLATE_PARAM.matcher(template);
            while (paramMatcher.find()) params.put(paramMatcher.group(1).strip(), paramMatcher.group(2));
            List<String> positional = new ArrayList<>();
            for (String rawLine : template.split("\n", -1)) {
                String line = rawLine.strip();
                if (!line.startsWith("|")) continue;
                String body = line.substring(1).strip();
                if (body.isEmpty() || body.contains("=")) continue;
                positional.add(body);
            }

            int outputIndex = 0;
            String output = "";
            for (String key : new String[]{"Output", "output", "产物", "结果"}) {
                if (params.containsKey(key) && !params.get(key).strip().isEmpty()) {
                    String rawOutput = params.get(key).strip();
                    outputIndex = inferOutputIndex(itemName, rawOutput);
                    output = translateTerms(cleanWikitext(pickVariant(rawOutput, outputIndex), 120));
                    break;
                }
            }

            List<String> ingredients = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String normalizedKey = entry.getKey().strip().toLowerCase();
                if (INGREDIENT_KEY.matcher(normalizedKey).matches() && !entry.getValue().strip().isEmpty()) {
                    String cleaned = translateTerms(cleanWikitext(pickVariant(entry.getValue().strip(), outputIndex), 120));
                    if (!cleaned.isEmpty() && seen.add(cleaned)) ingredients.add(cleaned);
                }
            }
            for (String value : positional) {
                String cleaned = translateTerms(cleanWikitext(value, 120));
                if (!cleaned.isEmpty() && seen.add(cleaned)) ingredients.add(cleaned);
            }

            List<String> lines = new ArrayList<>();
            if (!output.isEmpty()) lines.add("合成产物: " + output);
            if (!ingredients.isEmpty()) {
                lines.add("材料: " + String.join("、", ingredients.subList(0, Math.min(9, ingredients.size()))));
            }
            if (!lines.isEmpty()) return limitText(String.join("\n", lines), maxChars);
        }

        for (String name : new String[]{"合成", "配方", "Crafting", "用途", "获取"}) {
            String content = extractSection(wikitext, name, maxChars);
            if (!content.isEmpty()) return content;
        }

        List<String> recipeLines = new ArrayList<>();
        for (String line : (wikitext == null ? "" : wikitext).split("\n", -1)) {
            String lower = line.toLowerCase();
            if (lower.contains("recipe") || lower.contains("craft") || line.contains("合成")) recipeLines.add(line);
            if (recipeLines.size() >= 14) break;
        }
        return cleanWikitext(String.join("\n", recipeLines), maxChars);
    }

    private static String extractMechanicDescription(String wikitext, int maxChars) {
        for (String name : new String[]{"机制", "原理", "行为", "用途", "生成", "交易", "刷怪", "掉落"}) {
            String content = extractSection(wikitext, name, maxChars);
            if (!content.isEmpty()) return content;
        }
        return cleanWikitext(wikitext, maxChars);
    }

    private static String fallbackCommandLines(String wikitext, String command, int maxChars) {
        Pattern slash = Pattern.compile("/" + Pattern.quote(command) + "\\b.*", Pattern.CASE_INSENSITIVE);
        Pattern word = Pattern.compile("\\b" + Pattern.quote(command) + "\\b.*", Pattern.CASE_INSENSITIVE);
        List<String> lines = new ArrayList<>();
        for (String rawLine : (wikitext == null ? "" : wikitext).split("\n", -1)) {
            String line = rawLine.strip();
            if (slash.matcher(line).find() || word.matcher(line).find()) {
                String cleaned = cleanWikitext(line, 240);
                if (!cleaned.isEmpty()) lines.add(cleaned);
            }
            if (lines.size() >= 8) break;
        }
        return cleanWikitext(String.join("\n", lines), maxChars);
    }

    private static String cleanupMechanicText(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        for (String rawLine : (text == null ? "" : text).split("\n", -1)) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#") || line.contains("|reason=")) continue;
            if (count(line, '|') >= 2) continue;
            if (count(line, ';') >= 2 && !line.contains("。")) continue;
            lines.add(line);
        }
        return limitText(String.join("\n\n", lines), maxChars);
    }

    private static boolean isNoisy(String text) {
        if (text == null || text.isEmpty()) return true;
        Matcher matcher = NOISE.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
            if (count >= 2) return true;
        }
        return false;
    }

    private static String resolveRedirectTitle(String wikitext) {
        Matcher matcher = REDIRECT.matcher(wikitext == null ? "" : wikitext);
        return matcher.find() ? matcher.group(1).strip() : "";
    }

    private static String pickVariant(String value, int targetIndex) {
        String[] parts = value.split(";");
        if (parts.length > targetIndex) return parts[targetIndex].strip();
        return value.strip();
    }

    private static int inferOutputIndex(String itemName, String rawOutput) {
        String[] outputs = rawOutput.split(";");
        if (itemName == null || itemName.isEmpty()) return 0;
        String normalized = cleanWikitext(itemName, 120).toLowerCase();
        for (int i = 0; i < outputs.length; i++) {
            if (cleanWikitext(outputs[i], 120).toLowerCase().contains(normalized)) return i;
        }
        for (Map.Entry<String, String> entry : VARIANT_KEYWORDS.entrySet()) {
            if (itemName.contains(entry.getKey())) {
                for (int i = 0; i < outputs.length; i++) {
                    if (cleanWikitext(outputs[i], 120).toLowerCase().contains(entry.getValue().toLowerCase())) return i;
                }
            }
        }
        return 0;
    }

    private static String translateTerms(String text) {
        String translated = text;
        for (Map.Entry<String, String> entry : TERM_TRANSLATIONS.entrySet()) {
            translated = translated.replace(entry.getKey(), entry.getValue());
        }
        return translated;
    }

    private static String stripTemplates(String text) {
        String previous = null;
        String current = text;
        while (!current.equals(previous)) {
            previous = current;
            current = replace(current, "\\{\\{[^{}]*\\}\\}", "", 0);
        }
        return current;
    }

    private static String replace(String text, String regex, String replacement, int flags) {
        return Pattern.compile(regex, flags).matcher(text).replaceAll(replacement);
    }

    private static String limitText(String text, int maxChars) {
        String value = text == null ? "" : text.strip();
        if (value.length() <= maxChars) return value;
        return value.substring(0, Math.max(0, maxChars - 3)).stripTrailing() + "...";
    }

    private static String stripHtml(String text) {
        return (text == null ? "" : text).replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").strip();
    }

    private static String strip(String text, String chars) {
        int start = 0;
        int end = text.length();
        while (start < end && chars.indexOf(text.charAt(start)) >= 0) start++;
        while (end > start && chars.indexOf(text.charAt(end - 1)) >= 0) end--;
        return text.substring(start, end);
    }

    private static int count(String text, char target) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == target) total++;
        return total;
    }

    private static String text(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        return object.get(key).getAsString();
    }

    private static String extractText(JsonObject object, String key) {
        return text(object, key);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Map<String, String> mapOf(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    private static Map<String, String> buildTranslations() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Wooden Sword", "木剑");
        map.put("Stone Sword", "石剑");
        map.put("Copper Sword", "铜剑");
        map.put("Iron Sword", "铁剑");
        map.put("Golden Sword", "金剑");
        map.put("Diamond Sword", "钻石剑");
        map.put("Netherite Sword", "下界合金剑");
        map.put("Diamond", "钻石");
        map.put("Stick", "木棍");
        map.put("Iron Ingot", "铁锭");
        map.put("Gold Ingot", "金锭");
        map.put("Copper Ingot", "铜锭");
        map.put("Any Planks", "任意木板");
        map.put("Any stone-tier block", "任意石质材料");
        map.put("Rabbit Stew", "兔肉煲");
        map.put("Cooked Rabbit", "熟兔肉");
        map.put("Carrot", "胡萝卜");
        map.put("Baked Potato", "烤马铃薯");
        map.put("Any Mushroom", "任意蘑菇");
        map.put("Bowl", "碗");
        return map;
    }

    private String cachedString(String key, String field) {
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;
        if (entry.expiresAt() < System.currentTimeMillis()) {
            cache.remove(key);
            return null;
        }
        if (entry.value() instanceof Map<?, ?> map && map.get(field) instanceof String value) return value;
        return null;
    }

    @SuppressWarnings("unchecked")
    private void store(String key, String field, String value) {
        CacheEntry existing = cache.get(key);
        Map<String, String> fields = new LinkedHashMap<>();
        if (existing != null && existing.value() instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String k && entry.getValue() instanceof String v) fields.put(k, v);
            }
        }
        fields.put(field, value);
        cache.put(key, new CacheEntry(fields, System.currentTimeMillis() + cacheTtlMillis));
    }
}
