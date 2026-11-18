package com.hitmusic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Modules {
    private static final Map<String, Feature> MAP = new LinkedHashMap<>();
    private static final String[] CLASSES = {"com.hitmusic.Fullbright", "com.hitmusic.Freecam", "com.hitmusic.AutoTree", "com.hitmusic.PickMine"};

    private Modules() {}

    public static void init() {
        for (String c : CLASSES) {
            try {
                Object o = Class.forName(c).getDeclaredConstructor().newInstance();
                if (o instanceof Feature) {
                    Feature f = (Feature) o;
                    MAP.put(f.id(), f);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static Feature get(String id) {
        return MAP.get(id);
    }

    private static void kill(Feature f, MinecraftClient mc) {
        try {
            f.setOn(false, mc);
        } catch (Throwable ignored) {
        }
    }

    public static void startTick(MinecraftClient mc) {
        for (Feature f : new ArrayList<>(MAP.values())) {
            try {
                f.startTick(mc);
            } catch (Throwable t) {
                kill(f, mc);
            }
        }
    }

    public static void tick(MinecraftClient mc) {
        for (Feature f : new ArrayList<>(MAP.values())) {
            try {
                f.tick(mc);
            } catch (Throwable t) {
                kill(f, mc);
            }
        }
    }

    public static void frame(MinecraftClient mc, float dt) {
        for (Feature f : new ArrayList<>(MAP.values())) {
            try {
                f.frame(mc, dt);
            } catch (Throwable t) {
                kill(f, mc);
            }
        }
    }

    public static void hud(DrawContext ctx, MinecraftClient mc) {
        for (Feature f : new ArrayList<>(MAP.values())) {
            try {
                f.hud(ctx, mc);
            } catch (Throwable ignored) {
            }
        }
    }

    public static int[] bounds() {
        for (Feature f : new ArrayList<>(MAP.values())) {
            try {
                int[] b = f.bounds();
                if (b != null) return b;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static Vec3d cameraPos() {
        for (Feature f : new ArrayList<>(MAP.values())) {
            try {
                Vec3d v = f.cameraPos();
                if (v != null) return v;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
