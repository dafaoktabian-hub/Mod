package com.hitmusic;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AutoTree implements Feature {
    private static final double REACH = 4.5;

    private boolean on = false;
    private BlockPos base = null;
    private int cool = 0;
    private int plantTries = 0;
    private boolean warnedSapling = false;
    private boolean walking = false;

    private float tYaw = 0.0f;
    private float tPitch = 0.0f;
    private boolean hasT = false;
    private float vYaw = 0.0f;
    private float vPitch = 0.0f;

    @Override
    public String id() {
        return "autotree";
    }

    @Override
    public String label() {
        return "Auto Tebang";
    }

    @Override
    public boolean isOn() {
        return on;
    }

    @Override
    public void setOn(boolean v, MinecraftClient mc) {
        on = v;
        base = null;
        cool = 0;
        plantTries = 0;
        walking = false;
        hasT = false;
        vYaw = 0.0f;
        vPitch = 0.0f;
        if (mc.options != null) mc.options.forwardKey.setPressed(false);
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(v ? "Auto Tebang ON (kapak di tangan kanan, sapling di tangan kiri)" : "Auto Tebang OFF"), true);
        }
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

    private float aim(ClientPlayerEntity p, Vec3d eye, double x, double y, double z, boolean level) {
        double dx = x - eye.x;
        double dy = y - eye.y;
        double dz = z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float wantYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float wantPitch = level ? 0.0f : clamp((float) -Math.toDegrees(Math.atan2(dy, h)), -90.0f, 90.0f);
        float dyaw = wrap(wantYaw - p.getYaw());
        float dpitch = wantPitch - p.getPitch();
        tYaw = p.getYaw() + dyaw;
        tPitch = wantPitch;
        hasT = true;
        return Math.max(Math.abs(dyaw), Math.abs(dpitch));
    }

    @Override
    public void frame(MinecraftClient mc, float dt) {
        ClientPlayerEntity p = mc.player;
        if (!on || !hasT || p == null || mc.currentScreen != null) {
            vYaw = 0.0f;
            vPitch = 0.0f;
            return;
        }
        float dyaw = wrap(tYaw - p.getYaw());
        float dpitch = tPitch - p.getPitch();
        float wantVy = clamp(dyaw * 5.0f, -240.0f, 240.0f);
        float wantVp = clamp(dpitch * 5.0f, -240.0f, 240.0f);
        float a = 1.0f - (float) Math.exp(-9.0f * dt);
        vYaw += (wantVy - vYaw) * a;
        vPitch += (wantVp - vPitch) * a;
        float sy = vYaw * dt;
        float sp = vPitch * dt;
        if (Math.abs(sy) > Math.abs(dyaw)) sy = dyaw;
        if (Math.abs(sp) > Math.abs(dpitch)) sp = dpitch;
        p.setYaw(p.getYaw() + sy);
        p.setPitch(clamp(p.getPitch() + sp, -90.0f, 90.0f));
    }

    private boolean hasLeavesNear(ClientWorld w, BlockPos log) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 2; dy <= 8; dy++) {
                    if (w.getBlockState(log.add(dx, dy, dz)).isIn(BlockTags.LEAVES)) return true;
                }
            }
        }
        return false;
    }

    private BlockPos findTree(ClientWorld w, ClientPlayerEntity p) {
        int r = Math.max(4, Math.min(32, Config.getInt("tree.radius", 12)));
        BlockPos pp = p.getBlockPos();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -4; dy <= 6; dy++) {
                    BlockPos bp = pp.add(dx, dy, dz);
                    BlockState s = w.getBlockState(bp);
                    if (!s.isIn(BlockTags.LOGS)) continue;
                    if (!w.getBlockState(bp.down()).isIn(BlockTags.DIRT)) continue;
                    double d = dx * dx + dz * dz + dy * dy;
                    if (d >= bestD) continue;
                    if (!hasLeavesNear(w, bp)) continue;
                    bestD = d;
                    best = bp;
                }
            }
        }
        return best;
    }

    private List<BlockPos> treeLogs(ClientWorld w, BlockPos start) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        if (w.getBlockState(start).isIn(BlockTags.LOGS)) {
            queue.add(start);
            seen.add(start);
        }
        while (!queue.isEmpty() && out.size() < 200) {
            BlockPos cur = queue.poll();
            out.add(cur);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos n = cur.add(dx, dy, dz);
                        if (n.getY() < start.getY()) continue;
                        if (seen.contains(n)) continue;
                        if (w.getBlockState(n).isIn(BlockTags.LOGS)) {
                            seen.add(n);
                            queue.add(n);
                        }
                    }
                }
            }
        }
        return out;
    }

    @Override
    public void tick(MinecraftClient mc) {
        if (!on) return;
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        if (p == null || w == null || mc.interactionManager == null) {
            on = false;
            return;
        }
        if (mc.currentScreen != null) {
            mc.options.forwardKey.setPressed(false);
            return;
        }
        if (p.getHealth() < 8.0f) {
            setOn(false, mc);
            p.sendMessage(Text.literal("Auto Tebang berhenti: darah rendah"), true);
            return;
        }
        if (cool > 0) {
            cool--;
            return;
        }
        Vec3d eye = p.getEyePos();

        if (base == null) {
            base = findTree(w, p);
            plantTries = 0;
            if (base == null) {
                mc.options.forwardKey.setPressed(false);
                walking = false;
                cool = 30;
                return;
            }
        }

        List<BlockPos> logs = treeLogs(w, base);
        if (!logs.isEmpty()) {
            BlockPos tgt = null;
            double bestD = Double.MAX_VALUE;
            for (BlockPos l : logs) {
                double dx = l.getX() + 0.5 - eye.x;
                double dy = l.getY() + 0.5 - eye.y;
                double dz = l.getZ() + 0.5 - eye.z;
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 > REACH * REACH) continue;
                if (d2 < bestD) {
                    bestD = d2;
                    tgt = l;
                }
            }
            if (tgt != null) {
                mc.options.forwardKey.setPressed(false);
                walking = false;
                float err = aim(p, eye, tgt.getX() + 0.5, tgt.getY() + 0.5, tgt.getZ() + 0.5, false);
                if (err < 6.0f) {
                    Direction side = Direction.getFacing(
                            eye.x - (tgt.getX() + 0.5),
                            eye.y - (tgt.getY() + 0.5),
                            eye.z - (tgt.getZ() + 0.5));
                    mc.interactionManager.updateBlockBreakingProgress(tgt, side);
                    p.swingHand(Hand.MAIN_HAND);
                }
                return;
            }
            walkTo(mc, p, eye, base);
            return;
        }

        boolean replant = Config.getBool("tree.replant", true);
        if (!replant) {
            base = null;
            cool = 10;
            return;
        }
        ItemStack off = p.getOffHandStack();
        if (!off.isIn(ItemTags.SAPLINGS)) {
            if (!warnedSapling) {
                p.sendMessage(Text.literal("Taruh sapling di tangan kiri buat tanam ulang"), true);
                warnedSapling = true;
            }
            base = null;
            cool = 20;
            return;
        }
        warnedSapling = false;

        BlockState at = w.getBlockState(base);
        if (at.isIn(BlockTags.SAPLINGS)) {
            base = null;
            cool = 10;
            return;
        }
        BlockPos ground = base.down();
        Vec3d hitPos = new Vec3d(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5);
        double dist = Math.sqrt(hitPos.squaredDistanceTo(eye));
        if (dist > 3.6) {
            walkTo(mc, p, eye, base);
            return;
        }
        mc.options.forwardKey.setPressed(false);
        walking = false;
        float err = aim(p, eye, hitPos.x, hitPos.y, hitPos.z, false);
        if (err < 8.0f) {
            BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, ground, false);
            mc.interactionManager.interactBlock(p, Hand.OFF_HAND, hit);
            p.swingHand(Hand.OFF_HAND);
            plantTries++;
            cool = 6;
            if (plantTries > 8) {
                p.sendMessage(Text.literal("Tanam ulang gagal, lanjut pohon lain"), true);
                base = null;
                cool = 20;
            }
        }
    }

    private void walkTo(MinecraftClient mc, ClientPlayerEntity p, Vec3d eye, BlockPos target) {
        double dx = target.getX() + 0.5 - p.getX();
        double dz = target.getZ() + 0.5 - p.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        float err = aim(p, eye, target.getX() + 0.5, eye.y, target.getZ() + 0.5, true);
        if (walking) {
            if (err > 50.0f || d < 1.6) walking = false;
        } else if (err < 25.0f && d > 2.0) {
            walking = true;
        }
        mc.options.forwardKey.setPressed(walking);
    }
}
