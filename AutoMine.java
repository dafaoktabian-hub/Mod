package com.hitmusic;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import org.lwjgl.glfw.GLFW;

import java.util.Random;

public final class AutoMine {
    public static boolean running = false;
    public static int remaining = 0;

    public static final String[] SPEEDS = {"Cepat", "Normal", "Santai"};
    public static final String[] SMOOTHS = {"Halus", "Pelan", "Sangat Pelan"};
    public static final int[] RADII = {16, 32, 64, 128};

    public static final String[] ORES = {"Iron", "Gold", "Diamond", "Coal", "Copper",
            "Redstone", "Lapis", "Emerald", "Ancient Debris", "Quartz"};
    private static final Block[][] ORE_BLOCKS = {
            {Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE},
            {Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE},
            {Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE},
            {Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE},
            {Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE},
            {Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE},
            {Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE},
            {Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE},
            {Blocks.ANCIENT_DEBRIS},
            {Blocks.NETHER_QUARTZ_ORE}
    };

    public static boolean oreSearch = false;

    private static final int[] DELAY = {0, 2, 6};
    private static final float[] FACTOR = {1.0f, 0.4f, 0.2f};
    private static final float[] MAXTURN = {360f, 35f, 14f};
    private static final Random RNG = new Random();

    private static BlockPos walkTarget = null;
    private static BlockPos lastTarget = null;
    private static int ticks = 0;
    private static int pause = 0;
    private static boolean walking = false;

    private static float targetYaw = 0.0f;
    private static float targetPitch = 0.0f;
    private static boolean hasTarget = false;
    private static float velYaw = 0.0f;
    private static float velPitch = 0.0f;
    private static long lastFrame = 0L;
    private static float jitYaw = 0.0f;
    private static float jitPitch = 0.0f;
    private static int jitTimer = 0;
    private static final float[] SPEED_K = {6.0f, 3.5f, 2.2f};
    private static final float[] MAX_DPS = {300.0f, 170.0f, 100.0f};
    private static final float ACCEL = 9.0f;

    private static double reach() {
        int v = Math.max(30, Math.min(45, Config.getInt("mine.range10", 45)));
        return v / 10.0;
    }

    private static boolean tunnelActive = false;
    private static int tunAxis = 0;
    private static int tunSign = 1;
    private static int tunCenter = 0;
    private static int tunY0 = 0;
    private static int bx1;
    private static int by1;
    private static int bz1;
    private static int bx2;
    private static int by2;
    private static int bz2;
    private static final long MAX_VOLUME = 100000L;

    private AutoMine() {}

    public static long volume() {
        if (Config.pos1 == null || Config.pos2 == null) return 0;
        long dx = Math.abs(Config.pos1.getX() - Config.pos2.getX()) + 1;
        long dy = Math.abs(Config.pos1.getY() - Config.pos2.getY()) + 1;
        long dz = Math.abs(Config.pos1.getZ() - Config.pos2.getZ()) + 1;
        return dx * dy * dz;
    }

    private static void msg(MinecraftClient mc, String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), true);
    }

    public static void start(MinecraftClient mc) {
        start(mc, false);
    }

    public static void start(MinecraftClient mc, boolean ore) {
        tunnelActive = false;
        if (!ore && Config.getInt("mine.mode", 0) == 1) {
            start3(mc);
            return;
        }
        if (Config.pos1 == null || Config.pos2 == null) {
            msg(mc, "Set Posisi 1 dan 2 dulu (tab Area)");
            return;
        }
        if (volume() > MAX_VOLUME) {
            msg(mc, "Area kegedean (maks 100.000 blok)");
            return;
        }
        oreSearch = ore;
        running = true;
        ticks = 0;
        pause = 0;
        walking = false;
        hasTarget = false;
        velYaw = 0.0f;
        velPitch = 0.0f;
        walkTarget = null;
        lastTarget = null;
        remaining = 0;
        msg(mc, ore ? "Cari " + ORES[oreMode()] + " mulai. Pegang pickaxe!" : "Auto Mine mulai. Pegang pickaxe!");
    }

    public static void stop(MinecraftClient mc, String why) {
        running = false;
        tunnelActive = false;
        walkTarget = null;
        lastTarget = null;
        pause = 0;
        walking = false;
        hasTarget = false;
        velYaw = 0.0f;
        velPitch = 0.0f;
        mc.options.forwardKey.setPressed(false);
        msg(mc, why);
    }

    public static int oreMode() {
        return Math.floorMod(Config.getInt("mine.ore", 0), ORES.length);
    }

    public static boolean oreHidden() {
        return Config.getBool("mine.hidden", true);
    }

    private static double layerWeight() {
        return oreSearch ? 0.0 : 1000.0;
    }

    private static boolean exposed(ClientWorld w, BlockPos bp) {
        for (Direction d : Direction.values()) {
            BlockState n = w.getBlockState(bp.offset(d));
            if (n.isAir() || !n.getFluidState().isEmpty()) return true;
        }
        return false;
    }

    private static boolean mineable(ClientWorld w, BlockPos bp) {
        BlockState s = w.getBlockState(bp);
        if (s.isAir()) return false;
        if (!s.getFluidState().isEmpty()) return false;
        if (s.getHardness(w, bp) < 0) return false;
        if (oreSearch) {
            boolean match = false;
            for (Block b : ORE_BLOCKS[oreMode()]) {
                if (s.isOf(b)) {
                    match = true;
                    break;
                }
            }
            if (!match) return false;
            return oreHidden() || exposed(w, bp);
        }
        return true;
    }

    private static boolean solid(ClientWorld w, BlockPos bp) {
        BlockState s = w.getBlockState(bp);
        return !s.isAir() && s.getFluidState().isEmpty() && s.getHardness(w, bp) >= 0;
    }

    private static boolean fluidNear(ClientWorld w, BlockPos bp) {
        if (!w.getBlockState(bp).getFluidState().isEmpty()) return true;
        for (Direction d : Direction.values()) {
            if (!w.getBlockState(bp.offset(d)).getFluidState().isEmpty()) return true;
        }
        return false;
    }

    private static BlockPos digTarget(ClientWorld w, ClientPlayerEntity p, Vec3d eye, BlockPos t) {
        double tx = t.getX() + 0.5;
        double ty = t.getY() + 0.5;
        double tz = t.getZ() + 0.5;
        double dx = tx - eye.x;
        double dy = ty - eye.y;
        double dz = tz - eye.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0.01) {
            double nx = dx / len;
            double ny = dy / len;
            double nz = dz / len;
            double maxD = Math.min(len, reach());
            for (double d = 0.6; d <= maxD; d += 0.4) {
                BlockPos bp = BlockPos.ofFloored(eye.x + nx * d, eye.y + ny * d, eye.z + nz * d);
                if (bp.equals(t)) break;
                if (solid(w, bp)) return bp;
            }
        }
        double hlen = Math.sqrt(dx * dx + dz * dz);
        if (hlen > 1.5) {
            double hx = dx / hlen;
            double hz = dz / hlen;
            BlockPos feet = BlockPos.ofFloored(p.getX() + hx * 1.0, p.getY(), p.getZ() + hz * 1.0);
            BlockPos head = feet.up();
            if (solid(w, head)) return head;
            if (solid(w, feet)) return feet;
        }
        return null;
    }

    private static float wrap(float a) {
        a = a % 360.0f;
        if (a >= 180.0f) a -= 360.0f;
        if (a < -180.0f) a += 360.0f;
        return a;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float aim(ClientPlayerEntity p, Vec3d eye, double tx, double ty, double tz, boolean levelPitch, boolean drift) {
        if (drift && Config.mineJitter) {
            double t = System.nanoTime() / 1.0e9;
            tx += 0.22 * Math.sin(t * 1.7);
            ty += 0.18 * Math.sin(t * 2.3 + 1.0);
            tz += 0.22 * Math.sin(t * 1.3 + 2.0);
        }
        double dx = tx - eye.x;
        double dy = ty - eye.y;
        double dz = tz - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float wantYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float wantPitch = levelPitch ? 0.0f : (float) -Math.toDegrees(Math.atan2(dy, h));
        if (Config.mineJitter) {
            jitTimer--;
            if (jitTimer <= 0) {
                jitTimer = 8 + RNG.nextInt(14);
                jitYaw = (RNG.nextFloat() - 0.5f) * 1.6f;
                jitPitch = (RNG.nextFloat() - 0.5f) * 1.2f;
            }
            wantYaw += jitYaw;
            wantPitch += jitPitch;
        }
        wantPitch = clamp(wantPitch, -90.0f, 90.0f);
        float dyaw = wrap(wantYaw - p.getYaw());
        float dpitch = wantPitch - p.getPitch();
        targetYaw = p.getYaw() + dyaw;
        targetPitch = wantPitch;
        hasTarget = true;
        return Math.max(Math.abs(dyaw), Math.abs(dpitch));
    }

    public static void frame(MinecraftClient mc) {
        long now = System.nanoTime();
        float dt = (now - lastFrame) / 1.0e9f;
        lastFrame = now;
        if (dt <= 0.0f || dt > 0.1f) dt = 0.016f;
        ClientPlayerEntity p = mc.player;
        if (!running || !hasTarget || p == null || mc.currentScreen != null) {
            velYaw = 0.0f;
            velPitch = 0.0f;
            return;
        }
        int m = Math.floorMod(Config.mineSmooth, 3);
        float dyaw = wrap(targetYaw - p.getYaw());
        float dpitch = targetPitch - p.getPitch();
        float maxV = MAX_DPS[m];
        float wantVy = clamp(dyaw * SPEED_K[m], -maxV, maxV);
        float wantVp = clamp(dpitch * SPEED_K[m], -maxV, maxV);
        float a = 1.0f - (float) Math.exp(-ACCEL * dt);
        velYaw += (wantVy - velYaw) * a;
        velPitch += (wantVp - velPitch) * a;
        float sy = velYaw * dt;
        float sp = velPitch * dt;
        if (Math.abs(sy) > Math.abs(dyaw)) sy = dyaw;
        if (Math.abs(sp) > Math.abs(dpitch)) sp = dpitch;
        p.setYaw(p.getYaw() + sy);
        p.setPitch(clamp(p.getPitch() + sp, -90.0f, 90.0f));
    }

    private static boolean playerNearby(MinecraftClient mc, ClientPlayerEntity p, ClientWorld w) {
        float radius = RADII[Math.floorMod(Config.panicRadius, RADII.length)];
        for (PlayerEntity other : w.getPlayers()) {
            if (other == p) continue;
            if (p.distanceTo(other) <= radius) {
                ClientPlayNetworkHandler handler = mc.getNetworkHandler();
                if (handler != null) handler.sendChatCommand("spawn");
                stop(mc, "Ada player! /spawn dikirim");
                return true;
            }
        }
        return false;
    }

    private static void fullScan(ClientWorld w, Vec3d eye) {
        int x1 = Math.min(Config.pos1.getX(), Config.pos2.getX());
        int x2 = Math.max(Config.pos1.getX(), Config.pos2.getX());
        int y1 = Math.min(Config.pos1.getY(), Config.pos2.getY());
        int y2 = Math.max(Config.pos1.getY(), Config.pos2.getY());
        int z1 = Math.min(Config.pos1.getZ(), Config.pos2.getZ());
        int z2 = Math.max(Config.pos1.getZ(), Config.pos2.getZ());
        int count = 0;
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int y = y2; y >= y1; y--) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    BlockPos bp = new BlockPos(x, y, z);
                    if (!mineable(w, bp)) continue;
                    count++;
                    double dx = x + 0.5 - eye.x;
                    double dy = y + 0.5 - eye.y;
                    double dz = z + 0.5 - eye.z;
                    double score = (y2 - y) * layerWeight() + dx * dx + dy * dy + dz * dz;
                    if (Config.pick3 && !oreSearch && !(((x - x1) % 3 == 1) && ((z - z1) % 3 == 1))) score += 700.0;
                    if (score < bestScore) {
                        bestScore = score;
                        best = bp;
                    }
                }
            }
        }
        remaining = count;
        walkTarget = best;
    }

    private static boolean inBox(int x, int y, int z) {
        return x >= bx1 && x <= bx2 && y >= by1 && y <= by2 && z >= bz1 && z <= bz2;
    }

    public static int[] displayBounds() {
        if (running && tunnelActive) {
            return new int[]{bx1, by1, bz1, bx2, by2, bz2};
        }
        if (Config.pos1 != null && Config.pos2 != null && Config.getBool("mine.showBox", true)) {
            return new int[]{
                    Math.min(Config.pos1.getX(), Config.pos2.getX()), Math.min(Config.pos1.getY(), Config.pos2.getY()),
                    Math.min(Config.pos1.getZ(), Config.pos2.getZ()), Math.max(Config.pos1.getX(), Config.pos2.getX()),
                    Math.max(Config.pos1.getY(), Config.pos2.getY()), Math.max(Config.pos1.getZ(), Config.pos2.getZ())};
        }
        return null;
    }

    private static void start3(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        boolean protect = Config.getBool("mine.protect", true);
        boolean hasArea = Config.pos1 != null && Config.pos2 != null;
        if (!hasArea && protect) {
            msg(mc, "Penanda batas belum ada: set Posisi 1 dan 2 di tab Area");
            return;
        }
        double yr = Math.toRadians(p.getYaw());
        double fx = -Math.sin(yr);
        double fz = Math.cos(yr);
        if (Math.abs(fz) >= Math.abs(fx)) {
            tunAxis = 1;
            tunSign = fz >= 0 ? 1 : -1;
        } else {
            tunAxis = 0;
            tunSign = fx >= 0 ? 1 : -1;
        }
        int px = (int) Math.floor(p.getX());
        int py = (int) Math.floor(p.getY());
        int pz = (int) Math.floor(p.getZ());
        tunCenter = tunAxis == 0 ? pz : px;
        tunY0 = py;
        if (hasArea) {
            bx1 = Math.min(Config.pos1.getX(), Config.pos2.getX());
            bx2 = Math.max(Config.pos1.getX(), Config.pos2.getX());
            by1 = Math.min(Config.pos1.getY(), Config.pos2.getY());
            by2 = Math.max(Config.pos1.getY(), Config.pos2.getY());
            bz1 = Math.min(Config.pos1.getZ(), Config.pos2.getZ());
            bz2 = Math.max(Config.pos1.getZ(), Config.pos2.getZ());
        } else {
            int len = Math.max(4, Math.min(64, Config.getInt("mine.len", 16)));
            by1 = py;
            by2 = py + 2;
            if (tunAxis == 0) {
                bx1 = tunSign > 0 ? px : px - len;
                bx2 = tunSign > 0 ? px + len : px;
                bz1 = pz - 1;
                bz2 = pz + 1;
            } else {
                bz1 = tunSign > 0 ? pz : pz - len;
                bz2 = tunSign > 0 ? pz + len : pz;
                bx1 = px - 1;
                bx2 = px + 1;
            }
        }
        if (!inBox(px, py, pz)) {
            msg(mc, "Berdiri di dalam batas area dulu");
            return;
        }
        oreSearch = false;
        tunnelActive = true;
        running = true;
        ticks = 0;
        pause = 0;
        walking = false;
        hasTarget = false;
        velYaw = 0.0f;
        velPitch = 0.0f;
        walkTarget = null;
        lastTarget = null;
        remaining = 0;
        msg(mc, "Mode 3x3 mulai. Pegang pickaxe!");
    }

    private static boolean manualInput(MinecraftClient mc) {
        long h = mc.getWindow().getHandle();
        return GLFW.glfwGetKey(h, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS;
    }

    private static double angleFrom(ClientPlayerEntity p, double dx, double dy, double dz) {
        double h = Math.sqrt(dx * dx + dz * dz);
        float wy = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float wp = (float) -Math.toDegrees(Math.atan2(dy, h));
        float ay = wrap(wy - p.getYaw());
        float ap = wp - p.getPitch();
        return Math.sqrt(ay * ay + ap * ap);
    }

    private static int count3(ClientWorld w, int along) {
        int total = 0;
        for (int k = 1; k <= 300; k++) {
            int a = along + tunSign * k;
            boolean any = false;
            for (int off = -1; off <= 1; off++) {
                for (int dy = 0; dy <= 2; dy++) {
                    int x = tunAxis == 0 ? a : tunCenter + off;
                    int z = tunAxis == 0 ? tunCenter + off : a;
                    int y = tunY0 + dy;
                    if (!inBox(x, y, z)) continue;
                    any = true;
                    if (solid(w, new BlockPos(x, y, z))) total++;
                }
            }
            if (!any) break;
        }
        return total;
    }

    private static void tick3(MinecraftClient mc, ClientPlayerEntity p, ClientWorld w) {
        if (Config.autoWalk && manualInput(mc)) {
            stop(mc, "Kontrol manual terdeteksi, berhenti");
            return;
        }
        ticks++;
        Vec3d eye = p.getEyePos();
        int along = tunAxis == 0 ? (int) Math.floor(p.getX()) : (int) Math.floor(p.getZ());
        double range = reach();
        if (ticks % 20 == 1) remaining = count3(w, along);

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int maxK = (int) Math.ceil(range) + 1;
        for (int k = 1; k <= maxK; k++) {
            int a = along + tunSign * k;
            for (int off = -1; off <= 1; off++) {
                for (int dy = 0; dy <= 2; dy++) {
                    int x = tunAxis == 0 ? a : tunCenter + off;
                    int z = tunAxis == 0 ? tunCenter + off : a;
                    int y = tunY0 + dy;
                    if (!inBox(x, y, z)) continue;
                    BlockPos bp = new BlockPos(x, y, z);
                    if (!solid(w, bp)) continue;
                    double ddx = x + 0.5 - eye.x;
                    double ddy = y + 0.5 - eye.y;
                    double ddz = z + 0.5 - eye.z;
                    if (ddx * ddx + ddy * ddy + ddz * ddz > range * range) continue;
                    double score = k * 20.0 + angleFrom(p, ddx, ddy, ddz);
                    if (score < bestScore) {
                        bestScore = score;
                        best = bp;
                    }
                }
            }
        }

        if (best != null) {
            mc.options.forwardKey.setPressed(false);
            walking = false;
            if (fluidNear(w, best)) {
                stop(mc, "Ada cairan di depan, berhenti");
                return;
            }
            if (lastTarget != null && !lastTarget.equals(best)) {
                lastTarget = best;
                int d = DELAY[Config.mineSpeed] + (Config.mineJitter ? RNG.nextInt(3) : 0);
                if (d > 0) {
                    pause = d;
                    return;
                }
            }
            lastTarget = best;
            if (Config.mineJitter && RNG.nextInt(150) == 0) {
                pause = 5 + RNG.nextInt(10);
                return;
            }
            float err = aim(p, eye, best.getX() + 0.5, best.getY() + 0.5, best.getZ() + 0.5, false, true);
            if (err < 6.0f) {
                Direction side = Direction.getFacing(
                        eye.x - (best.getX() + 0.5),
                        eye.y - (best.getY() + 0.5),
                        eye.z - (best.getZ() + 0.5));
                mc.interactionManager.updateBlockBreakingProgress(best, side);
                p.swingHand(Hand.MAIN_HAND);
            }
            return;
        }

        int nAlong = along + tunSign;
        int nx = tunAxis == 0 ? nAlong : (int) Math.floor(p.getX());
        int nz = tunAxis == 0 ? (int) Math.floor(p.getZ()) : nAlong;
        if (!inBox(nx, tunY0, nz)) {
            stop(mc, "Batas area tercapai, selesai");
            return;
        }
        if (!Config.autoWalk) {
            mc.options.forwardKey.setPressed(false);
            walking = false;
            if (ticks % 40 == 0) msg(mc, "Layer selesai: maju sendiri");
            return;
        }
        BlockPos nf = new BlockPos(nx, tunY0, nz);
        if (!solid(w, nf.down())) {
            stop(mc, "Lantai kosong di depan, berhenti");
            return;
        }
        if (fluidNear(w, nf) || fluidNear(w, nf.up())) {
            stop(mc, "Ada cairan di depan, berhenti");
            return;
        }
        if (!w.getBlockState(nf).isAir() || !w.getBlockState(nf.up()).isAir()) {
            stop(mc, "Terhalang blok yang tidak bisa digali");
            return;
        }
        double tx = tunAxis == 0 ? nx + 0.5 : tunCenter + 0.5;
        double tz = tunAxis == 0 ? tunCenter + 0.5 : nz + 0.5;
        float err2 = aim(p, eye, tx, eye.y, tz, true, false);
        if (walking) {
            if (err2 > 50.0f) walking = false;
        } else if (err2 < 25.0f) {
            walking = true;
        }
        mc.options.forwardKey.setPressed(walking);
    }

    public static void tick(MinecraftClient mc) {
        if (!running) return;
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        if (p == null || w == null || mc.interactionManager == null
                || (!tunnelActive && (Config.pos1 == null || Config.pos2 == null))) {
            running = false;
            return;
        }
        if (mc.currentScreen != null) {
            mc.options.forwardKey.setPressed(false);
            return;
        }
        if (p.getHealth() < 8.0f) {
            stop(mc, "Auto Mine berhenti: darah rendah");
            return;
        }
        ItemStack held = p.getMainHandStack();
        if (held.getMaxDamage() > 0 && held.getMaxDamage() - held.getDamage() <= 5) {
            stop(mc, "Alat hampir rusak, berhenti");
            return;
        }
        if (Config.panic && playerNearby(mc, p, w)) return;
        if (pause > 0) {
            pause--;
            mc.options.forwardKey.setPressed(false);
            return;
        }

        if (tunnelActive) {
            tick3(mc, p, w);
            return;
        }

        ticks++;
        Vec3d eye = p.getEyePos();
        if (ticks % 20 == 1) fullScan(w, eye);

        int x1 = Math.min(Config.pos1.getX(), Config.pos2.getX());
        int x2 = Math.max(Config.pos1.getX(), Config.pos2.getX());
        int y1 = Math.min(Config.pos1.getY(), Config.pos2.getY());
        int y2 = Math.max(Config.pos1.getY(), Config.pos2.getY());
        int z1 = Math.min(Config.pos1.getZ(), Config.pos2.getZ());
        int z2 = Math.max(Config.pos1.getZ(), Config.pos2.getZ());
        int ex = (int) Math.floor(eye.x);
        int ey = (int) Math.floor(eye.y);
        int ez = (int) Math.floor(eye.z);
        int r = 5;

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int y = Math.min(y2, ey + r); y >= Math.max(y1, ey - r); y--) {
            for (int x = Math.max(x1, ex - r); x <= Math.min(x2, ex + r); x++) {
                for (int z = Math.max(z1, ez - r); z <= Math.min(z2, ez + r); z++) {
                    BlockPos bp = new BlockPos(x, y, z);
                    if (!mineable(w, bp)) continue;
                    double dx = x + 0.5 - eye.x;
                    double dy = y + 0.5 - eye.y;
                    double dz = z + 0.5 - eye.z;
                    double d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > reach() * reach()) continue;
                    double score = (y2 - y) * layerWeight() + d2;
                    if (Config.pick3 && !oreSearch && !(((x - x1) % 3 == 1) && ((z - z1) % 3 == 1))) score += 700.0;
                    if (score < bestScore) {
                        bestScore = score;
                        best = bp;
                    }
                }
            }
        }

        if (best != null) {
            mc.options.forwardKey.setPressed(false);
            if (lastTarget != null && !lastTarget.equals(best)) {
                lastTarget = best;
                int d = DELAY[Config.mineSpeed] + (Config.mineJitter ? RNG.nextInt(3) : 0);
                if (d > 0) {
                    pause = d;
                    return;
                }
            }
            lastTarget = best;
            if (Config.mineJitter && RNG.nextInt(150) == 0) {
                pause = 5 + RNG.nextInt(10);
                return;
            }
            float err = aim(p, eye, best.getX() + 0.5, best.getY() + 0.5, best.getZ() + 0.5, false, true);
            if (err < 6.0f) {
                Direction side = Direction.getFacing(
                        eye.x - (best.getX() + 0.5),
                        eye.y - (best.getY() + 0.5),
                        eye.z - (best.getZ() + 0.5));
                mc.interactionManager.updateBlockBreakingProgress(best, side);
                p.swingHand(Hand.MAIN_HAND);
            }
            return;
        }

        if (walkTarget == null || !mineable(w, walkTarget)) {
            fullScan(w, eye);
        }
        if (walkTarget == null) {
            stop(mc, oreSearch ? "Tidak ada " + ORES[oreMode()] + " di area" : "Selesai! Area sudah bersih");
            return;
        }
        if (oreSearch) {
            BlockPos dig = digTarget(w, p, eye, walkTarget);
            if (dig != null) {
                if (fluidNear(w, dig)) {
                    stop(mc, "Ada cairan di depan, berhenti");
                    return;
                }
                mc.options.forwardKey.setPressed(false);
                walking = false;
                float derr = aim(p, eye, dig.getX() + 0.5, dig.getY() + 0.5, dig.getZ() + 0.5, false, true);
                if (derr < 6.0f) {
                    Direction dside = Direction.getFacing(
                            eye.x - (dig.getX() + 0.5),
                            eye.y - (dig.getY() + 0.5),
                            eye.z - (dig.getZ() + 0.5));
                    mc.interactionManager.updateBlockBreakingProgress(dig, dside);
                    p.swingHand(Hand.MAIN_HAND);
                }
                return;
            }
        }
        if (Config.autoWalk) {
            float err2 = aim(p, eye, walkTarget.getX() + 0.5, walkTarget.getY() + 0.5, walkTarget.getZ() + 0.5, true, false);
            if (walking) {
                if (err2 > 50.0f) walking = false;
            } else if (err2 < 25.0f) {
                walking = true;
            }
            mc.options.forwardKey.setPressed(walking);
        } else {
            walking = false;
            mc.options.forwardKey.setPressed(false);
            msg(mc, "Dekati area (di luar jangkauan)");
        }
    }
}
