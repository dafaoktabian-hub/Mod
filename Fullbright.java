package com.hitmusic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class Fullbright implements Feature {
    private boolean on = Config.getBool("feat.fullbright", false);
    private boolean applied = false;

    @Override
    public String id() {
        return "fullbright";
    }

    @Override
    public String label() {
        return "Fullbright";
    }

    @Override
    public boolean isOn() {
        return on;
    }

    @Override
    public void setOn(boolean v, MinecraftClient mc) {
        on = v;
        Config.setBool("feat.fullbright", v);
        Config.save();
        if (!v) removeEffect(mc);
    }

    private void removeEffect(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p != null && applied) {
            StatusEffectInstance inst = p.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (inst != null && inst.getDuration() > 500000) {
                p.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }
        }
        applied = false;
    }

    @Override
    public void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        if (on) {
            if (!p.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000000, 0, false, false, false));
            }
            applied = true;
        } else if (applied) {
            removeEffect(mc);
        }
    }
}
