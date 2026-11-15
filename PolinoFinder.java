package com.hitmusic;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PolinoFinder {
    public static final class Entry {
        public final BlockPos pos;
        public final boolean spawner;
        public final double dist;
        public final String dir;

        Entry(BlockPos pos, boolean spawner, double dist, String dir) {
            this.pos = pos;
            this.spawner = spawner;
            this.dist = dist;
            this.dir = dir;
        }
    }

    public static final List<Entry> results = new ArrayList<>();
    public static int spawnerCount = 0;
    public static int chestCount = 0;
    private static int ticks = 0;

    private static final String[] DIRS = {"Utara", "Timur Laut", "Timur", "Tenggara", "Selatan", "Barat Daya", "Barat", "Barat Laut"};

    private PolinoFinder() {}

    public static String compass(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        int idx = (int) Math.round(angle / 45.0);
        return DIRS[Math.floorMod(idx, 8)];
    }

    public static void tick(MinecraftClient mc) {
        if (!PolinoConfig.showSpawner && !PolinoConfig.showChest) {
            results.clear();
            spawnerCount = 0;
            chestCount = 0;
            return;
        }
        ticks++;
        if (ticks % 20 != 0) return;
        ClientWorld w = mc.world;
        ClientPlayerEntity p = mc.player;
        if (w == null || p == null) {
            results.clear();
            spawnerCount = 0;
            chestCount = 0;
            return;
        }
        try {
            List<Entry> found = new ArrayList<>();
            int sc = 0;
            int cc = 0;
            int r = Math.min(mc.options.getViewDistance().getValue(), 16);
            int pcx = p.getBlockPos().getX() >> 4;
            int pcz = p.getBlockPos().getZ() >> 4;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    WorldChunk chunk = w.getChunkManager().getWorldChunk(pcx + dx, pcz + dz);
                    if (chunk == null) continue;
                    for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                        boolean spawner = be instanceof MobSpawnerBlockEntity;
                        boolean chest = be instanceof ChestBlockEntity;
                        if ((spawner && PolinoConfig.showSpawner) || (chest && PolinoConfig.showChest)) {
                            BlockPos bp = be.getPos();
                            double ex = bp.getX() + 0.5 - p.getX();
                            double ey = bp.getY() + 0.5 - p.getY();
                            double ez = bp.getZ() + 0.5 - p.getZ();
                            double dist = Math.sqrt(ex * ex + ey * ey + ez * ez);
                            found.add(new Entry(bp, spawner, dist, compass(ex, ez)));
                            if (spawner) sc++;
                            else cc++;
                        }
                    }
                }
            }
            found.sort(Comparator.comparingDouble(e -> e.dist));
            results.clear();
            results.addAll(found);
            spawnerCount = sc;
            chestCount = cc;
        } catch (Exception ignored) {
        }
    }
}
