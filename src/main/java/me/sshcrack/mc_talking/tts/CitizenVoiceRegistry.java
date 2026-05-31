package me.sshcrack.mc_talking.tts;

import java.util.UUID;

/**
 * Deterministically maps a citizen's UUID to a speaker ID.
 * The LibriTTS multi-speaker model has 904 built-in speakers.
 */
public class CitizenVoiceRegistry {
    private static final int NUM_SPEAKERS = 904;

    public static int getSpeakerIdForCitizen(UUID citizenId) {
        return Math.floorMod(citizenId.hashCode(), NUM_SPEAKERS);
    }
}
