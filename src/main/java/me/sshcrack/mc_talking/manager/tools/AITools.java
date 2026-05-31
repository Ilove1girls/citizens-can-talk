package me.sshcrack.mc_talking.manager.tools;

import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.deepseek.DeepSeekChatClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class AITools {
    private AITools() {
        /* This utility class should not be instantiated */
    }

    public static final Map<String, FunctionAction> registeredFunctions = new HashMap<>();
    public static final Map<String, FunctionAction> playerConversationOnlyTools = new HashMap<>();

    public static void add(Map<String, FunctionAction> map, FunctionAction action) {
        map.put(action.getName(), action);
    }

    public static void addAll(Map<String, FunctionAction> map, List<FunctionAction> actions) {
        for (var action : actions) {
            add(map, action);
        }
    }

    public static List<String> getRegisteredFunctionNames() {
        return new ArrayList<>(registeredFunctions.keySet());
    }

    public static List<DeepSeekChatClient.Tool> getEnabledTools(boolean isPlayerConversation) {
        var list = new ArrayList<DeepSeekChatClient.Tool>();
        var rawToolsDisabled = McTalkingConfig.INSTANCE.instance().disabledTools;

        var functions = registeredFunctions.values().stream();
        if (isPlayerConversation) {
            functions = Stream.concat(functions, playerConversationOnlyTools.values().stream());
        }

        functions
                .filter(e -> !rawToolsDisabled.contains(e.getName()))
                .forEach(e -> list.add(new DeepSeekChatClient.Tool(
                        e.getName(),
                        e.getDescription(),
                        e.getParameters()
                )));

        return list;
    }

    public static void register() {
        addAll(registeredFunctions, List.of(
                new GetCitizenInfoAction(),
                new ListCitizenAction(),
                new GetInventoryAction(),
                new GetColonyAction(),
                new DescribeSurroundingsAction(),
                new EndConversationAction(),
                new RecordRelationshipChange(),
                new AddEventToMemory()
//                new JobSpecificAction()
        ));
        addAll(playerConversationOnlyTools, List.of(
                new DropItemAction(),
                new LeaveColonyAction()
        ));
    }
}
