package me.sshcrack.mc_talking;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import me.sshcrack.mc_talking.conversations.memory.CitizenMemoryGenerator;
import me.sshcrack.mc_talking.conversations.memory.PlayerConversationMemoryGenerator;
import net.minecraft.server.level.ServerPlayer;
import me.sshcrack.mc_talking.config.McTalkingConfig;

@SuppressWarnings("unused")
@ForgeVoicechatPlugin
public class McTalkingVoicechatPlugin implements VoicechatPlugin {
    public static final int TARGET_SAMPLE_RATE = 48000;
    public static VoicechatServerApi vcApi;
    public static final String DIRECT_PLAYER_DIALOG = "ptc_dialog";
    public static final String CITIZEN_CONVERSATION = "ctc_dialog";

    @Override
    public String getPluginId() {
        return McTalking.MODID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        McTalking.LOGGER.info("Initializing Voicechat Plugin");
    }

    public void onStop(VoicechatServerStoppedEvent event) {
        CitizenMemoryGenerator.stopAllGenerators();
        PlayerConversationMemoryGenerator.stopAllGenerators();
    }

    public void onServerStarted(VoicechatServerStartedEvent event) {
        McTalking.LOGGER.info("Voicechat Server Started");
        vcApi = event.getVoicechat();

        VolumeCategory directDialog = vcApi.volumeCategoryBuilder()
                .setId(DIRECT_PLAYER_DIALOG)
                .setName("P2CitizenDialog")
                .setDescription("The volume of the citizens voice when talking directly to the player (so when the player used the Talking Device)")
                .build();
        VolumeCategory citizenConversation = vcApi.volumeCategoryBuilder()
                .setId(CITIZEN_CONVERSATION)
                .setName("C2CitizenDialog")
                .setDescription("The volume of the citizens voice when talking in a conversation with other citizens (so not directly to the player)")
                .build();

        //TODO add icons here

        vcApi.registerVolumeCategory(directDialog);
        vcApi.registerVolumeCategory(citizenConversation);
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::handleMicPacket);
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
        registration.registerEvent(VoicechatServerStoppedEvent.class, this::onStop);
    }

    public void handleMicPacket(MicrophonePacketEvent event) {
        var sender = event.getSenderConnection();
        if (sender == null) {
            return;
        }

        var packet = event.getPacket();
        if (packet.isWhispering()) {
            return;
        }
        if (sender.isDisabled()) {
            return;
        }
        var vcPlayer = sender.getPlayer();
        if (sender.isInGroup() && !McTalkingConfig.INSTANCE.instance().respondInGroups) {
            return;
        }

        var player = (ServerPlayer) vcPlayer.getPlayer();
        var entity = ConversationManager.getActiveEntityForPlayer(player.getUUID());
        if (entity == null) {
            return;
        }

        var manager = ConversationManager.getClientForEntity(entity.getUUID());
        if (manager == null) {
            return;
        }

        if (me.sshcrack.mc_talking.config.McTalkingConfig.INSTANCE.instance().debugMode) {
            McTalking.LOGGER.info("[VoiceChat] Received mic packet from {}, forwarding to citizen {}", player.getName().getString(), entity.getName().getString());
        }
        manager.promptAudioOpus(packet.getOpusEncodedData());
    }
}
