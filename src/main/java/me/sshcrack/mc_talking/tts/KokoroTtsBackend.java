package me.sshcrack.mc_talking.tts;

import ai.onnxruntime.*;
import com.mojang.logging.LogUtils;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Kokoro TTS backend using ONNX Runtime Java.
 *
 * <p>Pipeline: text → espeak-ng phonemization → vocab tokenization → ONNX inference → audio</p>
 */
public class KokoroTtsBackend implements TtsBackend {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_PHONEME_LENGTH = 510;
    private static final int SAMPLE_RATE = 24000;
    private static final int VOICE_DEPTH = 256;

    private OrtEnvironment env;
    private OrtSession session;
    private KokoroPhonemizer phonemizer;
    private List<float[]> voiceBlends; // 40 blends, each shape (510, 256) flattened
    private boolean available = false;
    private boolean usesNewInputFormat = false;

    public void init() {
        if (available) return;

        if (!KokoroModelManager.isModelReady()) {
            LOGGER.info("[KokoroTTS] Model not downloaded yet");
            return;
        }

        phonemizer = new KokoroPhonemizer();
        if (!phonemizer.isAvailable()) {
            LOGGER.warn("[KokoroTTS] Phonemizer unavailable (espeak-ng missing)");
            return;
        }

        try {
            voiceBlends = loadVoiceBlends();
            if (voiceBlends.isEmpty()) {
                LOGGER.error("[KokoroTTS] No voice blends loaded");
                return;
            }

            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setIntraOpNumThreads(Math.max(1, McTalkingConfig.INSTANCE.instance().ttsNumThreads));
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            opts.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.PARALLEL);

            session = env.createSession(KokoroModelManager.getModelPath().toString(), opts);

            // Detect input format (newer exports use "input_ids", older use "tokens")
            for (NodeInfo ni : session.getInputInfo().values()) {
                if (ni.getName().equals("input_ids")) {
                    usesNewInputFormat = true;
                    break;
                }
            }

            available = true;
            LOGGER.info("[KokoroTTS] Initialized with {} voice blends", voiceBlends.size());
        } catch (Exception e) {
            LOGGER.error("[KokoroTTS] Initialization failed", e);
            shutdown();
        }
    }

    @Override
    public float[] synthesize(String text, int voiceId, float speed) {
        if (!available || session == null) {
            return new float[0];
        }
        try {
            int[] tokens = phonemizer.phonemizeAndTokenize(text);
            if (tokens.length == 0) {
                return new float[0];
            }

            // Clamp speed
            speed = Math.max(0.5f, Math.min(2.0f, speed));

            // Get voice style for this token length
            float[] voice = getVoiceStyle(voiceId, tokens.length);

            // Batch if too long
            if (tokens.length > MAX_PHONEME_LENGTH - 2) {
                return synthesizeBatched(tokens, voice, speed);
            }

            return runInference(tokens, voice, speed);
        } catch (Exception e) {
            LOGGER.error("[KokoroTTS] Synthesis failed for text: {}", text, e);
            return new float[0];
        }
    }

    private float[] synthesizeBatched(int[] tokens, float[] voice, float speed) throws OrtException {
        // Split tokens into chunks of ~MAX_PHONEME_LENGTH - 2
        List<float[]> chunks = new ArrayList<>();
        int start = 0;
        while (start < tokens.length) {
            int end = Math.min(start + MAX_PHONEME_LENGTH - 2, tokens.length);
            int[] chunk = Arrays.copyOfRange(tokens, start, end);
            chunks.add(runInference(chunk, voice, speed));
            start = end;
        }

        // Concatenate
        int totalLen = 0;
        for (float[] c : chunks) totalLen += c.length;
        float[] result = new float[totalLen];
        int pos = 0;
        for (float[] c : chunks) {
            System.arraycopy(c, 0, result, pos, c.length);
            pos += c.length;
        }
        return result;
    }

    private float[] runInference(int[] tokens, float[] voice, float speed) throws OrtException {
        // Pad: [0, ...tokens..., 0]
        long[] inputIds = new long[tokens.length + 2];
        inputIds[0] = 0;
        for (int i = 0; i < tokens.length; i++) {
            inputIds[i + 1] = tokens[i];
        }
        inputIds[inputIds.length - 1] = 0;

        // Create tensors
        OnnxTensor inputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), new long[]{1, inputIds.length});
        OnnxTensor styleTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(voice), new long[]{1, VOICE_DEPTH});
        OnnxTensor speedTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(new float[]{speed}), new long[]{1});

        Map<String, OnnxTensor> inputs = new HashMap<>();
        if (usesNewInputFormat) {
            inputs.put("input_ids", inputTensor);
        } else {
            inputs.put("tokens", inputTensor);
        }
        inputs.put("style", styleTensor);
        inputs.put("speed", speedTensor);

        try (OrtSession.Result result = session.run(inputs)) {
            OnnxValue output = result.get(0);
            Object rawValue = output.getValue();
            // Kokoro output shape is [1, seq_len] but ONNX Runtime may return float[] or float[][]
            if (rawValue instanceof float[][]) {
                return ((float[][]) rawValue)[0];
            } else if (rawValue instanceof float[]) {
                return (float[]) rawValue;
            } else {
                LOGGER.error("[KokoroTTS] Unexpected output type: {}", rawValue.getClass().getName());
                return new float[0];
            }
        } finally {
            inputTensor.close();
            styleTensor.close();
            speedTensor.close();
        }
    }

    private float[] getVoiceStyle(int voiceId, int tokenLength) {
        if (voiceId < 0 || voiceId >= voiceBlends.size()) {
            voiceId = 0;
        }
        float[] full = voiceBlends.get(voiceId); // length = 510 * 256
        int idx = Math.min(tokenLength, 509); // clamp to valid range
        float[] style = new float[VOICE_DEPTH];
        System.arraycopy(full, idx * VOICE_DEPTH, style, 0, VOICE_DEPTH);
        return style;
    }

    private List<float[]> loadVoiceBlends() throws IOException {
        List<float[]> blends = new ArrayList<>();
        try (InputStream is = McTalking.class.getResourceAsStream("/assets/mc_talking/kokoro/kokoro_blends.bin.gz")) {
            if (is == null) {
                LOGGER.error("[KokoroTTS] blends.bin.gz not found in resources");
                return blends;
            }
            try (GZIPInputStream gzis = new GZIPInputStream(is);
                 DataInputStream dis = new DataInputStream(gzis)) {

                int count = readLittleEndianInt(dis);
                for (int i = 0; i < count; i++) {
                    int nameLen = readLittleEndianInt(dis);
                    byte[] nameBytes = new byte[nameLen];
                    dis.readFully(nameBytes);
                    String name = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);

                    int dataLen = readLittleEndianInt(dis); // number of floats
                    byte[] data = new byte[dataLen * 4];
                    dis.readFully(data);

                    float[] floats = new float[dataLen];
                    ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats);
                    blends.add(floats);
                    LOGGER.debug("[KokoroTTS] Loaded blend: {} ({} floats)", name, dataLen);
                }
            }
        }
        return blends;
    }

    private static int readLittleEndianInt(DataInputStream dis) throws IOException {
        int b0 = dis.readUnsignedByte();
        int b1 = dis.readUnsignedByte();
        int b2 = dis.readUnsignedByte();
        int b3 = dis.readUnsignedByte();
        return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    @Override
    public int getSampleRate() {
        return SAMPLE_RATE;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void shutdown() {
        available = false;
        if (session != null) {
            try { session.close(); } catch (Exception ignored) {}
            session = null;
        }
        if (env != null) {
            try { env.close(); } catch (Exception ignored) {}
            env = null;
        }
        if (phonemizer != null) {
            phonemizer.shutdown();
            phonemizer = null;
        }
    }
}
