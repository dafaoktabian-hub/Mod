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

import java.util.Random;

public final class AutoMine {
    public static boolean running = false;
    public static int remaining = 0;

    public static final String[] SPEEDS = {"Cepat", "Normal", "Santai"};
    public static final String[] SMOOTHS = {"Halus", "Pelan", "Sangat Pelan"};
    public static final int[] RADII = {16, 32, 64, 128};

    public static final String[] ORES = {"Semua Blok", "Iron", "Gold", "Diamond", "Coal", "Copper",
            "Redstone", "Lapis", "Emerald", "Ancient Debris", "Quartz"};
    private static final Block[][] ORE_BLOCKS = {
            {},
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

    private static final double REACH = 4.5;
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
        if (Config.pos1 == null || Config.pos2 == null) {
            msg(mc, "Set Posisi 1 dan 2 dulu (tab Area)");
            return;
        }
        if (volume() > MAX_VOLUME) {
            msg(mc, "Area kegedean (maks 100.000 blok)");
            return;
        }
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
        msg(mc, "Auto Mine mulai. Pegang pickaxe!");
    }

    public static void stop(MinecraftClient mc, String why) {
        running = false;
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

    private static double layerWeight() {
        return oreMode() > 0 ? 0.0 : 1000.0;
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
        int mode = oreMode();
        if (mode > 0) {
            boolean match = false;
            for (Block b : ORE_BLOCKS[mode]) {
                if (s.isOf(b)) {
                    match = true;
                    break;
                }
            }
            if (!match) return false;
            return exposed(w, bp);
        }
        return true;
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
                    if (Config.pick3 && oreMode() == 0 && !(((x - x1) % 3 == 1) && ((z - z1) % 3 == 1))) score += 700.0;
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

    public static void tick(MinecraftClient mc) {
        if (!running) return;
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        if (p == null || w == null || mc.interactionManager == null
                || Config.pos1 == null || Config.pos2 == null) {
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
                    if (d2 > REACH * REACH) continue;
                    double score = (y2 - y) * layerWeight() + d2;
                    if (Config.pick3 && oreMode() == 0 && !(((x - x1) % 3 == 1) && ((z - z1) % 3 == 1))) score += 700.0;
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
            stop(mc, oreMode() > 0 ? "Tidak ada " + ORES[oreMode()] + " yang terlihat di area" : "Selesai! Area sudah bersih");
            return;
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
