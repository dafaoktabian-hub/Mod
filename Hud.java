package com.hitmusic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public final class Hud {
    public static int fps = 0;

    private static double camX;
    private static double camY;
    private static double camZ;
    private static double fwdX;
    private static double fwdY;
    private static double fwdZ;
    private static double rightX;
    private static double rightZ;
    private static double upX;
    private static double upY;
    private static double upZ;
    private static double focal;
    private static int scrW;
    private static int scrH;

    private static int frames = 0;
    private static long lastNano = 0L;
    private static long lastFrame = 0L;

    private static final int[] WP_COLORS = {0xFF55FFFF, 0xFFFFAA00, 0xFF55FF55, 0xFFFF55FF, 0xFFFFFF55};

    private Hud() {}

    private static void setup(MinecraftClient mc, ClientPlayerEntity p) {
        double yawDeg;
        double pitchDeg;
        double mult = 1.0;
        double[] cs = Modules.cameraState();
        if (cs != null) {
            camX = cs[0];
            camY = cs[1];
            camZ = cs[2];
            yawDeg = cs[3];
            pitchDeg = cs[4];
            mult = cs[5];
        } else {
            Vec3d cam = Modules.cameraPos();
            Vec3d eye = cam != null ? cam : p.getEyePos();
            camX = eye.x;
            camY = eye.y;
            camZ = eye.z;
            yawDeg = p.getYaw();
            pitchDeg = p.getPitch();
        }
        double yawR = Math.toRadians(yawDeg);
        double pitchR = Math.toRadians(pitchDeg);
        double sinYaw = Math.sin(yawR);
        double cosYaw = Math.cos(yawR);
        double sinPitch = Math.sin(pitchR);
        double cosPitch = Math.cos(pitchR);
        fwdX = -sinYaw * cosPitch;
        fwdY = -sinPitch;
        fwdZ = cosYaw * cosPitch;
        rightX = -cosYaw;
        rightZ = -sinYaw;
        upX = -sinYaw * sinPitch;
        upY = cosPitch;
        upZ = cosYaw * sinPitch;
        scrW = mc.getWindow().getScaledWidth();
        scrH = mc.getWindow().getScaledHeight();
        double cal = Config.getInt("esp.fovcal", 100) / 100.0;
        double fovDeg = Math.max(5.0, Math.min(170.0, mc.options.getFov().getValue() * mult * cal));
        focal = (scrH / 2.0) / Math.tan(Math.toRadians(fovDeg) / 2.0);
    }

    private static void toCam(double wx, double wy, double wz, double[] out) {
        double dx = wx - camX;
        double dy = wy - camY;
        double dz = wz - camZ;
        out[0] = dx * rightX + dz * rightZ;
        out[1] = dx * upX + dy * upY + dz * upZ;
        out[2] = dx * fwdX + dy * fwdY + dz * fwdZ;
    }

    private static boolean project(double wx, double wy, double wz, double[] out) {
        toCam(wx, wy, wz, out);
        if (out[2] < 0.05) return false;
        double sx = scrW / 2.0 + out[0] / out[2] * focal;
        double sy = scrH / 2.0 - out[1] / out[2] * focal;
        out[0] = sx;
        out[1] = sy;
        return true;
    }

    private static int clampI(double v) {
        return (int) Math.max(-4000, Math.min(4000, v));
    }

    private static void line(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i += 2) {
            int x = x0 + (int) ((long) dx * i / steps);
            int y = y0 + (int) ((long) dy * i / steps);
            if (x < -2 || y < -2 || x > scrW + 2 || y > scrH + 2) continue;
            ctx.fill(x, y, x + 2, y + 2, color);
        }
    }

    private static void lineWorld(DrawContext ctx, double ax, double ay, double az, double bx, double by, double bz, int color) {
        double[] a = new double[3];
        double[] b = new double[3];
        toCam(ax, ay, az, a);
        toCam(bx, by, bz, b);
        double near = 0.05;
        if (a[2] < near && b[2] < near) return;
        if (a[2] < near) {
            double t = (near - a[2]) / (b[2] - a[2]);
            a[0] += (b[0] - a[0]) * t;
            a[1] += (b[1] - a[1]) * t;
            a[2] = near;
        } else if (b[2] < near) {
            double t = (near - b[2]) / (a[2] - b[2]);
            b[0] += (a[0] - b[0]) * t;
            b[1] += (a[1] - b[1]) * t;
            b[2] = near;
        }
        int x0 = clampI(scrW / 2.0 + a[0] / a[2] * focal);
        int y0 = clampI(scrH / 2.0 - a[1] / a[2] * focal);
        int x1 = clampI(scrW / 2.0 + b[0] / b[2] * focal);
        int y1 = clampI(scrH / 2.0 - b[1] / b[2] * focal);
        line(ctx, x0, y0, x1, y1, color);
    }

    private static void wireBox(DrawContext ctx, double x1, double y1, double z1, double x2, double y2, double z2, int color) {
        lineWorld(ctx, x1, y1, z1, x2, y1, z1, color);
        lineWorld(ctx, x1, y1, z2, x2, y1, z2, color);
        lineWorld(ctx, x1, y2, z1, x2, y2, z1, color);
        lineWorld(ctx, x1, y2, z2, x2, y2, z2, color);
        lineWorld(ctx, x1, y1, z1, x1, y2, z1, color);
        lineWorld(ctx, x2, y1, z1, x2, y2, z1, color);
        lineWorld(ctx, x1, y1, z2, x1, y2, z2, color);
        lineWorld(ctx, x2, y1, z2, x2, y2, z2, color);
        lineWorld(ctx, x1, y1, z1, x1, y1, z2, color);
        lineWorld(ctx, x2, y1, z1, x2, y1, z2, color);
        lineWorld(ctx, x1, y2, z1, x1, y2, z2, color);
        lineWorld(ctx, x2, y2, z1, x2, y2, z2, color);
    }

    private static void drawEsp(DrawContext ctx, MinecraftClient mc) {
        if (Finder.results.isEmpty()) return;
        if (!Config.espBox && !Config.tracer) return;
        double[] out = new double[3];
        int n = 0;
        for (Finder.Entry e : Finder.results) {
            n++;
            int color = e.spawner ? 0xFFFF5555 : 0xFFFFAA00;
            double bx = e.pos.getX();
            double by = e.pos.getY();
            double bz = e.pos.getZ();
            double x1 = e.spawner ? bx : bx + 0.0625;
            double x2 = e.spawner ? bx + 1.0 : bx + 0.9375;
            double z1 = e.spawner ? bz : bz + 0.0625;
            double z2 = e.spawner ? bz + 1.0 : bz + 0.9375;
            double y1 = by;
            double y2 = e.spawner ? by + 1.0 : by + 0.875;
            double mx = (x1 + x2) / 2.0;
            double my = (y1 + y2) / 2.0;
            double mz = (z1 + z2) / 2.0;
            double dxc = mx - camX;
            double dyc = my - camY;
            double dzc = mz - camZ;
            long dist = Math.round(Math.sqrt(dxc * dxc + dyc * dyc + dzc * dzc));

            if (Config.tracer && n <= 8) {
                if (project(mx, my, mz, out)) {
                    line(ctx, scrW / 2, scrH, clampI(out[0]), clampI(out[1]), color);
                }
            }
            if (Config.espBox) {
                if (n <= 12) {
                    wireBox(ctx, x1, y1, z1, x2, y2, z2, color);
                }
                if (project(mx, y2 + 0.15, mz, out)) {
                    String label = (e.spawner ? "Spawner " : "Chest ") + dist + "m";
                    int tw = mc.textRenderer.getWidth(label);
                    ctx.drawTextWithShadow(mc.textRenderer, Text.literal(label), (int) out[0] - tw / 2, (int) out[1] - 10, color);
                }
            }
        }
    }

    private static void drawWaypoints(DrawContext ctx, MinecraftClient mc) {
        if (!Waypoints.show || Waypoints.list.isEmpty()) return;
        String dim = Waypoints.dimNow(mc);
        double[] out = new double[3];
        double[] cam = new double[3];
        int i = 0;
        for (Waypoints.WP w : Waypoints.list) {
            i++;
            if (!w.on) continue;
            if (!w.dim.isEmpty() && !dim.isEmpty() && !w.dim.equals(dim)) continue;
            double dx = w.x + 0.5 - camX;
            double dy = w.y + 0.5 - camY;
            double dz = w.z + 0.5 - camZ;
            long dist = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
            int sx;
            int sy;
            toCam(w.x + 0.5, w.y + 0.5, w.z + 0.5, cam);
            if (cam[2] >= 0.05) {
                project(w.x + 0.5, w.y + 0.5, w.z + 0.5, out);
                sx = (int) Math.max(10, Math.min(scrW - 10, out[0]));
                sy = (int) Math.max(16, Math.min(scrH - 10, out[1]));
            } else {
                sx = cam[0] >= 0 ? scrW - 10 : 10;
                sy = scrH / 2;
            }
            int color = WP_COLORS[(i - 1) % WP_COLORS.length];
            ctx.fill(sx - 4, sy - 1, sx + 5, sy + 2, color);
            ctx.fill(sx - 1, sy - 4, sx + 2, sy + 5, color);
            ctx.fill(sx - 2, sy - 2, sx + 3, sy + 3, color);
            String label = w.name + " " + dist + "m";
            int tw = mc.textRenderer.getWidth(label);
            int lx = Math.max(2, Math.min(scrW - tw - 2, sx - tw / 2));
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(label), lx, sy - 15, color);
        }
    }

    private static void drawBoundary(DrawContext ctx) {
        int[] b = AutoMine.displayBounds();
        if (b == null) b = Modules.bounds();
        if (b == null) return;
        wireBox(ctx, b[0], b[1], b[2], b[3] + 1.0, b[4] + 1.0, b[5] + 1.0, 0xFFFFDD55);
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();

        long now = System.nanoTime();
        float dt = (now - lastFrame) / 1.0e9f;
        lastFrame = now;
        if (dt <= 0.0f || dt > 0.1f) dt = 0.016f;
        frames++;
        if (now - lastNano >= 1000000000L) {
            fps = frames;
            frames = 0;
            lastNano = now;
        }

        AutoMine.frame(mc);
        Modules.frame(mc, dt);

        ClientPlayerEntity p = mc.player;
        if (p == null || mc.options.hudHidden) return;

        setup(mc, p);
        drawEsp(ctx, mc);
        drawWaypoints(ctx, mc);
        drawBoundary(ctx);
        HudLayout.drawAll(ctx, mc, p, false);
        Modules.hud(ctx, mc);
    }
}
