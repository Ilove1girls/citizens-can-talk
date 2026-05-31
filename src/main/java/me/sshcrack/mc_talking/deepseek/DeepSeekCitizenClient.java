package me.sshcrack.mc_talking.deepseek;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.ModalityModes;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import me.sshcrack.mc_talking.manager.CitizenAiClient;
import me.sshcrack.mc_talking.manager.CitizenPromptViewFactory;
import me.sshcrack.mc_talking.manager.audio.AudioProvider;
import me.sshcrack.mc_talking.manager.tools.AITools;
import me.sshcrack.mc_talking.manager.tools.FunctionAction;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.server.CitizenSpeechBroadcaster;
import me.sshcrack.mc_talking.stt.ServerSttEngine;
import me.sshcrack.mc_talking.util.AiStatusHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * DeepSeek-based citizen conversation client.
 *
 * <p>Flow:
 * <ol>
 *   <li>Buffers incoming PCM audio from the player</li>
 *   <li>After silence is detected, converts audio to WAV and sends to Whisper STT</li>
 *   <li>Sends transcript + system prompt + memories to DeepSeek Chat</li>
 *   <li>Displays the response in Minecraft chat as citizen speech</li>
 * </ol>
 *
 * <p>Tool calling is TODO for Phase 2.
 */
public class DeepSeekCitizenClient implements CitizenAiClient {

    private static final int SAMPLE_RATE = 48000;
    private static final long SILENCE_THRESHOLD_MS = 800;
    /** Minimum average absolute PCM amplitude to count as "voice" (16-bit range: 0-32767) */
    private static final int VOICE_ENERGY_THRESHOLD = 350;

    private final AbstractEntityCitizen entity;
    private final DeepSeekChatClient chat;
    /** Accumulated PCM audio samples from the player's microphone. Flushed when silence is detected. */
    private final List<Short> audioBuffer = new ArrayList<>();
    private final List<DeepSeekChatClient.Message> messageHistory = Collections.synchronizedList(new ArrayList<>());
    private final List<Runnable> onCloseActions = new ArrayList<>();
    @Nullable
    private final OpusDecoder decoder;

    @Nullable
    private ServerPlayer player;
    private boolean startedInSystemMode;
    @Nullable
    private VisibleCitizenStatus lastStatus;
    private boolean shouldEndConversation = false;
    private boolean closed = false;

    private volatile long lastAudioTimestamp = 0;
    private volatile boolean isProcessing = false;
    private final ScheduledExecutorService silenceChecker = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> silenceCheckTask;
    /** Pending queue tasks that can be cancelled when this client closes */
    private final List<Future<?>> pendingQueueTasks = new ArrayList<>();

    public DeepSeekCitizenClient(AbstractEntityCitizen entity, @Nullable ServerPlayer player) {
        this(null, entity, player);
    }

    public DeepSeekCitizenClient(@Nullable AudioProvider audioProvider, AbstractEntityCitizen entity, @Nullable ServerPlayer player) {
        this.entity = entity;
        this.player = player;
        this.startedInSystemMode = (player == null);
        this.decoder = audioProvider != null ? audioProvider.createDecoder() : null;

        var config = McTalkingConfig.INSTANCE.instance();
        String dsKey = config.deepseekApiKey;
        String model = config.deepseekModel.isBlank() ? DeepSeekModel.getDefaultModel() : config.deepseekModel;
        this.chat = new DeepSeekChatClient(dsKey, model);

        // Add system message
        String systemPrompt = buildSystemPrompt();
        if (!systemPrompt.isBlank()) {
            messageHistory.add(new DeepSeekChatClient.Message("system", systemPrompt));
        }

        // Start silence detection
        this.silenceCheckTask = silenceChecker.scheduleAtFixedRate(this::checkSilence, 200, 200, TimeUnit.MILLISECONDS);
    }

    // -------------------------------------------------------------------------
    // CitizenAiClient implementation
    // -------------------------------------------------------------------------

    @Override
    public void promptAudioOpus(byte[] audio) {
        if (decoder == null) {
            McTalking.LOGGER.warn("[DeepSeek] No OpusDecoder available, cannot decode audio");
            return;
        }
        short[] pcm = decoder.decode(audio);
        if (McTalkingConfig.INSTANCE.instance().debugMode) {
            if (pcm == null) {
                McTalking.LOGGER.info("[DeepSeek] Decoder returned null for opus packet (silence/empty)");
            } else {
                McTalking.LOGGER.info("[DeepSeek] Decoded opus packet to {} PCM samples", pcm.length);
            }
        }
        addPromptAudio(pcm);
    }

    @Override
    public void addPromptAudio(short[] audio) {
        if (closed || audio == null || audio.length == 0) return;

        // Accumulate audio for batch transcription on silence detection
        for (short s : audio) {
            audioBuffer.add(s);
        }

        // Acoustic VAD: only reset silence timer when there's actual voice energy.
        // This allows silence detection to work even when push-to-talk is held
        // but the player stops speaking.
        if (hasVoiceEnergy(audio)) {
            lastAudioTimestamp = System.currentTimeMillis();
            if (McTalkingConfig.INSTANCE.instance().debugMode) {
                McTalking.LOGGER.debug("[DeepSeek] Voice energy detected ({} samples)", audio.length);
            }
        }
    }

    /**
     * Quick RMS-based voice activity detection.
     * Returns true if the average absolute amplitude exceeds the threshold.
     */
    private static boolean hasVoiceEnergy(short[] pcm) {
        long sum = 0;
        for (short s : pcm) {
            sum += Math.abs(s);
        }
        int avg = (int) (sum / pcm.length);
        return avg > VOICE_ENERGY_THRESHOLD;
    }

    @Override
    public void addPromptTextImmediate(String text) {
        if (closed) return;
        // For DeepSeek, immediate text is treated as a user message
        Future<?> future = DeepSeekRequestQueue.submit(() -> sendToDeepSeek(text));
        pendingQueueTasks.add(future);
    }

    @Override
    public void addPromptTextAfterTalkingComplete(String text) {
        if (closed) return;
        // This is used for mumbling prompts. Treat as a user message.
        Future<?> future = DeepSeekRequestQueue.submit(() -> sendToDeepSeek(text));
        pendingQueueTasks.add(future);
    }

    @Override
    public void addOnCloseAction(Runnable action) {
        onCloseActions.add(action);
    }

    @Override
    public void endConversationWhenPossible() {
        this.shouldEndConversation = true;
    }

    @Override
    public AbstractEntityCitizen getEntity() {
        return entity;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;

        if (silenceCheckTask != null) {
            silenceCheckTask.cancel(false);
        }
        silenceChecker.shutdown();

        // Cancel any queued tasks that haven't started yet so they don't
        // waste queue slots on a dead client.
        for (Future<?> f : pendingQueueTasks) {
            if (!f.isDone()) {
                f.cancel(false);
            }
        }
        pendingQueueTasks.clear();

        // Generate player conversation memory via DeepSeek

        AiStatusHelper.setAiStatusSynced(entity, AiStatus.NONE);

        for (Runnable action : onCloseActions) {
            try {
                action.run();
            } catch (Exception e) {
                McTalking.LOGGER.error("[DeepSeek] Error in onClose action", e);
            }
        }

        McTalking.LOGGER.info("[DeepSeek] Closed client for {}", entity.getStringUUID());
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    @Nullable
    public VisibleCitizenStatus getLastStatus() {
        return lastStatus;
    }

    @Override
    public void setLastStatus(@Nullable VisibleCitizenStatus status) {
        this.lastStatus = status;
    }

    @Override
    public boolean isMumbling() {
        return startedInSystemMode;
    }

    // -------------------------------------------------------------------------
    // Internal logic
    // -------------------------------------------------------------------------

    private void checkSilence() {
        if (closed || isProcessing) return;

        long silenceDuration = System.currentTimeMillis() - lastAudioTimestamp;
        if (lastAudioTimestamp == 0) return; // No audio received yet
        if (silenceDuration < SILENCE_THRESHOLD_MS) return;

        lastAudioTimestamp = 0; // Reset so we don't reprocess
        isProcessing = true;
        if (McTalkingConfig.INSTANCE.instance().debugMode) {
            McTalking.LOGGER.info("[DeepSeek] Silence detected ({}ms), queueing speech task...", silenceDuration);
        }
        AiStatusHelper.setAiStatusSynced(entity, AiStatus.THINKING);

        // Offload STT + LLM to the shared FCFS queue (max 2 concurrent)
        Future<?> future = DeepSeekRequestQueue.submit(() -> {
            if (closed) {
                isProcessing = false;
                return;
            }
            try {
                processSpeech();
            } catch (Exception e) {
                McTalking.LOGGER.error("[DeepSeek] Queued speech task failed for {}", entity.getDisplayName().getString(), e);
            } finally {
                isProcessing = false;
            }
        });
        pendingQueueTasks.add(future);
    }

    private void processSpeech() {
        String transcript;
        if (audioBuffer.isEmpty()) {
            transcript = "";
        } else if (ServerSttEngine.getInstance().isAvailable()) {
            // Convert accumulated short samples to float [-1, 1] for Sherpa-ONNX
            float[] samples = new float[audioBuffer.size()];
            long sumAbs = 0;
            short maxAbs = 0;
            for (int i = 0; i < audioBuffer.size(); i++) {
                short s = audioBuffer.get(i);
                samples[i] = s / 32768.0f;
                sumAbs += Math.abs(s);
                if (Math.abs(s) > maxAbs) maxAbs = (short) Math.abs(s);
            }
            float avgAbs = audioBuffer.isEmpty() ? 0 : (float) sumAbs / audioBuffer.size();
            float durationSec = audioBuffer.size() / (float) SAMPLE_RATE;
            if (McTalkingConfig.INSTANCE.instance().debugMode) {
                McTalking.LOGGER.info(
                    "[DeepSeek] Audio stats: {} samples, {:.2f}s, max amp={}, avg amp={:.1f}",
                    audioBuffer.size(), durationSec, maxAbs, avgAbs
                );
                saveDebugWav(audioBuffer, SAMPLE_RATE);
            }

            transcript = ServerSttEngine.getInstance().transcribe(samples, SAMPLE_RATE);
        } else {
            McTalking.LOGGER.warn("[DeepSeek] STT engine not available, cannot transcribe speech");
            transcript = "";
        }
        audioBuffer.clear();

        if (McTalkingConfig.INSTANCE.instance().debugMode) {
            McTalking.LOGGER.info("[DeepSeek] Whisper transcript: '{}'", transcript);
        }
        if (transcript.isBlank()) {
            McTalking.LOGGER.info("[DeepSeek] Empty transcript, skipping");
            AiStatusHelper.setAiStatusSynced(entity, AiStatus.LISTENING);
            return;
        }

        if (McTalkingConfig.INSTANCE.instance().debugMode) {
            McTalking.LOGGER.info("[DeepSeek] Player said: {}", transcript);
        }
        sendToDeepSeek(transcript);
    }

    private void sendToDeepSeek(String userMessage) {
        if (closed) return;

        // Interrupt any ongoing speech from this citizen before generating a new response.
        // Clients will stop playback immediately so the citizen can listen to the player.
        CitizenSpeechBroadcaster.broadcastInterrupt(entity);

        if (McTalkingConfig.INSTANCE.instance().debugMode) {
            McTalking.LOGGER.info("[DeepSeek] Sending to DeepSeek: '{}'", userMessage);
        }

        synchronized (messageHistory) {
            messageHistory.add(new DeepSeekChatClient.Message("user", userMessage));
            trimHistory();
        }

        AiStatusHelper.setAiStatusSynced(entity, AiStatus.TALKING);

        boolean isPlayerConversation = resolveActivePlayer() != null;
        var tools = AITools.getEnabledTools(isPlayerConversation);

        // First call: may return tool calls
        DeepSeekChatClient.ChatResponse response = chat.chat(messageHistory, tools);

        if (closed) return;

        // Handle tool calls
        if (response.hasToolCalls()) {
            synchronized (messageHistory) {
                messageHistory.add(DeepSeekChatClient.Message.assistantWithTools(response.content(), response.toolCalls()));
            }

            var server = entity.level().getServer();
            if (server == null) {
                McTalking.LOGGER.error("[DeepSeek] Server is null, cannot execute tools");
                return;
            }

            for (DeepSeekChatClient.ToolCall tc : response.toolCalls()) {
                FunctionAction action = AITools.registeredFunctions.get(tc.name());
                if (action == null) {
                    action = AITools.playerConversationOnlyTools.get(tc.name());
                }

                String resultJson;
                if (action == null) {
                    McTalking.LOGGER.warn("[DeepSeek] Unknown tool call: {}", tc.name());
                    resultJson = "{\"error\":\"Unknown tool\"}";
                } else {
                    JsonObject args = JsonParser.parseString(tc.arguments()).getAsJsonObject();
                    IColony colony = entity.getCitizenColonyHandler().getColony();
                    final FunctionAction finalAction = action;
                    final JsonObject finalArgs = args;
                    final IColony finalColony = colony;
                    // Execute on server thread since it touches entity state
                    var resultHolder = new Object(){ JsonObject result; };
                    server.executeBlocking(() -> {
                        try {
                            resultHolder.result = finalAction.execute(entity, finalColony, finalArgs);
                        } catch (Exception e) {
                            McTalking.LOGGER.error("[DeepSeek] Tool {} threw exception", tc.name(), e);
                            var err = new JsonObject();
                            err.addProperty("error", e.getMessage());
                            resultHolder.result = err;
                        }
                    });
                    resultJson = resultHolder.result != null ? resultHolder.result.toString() : "{\"error\":\"null result\"}";
                }

                synchronized (messageHistory) {
                    messageHistory.add(DeepSeekChatClient.Message.toolResult(tc.id(), resultJson));
                }
            }

            // Second call: get final response after tool results
            response = chat.chat(messageHistory, tools);
            if (closed) return;
        }

        String reply = response.content();
        if (reply == null || reply.isBlank()) {
            reply = "...";
        }

        // Strip any *action* descriptors the AI might have ignored the prompt about
        reply = stripActionDescriptors(reply);

        if (McTalkingConfig.INSTANCE.instance().debugMode) {
            McTalking.LOGGER.info("[DeepSeek] Got reply: '{}'", reply);
        }

        synchronized (messageHistory) {
            messageHistory.add(new DeepSeekChatClient.Message("assistant", reply));
        }

        // Send to chat (if enabled)
        if (McTalkingConfig.INSTANCE.instance().showCitizenChat) {
            var targetPlayer = resolveActivePlayer();
            var message = entity.getDisplayName().copy().append(": ").append(Component.literal(reply));

            if (targetPlayer != null) {
                targetPlayer.sendSystemMessage(message);
                if (McTalkingConfig.INSTANCE.instance().debugMode) {
                    McTalking.LOGGER.info("[DeepSeek] Sent reply to player {}: {}", targetPlayer.getName().getString(), reply);
                }
            } else if (McTalkingConfig.INSTANCE.instance().sendMumblingAndConversationsToChat) {
                var server = entity.level().getServer();
                if (server != null) {
                    double range = McTalkingConfig.INSTANCE.instance().mumblingDetectionRange * 2;
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        if (p.level() == entity.level() && p.distanceTo(entity) <= range) {
                            p.sendSystemMessage(message);
                        }
                    }
                }
            }
        }

        if (McTalkingConfig.INSTANCE.instance().debugMode) {
            McTalking.LOGGER.info("[DeepSeek] Citizen {} replied: {}", entity.getDisplayName().getString(), reply);
        }

        // Broadcast speech to nearby clients for TTS synthesis
        // (client decides whether to play audio based on its own settings)
        CitizenSpeechBroadcaster.broadcastSpeech(entity, reply, 1.0f);

        AiStatusHelper.setAiStatusSynced(entity, AiStatus.LISTENING);

        if (shouldEndConversation || startedInSystemMode) {
            var playerUUID = ConversationManager.getPlayerForEntity(entity.getUUID());
            if (playerUUID != null && !startedInSystemMode) {
                ConversationManager.endConversation(playerUUID, false);
            } else {
                close();
            }
        }
    }

    private String buildSystemPrompt() {
        Map<UUID, String> interestedParties = new HashMap<>();
        if (player != null) {
            interestedParties.put(player.getUUID(), player.getName().getString());
        }

        String prompt;
        if (startedInSystemMode) {
            var view = CitizenPromptViewFactory.create(entity.getCitizenData(), new HashMap<>(), null);
            prompt = CitizenPromptService.generateSystemControlledRoleplayPrompt(view);
        } else {
            var promptView = CitizenPromptViewFactory.create(entity.getCitizenData(), interestedParties, player);
            prompt = CitizenPromptService.generateCitizenRoleplayPrompt(promptView);
        }

        // Append memories
        var data = entity.getCitizenData();
        if (data instanceof CitizenDataMemoryExtended ext) {
            var memory = ext.mc_talking$getMemory();
            if (memory != null) {
                String memPrompt = memory.toPrompt(interestedParties);
                if (!memPrompt.isBlank()) {
                    prompt += "\n\n# Your Memories\n" + memPrompt;
                }
            }
        }

        return prompt;
    }

    @Nullable
    private ServerPlayer resolveActivePlayer() {
        if (player != null) return player;
        var playerUUID = ConversationManager.getPlayerForEntity(entity.getUUID());
        if (playerUUID == null) return null;
        return Objects.requireNonNull(entity.level().getServer()).getPlayerList().getPlayer(playerUUID);
    }

    /**
     * Removes any text wrapped in asterisks (e.g. *chuckles*, *sighs*) from the AI response.
     * This is a safety net in case the AI ignores the prompt instructions.
     */
    private static String stripActionDescriptors(String text) {
        if (text == null || text.isBlank()) return text;
        // Remove standalone lines that are entirely *actions*
        StringBuilder result = new StringBuilder();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*") && trimmed.endsWith("*")) {
                continue; // Skip pure action lines
            }
            // Remove inline *action* descriptors within the line
            String cleaned = trimmed.replaceAll("\\*[^*]+\\*", "").trim();
            if (!cleaned.isEmpty()) {
                if (!result.isEmpty()) {
                    result.append("\n");
                }
                result.append(cleaned);
            }
        }
        String output = result.toString().trim();
        // Clean up any double spaces left behind
        output = output.replaceAll("  +", " ");
        return output.isEmpty() ? "..." : output;
    }

    /**
     * Saves the raw accumulated audio buffer to a debug WAV file for inspection.
     * Files are written to {@code <game-dir>/mc_talking/debug/stt/}.
     */
    private static void saveDebugWav(List<Short> buffer, int sampleRate) {
        try {
            Path debugDir = Path.of(System.getProperty("user.dir"), "mc_talking", "debug", "stt");
            Files.createDirectories(debugDir);
            String filename = "stt_" + System.currentTimeMillis() + ".wav";
            Path wavPath = debugDir.resolve(filename);

            int numSamples = buffer.size();
            int byteRate = sampleRate * 2; // 16-bit mono
            int dataSize = numSamples * 2;

            ByteBuffer header = ByteBuffer.allocate(44);
            header.order(ByteOrder.LITTLE_ENDIAN);
            header.put("RIFF".getBytes());
            header.putInt(36 + dataSize);
            header.put("WAVE".getBytes());
            header.put("fmt ".getBytes());
            header.putInt(16); // subchunk1Size
            header.putShort((short) 1); // audioFormat = PCM
            header.putShort((short) 1); // numChannels = mono
            header.putInt(sampleRate);
            header.putInt(byteRate);
            header.putShort((short) 2); // blockAlign
            header.putShort((short) 16); // bitsPerSample
            header.put("data".getBytes());
            header.putInt(dataSize);

            try (FileOutputStream fos = new FileOutputStream(wavPath.toFile())) {
                fos.write(header.array());
                ByteBuffer sampleData = ByteBuffer.allocate(dataSize);
                sampleData.order(ByteOrder.LITTLE_ENDIAN);
                for (short s : buffer) {
                    sampleData.putShort(s);
                }
                fos.write(sampleData.array());
            }
            McTalking.LOGGER.info("[DeepSeek] Debug WAV saved: {}", wavPath);
        } catch (Exception e) {
            McTalking.LOGGER.error("[DeepSeek] Failed to save debug WAV", e);
        }
    }

    private void trimHistory() {
        int maxMessages = 20;
        if (messageHistory.size() <= maxMessages + 1) return; // +1 for system message

        // Keep system message (index 0) and last maxMessages
        List<DeepSeekChatClient.Message> trimmed = new ArrayList<>();
        trimmed.add(messageHistory.get(0)); // system
        trimmed.addAll(messageHistory.subList(messageHistory.size() - maxMessages, messageHistory.size()));
        messageHistory.clear();
        messageHistory.addAll(trimmed);
    }
}
