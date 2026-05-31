package me.sshcrack.mc_talking.commands;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import me.sshcrack.mc_talking.manager.tools.AITools;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.StringWriter;

public class ListToolsCommand {
    private static final SimpleCommandExceptionType NO_TOOLS = new SimpleCommandExceptionType(Component.translatable("mc_talking.commands.no_tools_available"));
    private static final SimpleCommandExceptionType TOOL_NOT_FOUND = new SimpleCommandExceptionType(Component.translatable("mc_talking.commands.tool_not_found"));

    private static int get_description(CommandContext<CommandSourceStack> context, String toolName) throws CommandSyntaxException {
        var src = context.getSource();

        var tool = AITools.registeredFunctions.get(toolName);
        if (tool == null) {
            throw TOOL_NOT_FOUND.create();
        }

        var strWriter = new StringWriter();
        JsonObject params = tool.getParameters();
        if (params != null && params.has("properties")) {
            var props = params.getAsJsonObject("properties");
            var gson = new GsonBuilder().setPrettyPrinting().create();
            for (var entry : props.entrySet()) {
                strWriter.write("\n");
                strWriter.write(entry.getKey() + ": ");
                gson.toJson(entry.getValue(), strWriter);
            }
        }

        src.sendSuccess(() -> Component.translatable("mc_talking.commands.tool_description", toolName, tool.getDescription(), strWriter.toString()), true);
        return 0;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var builder = Commands.literal("list_tools");

        for (String fn_name : AITools.getRegisteredFunctionNames()) {
            builder
                    .then(Commands.literal(fn_name)
                            .executes(context -> get_description(context, fn_name)));
        }

        dispatcher.register(builder
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    var src = ctx.getSource();
                    var tools = AITools.getRegisteredFunctionNames();

                    if (tools.isEmpty()) {
                        throw NO_TOOLS.create();
                    }

                    src.sendSuccess(() -> Component.translatable("mc_talking.commands.list_tools", String.join(", ", tools)), true);
                    return 1;
                }));
    }
}
