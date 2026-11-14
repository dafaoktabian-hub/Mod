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
    public static boolean espBox = true;
    public static boolean tracer = true;
    public static boolean showList = true;

    public static int mineSpeed = 1;
    public static int mineSmooth = 1;
    public static boolean mineJitter = true;
    public static boolean autoWalk = true;
    public static boolean panic = true;
    public static int panicRadius = 1;

    public static boolean showCoords = true;
    public static boolean showFps = false;
    public static boolean autoSprint = false;
    public static boolean watermark = true;

    public static BlockPos pos1 = null;
    public static BlockPos pos2 = null;
    public static BlockPos base = null;

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

    private static boolean bool(Properties p, String key, boolean def) {
        return Boolean.parseBoolean(p.getProperty(key, String.valueOf(def)));
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(f)) {
                p.load(in);
            }
            hitEnabled = bool(p, "hitEnabled", true);
            hitIndex = num(p, "hitIndex", 0);
            hitVol = num(p, "hitVol", 100);
            songIndex = num(p, "songIndex", 0);
            songVol = num(p, "songVol", 100);
            showSpawner = bool(p, "showSpawner", true);
            showChest = bool(p, "showChest", true);
            espBox = bool(p, "espBox", true);
            tracer = bool(p, "tracer", true);
            showList = bool(p, "showList", true);
            mineSpeed = Math.floorMod(num(p, "mineSpeed", 1), 3);
            mineSmooth = Math.floorMod(num(p, "mineSmooth", 1), 3);
            mineJitter = bool(p, "mineJitter", true);
            autoWalk = bool(p, "autoWalk", true);
            panic = bool(p, "panic", true);
            panicRadius = Math.floorMod(num(p, "panicRadius", 1), 4);
            showCoords = bool(p, "showCoords", true);
            showFps = bool(p, "showFps", false);
            autoSprint = bool(p, "autoSprint", false);
            watermark = bool(p, "watermark", true);
            pos1 = parse(p.getProperty("pos1"));
            pos2 = parse(p.getProperty("pos2"));
            base = parse(p.getProperty("base"));
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
            p.setProperty("espBox", String.valueOf(espBox));
            p.setProperty("tracer", String.valueOf(tracer));
            p.setProperty("showList", String.valueOf(showList));
            p.setProperty("mineSpeed", String.valueOf(mineSpeed));
            p.setProperty("mineSmooth", String.valueOf(mineSmooth));
            p.setProperty("mineJitter", String.valueOf(mineJitter));
            p.setProperty("autoWalk", String.valueOf(autoWalk));
            p.setProperty("panic", String.valueOf(panic));
            p.setProperty("panicRadius", String.valueOf(panicRadius));
            p.setProperty("showCoords", String.valueOf(showCoords));
            p.setProperty("showFps", String.valueOf(showFps));
            p.setProperty("autoSprint", String.valueOf(autoSprint));
            p.setProperty("watermark", String.valueOf(watermark));
            p.setProperty("pos1", fmt(pos1));
            p.setProperty("pos2", fmt(pos2));
            p.setProperty("base", fmt(base));
            try (OutputStream out = Files.newOutputStream(file())) {
                p.store(out, "Polino Client");
            }
        } catch (Exception ignored) {
        }
    }
}
