package com.dwinovo.numen.core.tools.wiki;

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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * {@code numen.wiki}:中文 Minecraft Wiki 查询——搜索、简介、章节、配方、命令、机制、整页。
 * 与彗星插件的 minecraft-wiki 同一套 MediaWiki 解析，所有请求在后台线程上做。
 */
public final class WikiApi {

    private static final String API = "https://zh.minecraft.wiki/api.php";
    private static volatile MinecraftWiki wiki;

    private WikiApi() {}

    public static void install(NumenApi numen) {
        numen.api("wiki", "Looking things up on the Minecraft Wiki.", WikiApi.class);
    }

    private static MinecraftWiki wiki() {
        MinecraftWiki value = wiki;
        if (value == null) {
            synchronized (WikiApi.class) {
                value = wiki;
                if (value == null) {
                    value = new MinecraftWiki(API, 15, 86400, 3800);
                    wiki = value;
                }
            }
        }
        return value;
    }

    private static Pending<String> async(WikiCall body) {
        Pending<String> out = Pending.create();
        CompletableFuture.runAsync(() -> {
            try {
                out.complete(body.run(wiki()));
            } catch (Exception e) {
                out.fail(new ApiError(ErrorKind.FAILED, "wiki lookup failed: " + e.getMessage(), null));
            }
        });
        return out;
    }

    @FunctionalInterface
    private interface WikiCall {
        String run(MinecraftWiki wiki) throws Exception;
    }

    /** 搜什么、要几条。 */
    public record Search(@Doc("What to search the wiki for.") String query,
                         @Doc("How many pages to list (1-20).") @Omitted("5") Optional<Integer> limit) {}

    @Fn("Search the Minecraft Wiki; returns page titles and snippets.")
    @Example("numen.wiki.search(\"下界合金\")")
    @SeeAlso({"numen.wiki.summary", "numen.wiki.page"})
    public static Pending<String> search(ServerCall call, Search args) {
        return async(w -> w.search(args.query(), args.limit().orElse(5)));
    }

    /** 看哪一页的简介。 */
    public record Summary(@Doc("Page title, e.g. 钻石 or 下界合金.") String title) {}

    @Fn("Read the opening summary of a wiki page.")
    @Example("numen.wiki.summary(\"钻石\")")
    public static Pending<String> summary(ServerCall call, Summary args) {
        return async(w -> w.summary(args.title()));
    }

    /** 看哪一页的哪一节。 */
    public record Section(@Doc("Page title.") String title,
                          @Doc("Section name, e.g. 获取 or 用途.") String section) {}

    @Fn("Read one section of a wiki page.")
    @Example("numen.wiki.section(\"钻石\", \"获取\")")
    public static Pending<String> section(ServerCall call, Section args) {
        return async(w -> w.section(args.title(), args.section()));
    }

    /** 哪件东西的配方。 */
    public record Recipe(@Doc("Item name, e.g. 钻石剑.") String item) {}

    @Fn("Read an item's crafting recipe from the wiki.")
    @Example("numen.wiki.recipe(\"钻石剑\")")
    public static Pending<String> recipe(ServerCall call, Recipe args) {
        return async(w -> w.recipe(args.item()));
    }

    /** 哪条命令。 */
    public record Command(@Doc("Command name without the slash, e.g. locate.") String command) {}

    @Fn("Read a command's syntax, description and examples from the wiki.")
    @Example("numen.wiki.command(\"locate\")")
    public static Pending<String> command(ServerCall call, Command args) {
        return async(w -> w.command(args.command()));
    }

    /** 哪个机制。 */
    public record Mechanic(@Doc("Mechanic name, e.g. 刷怪机制.") String mechanic) {}

    @Fn("Read how a game mechanic works from the wiki.")
    @Example("numen.wiki.mechanic(\"刷怪机制\")")
    public static Pending<String> mechanic(ServerCall call, Mechanic args) {
        return async(w -> w.mechanic(args.mechanic()));
    }

    /** 哪一页的整页。 */
    public record Page(@Doc("Page title.") String title) {}

    @Fn("Read a whole wiki page: section list plus cleaned text.")
    @Example("numen.wiki.page(\"末地城\")")
    @Note("Long pages are cut; prefer numen.wiki.summary or numen.wiki.section when you only need one part.")
    public static Pending<String> page(ServerCall call, Page args) {
        return async(w -> w.fullPage(args.title()));
    }
}
