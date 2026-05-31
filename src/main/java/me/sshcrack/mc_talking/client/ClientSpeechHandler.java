package me.sshcrack.mc_talking.client;

import com.mojang.logging.LogUtils;
import me.sshcrack.mc_talking.client.audio.CitizenAudioQueue;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.network.CitizenInterruptPayload;
import me.sshcrack.mc_talking.network.CitizenSpeechPayload;
import me.sshcrack.mc_talking.tts.CitizenVoiceRegistry;
import me.sshcrack.mc_talking.tts.KokoroVoiceRegistry;

import java.util.UUID;

/**
 * Client-side handler for citizen speech and interruption packets.
 * Delegates audio queue management to {@link CitizenAudioQueue}.
 */
public class ClientSpeechHandler {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    public static void onCitizenSpeech(CitizenSpeechPayload payload) {
        var config = McTalkingConfig.INSTANCE.instance();
        if (!config.enableTts) return;

        UUID citizenId = payload.citizenId();
        int voiceId;

        if (config.ttsEngine == me.sshcrack.mc_talking.config.TtsEngineMode.KOKORO) {
            voiceId = KokoroVoiceRegistry.getBlendIndex(citizenId, payload.isFemale());
        } else {
            voiceId = CitizenVoiceRegistry.getSpeakerIdForCitizen(citizenId);
        }

        float speed = config.ttsSpeed != 0 ? (float) config.ttsSpeed : payload.speed();
        float volume = (float) config.ttsVolume;

        CitizenAudioQueue.enqueueSpeech(citizenId, payload.text(), voiceId, speed, volume);
    }

    public static void onCitizenInterrupt(CitizenInterruptPayload payload) {
        LOGGER.debug("[TTS] Received interrupt for citizen {}", payload.citizenId());
        CitizenAudioQueue.interrupt(payload.citizenId());
    }

    public static void shutdown() {
        CitizenAudioQueue.shutdown();
    }


}
