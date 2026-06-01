package me.sshcrack.mc_talking.stt;

import com.k2fsa.sherpa.onnx.*;
import com.mojang.logging.LogUtils;
import me.sshcrack.mc_talking.config.McTalkingConfig;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-side speech-to-text engine using Sherpa-ONNX with Whisper Base.EN.
 * Reuses the same native library infrastructure as the client-side TTS engine.
 *
 * <p>Whisper models expect 16kHz audio. Audio fed at other rates (e.g. 48kHz from
 * voice chat) is resampled to 16kHz internally before inference.</p>
 */
public class ServerSttEngine {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final int TARGET_SAMPLE_RATE = 16000;
    private static ServerSttEngine INSTANCE;

    private OfflineRecognizer recognizer;
    private boolean available = false;

    public static synchronized ServerSttEngine getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ServerSttEngine();
        }
        return INSTANCE;
    }

    public void init() {
        if (available) return;

        var config = McTalkingConfig.INSTANCE.instance();
        if (!config.enableStt) {
            LOGGER.info("[STT] Disabled in config, skipping initialization");
            return;
        }

        Path encoderPath = SttModelManager.getEncoderPath();
        Path decoderPath = SttModelManager.getDecoderPath();
        Path tokensPath = SttModelManager.getTokensPath();
        Path nativePath = SttModelManager.getNativeLibPath();

        // Log actual file sizes for debugging — if a file is truncated, the native loader will segfault
        try {
            LOGGER.info("[STT] Encoder: {} ({} bytes)", encoderPath, Files.size(encoderPath));
            LOGGER.info("[STT] Decoder: {} ({} bytes)", decoderPath, Files.size(decoderPath));
            LOGGER.info("[STT] Tokens:  {} ({} bytes)", tokensPath, Files.size(tokensPath));
            LOGGER.info("[STT] Native:  {}", nativePath);
        } catch (Exception e) {
            LOGGER.warn("[STT] Could not read model file sizes", e);
        }

        if (!SttModelManager.isModelReady()) {
            LOGGER.warn("[STT] Whisper model files missing or corrupted. Expected in: {}", SttModelManager.getWhisperPath());
            return;
        }

        if (!SttModelManager.isNativeLibReady()) {
            LOGGER.warn("[STT] Native libraries missing or corrupted. Expected in: {}", nativePath);
            return;
        }

        try {
            // Ensure native library path is set (client may have already done this in singleplayer)
            System.setProperty("sherpa_onnx.native.path", nativePath.toAbsolutePath().toString());

            // tailPaddings=-1 uses default 1000, which is much better for short utterances
            OfflineWhisperModelConfig whisperConfig = OfflineWhisperModelConfig.builder()
                    .setEncoder(encoderPath.toAbsolutePath().toString())
                    .setDecoder(decoderPath.toAbsolutePath().toString())
                    .setLanguage("en")
                    .setTask("transcribe")
                    .setTailPaddings(-1)
                    .build();

            int numThreads = Math.max(1, Math.min(8, config.sttNumThreads));
            OfflineModelConfig modelConfig = OfflineModelConfig.builder()
                    .setWhisper(whisperConfig)
                    .setTokens(tokensPath.toAbsolutePath().toString())
                    .setNumThreads(numThreads)
                    .setProvider("cpu")
                    .build();

            // Whisper computes mel-spectrograms internally — no external FeatureConfig needed.
            // Setting one can actually interfere with the model's built-in preprocessing.
            OfflineRecognizerConfig recognizerConfig = OfflineRecognizerConfig.builder()
                    .setOfflineModelConfig(modelConfig)
                    .setDecodingMethod("greedy_search")
                    .build();

            recognizer = new OfflineRecognizer(recognizerConfig);
            available = true;
            LOGGER.info("[STT] Whisper Base.EN initialized with {} threads", numThreads);
        } catch (UnsatisfiedLinkError e) {
            LOGGER.error("[STT] Failed to load native library. Ensure sherpa-onnx native libs are in {}", nativePath, e);
            available = false;
        } catch (Throwable e) {
            // Catch Throwable (not just Exception) because native code can throw Error subclasses
            LOGGER.error("[STT] Initialization failed — model may be corrupted. Try deleting {} and re-downloading.",
                    SttModelManager.getModelsPath(), e);
            available = false;
        }
    }

    /**
     * Resamples audio from an arbitrary sample rate to 16kHz using linear interpolation.
     *
     * @param samples    float audio samples in range [-1, 1]
     * @param sourceRate original sample rate in Hz
     * @return resampled audio at 16kHz
     */
    private static float[] resampleTo16kHz(float[] samples, int sourceRate) {
        if (sourceRate == TARGET_SAMPLE_RATE) {
            return samples;
        }
        double ratio = (double) TARGET_SAMPLE_RATE / sourceRate;
        int newLen = (int) (samples.length * ratio);
        float[] out = new float[newLen];
        for (int i = 0; i < newLen; i++) {
            double srcPos = i / ratio;
            int srcIdx = (int) srcPos;
            double frac = srcPos - srcIdx;
            if (srcIdx + 1 < samples.length) {
                out[i] = (float) (samples[srcIdx] * (1.0 - frac) + samples[srcIdx + 1] * frac);
            } else {
                out[i] = samples[Math.min(srcIdx, samples.length - 1)];
            }
        }
        return out;
    }

    /**
     * Transcribes a chunk of PCM audio to text.
     *
     * @param samples    float audio samples in range [-1, 1]
     * @param sampleRate sample rate in Hz (e.g. 48000)
     * @return transcribed text, or empty string if nothing recognized
     */
    public String transcribe(float[] samples, int sampleRate) {
        if (!available || recognizer == null || samples == null || samples.length == 0) {
            return "";
        }

        long start = System.currentTimeMillis();
        OfflineStream stream = null;
        try {
            // Whisper expects 16kHz — resample if necessary
            float[] resampled = resampleTo16kHz(samples, sampleRate);

            // Append 1 second of silence at 16kHz to help Whisper detect end-of-text
            float[] padded = new float[resampled.length + TARGET_SAMPLE_RATE];
            System.arraycopy(resampled, 0, padded, 0, resampled.length);
            // remaining elements are already 0.0f

            stream = recognizer.createStream();
            stream.acceptWaveform(padded, TARGET_SAMPLE_RATE);
            recognizer.decode(stream);
            OfflineRecognizerResult result = recognizer.getResult(stream);
            String text = result.getText();
            if (text == null) text = "";
            text = text.trim();

            long duration = System.currentTimeMillis() - start;
            if (me.sshcrack.mc_talking.config.McTalkingConfig.INSTANCE.instance().debugMode) {
                LOGGER.info("[STT] Transcribed ({}ms): '{}'", duration, text);
            }
            return text;
        } catch (Exception e) {
            LOGGER.error("[STT] Transcription failed", e);
            return "";
        } finally {
            if (stream != null) {
                stream.release();
            }
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public void shutdown() {
        if (recognizer != null) {
            recognizer.release();
            recognizer = null;
        }
        available = false;
        INSTANCE = null;
    }
}
