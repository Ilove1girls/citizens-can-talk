package me.sshcrack.mc_talking.conversations;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.conversations.memory.CitizenMemoryGenerator;
import me.sshcrack.mc_talking.deepseek.DeepSeekChatClient;
import me.sshcrack.mc_talking.manager.CitizenPromptViewFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CitizenConversationGenerator {
    private static final String CONVERSATION_SYSTEM_PROMPT = """
            Generate a dialogue transcript using the structured format below.
            Participants in this conversation are citizens from the MineColonies mod, each with unique personalities, needs, and relationships. Use the provided citizen information to create an immersive and realistic conversation that reflects their current states and emotional profiles.
            The citizens can not give each other blocks or items, the "manager" of the colony can however.
            Try to incorporate talking about the manager in the colony in this conversation.

            # Audio Profile
            For each character: Describe their vocal identity (tone, personality, emotional baseline).

            # Director's Note
            For each character include:
            - Style (e.g., empathetic, authoritative, unstable, etc.)
            - Pace (e.g., slow, staccato, rushed)
            - Accent (e.g., British RP, American Gen, regional, etc.)

            ## Scene:
            Describe the environment and atmosphere in 1-2 sentences.

            ## Sample Context:
            Define genre, tone, pacing behavior, and emotional tension.

            ## Transcript:
            Write a multi-character dialogue using the provided citizen names instead of generic speaker labels.

            Formatting rules:
            - Use character names exactly as given (e.g., "Tomas Reed:")
            - Include emotional/action cues in square brackets before or within lines (e.g., [shouting], [weakly], [suspicion])
            - Keep dialogue concise but expressive
            - Reflect each character's emotional state, needs, and personality
            - Escalate tension naturally toward the end if appropriate
            - Ensure characters reference their personal conditions, needs, and relationships when relevant
            - Maintain immersive, roleplay-style dialogue

            ---

            ## Input Data:
            You will receive one or more citizens in this format:

            # CITIZEN INFO
            Name: <Full Name>

            Type: <Description>

            ## RELATIONSHIPS
            <List of relationships>

            ## CURRENT STATE
            <List of physical and emotional conditions>

            ## NEEDS (first person)
            <List of urgent needs>

            ## EMOTIONAL PROFILE
            <Behavioral tendencies>

            ---

            ## Task:
            - Generate a realistic, immersive conversation between the given citizens
            - Ensure each character speaks according to their emotional profile and current condition
            - Incorporate their needs naturally into dialogue
            - Match tone and pacing to the context

            ## Rules
            - The transcript is the exact words the model will speak. An audio tag is a word in square brackets that indicates either how something should be said, a change of tone, or an interjection.
            - You can control style, tone, accent, and pace using natural language prompts or audio tags.
            - For audio tags, make sure to only focus on voices, not physical actions or sounds.
            Example:
            ```
            Thomas: I know right, [sarcastically] I couldn't believe it. [whispers] She should have totally left
            at that point.

            Nils: [cough] Well, [sighs] I guess it doesn't matter now.
            ```
            """;

    public static void generateConversation(List<AbstractEntityCitizen> conversationEntities, MinecraftServer server) throws ConversationGenerationException {
        StringBuilder citizenInfo = new StringBuilder();
        citizenInfo.append("-----\n");

        Map<UUID, String> interestedParties = new HashMap<>();
        conversationEntities.forEach(e -> interestedParties.put(e.getUUID(), e.getCitizenData().getName()));

        for (AbstractEntityCitizen entity : conversationEntities) {
            CitizenPromptView view = CitizenPromptViewFactory.create(entity.getCitizenData(), interestedParties, null);
            citizenInfo.append(CitizenPromptService.generateConversationalInfoPrompt(view)).append("\n-----\n");
        }

        RawConversation conversation = generateConversationAndMemory(conversationEntities, citizenInfo, server);

        // Send conversation lines to chat as text (TTS is future work)
        String[] lines = conversation.conversation().split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Try to find which citizen is speaking
            for (AbstractEntityCitizen citizen : conversationEntities) {
                String name = citizen.getCitizenData().getName();
                if (line.startsWith(name + ":")) {
                    String msg = line.substring(name.length() + 1).trim();
                    if (McTalkingConfig.INSTANCE.instance().showCitizenChat) {
                        var component = Component.literal(name + ": ").append(Component.literal(msg));
                        for (var player : server.getPlayerList().getPlayers()) {
                            if (player.level() == citizen.level() && player.distanceTo(citizen) <= 32) {
                                player.sendSystemMessage(component);
                            }
                        }
                    }
                    break;
                }
            }
        }

        var generator = conversation.generator();
        if (generator != null)
            generator.scheduleOrSaveMemory();
    }

    public record RawConversation(String conversation, @Nullable CitizenMemoryGenerator generator) {

    }

    @NotNull
    private static RawConversation generateConversationAndMemory(List<AbstractEntityCitizen> conversationEntities, StringBuilder citizenInfo, MinecraftServer server) throws ConversationGenerationException {
        var config = McTalkingConfig.INSTANCE.instance();
        String apiKey = config.deepseekApiKey;
        String model = config.deepseekModel.isBlank() ? "deepseek-chat" : config.deepseekModel;
        var client = new DeepSeekChatClient(apiKey, model);

        var messages = new ArrayList<DeepSeekChatClient.Message>();
        messages.add(new DeepSeekChatClient.Message("system", CONVERSATION_SYSTEM_PROMPT));
        messages.add(new DeepSeekChatClient.Message("user", citizenInfo.toString()));

        String rawConversationOutput;
        try {
            McTalking.LOGGER.info("[DeepSeek] Generating conversation for {} citizens", conversationEntities.size());
            var response = client.chat(messages, null);
            rawConversationOutput = response.content();
        } catch (Exception e) {
            throw new ConversationGenerationException("Failed to generate conversation using DeepSeek", e);
        }

        CitizenMemoryGenerator generator = null;
        if (McTalkingConfig.INSTANCE.instance().enableCitizenMemory) {
            generator = CitizenMemoryGenerator.addAndGenerateMemory(rawConversationOutput, conversationEntities, server);
        }
        return new RawConversation(rawConversationOutput, generator);
    }
}
