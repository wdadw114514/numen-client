package com.dwinovo.numen.core.locate;

import stellarai.lib.seedfinding.mcbiome.source.EndBiomeSource;
import stellarai.lib.seedfinding.mccore.rand.ChunkRand;
import stellarai.lib.seedfinding.mccore.util.data.Pair;
import stellarai.lib.seedfinding.mccore.util.pos.BPos;
import stellarai.lib.seedfinding.mccore.util.pos.CPos;
import stellarai.lib.seedfinding.mccore.version.MCVersion;
import stellarai.lib.seedfinding.mcfeature.structure.EndCity;
import stellarai.lib.seedfinding.mcfeature.structure.generator.Generator;
import stellarai.lib.seedfinding.mcfeature.structure.generator.structure.EndCityGenerator;
import stellarai.lib.seedfinding.mcterrain.terrain.EndTerrainGenerator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 末地城 / 末地船的离线查找——Cubiomes 不模拟末地城生成，这一份走随包带的 seedfinding。
 * 与彗星插件的 {@code EndCityCli} 同一套算法：按区域枚举、验群系与地形、生成结构取船头与鞘翅箱。
 */
public final class EndCityFinder {

    private static final MCVersion VERSION = MCVersion.v1_21_3;

    /** 一座末地城；{@code ship} 为真时 {@code head*} 是船头坐标，否则为 0。 */
    public record City(int x, int z, boolean ship, int headX, int headY, int headZ) {}

    private EndCityFinder() {}

    public static List<City> find(long seed, int centerX, int centerZ, int radiusBlocks, int limit) {
        int radius = Math.max(256, radiusBlocks);
        int cap = Math.max(1, limit);

        EndCity endCity = new EndCity(VERSION);
        EndCityGenerator generator = new EndCityGenerator(VERSION);
        EndBiomeSource source = new EndBiomeSource(VERSION, seed);
        EndTerrainGenerator terrain = new EndTerrainGenerator(source);
        ChunkRand rand = new ChunkRand();

        int spacing = endCity.getSpacing();
        int baseRegionX = Math.floorDiv(centerX >> 4, spacing);
        int baseRegionZ = Math.floorDiv(centerZ >> 4, spacing);
        int regionRadius = radius / (spacing * 16) + 1;

        List<City> cities = new ArrayList<>();
        for (int dx = -regionRadius; dx <= regionRadius; dx++) {
            for (int dz = -regionRadius; dz <= regionRadius; dz++) {
                CPos pos = endCity.getInRegion(seed, baseRegionX + dx, baseRegionZ + dz, rand);
                if (pos == null) continue;
                if (!endCity.canSpawn(pos, source)) continue;
                if (!endCity.canGenerate(pos, terrain)) continue;

                boolean ship = false;
                int headX = 0;
                int headY = 0;
                int headZ = 0;
                try {
                    generator.generate(terrain, pos.getX(), pos.getZ(), rand);
                    ship = generator.hasShip();
                    if (ship) {
                        BPos elytra = null;
                        long sternX = 0;
                        long sternZ = 0;
                        int sternCount = 0;
                        for (Pair<Generator.ILootType, BPos> pair : generator.getChestsPos()) {
                            Generator.ILootType loot = pair.getFirst();
                            BPos block = pair.getSecond();
                            if (loot == EndCityGenerator.LootType.SHIP_ELYTRA) {
                                elytra = block;
                            } else if (loot == EndCityGenerator.LootType.SHIP_SENTRY_2
                                    || loot == EndCityGenerator.LootType.SHIP_SENTRY_3) {
                                sternX += block.getX();
                                sternZ += block.getZ();
                                sternCount++;
                            }
                        }
                        if (elytra != null && sternCount > 0) {
                            int ddx = (int) (sternX / sternCount) - elytra.getX();
                            int ddz = (int) (sternZ / sternCount) - elytra.getZ();
                            headX = elytra.getX();
                            headY = elytra.getY() + 3;
                            headZ = elytra.getZ();
                            if (Math.abs(ddx) > Math.abs(ddz)) {
                                headX += ddx > 0 ? -7 : 7;
                            } else {
                                headZ += ddz > 0 ? -7 : 7;
                            }
                        }
                    }
                } catch (Throwable ignored) {
                    // skip broken cities
                } finally {
                    generator.reset();
                }

                cities.add(new City(pos.getX() * 16 + 8, pos.getZ() * 16 + 8, ship, headX, headY, headZ));
            }
        }

        cities.sort(Comparator.comparingLong(city -> distanceSquared(city.x(), city.z(), centerX, centerZ)));
        return cities.size() <= cap ? cities : new ArrayList<>(cities.subList(0, cap));
    }

    private static long distanceSquared(int x1, int z1, int x2, int z2) {
        long dx = (long) x1 - x2;
        long dz = (long) z1 - z2;
        return dx * dx + dz * dz;
    }
}
