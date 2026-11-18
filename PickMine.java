package com.hitmusic;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public class PickMine implements Feature {
    private static final int[] DELAY = {0, 2, 6};
    private static final float[] SPEED_K = {6.0f, 3.5f, 2.2f};
    private static final float[] MAX_DPS = {300.0f, 170.0f, 100.0f};
    private static final Random RNG = new Random();

    private boolean on = false;
    private boolean boxed = false;
    private int bx1;
    private int by1;
    private int bz1;
    private int bx2;
    private int by2;
    private int bz2;
    private int axis = 0;
    private int sign = 1;
    private int y0 = 0;
    private BlockPos lastTarget = null;
    private int pause = 0;
    private boolean walking = false;

    private float tYaw = 0.0f;
    private float tPitch = 0.0f;
    private boolean hasT = false;
    private float vYaw = 0.0f;
    private float vPitch = 0.0f;

    @Override
    public String id() {
        return "pickmine";
    }

    @Override
    public String label() {
        return "Pickaxe Mining";
    }

    @Override
    public boolean isOn() {
        return on;
    }

    @Override
    public int[] bounds() {
        return on && boxed ? new int[]{bx1, by1, bz1, bx2, by2, bz2} : null;
    }

    private static void msg(MinecraftClient mc, String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), true);
    }

    private boolean inBox(int x, int y, int z) {
        return x >= bx1 && x <= bx2 && y >= by1 && y <= by2 && z >= bz1 && z <= bz2;
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

    private static boolean manualInput(MinecraftClient mc) {
        long h = mc.getWindow().getHandle();
        return GLFW.glfwGetKey(h, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS;
    }

    private void release(MinecraftClient mc) {
        mc.options.attackKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        walking = false;
    }

    private void stop(MinecraftClient mc, String why) {
        on = false;
        hasT = false;
        vYaw = 0.0f;
        vPitch = 0.0f;
        lastTarget = null;
        pause = 0;
        release(mc);
        if (why != null) msg(mc, why);
    }

    @Override
    public void setOn(boolean v, MinecraftClient mc) {
        if (!v) {
            if (on) stop(mc, "Pickaxe Mining OFF");
            return;
        }
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        if (AutoMine.running) {
            AutoMine.stop(mc, "Auto Mine dihentikan");
        }
        boolean protect = Config.getBool("mine.protect", true);
        boolean hasArea = Config.pos1 != null && Config.pos2 != null;
        boolean auto = Config.getInt("pick.mode", 0) == 1;
        if (!hasArea && protect) {
            msg(mc, "Penanda batas belum ada: set Posisi 1 dan 2 di tab Area");
            return;
        }
        double yr = Math.toRadians(p.getYaw());
        double fx = -Math.sin(yr);
        double fz = Math.cos(yr);
        if (Math.abs(fz) >= Math.abs(fx)) {
            axis = 1;
            sign = fz >= 0 ? 1 : -1;
        } else {
            axis = 0;
            sign = fx >= 0 ? 1 : -1;
        }
        int px = (int) Math.floor(p.getX());
        int py = (int) Math.floor(p.getY());
        int pz = (int) Math.floor(p.getZ());
        y0 = py;
        boxed = false;
        if (hasArea) {
            bx1 = Math.min(Config.pos1.getX(), Config.pos2.getX());
            bx2 = Math.max(Config.pos1.getX(), Config.pos2.getX());
            by1 = Math.min(Config.pos1.getY(), Config.pos2.getY());
            by2 = Math.max(Config.pos1.getY(), Config.pos2.getY());
            bz1 = Math.min(Config.pos1.getZ(), Config.pos2.getZ());
            bz2 = Math.max(Config.pos1.getZ(), Config.pos2.getZ());
            boxed = true;
        } else if (auto) {
            int len = Math.max(4, Math.min(64, Config.getInt("mine.len", 16)));
            by1 = py;
            by2 = py + 2;
            if (axis == 0) {
                bx1 = sign > 0 ? px : px - len;
                bx2 = sign > 0 ? px + len : px;
                bz1 = pz;
                bz2 = pz;
            } else {
                bz1 = sign > 0 ? pz : pz - len;
                bz2 = sign > 0 ? pz + len : pz;
                bx1 = px;
                bx2 = px;
            }
            boxed = true;
        }
        if (boxed && !inBox(px, py, pz)) {
            msg(mc, "Berdiri di dalam batas area dulu");
            boxed = false;
            return;
        }
        lastTarget = null;
        pause = 0;
        walking = false;
        hasT = false;
        vYaw = 0.0f;
        vPitch = 0.0f;
        on = true;
        msg(mc, auto ? "Pickaxe Auto mulai. Pegang pickaxe!" : "Pickaxe Manual mulai: tatap blok yang mau digali");
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
            release(mc);
            return;
        }
        if (p.getHealth() < 8.0f) {
            stop(mc, "Berhenti: darah rendah");
            return;
        }
        ItemStack held = p.getMainHandStack();
        if (held.getMaxDamage() > 0 && held.getMaxDamage() - held.getDamage() <= 5) {
            stop(mc, "Alat hampir rusak, berhenti");
            return;
        }
        if (Config.getInt("pick.mode", 0) == 1) {
            tickAuto(mc, p, w);
        } else {
            tickManual(mc, p, w);
        }
    }

    private void tickManual(MinecraftClient mc, ClientPlayerEntity p, ClientWorld w) {
        if (boxed && !inBox((int) Math.floor(p.getX()), (int) Math.floor(p.getY()), (int) Math.floor(p.getZ()))) {
            stop(mc, "Keluar dari batas, mining berhenti");
            return;
        }
        if (pause > 0) {
            pause--;
            mc.options.attackKey.setPressed(false);
            return;
        }
        HitResult hr = mc.crosshairTarget;
        BlockPos tgt = null;
        if (hr != null && hr.getType() == HitResult.Type.BLOCK) {
            tgt = ((BlockHitResult) hr).getBlockPos();
        }
        boolean ok = tgt != null && solid(w, tgt) && (!boxed || inBox(tgt.getX(), tgt.getY(), tgt.getZ()));
        if (ok) {
            if (lastTarget != null && !lastTarget.equals(tgt)) {
                lastTarget = tgt;
                int d = DELAY[Config.mineSpeed] + RNG.nextInt(3);
                if (d > 0) {
                    pause = d;
                    mc.options.attackKey.setPressed(false);
                    return;
                }
            }
            lastTarget = tgt;
        }
        mc.options.attackKey.setPressed(ok);
    }

    private float aimAxis(ClientPlayerEntity p, Vec3d eye, BlockPos tgt) {
        float wantYaw = axis == 0 ? (sign > 0 ? -90.0f : 90.0f) : (sign > 0 ? 0.0f : 180.0f);
        float wantPitch = 0.0f;
        if (tgt != null) {
            double ca = axis == 0 ? tgt.getX() + 0.5 - eye.x : tgt.getZ() + 0.5 - eye.z;
            double horiz = Math.max(0.3, Math.abs(ca));
            double dy = tgt.getY() + 0.5 - eye.y;
            wantPitch = clamp((float) -Math.toDegrees(Math.atan2(dy, horiz)), -90.0f, 90.0f);
        }
        float dyaw = wrap(wantYaw - p.getYaw());
        float dpitch = wantPitch - p.getPitch();
        tYaw = p.getYaw() + dyaw;
        tPitch = wantPitch;
        hasT = true;
        return Math.max(Math.abs(dyaw), Math.abs(dpitch));
    }

    private void tickAuto(MinecraftClient mc, ClientPlayerEntity p, ClientWorld w) {
        if (manualInput(mc)) {
            stop(mc, "Kontrol manual terdeteksi, berhenti");
            return;
        }
        if (pause > 0) {
            pause--;
            mc.options.forwardKey.setPressed(false);
            return;
        }
        int px = (int) Math.floor(p.getX());
        int pz = (int) Math.floor(p.getZ());
        int along = axis == 0 ? px : pz;
        int nAlong = along + sign;
        int nx = axis == 0 ? nAlong : px;
        int nz = axis == 0 ? pz : nAlong;
        if (boxed && !inBox(nx, y0, nz)) {
            stop(mc, "Batas tercapai, mining berhenti");
            return;
        }
        BlockPos head = new BlockPos(nx, y0 + 1, nz);
        BlockPos feet = new BlockPos(nx, y0, nz);
        BlockPos tgt = solid(w, head) ? head : (solid(w, feet) ? feet : null);
        Vec3d eye = p.getEyePos();

        if (tgt != null) {
            mc.options.forwardKey.setPressed(false);
            walking = false;
            if (fluidNear(w, tgt)) {
                stop(mc, "Ada cairan di depan, berhenti");
                return;
            }
            if (lastTarget != null && !lastTarget.equals(tgt)) {
                lastTarget = tgt;
                int d = DELAY[Config.mineSpeed] + RNG.nextInt(3);
                if (d > 0) {
                    pause = d;
                    return;
                }
            }
            lastTarget = tgt;
            float err = aimAxis(p, eye, tgt);
            if (err < 5.0f) {
                Direction side = axis == 0 ? (sign > 0 ? Direction.WEST : Direction.EAST)
                        : (sign > 0 ? Direction.NORTH : Direction.SOUTH);
                mc.interactionManager.updateBlockBreakingProgress(tgt, side);
                p.swingHand(Hand.MAIN_HAND);
            }
            return;
        }

        if (!w.getBlockState(head).isAir() || !w.getBlockState(feet).isAir()) {
            stop(mc, "Terhalang blok yang tidak bisa digali");
            return;
        }
        if (!solid(w, feet.down())) {
            stop(mc, "Lantai kosong di depan, berhenti");
            return;
        }
        if (fluidNear(w, feet) || fluidNear(w, head)) {
            stop(mc, "Ada cairan di depan, berhenti");
            return;
        }
        float err2 = aimAxis(p, eye, null);
        if (walking) {
            if (err2 > 40.0f) walking = false;
        } else if (err2 < 12.0f) {
            walking = true;
        }
        mc.options.forwardKey.setPressed(walking);
    }

    @Override
    public void frame(MinecraftClient mc, float dt) {
        ClientPlayerEntity p = mc.player;
        if (!on || !hasT || p == null || mc.currentScreen != null || Config.getInt("pick.mode", 0) != 1) {
            vYaw = 0.0f;
            vPitch = 0.0f;
            return;
        }
        int m = Math.floorMod(Config.mineSmooth, 3);
        float dyaw = wrap(tYaw - p.getYaw());
        float dpitch = tPitch - p.getPitch();
        float maxV = MAX_DPS[m];
        float wantVy = clamp(dyaw * SPEED_K[m], -maxV, maxV);
        float wantVp = clamp(dpitch * SPEED_K[m], -maxV, maxV);
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
}
