package me.sshcrack.mc_talking.client.audio;

import com.mojang.logging.LogUtils;
import me.sshcrack.mc_talking.tts.ClientTtsEngine;
import net.minecraft.client.Minecraft;

import java.util.*;
import java.util.concurrent.*;

/**
 * Per-citizen audio queue that manages segmented TTS synthesis and sequential playback.
 *
 * <p>Flow:
 * <ol>
 *   <li>Full AI text is split into small segments (~5 words each) by {@link TextSegmenter}</li>
 *   <li>First 2 segments are submitted for parallel synthesis immediately</li>
 *   <li>When segment N finishes playing, segment N+1 is played (if ready)</li>
 *   <li>Lookahead synthesis: when segment N starts playing, segment N+2 is submitted</li>
 * </ol>
 */
public class CitizenAudioQueue {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    /** Shared executor for TTS synthesis. Size is read from config at init time. */
    private static ExecutorService TTS_EXECUTOR = createExecutor(4);

    /** Per-citizen speech state. */
    private static final Map<UUID, CitizenSpeechState> STATES = new ConcurrentHashMap<>();

    /**
     * Creates or recreates the TTS executor with the given thread count.
     * Call this after config changes (requires restart or explicit reinit).
     */
    public static synchronized void setThreadCount(int threads) {
        if (TTS_EXECUTOR != null) {
            TTS_EXECUTOR.shutdown();
        }
        TTS_EXECUTOR = createExecutor(Math.max(1, Math.min(16, threads)));
        LOGGER.info("[TTS] Synthesis executor resized to {} threads", threads);
    }

    private static ExecutorService createExecutor(int threads) {
        return Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "McTalking-TTS");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts playback of a new citizen speech, interrupting any existing speech for that citizen.
     *
     * @param citizenId  the citizen's UUID
     * @param text       full AI response text
     * @param speakerId  TTS speaker ID
     * @param speed      speech speed multiplier
     * @param volume     audio volume multiplier
     */
    public static void enqueueSpeech(UUID citizenId, String text, int speakerId, float speed, float volume) {
        // Stop any existing speech for this citizen
        interrupt(citizenId);

        List<String> segments = TextSegmenter.split(text);
        if (segments.isEmpty()) {
            LOGGER.debug("[TTS] No segments produced for citizen {}", citizenId);
            return;
        }

        if (me.sshcrack.mc_talking.config.McTalkingConfig.INSTANCE.instance().debugMode) {
            LOGGER.info("[TTS] Enqueued {} segments for citizen {}", segments.size(), citizenId);
        }
        for (int i = 0; i < segments.size(); i++) {
            LOGGER.debug("[TTS]   Segment {}: '{}'", i, segments.get(i));
        }

        CitizenSpeechState state = new CitizenSpeechState(citizenId, segments, speakerId, speed, volume);
        STATES.put(citizenId, state);

        // Synthesize first 2 segments in parallel for fastest time-to-audio
        submitSynthesis(state, 0);
        if (segments.size() > 1) {
            submitSynthesis(state, 1);
        }
    }

    /**
     * Immediately stops playback and clears all pending segments for a citizen.
     * Called when the player interrupts the citizen.
     */
    public static void interrupt(UUID citizenId) {
        CitizenSpeechState state = STATES.remove(citizenId);
        if (state != null) {
            state.interrupted = true;
            LOGGER.debug("[TTS] Interrupted citizen {}", citizenId);
        }
        PositionalAudioPlayer.stop(citizenId);
    }

    /**
     * Called every client tick to advance the playback queue.
     * Checks if the current segment finished playing and starts the next one.
     */
    public static void tick() {
        if (STATES.isEmpty()) return;

        Iterator<Map.Entry<UUID, CitizenSpeechState>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CitizenSpeechState> entry = it.next();
            CitizenSpeechState state = entry.getValue();

            if (state.interrupted) {
                it.remove();
                continue;
            }

            if (state.isPlaying) {
                // Check if the active sound finished
                if (!PositionalAudioPlayer.isPlaying(state.citizenId)) {
                    state.isPlaying = false;
                    state.currentSegmentIndex++;
                    tryPlayNext(state);
                }
            } else {
                tryPlayNext(state);
            }

            // Cleanup: all segments played and nothing active
            if (state.currentSegmentIndex >= state.segments.size() - 1 && !state.isPlaying) {
                it.remove();
                LOGGER.debug("[TTS] Finished all segments for citizen {}", state.citizenId);
            }
        }
    }

    /** Attempts to play the next segment if its audio has been synthesized. */
    private static void tryPlayNext(CitizenSpeechState state) {
        int nextIndex = state.currentSegmentIndex + 1;
        if (nextIndex >= state.segments.size()) return;

        short[] audio = state.synthesizedSegments.get(nextIndex);
        if (audio == null) return; // Not ready yet

        if (audio.length == 0) {
            // Empty audio (synthesis failed) — skip and advance immediately
            state.currentSegmentIndex++;
            LOGGER.debug("[TTS] Skipping empty segment {} for citizen {}", nextIndex, state.citizenId);
            tryPlayNext(state); // Try next
            return;
        }

        Minecraft.getInstance().execute(() -> {
            if (state.interrupted) return;
            PositionalAudioPlayer.play(state.citizenId, audio, ClientTtsEngine.getInstance().getSampleRate(), state.volume);
        });

        state.isPlaying = true;
        LOGGER.debug("[TTS] Playing segment {} for citizen {}", nextIndex, state.citizenId);

        // Lookahead: submit the segment after next for synthesis
        int lookahead = nextIndex + 1;
        if (lookahead < state.segments.size()) {
            submitSynthesis(state, lookahead);
        }
    }

    private static void submitSynthesis(CitizenSpeechState state, int index) {
        if (!state.submittedForSynthesis.add(index)) return; // Already submitted

        TTS_EXECUTOR.submit(() -> {
            if (state.interrupted) return;
            try {
                String text = state.segments.get(index);
                float[] samples = ClientTtsEngine.getInstance().synthesize(text, state.speakerId, state.speed);
                if (samples != null && samples.length > 0) {
                    state.synthesizedSegments.put(index, floatToShort(samples));
                    LOGGER.debug("[TTS] Synthesized segment {} for citizen {}", index, state.citizenId);
                } else {
                    LOGGER.warn("[TTS] Segment {} produced no audio for citizen {}", index, state.citizenId);
                    // Put empty array so playback doesn't stall forever
                    state.synthesizedSegments.put(index, new short[0]);
                }
            } catch (Exception e) {
                LOGGER.error("[TTS] Synthesis failed for citizen {} segment {}", state.citizenId, index, e);
                state.synthesizedSegments.put(index, new short[0]);
            }
        });
    }

    private static short[] floatToShort(float[] samples) {
        short[] pcm = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            float s = Math.max(-1.0f, Math.min(1.0f, samples[i]));
            pcm[i] = (short) (s * 32767.0f);
        }
        return pcm;
    }

    public static void shutdown() {
        STATES.clear();
        // NOTE: Do NOT shut down TTS_EXECUTOR here. It's a daemon thread pool
        // that should persist across world disconnect/reconnect cycles.
        // onClientSetup() only runs once at mod init, so if we terminate it
        // here, TTS will break forever until Minecraft restarts.
    }

    // -------------------------------------------------------------------------
    // Internal state holder
    // -------------------------------------------------------------------------

    private static class CitizenSpeechState {
        final UUID citizenId;
        final List<String> segments;
        final int speakerId;
        final float speed;
        final float volume;

        int currentSegmentIndex = -1;
        final ConcurrentNavigableMap<Integer, short[]> synthesizedSegments = new ConcurrentSkipListMap<>();
        final Set<Integer> submittedForSynthesis = ConcurrentHashMap.newKeySet();
        volatile boolean interrupted = false;
        volatile boolean isPlaying = false;

        CitizenSpeechState(UUID citizenId, List<String> segments, int speakerId, float speed, float volume) {
            this.citizenId = citizenId;
            this.segments = segments;
            this.speakerId = speakerId;
            this.speed = speed;
            this.volume = volume;
        }
    }
}
