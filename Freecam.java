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
    private double ox;
    private double oy;
    private double oz;
    private double vx = 0.0;
    private double vy = 0.0;
    private double vz = 0.0;
    private float prevYaw = 0.0f;
    private float prevPitch = 0.0f;
    private double eyeH = 1.6;
    private long lastTickNano = 0L;

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
            ox = cx;
            oy = cy;
            oz = cz;
            vx = 0.0;
            vy = 0.0;
            vz = 0.0;
            prevYaw = mc.player.getYaw();
            prevPitch = mc.player.getPitch();
            cam = new ArmorStandEntity(mc.world, cx, cy, cz);
            eyeH = cam.getEyeY() - cam.getY();
            cam.refreshPositionAndAngles(cx, cy - eyeH, cz, prevYaw, prevPitch);
            mc.setCameraEntity(cam);
            lastTickNano = System.nanoTime();
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
    public void tick(MinecraftClient mc) {
        if (!on || cam == null || mc.player == null) return;
        if (mc.getCameraEntity() != cam) {
            mc.setCameraEntity(cam);
        }
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();

        double wantX = 0.0;
        double wantY = 0.0;
        double wantZ = 0.0;
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
            wantX = mx * speed * 0.05;
            wantY = my * speed * 0.05;
            wantZ = mz * speed * 0.05;
        }
        vx += (wantX - vx) * 0.4;
        vy += (wantY - vy) * 0.4;
        vz += (wantZ - vz) * 0.4;

        ox = cx;
        oy = cy;
        oz = cz;
        cx += vx;
        cy += vy;
        cz += vz;

        cam.refreshPositionAndAngles(ox, oy - eyeH, oz, prevYaw, prevPitch);
        cam.setPosition(cx, cy - eyeH, cz);
        cam.setYaw(yaw);
        cam.setPitch(pitch);
        prevYaw = yaw;
        prevPitch = pitch;
        lastTickNano = System.nanoTime();
    }

    @Override
    public Vec3d cameraPos() {
        if (!on) return null;
        double a = (System.nanoTime() - lastTickNano) / 50.0e6;
        if (a < 0.0) a = 0.0;
        if (a > 1.0) a = 1.0;
        return new Vec3d(ox + (cx - ox) * a, oy + (cy - oy) * a, oz + (cz - oz) * a);
    }

    @Override
    public void hud(DrawContext ctx, MinecraftClient mc) {
        if (!on) return;
        String s = "\u00a7e\u00a7lFREECAM";
        int w = mc.textRenderer.getWidth(s);
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal(s), (mc.getWindow().getScaledWidth() - w) / 2, 6, 0xFFFFFFFF);
    }
}
