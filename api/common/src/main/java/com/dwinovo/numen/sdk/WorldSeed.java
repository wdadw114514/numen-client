package com.dwinovo.numen.sdk;

/**
 * 当前服务器的世界种子——入服向导第 1 步填的那一格。客户端身体没有服务端世界生成器，
 * 找结构、找矿物这类离线算法只认这一份种子。服务端身体也可以在这里填，留空则退回服务端
 * 自己的世界种子。
 */
public final class WorldSeed {

    private static volatile String seed = "";

    private WorldSeed() {}

    public static String get() {
        return seed;
    }

    public static void set(String value) {
        seed = value == null ? "" : value.strip();
    }

    /** 种子按 long 解析；不是数字（或空）时 null。 */
    public static Long asLong() {
        String value = seed;
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
