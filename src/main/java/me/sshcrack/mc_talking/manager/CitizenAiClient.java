package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Common interface for citizen AI conversation clients.
 * Abstracts over AI client implementations (currently DeepSeek-only).
 */
public interface CitizenAiClient {
    void promptAudioOpus(byte[] audio);
    void addPromptAudio(short[] audio);
    void addPromptTextImmediate(String text);
    void addPromptTextAfterTalkingComplete(String text);
    void addOnCloseAction(Runnable action);
    void endConversationWhenPossible();
    AbstractEntityCitizen getEntity();
    void close();
    boolean isOpen();
    boolean isClosed();

    @Nullable
    VisibleCitizenStatus getLastStatus();

    void setLastStatus(@Nullable VisibleCitizenStatus status);

    default boolean isMumbling() {
        return false;
    }

    default boolean sendStatusUpdates() {
        return true;
    }
}
