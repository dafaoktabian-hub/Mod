package com.hitmusic;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class AutoMine {
    public static boolean running = false;
    public static int remaining = 0;

    private static BlockPos walkTarget = null;
    private static int ticks = 0;

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
            msg(mc, "Set Pos1 dan Pos2 dulu");
            return;
        }
        if (volume() > MAX_VOLUME) {
            msg(mc, "Area kegedean (maks 100.000 blok)");
            return;
        }
        running = true;
        ticks = 0;
        walkTarget = null;
        remaining = 0;
        msg(mc, "Auto Mine mulai. Pegang pickaxe!");
    }

    public static void stop(MinecraftClient mc, String why) {
        running = false;
        walkTarget = null;
        mc.options.forwardKey.setPressed(false);
        msg(mc, why);
    }

    private static boolean mineable(ClientWorld w, BlockPos bp) {
        BlockState s = w.getBlockState(bp);
        if (s.isAir()) return false;
        if (!s.getFluidState().isEmpty()) return false;
        return s.getHardness(w, bp) >= 0;
    }

    private static void lookAt(ClientPlayerEntity p, BlockPos t) {
        Vec3d eye = p.getEyePos();
        double dx = t.getX() + 0.5 - eye.x;
        double dy = t.getY() + 0.5 - eye.y;
        double dz = t.getZ() + 0.5 - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        p.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        p.setPitch((float) -Math.toDegrees(Math.atan2(dy, h)));
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
                    double score = (y2 - y) * 1000.0 + dx * dx + dy * dy + dz * dz;
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
        if (p == null || w == null || mc.interactionManager == null || Config.pos1 == null || Config.pos2 == null) {
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
                    double score = (y2 - y) * 1000.0 + d2;
                    if (score < bestScore) {
                        bestScore = score;
                        best = bp;
                    }
                }
            }
        }

        if (best != null) {
            mc.options.forwardKey.setPressed(false);
            lookAt(p, best);
            Direction side = Direction.getFacing(
                    eye.x - (best.getX() + 0.5),
                    eye.y - (best.getY() + 0.5),
                    eye.z - (best.getZ() + 0.5));
            mc.interactionManager.updateBlockBreakingProgress(best, side);
            p.swingHand(Hand.MAIN_HAND);
            return;
        }

        if (walkTarget == null || !mineable(w, walkTarget)) {
            fullScan(w, eye);
        }
        if (walkTarget == null) {
            stop(mc, "Selesai! Area sudah bersih");
            return;
        }
        if (Config.autoWalk) {
            lookAt(p, walkTarget);
            p.setPitch(0.0f);
            mc.options.forwardKey.setPressed(true);
        } else {
            mc.options.forwardKey.setPressed(false);
            msg(mc, "Dekati area (di luar jangkauan)");
        }
    }
}
