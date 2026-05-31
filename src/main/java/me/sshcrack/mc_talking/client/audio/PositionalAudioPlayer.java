package me.sshcrack.mc_talking.client.audio;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays synthesized PCM audio at a citizen's position using Minecraft's SoundEngine.
 */
public class PositionalAudioPlayer {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, StreamingSpeechSoundInstance> ACTIVE_SOUNDS = new ConcurrentHashMap<>();

    /**
     * Plays PCM audio for a citizen. Stops any previous speech from the same citizen.
     *
     * @param citizenId  the citizen's UUID
     * @param pcm        16-bit mono PCM samples
     * @param sampleRate sample rate in Hz (e.g. 22050)
     * @param volume     volume multiplier
     */
    public static void play(UUID citizenId, short[] pcm, int sampleRate, float volume) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        var entity = findEntityByUuid(mc, citizenId);
        if (entity == null) {
            LOGGER.debug("[TTS] Citizen {} not found in client level, skipping audio playback", citizenId);
            return;
        }

        // Stop any previous speech from this citizen so they don't overlap
        stop(citizenId);

        var sound = new StreamingSpeechSoundInstance(
                entity.getX(), entity.getY(), entity.getZ(),
                pcm, sampleRate, volume
        );
        ACTIVE_SOUNDS.put(citizenId, sound);
        mc.getSoundManager().play(sound);
    }

    /**
     * Stops any active speech for the given citizen and removes it from tracking.
     */
    public static void stop(UUID citizenId) {
        StreamingSpeechSoundInstance previous = ACTIVE_SOUNDS.remove(citizenId);
        if (previous != null) {
            Minecraft.getInstance().getSoundManager().stop(previous);
        }
    }

    /**
     * Returns true if the citizen currently has an active sound playing.
     */
    public static boolean isPlaying(UUID citizenId) {
        StreamingSpeechSoundInstance sound = ACTIVE_SOUNDS.get(citizenId);
        if (sound == null) return false;
        return Minecraft.getInstance().getSoundManager().isActive(sound);
    }

    private static net.minecraft.world.entity.Entity findEntityByUuid(Minecraft mc, UUID uuid) {
        for (var entity : mc.level.entitiesForRendering()) {
            if (uuid.equals(entity.getUUID())) {
                return entity;
            }
        }
        return null;
    }
}
