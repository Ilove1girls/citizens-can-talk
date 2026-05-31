package me.sshcrack.mc_talking.conversations;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import me.sshcrack.mc_talking.config.McTalkingConfig;

/**
 * Orchestrates a citizen-to-citizen conversation.
 *
 * <p>DeepSeek-only mode: generates a dialogue script via DeepSeek Chat and displays
 * it as text messages in Minecraft chat. Audio/TTS is a future enhancement.</p>
 */
public class CitizenConversation {
    private final List<AbstractEntityCitizen> participants;
    private final AtomicReference<ConversationState> state = new AtomicReference<>(ConversationState.GENERATING);
    private final MinecraftServer server;

    private Consumer<ConversationState> onStateChanged;

    public enum ConversationState {
        GENERATING,
        PLAYING,
        ENDED
    }

    public CitizenConversation(MinecraftServer server, List<AbstractEntityCitizen> participants) {
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("Conversation must have at least one participant");
        }

        this.participants = participants;
        this.server = server;

        McTalking.LOGGER.info("Starting citizen-to-citizen conversation for participants: {}",
                participants.stream().map(c -> c.getName().getString()).toList());
    }

    public void performConversation() {
        if (participants.size() < 2) {
            McTalking.LOGGER.warn("[CitizenConversation] Need at least 2 participants, got {}. Aborting.", participants.size());
            setState(ConversationState.ENDED);
            return;
        }

        AbstractEntityCitizen citizenA = participants.get(0);
        AbstractEntityCitizen citizenB = participants.get(1);
        UUID idA = citizenA.getUUID();
        UUID idB = citizenB.getUUID();

        if (ConversationManager.isCitizenBusy(idA) || ConversationManager.isCitizenBusy(idB)) {
            McTalking.LOGGER.info("[CitizenConversation] One or both citizens already busy, aborting");
            setState(ConversationState.ENDED);
            return;
        }

        if (!ConversationManager.hasLowPriorityCapacity(2)) {
            McTalking.LOGGER.info("[CitizenConversation] Not enough free slots, aborting");
            setState(ConversationState.ENDED);
            return;
        }

        if (!ConversationManager.claimSlot(idA, false) || !ConversationManager.claimSlot(idB, false)) {
            ConversationManager.releaseSlot(idA);
            ConversationManager.releaseSlot(idB);
            McTalking.LOGGER.warn("[CitizenConversation] Failed to claim slots, aborting");
            setState(ConversationState.ENDED);
            return;
        }

        setState(ConversationState.GENERATING);

        new Thread(() -> {
            try {
                CitizenConversationGenerator.generateConversation(participants, server);
            } catch (ConversationGenerationException e) {
                McTalking.LOGGER.error("Failed to generate citizen conversation: {}", e.getMessage(), e);
            } finally {
                ConversationManager.releaseSlot(idA);
                ConversationManager.releaseSlot(idB);
                ConversationManager.recordCooldown(idA);
                ConversationManager.recordCooldown(idB);
                setState(ConversationState.ENDED);
            }
        }).start();
    }

    public void setOnStateChanged(Consumer<ConversationState> callback) {
        this.onStateChanged = callback;
    }

    private void setState(ConversationState newState) {
        McTalking.LOGGER.info("Conversation state changed to {}", newState);
        state.set(newState);
        if (onStateChanged != null) {
            onStateChanged.accept(newState);
        }
    }
}
