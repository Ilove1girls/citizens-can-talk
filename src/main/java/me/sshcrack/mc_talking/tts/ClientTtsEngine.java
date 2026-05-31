package me.sshcrack.mc_talking.tts;

import com.mojang.logging.LogUtils;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.TtsEngineMode;

/**
 * Router that delegates TTS synthesis to the active backend (Kokoro or Piper).
 * Initialized on first use based on config and availability.
 */
public class ClientTtsEngine {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static volatile ClientTtsEngine INSTANCE;
    private static final Object LOCK = new Object();

    private TtsBackend backend;
    private volatile boolean initialized = false;

    public static ClientTtsEngine getInstance() {
        if (INSTANCE == null) {
            synchronized (LOCK) {
                if (INSTANCE == null) {
                    INSTANCE = new ClientTtsEngine();
                }
            }
        }
        return INSTANCE;
    }

    /** Re-initializes the backend (e.g. after config change). */
    public void init() {
        synchronized (LOCK) {
            if (initialized) {
                if (backend != null) {
                    backend.shutdown();
                    backend = null;
                }
                initialized = false;
            }
            ensureInitializedLocked();
        }
    }

    private void ensureInitialized() {
        if (initialized) return;
        synchronized (LOCK) {
            ensureInitializedLocked();
        }
    }

    private void ensureInitializedLocked() {
        if (initialized) return;
        initialized = true;

        var config = McTalkingConfig.INSTANCE.instance();

        // Try Kokoro first if configured
        if (config.ttsEngine == TtsEngineMode.KOKORO) {
            KokoroTtsBackend kokoro = new KokoroTtsBackend();
            kokoro.init();
            if (kokoro.isAvailable()) {
                backend = kokoro;
                LOGGER.info("[ClientTTS] Using Kokoro backend (instance={})", System.identityHashCode(this));
                return;
            }
            LOGGER.warn("[ClientTTS] Kokoro unavailable, falling back to Piper");
        }

        // Fallback to Piper
        PiperTtsBackend piper = new PiperTtsBackend();
        piper.init();
        if (piper.isAvailable()) {
            backend = piper;
            LOGGER.info("[ClientTTS] Using Piper backend (instance={})", System.identityHashCode(this));
        } else {
            LOGGER.error("[ClientTTS] No TTS backend available");
        }
    }

    public float[] synthesize(String text, int voiceId, float speed) {
        ensureInitialized();
        if (backend == null) {
            LOGGER.warn("[ClientTTS] No backend available, returning silence");
            return new float[0];
        }
        String backendName = backend.getClass().getSimpleName();
        LOGGER.debug("[ClientTTS] Synthesizing with {} (instance={}): {}", backendName, System.identityHashCode(this), text);
        return backend.synthesize(text, voiceId, speed);
    }

    public int getSampleRate() {
        ensureInitialized();
        return backend != null ? backend.getSampleRate() : 22050;
    }

    public boolean isAvailable() {
        ensureInitialized();
        return backend != null && backend.isAvailable();
    }

    public TtsBackend getActiveBackend() {
        ensureInitialized();
        return backend;
    }

    public void shutdown() {
        synchronized (LOCK) {
            if (backend != null) {
                backend.shutdown();
                backend = null;
            }
            initialized = false;
            INSTANCE = null;
        }
    }
}
