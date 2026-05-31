package me.sshcrack.mc_talking.commands;

import com.mojang.brigadier.CommandDispatcher;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class ModChatCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cct_chat")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("on")
                        .executes(ctx -> {
                            McTalkingConfig.INSTANCE.instance().showCitizenChat = true;
                            McTalkingConfig.INSTANCE.save();
                            ctx.getSource().sendSuccess(() -> Component.literal("Citizen chat output enabled"), true);
                            return 1;
                        }))
                .then(Commands.literal("off")
                        .executes(ctx -> {
                            McTalkingConfig.INSTANCE.instance().showCitizenChat = false;
                            McTalkingConfig.INSTANCE.save();
                            ctx.getSource().sendSuccess(() -> Component.literal("Citizen chat output disabled — citizens will speak via voice only"), true);
                            return 1;
                        }))
                .executes(ctx -> {
                    boolean current = McTalkingConfig.INSTANCE.instance().showCitizenChat;
                    ctx.getSource().sendSuccess(() -> Component.literal("Citizen chat output is currently: " + (current ? "ON" : "OFF")), false);
                    return 1;
                }));
    }
}
