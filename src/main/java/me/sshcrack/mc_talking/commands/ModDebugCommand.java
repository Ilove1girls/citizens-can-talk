package me.sshcrack.mc_talking.commands;

import com.mojang.brigadier.CommandDispatcher;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class ModDebugCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cct_debug")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("on")
                        .executes(ctx -> {
                            McTalkingConfig.INSTANCE.instance().debugMode = true;
                            McTalkingConfig.INSTANCE.save();
                            ctx.getSource().sendSuccess(() -> Component.literal("McTalking debug mode enabled"), true);
                            return 1;
                        }))
                .then(Commands.literal("off")
                        .executes(ctx -> {
                            McTalkingConfig.INSTANCE.instance().debugMode = false;
                            McTalkingConfig.INSTANCE.save();
                            ctx.getSource().sendSuccess(() -> Component.literal("McTalking debug mode disabled"), true);
                            return 1;
                        }))
                .executes(ctx -> {
                    boolean current = McTalkingConfig.INSTANCE.instance().debugMode;
                    ctx.getSource().sendSuccess(() -> Component.literal("McTalking debug mode is currently: " + (current ? "ON" : "OFF")), false);
                    return 1;
                }));
    }
}
