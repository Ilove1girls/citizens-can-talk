package me.sshcrack.mc_talking.tts;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import me.sshcrack.mc_talking.McTalking;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/**
 * Phonemizes English text using espeak-ng, then tokenizes into Kokoro token IDs.
 *
 * <p>Fast path: calls libespeak-ng directly via JNA (microseconds per call).</p>
 * <p>Fallback: spawns espeak-ng subprocess per segment if native lib unavailable.</p>
 */
public class KokoroPhonemizer {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_PHONEME_LENGTH = 510;
    private static final int CACHE_SIZE = 256;

    private final Map<Character, Integer> vocab;
    private final Map<String, String> phonemeCache;

    // Fast path: JNA direct call
    private EspeakNgJna jna;
    private boolean useJna = false;

    // Fallback: subprocess path (only used if JNA fails)
    private String espeakPath;

    public KokoroPhonemizer() {
        this.vocab = loadVocab();
        this.phonemeCache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > CACHE_SIZE;
            }
        };

        // Try JNA fast path first
        try {
            String dataPath = TtsModelManager.getBasePath().toAbsolutePath().toString();
            String nativeLibDir = EspeakNgNativeDownloader.getExtractDir().toAbsolutePath().toString();
            this.jna = new EspeakNgJna(dataPath, nativeLibDir);
            this.useJna = true;
            LOGGER.info("[KokoroPhonemizer] Using JNA direct call to libespeak-ng");
        } catch (Throwable e) {
            LOGGER.warn("[KokoroPhonemizer] JNA unavailable ({}), falling back to subprocess", e.getMessage());
            this.espeakPath = findEspeakNg();
            if (this.espeakPath != null) {
                LOGGER.info("[KokoroPhonemizer] Subprocess fallback using espeak-ng at: {}", this.espeakPath);
            } else {
                LOGGER.warn("[KokoroPhonemizer] espeak-ng not found. Kokoro TTS will fall back to Piper.");
            }
        }
    }

    public boolean isAvailable() {
        return useJna || espeakPath != null;
    }

    public void shutdown() {
        if (jna != null) {
            jna.shutdown();
            jna = null;
        }
    }

    /**
     * Convert text to IPA phonemes.
     */
    public String phonemize(String text) {
        if (!isAvailable()) return "";
        String cached = phonemeCache.get(text);
        if (cached != null) return cached;

        String result = useJna ? phonemizeJna(text) : phonemizeSubprocess(text);
        if (result != null && !result.isEmpty()) {
            phonemeCache.put(text, result);
        }
        return result != null ? result : "";
    }

    private String phonemizeJna(String text) {
        String raw = jna.phonemize(text);
        return filterToVocab(raw);
    }

    private String phonemizeSubprocess(String text) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    espeakPath,
                    "-v", "en-us",
                    "-q",
                    "-x",
                    "--ipa",
                    text
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() > 0) {
                        output.append(' ');
                    }
                    output.append(line);
                }

                boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    LOGGER.warn("[KokoroPhonemizer] espeak-ng timed out for text");
                    return null;
                }

                if (process.exitValue() != 0) {
                    LOGGER.warn("[KokoroPhonemizer] espeak-ng exited with code {}", process.exitValue());
                    return null;
                }

                return filterToVocab(output.toString().trim());
            }
        } catch (Exception e) {
            LOGGER.error("[KokoroPhonemizer] espeak-ng failed", e);
            return null;
        }
    }

    private String filterToVocab(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder filtered = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (vocab.containsKey(c)) {
                filtered.append(c);
            }
        }
        return filtered.toString();
    }

    /**
     * Tokenize IPA phonemes into integer token IDs using the vocab.
     */
    public int[] tokenize(String phonemes) {
        if (phonemes == null || phonemes.isEmpty()) return new int[0];
        List<Integer> tokens = new ArrayList<>();
        for (char c : phonemes.toCharArray()) {
            Integer id = vocab.get(c);
            if (id != null) {
                tokens.add(id);
            }
        }
        return tokens.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Convenience: text → tokens in one call.
     */
    public int[] phonemizeAndTokenize(String text) {
        String phonemes = phonemize(text);
        return tokenize(phonemes);
    }

    private String findEspeakNg() {
        Path bundled = KokoroModelManager.getEspeakNgPath();
        if (bundled != null && java.nio.file.Files.exists(bundled)) {
            return bundled.toAbsolutePath().toString();
        }

        String[] names = isWindows()
                ? new String[]{"espeak-ng.exe", "espeak.exe"}
                : new String[]{"espeak-ng", "espeak"};

        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
                for (String name : names) {
                    Path candidate = java.nio.file.Path.of(dir, name);
                    if (java.nio.file.Files.exists(candidate)) {
                        return candidate.toAbsolutePath().toString();
                    }
                }
            }
        }

        String[] commonPaths = isWindows()
                ? new String[]{"C:\\Program Files\\eSpeak NG\\espeak-ng.exe"}
                : new String[]{"/usr/bin/espeak-ng", "/usr/local/bin/espeak-ng"};

        for (String p : commonPaths) {
            if (java.nio.file.Files.exists(java.nio.file.Path.of(p))) {
                return p;
            }
        }

        return null;
    }

    private Map<Character, Integer> loadVocab() {
        try (InputStream is = McTalking.class.getResourceAsStream("/assets/mc_talking/kokoro/kokoro_vocab.json")) {
            if (is == null) {
                LOGGER.error("[KokoroPhonemizer] vocab.json not found in resources");
                return Collections.emptyMap();
            }
            Map<String, Integer> raw = new Gson().fromJson(
                    new InputStreamReader(is, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, Integer>>() {}.getType()
            );
            Map<Character, Integer> vocab = new HashMap<>();
            for (Map.Entry<String, Integer> e : raw.entrySet()) {
                if (e.getKey().length() == 1) {
                    vocab.put(e.getKey().charAt(0), e.getValue());
                }
            }
            LOGGER.info("[KokoroPhonemizer] Loaded vocab with {} entries", vocab.size());
            return vocab;
        } catch (Exception e) {
            LOGGER.error("[KokoroPhonemizer] Failed to load vocab", e);
            return Collections.emptyMap();
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
