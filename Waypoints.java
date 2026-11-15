package com.hitmusic;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class Waypoints {
    public static final class WP {
        public String name = "WP";
        public int x;
        public int y;
        public int z;
        public String dim = "";
        public boolean on = true;
    }

    public static final List<WP> list = new ArrayList<>();
    public static int selected = 0;
    public static boolean show = true;

    private Waypoints() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("polino_waypoints.properties");
    }

    public static String dimNow(MinecraftClient mc) {
        try {
            if (mc.world == null) return "";
            return mc.world.getRegistryKey().getValue().toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static WP current() {
        if (list.isEmpty()) return null;
        selected = Math.floorMod(selected, list.size());
        return list.get(selected);
    }

    public static void addHere(MinecraftClient mc) {
        if (mc.player == null) return;
        WP w = new WP();
        w.name = "WP " + (list.size() + 1);
        w.x = mc.player.getBlockPos().getX();
        w.y = mc.player.getBlockPos().getY();
        w.z = mc.player.getBlockPos().getZ();
        w.dim = dimNow(mc);
        list.add(w);
        selected = list.size() - 1;
        save();
    }

    public static void removeSelected() {
        WP w = current();
        if (w == null) return;
        list.remove(w);
        selected = 0;
        save();
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
            list.clear();
            show = Boolean.parseBoolean(p.getProperty("show", "true"));
            int n = num(p, "count", 0);
            for (int i = 0; i < n; i++) {
                WP w = new WP();
                w.name = p.getProperty("wp." + i + ".name", "WP " + (i + 1));
                w.x = num(p, "wp." + i + ".x", 0);
                w.y = num(p, "wp." + i + ".y", 0);
                w.z = num(p, "wp." + i + ".z", 0);
                w.dim = p.getProperty("wp." + i + ".dim", "");
                w.on = Boolean.parseBoolean(p.getProperty("wp." + i + ".on", "true"));
                list.add(w);
            }
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        try {
            Properties p = new Properties();
            p.setProperty("show", String.valueOf(show));
            p.setProperty("count", String.valueOf(list.size()));
            for (int i = 0; i < list.size(); i++) {
                WP w = list.get(i);
                p.setProperty("wp." + i + ".name", w.name);
                p.setProperty("wp." + i + ".x", String.valueOf(w.x));
                p.setProperty("wp." + i + ".y", String.valueOf(w.y));
                p.setProperty("wp." + i + ".z", String.valueOf(w.z));
                p.setProperty("wp." + i + ".dim", w.dim);
                p.setProperty("wp." + i + ".on", String.valueOf(w.on));
            }
            try (OutputStream out = Files.newOutputStream(file())) {
                p.store(out, "Polino Waypoints");
            }
        } catch (Exception ignored) {
        }
    }
}
