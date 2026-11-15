package com.hitmusic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;

public interface Feature {
    String id();

    String label();

    boolean isOn();

    void setOn(boolean on, MinecraftClient mc);

    default void startTick(MinecraftClient mc) {
    }

    default void tick(MinecraftClient mc) {
    }

    default void frame(MinecraftClient mc, float dt) {
    }

    default void hud(DrawContext ctx, MinecraftClient mc) {
    }

    default Vec3d cameraPos() {
        return null;
    }
}
