package com.hitmusic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.resource.ResourceManager;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class Audio {
    public static SoundInstance current = null;

    private static final String[][] VANILLA_HITS = {
            {"Orb XP", "minecraft:entity.experience_orb.pickup"},
            {"Level Up", "minecraft:entity.player.levelup"},
            {"Panah Kena", "minecraft:entity.arrow.hit_player"},
            {"Crit", "minecraft:entity.player.attack.crit"},
            {"Anvil", "minecraft:block.anvil.land"},
            {"Bell", "minecraft:block.bell.use"}
    };

    private static final String[][] VANILLA_SONGS = {
            {"Cat", "minecraft:music_disc.cat"},
            {"Pigstep", "minecraft:music_disc.pigstep"},
            {"Otherside", "minecraft:music_disc.otherside"},
            {"Chirp", "minecraft:music_disc.chirp"},
            {"Mellohi", "minecraft:music_disc.mellohi"},
            {"Stal", "minecraft:music_disc.stal"},
            {"Relic", "minecraft:music_disc.relic"}
    };

    private Audio() {}

    private static boolean exists(String path) {
        ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
        return rm.getResource(Identifier.of("hitmusic", path)).isPresent();
    }

    public static List<String[]> hits() {
        List<String[]> list = new ArrayList<>();
        if (exists("sounds/hit.ogg")) {
            list.add(new String[]{"Custom (hit.ogg)", "hitmusic:hit"});
        }
        for (String[] s : VANILLA_HITS) list.add(s);
        return list;
    }

    public static List<String[]> songs() {
        List<String[]> list = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            if (exists("sounds/song" + i + ".ogg")) {
                list.add(new String[]{"Lagu " + i + " (song" + i + ".ogg)", "hitmusic:song" + i});
            }
        }
        for (String[] s : VANILLA_SONGS) list.add(s);
        return list;
    }

    public static void playHit() {
        List<String[]> hits = hits();
        if (hits.isEmpty()) return;
        String[] h = hits.get(Math.floorMod(Config.hitIndex, hits.size()));
        SoundEvent ev = SoundEvent.of(Identifier.of(h[1]));
        MinecraftClient.getInstance().getSoundManager()
                .play(PositionedSoundInstance.master(ev, 1.0f, Config.hitVol / 100f));
    }

    public static void playSong() {
        List<String[]> songs = songs();
        if (songs.isEmpty()) return;
        String[] s = songs.get(Math.floorMod(Config.songIndex, songs.size()));
        stopSong();
        SoundEvent ev = SoundEvent.of(Identifier.of(s[1]));
        current = PositionedSoundInstance.master(ev, 1.0f, Config.songVol / 100f);
        MinecraftClient.getInstance().getSoundManager().play(current);
    }

    public static void stopSong() {
        if (current != null) {
            MinecraftClient.getInstance().getSoundManager().stop(current);
            current = null;
        }
    }

    public static boolean isPlaying() {
        return current != null && MinecraftClient.getInstance().getSoundManager().isPlaying(current);
    }
}
