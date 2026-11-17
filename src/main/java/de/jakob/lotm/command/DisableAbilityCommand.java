package de.jakob.lotm.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.beyonders.abilities.core.Ability;
import de.jakob.lotm.beyonders.abilities.core.AbilityHandler;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.playerMap.StoredData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class DisableAbilityCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("disableability")
            .requires(source -> source.hasPermission(2)) // Requires OP level 2
            .then(Commands.argument("ability", StringArgumentType.string())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(LOTMCraft.abilityHandler.getAbilities().stream().map(Ability::getId), builder))
                    .executes(context -> {
                        CommandSourceStack source = context.getSource();
                        String abilityId = StringArgumentType.getString(context, "ability");

                        AbilityHandler abilityHandler = LOTMCraft.abilityHandler;
                        Ability ability = abilityHandler.getById(abilityId);

                        if (ability == null) {
                            source.sendFailure(Component.translatableWithFallback("lotmcraft.text.ability_not_found", "Ability not found: %s", String.valueOf(abilityId)));
                            return 0;
                        }

                        if(abilityHandler.isDisabled(ability)) {
                            source.sendFailure(Component.translatableWithFallback("lotmcraft.text.ability_is_already_disabled", "Ability is already disabled: %s", String.valueOf(abilityId)));
                            return 0;
                        }

                        abilityHandler.disableAbility(ability);

                        source.sendSuccess(() -> Component.translatableWithFallback("lotmcraft.text.disabled_ability", "Disabled ability: %s", String.valueOf(abilityId)), true);
                        return 1; // Success
                    })
            )
        );
    }
    
    private static int executeBeyonderCommand(CommandSourceStack source, LivingEntity target, String pathway, int sequence) {
        try {
            // Validate pathway exists in the list
            if (!BeyonderData.pathways.contains(pathway)) {
                source.sendFailure(Component.translatableWithFallback("lotmcraft.text.invalid_pathway", "Invalid pathway: %s", String.valueOf(pathway)));
                return 0;
            }
            
            // Call the setBeyonder method
            BeyonderData.setBeyonder(target, pathway, sequence);

            if(target instanceof Player player) {
                var optional = BeyonderData.playerMap.get(player);

                if(optional.isPresent()) {
                    StoredData data = optional.get();

                    if (data.sequence() != sequence || (!data.pathway().equals(pathway)
                            && !data.pathway().equals("none"))) {
                        source.sendFailure(Component.translatableWithFallback("lotmcraft.text.failed_to_advance_due_to_insufficient", "Failed to advance due to insufficient amount of characteristics"));
                        return 0;
                    }
                }
            }

            // Send success message
            String targetName = target instanceof Player player ? player.getGameProfile().getName() : target.getDisplayName().getString();
            source.sendSuccess(() -> Component.translatableWithFallback("lotmcraft.text.set_to_sequence", "Set %s to %s sequence %s", String.valueOf(targetName), String.valueOf(pathway), String.valueOf(sequence)), true);
            
            return 1; // Success
        } catch (Exception e) {
            source.sendFailure(Component.translatableWithFallback("lotmcraft.text.failed_to_set_beyonder_data", "Failed to set beyonder data: %s", String.valueOf(e.getMessage())));
            return 0;
        }
    }
}