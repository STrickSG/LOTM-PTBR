package de.jakob.lotm.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import de.jakob.lotm.beyonders.abilities.twilight_giant.handlers.TwilightAging;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class AgeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("age")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("years", FloatArgumentType.floatArg(0))
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                    float years = FloatArgumentType.getFloat(context, "years");
                                    TwilightAging.setAge(target, years);
                                    context.getSource().sendSuccess(() -> Component.translatableWithFallback("lotmcraft.text.set_age_of_to", "Set age of %s to %s", String.valueOf(target.getName().getString()), String.valueOf(years)), true);
                                    return 1;
                                }))));
    }
}
