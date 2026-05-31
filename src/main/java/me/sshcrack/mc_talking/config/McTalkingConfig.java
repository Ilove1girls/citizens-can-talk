package me.sshcrack.mc_talking.config;

import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.controller.ControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.ConfigField;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.autogen.AutoGen;
import dev.isxander.yacl3.config.v2.api.autogen.DoubleField;
import dev.isxander.yacl3.config.v2.api.autogen.DoubleSlider;
import dev.isxander.yacl3.config.v2.api.autogen.EnumCycler;
import dev.isxander.yacl3.config.v2.api.autogen.IntField;
import dev.isxander.yacl3.config.v2.api.autogen.ListGroup;
import dev.isxander.yacl3.config.v2.api.autogen.StringField;
import dev.isxander.yacl3.config.v2.api.autogen.TickBox;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import dev.isxander.yacl3.platform.YACLPlatform;
import me.sshcrack.mc_talking.McTalking;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration class for the McTalking mod.
 * Handles loading and managing configuration options.
 */
public class McTalkingConfig {
    public static final ConfigClassHandler<McTalkingConfig> INSTANCE = ConfigClassHandler.createBuilder(McTalkingConfig.class)
            .id(YACLPlatform.rl("mc_talking", "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(YACLPlatform.getConfigDir().resolve("yacl-mc_talking.json5"))
                    .setJson5(true)
                    .build())
            .build();

    // DeepSeek Configuration
    @AutoGen(category = "api")
    @StringField
    @SerialEntry(comment = "API key for DeepSeek. Get one at https://platform.deepseek.com/")
    public String deepseekApiKey = "";

    @AutoGen(category = "api")
    @StringField
    @SerialEntry(comment = "DeepSeek model to use. Default: deepseek-chat")
    public String deepseekModel = "deepseek-chat";

    // Language Configuration
    @AutoGen(category = "general")
    @StringField
    @SerialEntry(comment = "The language the AI should use to speak")
    public String language = "en-US";

    // Interaction Configuration
    @AutoGen(category = "general", group = "interaction")
    @TickBox
    @SerialEntry(comment = "Whether the citizens should respond if the player is in a group or not.")
    public boolean respondInGroups = false;

    @AutoGen(category = "general", group = "interaction")
    @TickBox
    @SerialEntry(comment = "If true, text messages from mumbling and citizen-to-citizen conversations will also be sent to nearby players in chat.")
    public boolean sendMumblingAndConversationsToChat = false;

    // Citizen - Citizen Interaction (Conversations between them)
    @AutoGen(category = "citizens", group = "citizen_to_citizen")
    @TickBox
    @SerialEntry(comment = "If true, citizens will remember previous conversations that happened between them. Not recommended for free tier")
    public boolean enableCitizenMemory = false;

    @AutoGen(category = "citizens", group = "citizen_to_citizen")
    @TickBox
    @SerialEntry(comment = "If true, citizens will be able to start conversations with each other without player involvement.")
    public boolean enableCitizenToCitizenConversation = true;

    // Random citizen-to-citizen conversations
    @AutoGen(category = "citizens", group = "random_conversations")
    @TickBox
    @SerialEntry(comment = "If true, citizens will randomly start conversations with each other based on the chance below. Requires enableCitizenToCitizenConversation to be true.")
    public boolean enableRandomConversations = true;

    @AutoGen(category = "citizens", group = "random_conversations")
    @DoubleSlider(min = 0.0, max = 1.0, step = 0.01)
    @SerialEntry(comment = "Chance (0.0-1.0) that a pair of nearby citizens start a random conversation per check interval")
    public double randomConversationChance = 0.05;

    @AutoGen(category = "citizens", group = "random_conversations")
    @IntField(min = 1, max = 10000)
    @SerialEntry(comment = "How often (in server ticks) to check for random citizen conversations. 20 ticks = 1 second")
    public int randomConversationCheckIntervalTicks = 400;

    // Resource Management
    @AutoGen(category = "general", group = "resource_management")
    @IntField(min = 1, max = 100)
    @SerialEntry(comment = "Maximum number of AI agents that can be activated at once (for free tier Flash2.0 this is limited to 3, for Flash2.5 to 1)")
    public int maxConcurrentAgents = 3;

    @AutoGen(category = "general", group = "interaction")
    @DoubleField(min = 1.0, max = 100.0)
    @SerialEntry(comment = "Maximum distance the player can be from a citizen before the conversation is ended")
    public double maxConversationDistance = 16.0;

    @AutoGen(category = "general", group = "interaction")
    @EnumCycler
    @SerialEntry(comment = "In which format the AI should respond. TEXT = chat only, AUDIO = voice only, TEXT_AND_AUDIO = both.")
    public ModalityModes modality = ModalityModes.TEXT_AND_AUDIO;

    @AutoGen(category = "general")
    @ListGroup(valueFactory = ToolListFactory.class, controllerFactory = ToolListFactory.class)
    @SerialEntry(comment = "List of disabled tools for the AI. These tools can't be used by the AI to perform actions. Use the /list_tools command to see a list of the available tools.")
    public List<String> disabledTools = new ArrayList<>();

    @AutoGen(category = "general")
    @TickBox
    @SerialEntry(comment = "If true, errors will be sent to players that have OP permissions. If false, errors will only be logged to the console.")
    public boolean sendErrorsToPlayers = true;

    @AutoGen(category = "general")
    @TickBox
    @SerialEntry(comment = "If true, enables verbose debug logging and saves debug files (e.g. STT WAV dumps). Use only for troubleshooting — fills disk and logs quickly.")
    public boolean debugMode = false;

    @AutoGen(category = "general")
    @TickBox
    @SerialEntry(comment = "If true, citizen speech text will appear in the Minecraft chat. If false, citizens speak only via voice/TTS.")
    public boolean showCitizenChat = false;

    // TTS Configuration
    @AutoGen(category = "general", group = "tts")
    @TickBox
    @SerialEntry(comment = "If true, citizen speech will be synthesized to audio using the downloaded TTS model.")
    public boolean enableTts = true;

    @AutoGen(category = "general", group = "tts")
    @EnumCycler
    @SerialEntry(comment = "TTS engine to use. PIPER = Sherpa-ONNX with LibriTTS model (default). KOKORO = ONNX Runtime with Kokoro model (higher quality, requires download).")
    public TtsEngineMode ttsEngine = TtsEngineMode.PIPER;

    @AutoGen(category = "general", group = "tts")
    @DoubleSlider(min = 0.5, max = 2.0, step = 0.1)
    @SerialEntry(comment = "Speech speed multiplier for TTS. 1.0 = normal speed.")
    public double ttsSpeed = 1.0;

    @AutoGen(category = "general", group = "tts")
    @DoubleSlider(min = 0.0, max = 2.0, step = 0.1)
    @SerialEntry(comment = "Volume multiplier for TTS audio. 1.0 = normal volume.")
    public double ttsVolume = 1.0;

    @AutoGen(category = "general", group = "tts")
    @TickBox
    @SerialEntry(comment = "If true, attempts to use CUDA for TTS inference when a GPU is available. Requires downloading the GPU runtime.")
    public boolean ttsUseGpu = true;

    @AutoGen(category = "general", group = "tts")
    @IntField(min = 1, max = 16)
    @SerialEntry(comment = "Number of CPU threads the TTS engine uses for synthesis. More threads = faster but heavier CPU usage. Default: 4. Requires game restart to apply.")
    public int ttsNumThreads = 4;

    // STT Configuration
    @AutoGen(category = "general", group = "stt")
    @TickBox
    @SerialEntry(comment = "If true, enables Whisper-based speech-to-text for player voice input. Requires downloading the STT model. Disabling falls back to no voice input (text-only conversations).")
    public boolean enableStt = true;

    @AutoGen(category = "general", group = "stt")
    @IntField(min = 1, max = 8)
    @SerialEntry(comment = "Number of CPU threads the Whisper STT engine uses for inference. Default: 4.")
    public int sttNumThreads = 4;

    // Proximity Mumbling
    @AutoGen(category = "citizens", group = "mumbling")
    @DoubleSlider(min = 0.0, max = 1.0, step = 0.01)
    @SerialEntry(comment = "Chance (0.0-1.0) that a nearby citizen starts mumbling to themselves per check interval")
    public double mumblingChance = 0.05;

    @AutoGen(category = "citizens", group = "mumbling")
    @DoubleField(min = 1.0, max = 100.0)
    @SerialEntry(comment = "Distance in blocks within which a citizen can be triggered to mumble when a player is nearby")
    public double mumblingDetectionRange = 10.0;

    @AutoGen(category = "citizens", group = "mumbling")
    @IntField(min = 1, max = 10000)
    @SerialEntry(comment = "How often (in server ticks) to check for citizens to trigger mumbling near players. 20 ticks = 1 second")
    public int mumblingCheckIntervalTicks = 200;

    // Citizen-Initiated Contact
    @AutoGen(category = "citizens", group = "citizen_contact")
    @TickBox
    @SerialEntry(comment = "If true, citizens with urgent needs will proactively speak to nearby players.")
    public boolean enableCitizenInitiatedContact = true;

    @AutoGen(category = "citizens", group = "citizen_contact")
    @DoubleSlider(min = 0.0, max = 1.0, step = 0.01)
    @SerialEntry(comment = "Base chance (0.0-1.0) per check interval that an urgent citizen speaks to a nearby player. Multiplied by an urgency weight derived from the citizen's state (unhappiness, injury, hunger, etc.).")
    public double citizenContactBaseChance = 0.02;

    @AutoGen(category = "citizens", group = "citizen_contact")
    @IntField(min = 1, max = 10000)
    @SerialEntry(comment = "How often (in server ticks) to check for citizens that should initiate contact. 20 ticks = 1 second")
    public int citizenContactCheckIntervalTicks = 400;

    @AutoGen(category="citizens", group="voice_chat")
    @TickBox
    @SerialEntry(comment= "If true, citizens will whisper when talking")
    public boolean citizenVoiceWhisper = true;

    @AutoGen(category="citizens", group="voice_chat")
    @IntField(min=0)
    @SerialEntry(comment = "Max voice distance of the citizen. Use 0 to use default distance")
    public int citizenVoiceDistance = 0;

    // Per-citizen automatic-session cooldown
    @AutoGen(category = "citizens")
    @IntField(min = 0, max = 10000)
    @SerialEntry(comment = "Minimum number of seconds that must pass after a citizen's automatic session (mumble or citizen-to-citizen) ends before they can be selected for another one. Set to 0 to disable.")
    public int citizenCooldownSeconds = 120;

    // Post-Raid Trauma
    @AutoGen(category = "citizens", group = "raid_trauma")
    @IntField(min = 0, max = 7200)
    @SerialEntry(comment = "How long (in seconds) citizens express post-raid trauma in their prompts after a raid ends. Set to 0 to disable.")
    public int raidTraumaDurationSeconds = 1200;

    // Personality Archetypes
    @AutoGen(category = "citizens", group = "personality")
    @TickBox
    @SerialEntry(comment = "If true, each citizen is randomly assigned a personality archetype that influences their speech style and tone.")
    public boolean enablePersonalityArchetypes = true;

    @AutoGen(category = "citizens")
    @ListGroup(valueFactory = ToolListFactory.class, controllerFactory = ToolListFactory.class)
    @SerialEntry(comment = "Custom personality archetype strings added to the random pool citizens can be assigned. Each entry is a freeform instruction injected into the citizen's system prompt. Example: 'Always speak in rhyming couplets.'")
    public List<String> customPersonalityArchetypes = new ArrayList<>();

    public static class ToolListFactory implements ListGroup.ValueFactory<String>, ListGroup.ControllerFactory<String> {
        @Override
        public String provideNewValue() {
            return "";
        }

        @Override
        public ControllerBuilder<String> createController(ListGroup annotation, ConfigField<List<String>> field, dev.isxander.yacl3.config.v2.api.autogen.OptionAccess storage, Option<String> option) {
            return StringControllerBuilder.create(option);
        }
    }

    public static void loadConfig() {
        Path oldConfig = YACLPlatform.getConfigDir().resolve("mc_talking-common.toml");
        Path newConfig = YACLPlatform.getConfigDir().resolve("yacl-mc_talking.json5");

        boolean shouldMigrate = Files.exists(oldConfig) && !Files.exists(newConfig);
        INSTANCE.load();

        if (shouldMigrate) {
            McTalking.LOGGER.info("Migrating old TOML config to YACL JSON5 config...");
            try {
                List<String> lines = Files.readAllLines(oldConfig);
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] parts = line.split("=", 2);
                    if (parts.length != 2) continue;
                    String key = parts[0].trim();
                    String val = parts[1].trim();

                    if (val.startsWith("\"") && val.endsWith("\"")) {
                        val = val.substring(1, val.length() - 1);
                    }

                    switch (key) {
                        case "gemini_key":
                            // Migrate old Gemini key to DeepSeek key (user can change later)
                            INSTANCE.instance().deepseekApiKey = val;
                            break;
                        case "language":
                            INSTANCE.instance().language = val;
                            break;
                        case "respond_in_group":
                            INSTANCE.instance().respondInGroups = java.lang.Boolean.parseBoolean(val);
                            break;
                        case "max_conversation_distance":
                            INSTANCE.instance().maxConversationDistance = Double.parseDouble(val);
                            break;
                        case "ai_modality":
                            try {
                                INSTANCE.instance().modality = ModalityModes.valueOf(val);
                            } catch (Exception ignored) {
                            }
                            break;
                        case "send_errors_to_players":
                            INSTANCE.instance().sendErrorsToPlayers = Boolean.parseBoolean(val);
                            break;
                        case "disabled_tools":
                            if (!val.equals("[]")) {
                                String toolsStr = val.replaceAll("[\\[\\]\"]", "");
                                String[] tools = toolsStr.split(",");
                                List<String> disabledTools = new ArrayList<>();
                                for (String t : tools) {
                                    if (!t.trim().isEmpty()) {
                                        disabledTools.add(t.trim());
                                    }
                                }
                                INSTANCE.instance().disabledTools = disabledTools;
                            }
                            break;
                        default:
                            McTalking.LOGGER.info("Unknown config key in old TOML config: {}", key);
                    }
                }

                INSTANCE.save();
                Files.deleteIfExists(oldConfig);
                McTalking.LOGGER.info("Successfully migrated old TOML config.");
            } catch (Exception e) {
                McTalking.LOGGER.error("Failed to migrate old TOML config", e);
            }
        }
    }
}
