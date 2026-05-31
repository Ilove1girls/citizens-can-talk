package me.sshcrack.mc_talking.tts;

import java.util.UUID;

/**
 * Deterministically maps a citizen's UUID to a Kokoro voice blend index.
 * Female citizens are assigned from the 20 female blends (indices 0-19).
 * Male citizens are assigned from the 20 male blends (indices 20-39).
 */
public class KokoroVoiceRegistry {
    private static final int FEMALE_COUNT = 20;
    private static final int MALE_COUNT = 20;

    /**
     * Returns a blend index for the given citizen.
     * 0-19  = female blends (f01-f20)
     * 20-39 = male blends (m01-m20)
     */
    public static int getBlendIndex(UUID citizenId, boolean isFemale) {
        int pool = isFemale ? FEMALE_COUNT : MALE_COUNT;
        int idx = Math.floorMod(citizenId.hashCode(), pool);
        return isFemale ? idx : idx + FEMALE_COUNT;
    }
}
