package me.sshcrack.mc_talking.config;

import java.util.List;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

public enum ModalityModes implements NameableEnum {
    TEXT,
    AUDIO,
    TEXT_AND_AUDIO;

    public List<String> getModalities() {
        return switch (this) {
            case TEXT -> List.of("TEXT");
            case AUDIO -> List.of("AUDIO");
            case TEXT_AND_AUDIO -> List.of("TEXT", "AUDIO");
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.literal(name());
    }
}
