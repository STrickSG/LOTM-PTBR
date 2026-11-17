package de.jakob.lotm.command;

import com.mojang.brigadier.CommandDispatcher;
import de.jakob.lotm.attachments.LuckComponent;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.attachments.SanityComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

public class LuckCheckCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("luck")
            .requires(source -> source.hasPermission(2)) // Requires OP level 2
                .then(Commands.argument("target", EntityArgument.entity())
                    .executes(context -> {
                        // Execute on a target entity
                        CommandSourceStack source = context.getSource();
                        var targetEntity = EntityArgument.getEntity(context, "target");

                        if (!(targetEntity instanceof LivingEntity livingEntity)) {
                            source.sendFailure(Component.translatableWithFallback("lotmcraft.text.target_must_be_a_living_entity", "Target must be a living entity!"));
                            return 0;
                        }

                        return executeCommand(source, livingEntity);
                    })
                )
        );
    }
    
    private static int executeCommand(CommandSourceStack source, LivingEntity target) {
        try {
            LuckComponent component = target.getData(ModAttachments.LUCK_COMPONENT);
            source.sendSuccess(() -> Component.translatableWithFallback("lotmcraft.text.luck_of_is", "Luck of %s is %s", String.valueOf(target.getName().getString()), String.valueOf(component.getLuck())), true);
            return 1;

        } catch (Exception e) {
            source.sendFailure(Component.translatableWithFallback("lotmcraft.text.failed_to_get_luck", "Failed to get luck: %s", String.valueOf(e.getMessage())));
            return 0;
        }
    }
}