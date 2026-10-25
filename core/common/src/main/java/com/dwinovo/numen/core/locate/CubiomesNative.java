package com.dwinovo.numen.core.locate;

import com.sun.jna.Library;

/** JNA binding for the flat C API exported by cubiomes.dll. */
public interface CubiomesNative extends Library {
    /** Returns the number of results written into {@code out} as x,z pairs, or -1. */
    int cb_find_structures(String structure, int mc, long seed, int centerX, int centerZ,
                           int radiusBlocks, int limit, int[] out);

    int cb_find_strongholds(int mc, long seed, int centerX, int centerZ, int limit, int[] out);

    int cb_version_id(String name);

    int cb_newest_version();
}
