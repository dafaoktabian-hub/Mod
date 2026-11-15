package com.hitmusic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class Freecam implements Feature {
    private boolean on = false;
    private ArmorStandEntity cam = null;
    private double cx;
    private double cy;
    private double cz;
    private double eyeH = 1.6;

    @Override
    public String id() {
        return "freecam";
    }

    @Override
    public String label() {
        return "Freecam";
    }

    @Override
    public boolean isOn() {
        return on;
    }

    @Override
    public void setOn(boolean v, MinecraftClient mc) {
        if (v == on) return;
        if (v) {
            if (mc.player == null || mc.world == null) return;
            Vec3d eye = mc.player.getEyePos();
            cx = eye.x;
            cy = eye.y;
            cz = eye.z;
            cam = new ArmorStandEntity(mc.world, cx, cy, cz);
            eyeH = cam.getEyeY() - cam.getY();
            mc.setCameraEntity(cam);
            on = true;
        } else {
            on = false;
            cam = null;
            if (mc.player != null) mc.setCameraEntity(mc.player);
        }
    }

    @Override
    public void startTick(MinecraftClient mc) {
        if (!on) return;
        if (mc.player == null || mc.world == null) {
            on = false;
            cam = null;
            return;
        }
        if (mc.currentScreen == null) {
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            mc.options.sneakKey.setPressed(false);
            mc.options.sprintKey.setPressed(false);
        }
    }

    private static boolean key(long handle, int k) {
        return GLFW.glfwGetKey(handle, k) == GLFW.GLFW_PRESS;
    }

    @Override
    public void frame(MinecraftClient mc, float dt) {
        if (!on || cam == null || mc.player == null) return;
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        if (mc.currentScreen == null) {
            long h = mc.getWindow().getHandle();
            double yr = Math.toRadians(yaw);
            double pr = Math.toRadians(pitch);
            double fx = -Math.sin(yr) * Math.cos(pr);
            double fy = -Math.sin(pr);
            double fz = Math.cos(yr) * Math.cos(pr);
            double rx = -Math.cos(yr);
            double rz = -Math.sin(yr);
            double mx = 0.0;
            double my = 0.0;
            double mz = 0.0;
            if (key(h, GLFW.GLFW_KEY_W)) {
                mx += fx;
                my += fy;
                mz += fz;
            }
            if (key(h, GLFW.GLFW_KEY_S)) {
                mx -= fx;
                my -= fy;
                mz -= fz;
            }
            if (key(h, GLFW.GLFW_KEY_D)) {
                mx += rx;
                mz += rz;
            }
            if (key(h, GLFW.GLFW_KEY_A)) {
                mx -= rx;
                mz -= rz;
            }
            if (key(h, GLFW.GLFW_KEY_SPACE)) my += 1.0;
            if (key(h, GLFW.GLFW_KEY_LEFT_SHIFT)) my -= 1.0;
            int level = Math.max(1, Math.min(4, Config.getInt("freecam.speed", 2)));
            double speed = 4.0 * Math.pow(2.0, level - 1);
            if (key(h, GLFW.GLFW_KEY_LEFT_CONTROL)) speed *= 2.5;
            cx += mx * speed * dt;
            cy += my * speed * dt;
            cz += mz * speed * dt;
        }
        cam.refreshPositionAndAngles(cx, cy - eyeH, cz, yaw, pitch);
    }

    @Override
    public Vec3d cameraPos() {
        return on ? new Vec3d(cx, cy, cz) : null;
    }

    @Override
    public void hud(DrawContext ctx, MinecraftClient mc) {
        if (!on) return;
        String s = "\u00a7e\u00a7lFREECAM";
        int w = mc.textRenderer.getWidth(s);
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal(s), (mc.getWindow().getScaledWidth() - w) / 2, 6, 0xFFFFFFFF);
    }
}
