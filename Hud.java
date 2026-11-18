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
        Vec3d cam = Modules.cameraPos();
        Vec3d eye = cam != null ? cam : p.getEyePos();
        camX = eye.x;
        camY = eye.y;
        camZ = eye.z;
        double yawR = Math.toRadians(p.getYaw());
        double pitchR = Math.toRadians(p.getPitch());
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
        double fov = Math.toRadians(mc.options.getFov().getValue());
        focal = (scrH / 2.0) / Math.tan(fov / 2.0);
    }

    private static boolean project(double wx, double wy, double wz, double[] out) {
        double dx = wx - camX;
        double dy = wy - camY;
        double dz = wz - camZ;
        double z = dx * fwdX + dy * fwdY + dz * fwdZ;
        if (z < 0.1) return false;
        double x = dx * rightX + dz * rightZ;
        double y = dx * upX + dy * upY + dz * upZ;
        out[0] = scrW / 2.0 + x / z * focal;
        out[1] = scrH / 2.0 - y / z * focal;
        out[2] = z;
        return true;
    }

    private static void box(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        ctx.fill(x1, y1, x2 + 1, y1 + 1, color);
        ctx.fill(x1, y2, x2 + 1, y2 + 1, color);
        ctx.fill(x1, y1, x1 + 1, y2 + 1, color);
        ctx.fill(x2, y1, x2 + 1, y2 + 1, color);
    }

    private static void line(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i += 3) {
            int x = x0 + (int) ((long) dx * i / steps);
            int y = y0 + (int) ((long) dy * i / steps);
            if (x < -2 || y < -2 || x > scrW + 2 || y > scrH + 2) continue;
            ctx.fill(x, y, x + 2, y + 2, color);
        }
    }

    private static void drawEsp(DrawContext ctx, MinecraftClient mc) {
        if (Finder.results.isEmpty()) return;
        if (!Config.espBox && !Config.tracer) return;
        double[] out = new double[3];
        int tracers = 0;
        for (Finder.Entry e : Finder.results) {
            int color = e.spawner ? 0xFFFF5555 : 0xFFFFAA00;
            double dxc = e.pos.getX() + 0.5 - camX;
            double dyc = e.pos.getY() + 0.5 - camY;
            double dzc = e.pos.getZ() + 0.5 - camZ;
            long dist = Math.round(Math.sqrt(dxc * dxc + dyc * dyc + dzc * dzc));

            if (Config.tracer && tracers < 8) {
                if (project(e.pos.getX() + 0.5, e.pos.getY() + 0.5, e.pos.getZ() + 0.5, out)) {
                    double tx = Math.max(-4000, Math.min(4000, out[0]));
                    double ty = Math.max(-4000, Math.min(4000, out[1]));
                    line(ctx, scrW / 2, scrH, (int) tx, (int) ty, color);
                    tracers++;
                }
            }

            if (Config.espBox) {
                double minX = 1e9;
                double minY = 1e9;
                double maxX = -1e9;
                double maxY = -1e9;
                boolean ok = true;
                for (int i = 0; i < 8; i++) {
                    if (!project(e.pos.getX() + (i & 1), e.pos.getY() + ((i >> 1) & 1), e.pos.getZ() + ((i >> 2) & 1), out)) {
                        ok = false;
                        break;
                    }
                    minX = Math.min(minX, out[0]);
                    maxX = Math.max(maxX, out[0]);
                    minY = Math.min(minY, out[1]);
                    maxY = Math.max(maxY, out[1]);
                }
                if (!ok) continue;
                int x1 = (int) Math.max(-4000, minX);
                int y1 = (int) Math.max(-4000, minY);
                int x2 = Math.max((int) Math.min(4000, maxX), x1 + 4);
                int y2 = Math.max((int) Math.min(4000, maxY), y1 + 4);
                box(ctx, x1, y1, x2, y2, color);
                String label = (e.spawner ? "Spawner " : "Chest ") + dist + "m";
                ctx.drawTextWithShadow(mc.textRenderer, Text.literal(label), x1, y1 - 10, color);
            }
        }
    }

    private static void drawWaypoints(DrawContext ctx, MinecraftClient mc) {
        if (!Waypoints.show || Waypoints.list.isEmpty()) return;
        String dim = Waypoints.dimNow(mc);
        double[] out = new double[3];
        int i = 0;
        for (Waypoints.WP w : Waypoints.list) {
            i++;
            if (!w.on) continue;
            if (!w.dim.isEmpty() && !dim.isEmpty() && !w.dim.equals(dim)) continue;
            double dx = w.x + 0.5 - camX;
            double dy = w.y + 0.5 - camY;
            double dz = w.z + 0.5 - camZ;
            long dist = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
            if (!project(w.x + 0.5, w.y + 0.5, w.z + 0.5, out)) continue;
            int sx = (int) Math.max(10, Math.min(scrW - 10, out[0]));
            int sy = (int) Math.max(16, Math.min(scrH - 10, out[1]));
            int color = WP_COLORS[(i - 1) % WP_COLORS.length];
            ctx.fill(sx - 4, sy - 1, sx + 5, sy + 2, color);
            ctx.fill(sx - 1, sy - 4, sx + 2, sy + 5, color);
            ctx.fill(sx - 2, sy - 2, sx + 3, sy + 3, color);
            String label = w.name + " " + dist + "m";
            int tw = mc.textRenderer.getWidth(label);
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(label), sx - tw / 2, sy - 15, color);
        }
    }

    private static void drawBoundary(DrawContext ctx) {
        int[] b = AutoMine.displayBounds();
        if (b == null) b = Modules.bounds();
        if (b == null) return;
        double x1 = b[0];
        double y1 = b[1];
        double z1 = b[2];
        double x2 = b[3] + 1.0;
        double y2 = b[4] + 1.0;
        double z2 = b[5] + 1.0;
        double[] o = new double[3];
        boolean[] ok = new boolean[8];
        int[][] pt = new int[8][2];
        for (int i = 0; i < 8; i++) {
            double cx = (i & 1) == 0 ? x1 : x2;
            double cy = ((i >> 1) & 1) == 0 ? y1 : y2;
            double cz = ((i >> 2) & 1) == 0 ? z1 : z2;
            ok[i] = project(cx, cy, cz, o);
            if (ok[i]) {
                pt[i][0] = (int) Math.max(-4000, Math.min(4000, o[0]));
                pt[i][1] = (int) Math.max(-4000, Math.min(4000, o[1]));
            }
        }
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            if (ok[e[0]] && ok[e[1]]) {
                line(ctx, pt[e[0]][0], pt[e[0]][1], pt[e[1]][0], pt[e[1]][1], 0xFFFFDD55);
            }
        }
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
