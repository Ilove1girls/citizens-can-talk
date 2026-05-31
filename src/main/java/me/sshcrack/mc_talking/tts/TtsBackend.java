package me.sshcrack.mc_talking.tts;

/**
 * Abstraction over TTS engines (Piper, Kokoro, etc.)
 */
public interface TtsBackend {
    float[] synthesize(String text, int voiceId, float speed);
    int getSampleRate();
    boolean isAvailable();
    void shutdown();
}
