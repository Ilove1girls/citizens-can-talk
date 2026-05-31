package me.sshcrack.mc_talking.tts;

import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;
import com.k2fsa.sherpa.onnx.GenerationConfig;
import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.mojang.logging.LogUtils;
import me.sshcrack.mc_talking.config.McTalkingConfig;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Piper TTS backend using Sherpa-ONNX.
 */
public class PiperTtsBackend implements TtsBackend {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static int getConfiguredThreads() {
        int cfg = McTalkingConfig.INSTANCE.instance().ttsNumThreads;
        return Math.max(1, Math.min(16, cfg));
    }

    private OfflineTts tts;
    private boolean available = false;

    public void init() {
        if (available) return;

        Path modelPath = TtsModelManager.getModelPath();
        Path tokensPath = TtsModelManager.getTokensPath();
        Path espeakDataPath = TtsModelManager.getEspeakDataPath();
        Path nativePath = TtsModelManager.getNativeLibPath();

        if (!Files.exists(modelPath) || !Files.exists(tokensPath) || !Files.exists(espeakDataPath)) {
            LOGGER.info("[PiperTTS] Model files not ready, skipping initialization");
            return;
        }

        if (!Files.exists(nativePath.resolve(getJniLibName()))) {
            LOGGER.warn("[PiperTTS] Native libraries not found at {}", nativePath);
            return;
        }

        try {
            System.setProperty("sherpa_onnx.native.path", nativePath.toAbsolutePath().toString());

            OfflineTtsVitsModelConfig vitsConfig = OfflineTtsVitsModelConfig.builder()
                    .setModel(modelPath.toAbsolutePath().toString())
                    .setTokens(tokensPath.toAbsolutePath().toString())
                    .setDataDir(espeakDataPath.toAbsolutePath().toString())
                    .build();

            boolean useGpu = GpuDetector.hasNvidiaGpu() && TtsModelManager.hasGpuRuntime();
            String provider = useGpu ? "cuda" : "cpu";

            int numThreads = getConfiguredThreads();
            OfflineTtsModelConfig modelConfig = OfflineTtsModelConfig.builder()
                    .setVits(vitsConfig)
                    .setNumThreads(numThreads)
                    .setProvider(provider)
                    .build();

            OfflineTtsConfig config = OfflineTtsConfig.builder()
                    .setModel(modelConfig)
                    .build();

            tts = new OfflineTts(config);
            available = true;
            LOGGER.info("[PiperTTS] Initialized with provider: {}", provider);
        } catch (UnsatisfiedLinkError e) {
            LOGGER.error("[PiperTTS] Failed to load native library", e);
            available = false;
        } catch (Exception e) {
            LOGGER.error("[PiperTTS] Initialization failed", e);
            available = false;
        }
    }

    @Override
    public float[] synthesize(String text, int speakerId, float speed) {
        if (!available || tts == null) {
            return new float[0];
        }
        try {
            GenerationConfig genConfig = new GenerationConfig();
            genConfig.setSid(speakerId);
            genConfig.setSpeed(speed);
            GeneratedAudio audio = tts.generateWithConfigAndCallback(text, genConfig, samples -> {});
            return audio.getSamples();
        } catch (Exception e) {
            LOGGER.error("[PiperTTS] Synthesis failed for text: {}", text, e);
            return new float[0];
        }
    }

    @Override
    public int getSampleRate() {
        return available && tts != null ? tts.getSampleRate() : 22050;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void shutdown() {
        if (tts != null) {
            tts.release();
            tts = null;
        }
        available = false;
    }

    private static String getJniLibName() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "sherpa-onnx-jni.dll";
        } else if (os.contains("mac") || os.contains("darwin")) {
            return "libsherpa-onnx-jni.dylib";
        } else {
            return "libsherpa-onnx-jni.so";
        }
    }
}
