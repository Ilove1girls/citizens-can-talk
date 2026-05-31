package me.sshcrack.mc_talking.tts;

import com.mojang.logging.LogUtils;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * JNA bridge to libespeak-ng for in-process phonemization.
 * Eliminates subprocess spawn overhead (~50-100ms per call).
 *
 * <p>Maps espeak-ng C API functions:</p>
 * <ul>
 *   <li>{@code espeak_Initialize} — load voices &amp; data</li>
 *   <li>{@code espeak_SetVoiceByName} — select voice (e.g. "en-us")</li>
 *   <li>{@code espeak_TextToPhonemes} — convert text to IPA phonemes</li>
 *   <li>{@code espeak_Terminate} — cleanup</li>
 * </ul>
 */
public class EspeakNgJna {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    // espeak-ng constants from speak_lib.h
    static final int AUDIO_OUTPUT_SYNCHRONOUS = 2; // matches Android JNI bindings
    static final int CHARS_UTF8 = 1;
    static final int PHONEMES_IPA = 0x02;
    // Note: espeak_TextToPhonemes only needs IPA flag; SHOW flag can cause issues
    private static final int PHONEME_MODE = PHONEMES_IPA;

    private final EspeakNgLib lib;
    private boolean initialized = false;

    /**
     * Attempts to load libespeak-ng and initialize it with the given data path.
     *
     * @param dataPath directory containing the {@code espeak-ng-data} folder,
     *                 or {@code null} to use system default
     * @throws UnsatisfiedLinkError if the native library cannot be found
     * @throws RuntimeException     if espeak-ng initialization fails
     */
    public EspeakNgJna(String dataPath) {
        this(dataPath, null);
    }

    /**
     * Attempts to load libespeak-ng from an optional local native directory.
     *
     * @param dataPath       directory containing the {@code espeak-ng-data} folder
     * @param nativeLibDir   optional directory containing the platform-specific
     *                       native library (e.g. {@code espeak-ng.dll} on Windows)
     */
    public EspeakNgJna(String dataPath, String nativeLibDir) {
        System.setProperty("jna.encoding", "UTF-8");

        // Add the mod's native directory to JNA's search path so downloaded
        // libraries are found even when they are not on the system PATH.
        if (nativeLibDir != null && !nativeLibDir.isEmpty()) {
            String currentPath = System.getProperty("jna.library.path", "");
            if (currentPath.isEmpty()) {
                System.setProperty("jna.library.path", nativeLibDir);
            } else if (!currentPath.contains(nativeLibDir)) {
                System.setProperty("jna.library.path",
                        nativeLibDir + File.pathSeparator + currentPath);
            }
            LOGGER.debug("[EspeakNgJna] jna.library.path = {}",
                    System.getProperty("jna.library.path"));
        }

        // On Windows the upstream DLL may be named either "espeak-ng.dll"
        // or "libespeak-ng.dll" depending on the build. Try both.
        String[] libNames = Platform.isWindows()
                ? new String[]{"espeak-ng", "libespeak-ng"}
                : new String[]{"espeak-ng"};

        UnsatisfiedLinkError lastError = null;
        EspeakNgLib loaded = null;
        for (String name : libNames) {
            try {
                loaded = Native.load(name, EspeakNgLib.class);
                LOGGER.info("[EspeakNgJna] Loaded native library '{}'", name);
                break;
            } catch (UnsatisfiedLinkError e) {
                lastError = e;
                LOGGER.debug("[EspeakNgJna] Failed to load '{}', trying next...", name);
            }
        }

        if (loaded == null) {
            throw lastError != null ? lastError
                    : new UnsatisfiedLinkError("Unable to load espeak-ng library");
        }
        this.lib = loaded;

        int rate = lib.espeak_Initialize(AUDIO_OUTPUT_SYNCHRONOUS, 0, dataPath, 0);
        if (rate == -1) {
            throw new RuntimeException("espeak_Initialize failed");
        }

        int rc = lib.espeak_SetVoiceByName("en-us");
        if (rc != 0) {
            throw new RuntimeException("espeak_SetVoiceByName failed with code " + rc);
        }

        this.initialized = true;
        LOGGER.info("[EspeakNgJna] Initialized (sampleRate={})", rate);
    }

    /**
     * Convert text to IPA phonemes. Thread-safe via synchronized.
     *
     * <p>espeak_TextToPhonemes expects {@code const void **textptr} — a pointer
     * to the text pointer. We must pass a {@link PointerByReference} so the
     * native function can read the initial text pointer and update it to the
     * remaining unprocessed text.</p>
     *
     * @param text UTF-8 text to phonemize
     * @return IPA phoneme string, or empty string on failure
     */
    public synchronized String phonemize(String text) {
        if (!initialized || text == null || text.isEmpty()) {
            return "";
        }
        try {
            // Allocate native memory for the null-terminated UTF-8 text
            byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
            Memory textMem = new Memory(textBytes.length + 1L);
            textMem.write(0, textBytes, 0, textBytes.length);
            textMem.setByte(textBytes.length, (byte) 0);

            // Pass a pointer-to-pointer; espeak-ng dereferences this to get the text
            // and updates it to point to the remaining unprocessed text.
            // espeak_TextToPhonemes processes ONE clause at a time, so we must loop.
            PointerByReference ref = new PointerByReference(textMem);
            StringBuilder phonemes = new StringBuilder();
            int clauses = 0;

            while (true) {
                Pointer ptr = lib.espeak_TextToPhonemes(ref, CHARS_UTF8, PHONEME_MODE);
                if (ptr == null || ptr == Pointer.NULL) {
                    break;
                }
                String chunk = ptr.getString(0, "UTF-8");
                if (chunk == null || chunk.isEmpty()) {
                    break;
                }
                if (phonemes.length() > 0) {
                    phonemes.append(' ');
                }
                phonemes.append(chunk.trim());
                clauses++;

                // Read remaining text from the updated pointer.
                // We must copy it into fresh memory for the next iteration because
                // the pointer returned by espeak-ng points to its internal decoder
                // buffer, which may be invalidated on the next call.
                Pointer remaining = ref.getValue();
                if (remaining == null || remaining == Pointer.NULL) {
                    break;
                }
                String remainingStr = remaining.getString(0, "UTF-8");
                if (remainingStr == null || remainingStr.isEmpty()) {
                    break;
                }

                // Allocate fresh memory with the remaining text for the next call
                byte[] remainingBytes = remainingStr.getBytes(StandardCharsets.UTF_8);
                Memory remainingMem = new Memory(remainingBytes.length + 1L);
                remainingMem.write(0, remainingBytes, 0, remainingBytes.length);
                remainingMem.setByte(remainingBytes.length, (byte) 0);
                ref.setValue(remainingMem);
            }

            String result = phonemes.toString().trim();
            LOGGER.debug("[EspeakNgJna] phonemized {} clause(s) '{}' -> '{}'", clauses, text, result);
            return result;
        } catch (Exception e) {
            LOGGER.error("[EspeakNgJna] phonemize failed for: {}", text, e);
            return "";
        }
    }

    public void shutdown() {
        if (initialized) {
            lib.espeak_Terminate();
            initialized = false;
            LOGGER.info("[EspeakNgJna] Terminated");
        }
    }

    public boolean isAvailable() {
        return initialized;
    }

    // -------------------------------------------------------------------------
    // JNA Interface
    // -------------------------------------------------------------------------

    interface EspeakNgLib extends Library {
        int espeak_Initialize(int output, int buf_length, String path, int options);

        int espeak_SetVoiceByName(String name);

        Pointer espeak_TextToPhonemes(PointerByReference textptr, int textmode, int phonememode);

        int espeak_Terminate();
    }
}
