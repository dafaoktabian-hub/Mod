package com.hitmusic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;

public class Cam implements Feature {
    private double mult = 1.0;

    @Override
    public String id() {
        return "cam";
    }

    @Override
    public String label() {
        return "Kamera";
    }

    @Override
    public boolean isOn() {
        return true;
    }

    @Override
    public void setOn(boolean on, MinecraftClient mc) {
    }

    @Override
    public double[] cameraState() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Camera c = mc.gameRenderer.getCamera();
        if (c == null) return null;
        net.minecraft.util.math.Vec3d p = c.getPos();
        return new double[]{p.x, p.y, p.z, c.getYaw(), c.getPitch(), mult};
    }

    @Override
    public void frame(MinecraftClient mc, float dt) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        double walk = p.getAbilities().getWalkSpeed();
        double target = 1.0;
        if (walk > 0.0) {
            double ratio = p.getMovementSpeed() / walk;
            target = (ratio + 1.0) / 2.0;
        }
        double scale = mc.options.getFovEffectScale().getValue();
        double want = 1.0 + (target - 1.0) * scale;
        if (Double.isNaN(want) || Double.isInfinite(want)) want = 1.0;
        want = Math.max(0.3, Math.min(1.6, want));
        mult += (want - mult) * (1.0 - Math.exp(-dt * 13.9));
    }
}
