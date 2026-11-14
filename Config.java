package com.hitmusic;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Config {
    public static boolean hitEnabled = true;
    public static int hitIndex = 0;
    public static int hitVol = 100;
    public static int songIndex = 0;
    public static int songVol = 100;
    public static boolean showSpawner = true;
    public static boolean showChest = true;
    public static boolean autoWalk = true;
    public static BlockPos pos1 = null;
    public static BlockPos pos2 = null;

    private Config() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("hitmusic.properties");
    }

    public static int nextVol(int v) {
        if (v >= 100) return 75;
        if (v >= 75) return 50;
        if (v >= 50) return 25;
        return 100;
    }

    private static String fmt(BlockPos p) {
        return p == null ? "" : p.getX() + "," + p.getY() + "," + p.getZ();
    }

    private static BlockPos parse(String s) {
        try {
            if (s == null || s.isEmpty()) return null;
            String[] a = s.split(",");
            return new BlockPos(Integer.parseInt(a[0].trim()), Integer.parseInt(a[1].trim()), Integer.parseInt(a[2].trim()));
        } catch (Exception e) {
            return null;
        }
    }

    private static int num(Properties p, String key, int def) {
        try {
            return Integer.parseInt(p.getProperty(key, String.valueOf(def)).trim());
        } catch (Exception e) {
            return def;
        }
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(f)) {
                p.load(in);
            }
            hitEnabled = Boolean.parseBoolean(p.getProperty("hitEnabled", "true"));
            hitIndex = num(p, "hitIndex", 0);
            hitVol = num(p, "hitVol", 100);
            songIndex = num(p, "songIndex", 0);
            songVol = num(p, "songVol", 100);
            showSpawner = Boolean.parseBoolean(p.getProperty("showSpawner", "true"));
            showChest = Boolean.parseBoolean(p.getProperty("showChest", "true"));
            autoWalk = Boolean.parseBoolean(p.getProperty("autoWalk", "true"));
            pos1 = parse(p.getProperty("pos1"));
            pos2 = parse(p.getProperty("pos2"));
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        try {
            Properties p = new Properties();
            p.setProperty("hitEnabled", String.valueOf(hitEnabled));
            p.setProperty("hitIndex", String.valueOf(hitIndex));
            p.setProperty("hitVol", String.valueOf(hitVol));
            p.setProperty("songIndex", String.valueOf(songIndex));
            p.setProperty("songVol", String.valueOf(songVol));
            p.setProperty("showSpawner", String.valueOf(showSpawner));
            p.setProperty("showChest", String.valueOf(showChest));
            p.setProperty("autoWalk", String.valueOf(autoWalk));
            p.setProperty("pos1", fmt(pos1));
            p.setProperty("pos2", fmt(pos2));
            try (OutputStream out = Files.newOutputStream(file())) {
                p.store(out, "Hit & Music");
            }
        } catch (Exception ignored) {
        }
    }
}
