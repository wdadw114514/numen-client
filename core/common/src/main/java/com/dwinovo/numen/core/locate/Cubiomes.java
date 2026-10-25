package com.dwinovo.numen.core.locate;

import com.sun.jna.Native;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Loads the bundled cubiomes.dll (patched for 1.21.11) through JNA and exposes a
 * small structure search helper.
 */
public final class Cubiomes {
    /** Structures the Cubiomes backend can answer for. */
    private static final Set<String> SUPPORTED = Set.of(
        "village", "desert_pyramid", "jungle_pyramid", "swamp_hut", "igloo", "mansion",
        "monument", "ocean_ruin", "shipwreck", "pillager_outpost", "ruined_portal",
        "ruined_portal_nether", "buried_treasure", "mineshaft", "stronghold", "end_city",
        "bastion_remnant", "fortress", "ancient_city", "trail_ruins", "trial_chambers",
        "desert_well", "geode"
    );

    private static CubiomesNative lib;
    private static boolean tried;
    private static String error = "";

    private Cubiomes() {}

    public static boolean supports(String type) {
        return type != null && SUPPORTED.contains(type);
    }

    public static synchronized CubiomesNative nativeLib() {
        if (lib != null || tried) return lib;
        tried = true;
        try {
            String custom = System.getProperty("numen.native.dir");
            Path dir = custom != null && !custom.isBlank()
                    ? Path.of(custom)
                    : Path.of(System.getProperty("numen.game.dir", System.getProperty("user.dir")),
                            "numen", "native");
            Files.createDirectories(dir);
            Path dll = dir.resolve("cubiomes.dll");
            try (InputStream in = Cubiomes.class.getResourceAsStream("/numen/native/cubiomes.dll")) {
                if (in == null) throw new IllegalStateException("jar 内没有 cubiomes.dll");
                Files.copy(in, dll, StandardCopyOption.REPLACE_EXISTING);
            }
            lib = Native.load(dll.toAbsolutePath().toString(), CubiomesNative.class);
        } catch (Throwable throwable) {
            error = throwable.toString();
        }
        return lib;
    }

    public static boolean isAvailable() {
        return nativeLib() != null;
    }

    public static String error() {
        return error;
    }

    /** Returns x,z pairs, or null when the backend cannot handle the request. */
    public static List<int[]> find(String type, String mcVersion, long seed,
                                   int centerX, int centerZ, int radiusBlocks, int limit) {
        CubiomesNative nativeLibrary = nativeLib();
        if (nativeLibrary == null) return null;
        String name = cubiomesName(type);
        if (name == null) return null;
        int mc = nativeLibrary.cb_version_id(mcVersion);
        if (mc < 0) return null;

        int[] out = new int[limit * 2];
        int count = nativeLibrary.cb_find_structures(name, mc, seed, centerX, centerZ, radiusBlocks, limit, out);
        if (count < 0) return null;

        List<int[]> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(new int[]{out[2 * i], out[2 * i + 1]});
        return result;
    }

    private static String cubiomesName(String type) {
        if (type == null) return null;
        return switch (type) {
            case "jungle_pyramid" -> "jungle_temple";
            case "bastion_remnant" -> "bastion";
            case "nether_fossil" -> null; // not simulated by Cubiomes
            default -> SUPPORTED.contains(type) ? type : null;
        };
    }
}
