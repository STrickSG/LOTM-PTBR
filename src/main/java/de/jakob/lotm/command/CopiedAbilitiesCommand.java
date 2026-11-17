package de.jakob.lotm.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.attachments.CopiedAbilityComponent;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.util.helper.CopiedAbilityHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public class CopiedAbilitiesCommand {

    private static final SuggestionProvider<CommandSourceStack> ABILITY_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    LOTMCraft.abilityHandler.getRegisteredAbilityIds(), builder
            );

    private static final SuggestionProvider<CommandSourceStack> OWN_SUGGESTIONS =
            (context, builder) -> context.getSource().getEntity() instanceof LivingEntity entity
                    ? SharedSuggestionProvider.suggest(entity.getData(ModAttachments.COPIED_ABILITY_COMPONENT).getAbilityIds(), builder)
                    : builder.buildFuture();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("copied_abilities")
                .then(Commands.literal("add")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("ability_id", StringArgumentType.string())
                                .suggests(ABILITY_SUGGESTIONS)
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (!(source.getEntity() instanceof LivingEntity livingEntity)) {
                                        source.sendFailure(Component.translatableWithFallback("lotmcraft.text.only_living_entities_can_use_this", "Only living entities can use this command!"));
                                        return 0;
                                    }
                                    String abilityId = StringArgumentType.getString(context, "ability_id");
                                    return executeAddAbility(source, livingEntity, abilityId);
                                })
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            var targetEntity = EntityArgument.getEntity(context, "target");

                                            if (!(targetEntity instanceof LivingEntity livingEntity)) {
                                                source.sendFailure(Component.translatableWithFallback("lotmcraft.text.target_must_be_a_living_entity", "Target must be a living entity!"));
                                                return 0;
                                            }

                                            String abilityId = StringArgumentType.getString(context, "ability_id");
                                            return executeAddAbility(source, livingEntity, abilityId);
                                        })
                                )
                        )
                )
                .then(Commands.literal("remove")
                        .then(Commands.argument("ability_id", StringArgumentType.string())
                                .suggests(OWN_SUGGESTIONS)
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (!(source.getEntity() instanceof LivingEntity livingEntity)) {
                                        source.sendFailure(Component.translatableWithFallback("lotmcraft.text.only_living_entities_can_use_this", "Only living entities can use this command!"));
                                        return 0;
                                    }
                                    String abilityId = StringArgumentType.getString(context, "ability_id");
                                    return executeRemoveAbility(source, livingEntity, abilityId);
                                })
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .requires(source -> source.hasPermission(2))
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            var targetEntity = EntityArgument.getEntity(context, "target");

                                            if (!(targetEntity instanceof LivingEntity livingEntity)) {
                                                source.sendFailure(Component.translatableWithFallback("lotmcraft.text.target_must_be_a_living_entity", "Target must be a living entity!"));
                                                return 0;
                                            }

                                            String abilityId = StringArgumentType.getString(context, "ability_id");
                                            return executeRemoveAbility(source, livingEntity, abilityId);
                                        })
                                )
                        )
                )
        );
    }

    private static int executeAddAbility(CommandSourceStack source, LivingEntity target, String abilityId) {
        try {
            if (LOTMCraft.abilityHandler.getById(abilityId) == null) {
                source.sendFailure(Component.translatableWithFallback("lotmcraft.text.unknown_ability_id", "Unknown ability ID: %s", String.valueOf(abilityId)));
                return 0;
            }

            CopiedAbilityComponent.CopiedAbilityData data = new CopiedAbilityComponent.CopiedAbilityData(
                    abilityId,
                    "replicated",
                    -1,
                    null
            );

            CopiedAbilityHelper.addAbility(target, data);

            String targetName = target instanceof ServerPlayer player ? player.getGameProfile().getName() : target.getDisplayName().getString();
            source.sendSuccess(() -> Component.translatableWithFallback("lotmcraft.text.added_copied_ability_to", "Added copied ability '%s' to %s", String.valueOf(abilityId), String.valueOf(targetName)), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatableWithFallback("lotmcraft.text.failed_to_add_ability", "Failed to add ability: %s", String.valueOf(e.getMessage())));
            return 0;
        }
    }

    private static int executeRemoveAbility(CommandSourceStack source, LivingEntity target, String abilityId) {
        try {
            CopiedAbilityHelper.removeAbilityID(target, abilityId);
            String targetName = target instanceof ServerPlayer player ? player.getGameProfile().getName() : target.getDisplayName().getString();

            source.sendSuccess(() -> Component.translatableWithFallback("lotmcraft.text.removed_copied_ability_from", "Removed copied ability '%s' from %s", String.valueOf(abilityId), String.valueOf(targetName)), true);
            return 1;

        } catch (Exception e) {
            source.sendFailure(Component.translatableWithFallback("lotmcraft.text.failed_to_remove_ability", "Failed to remove ability: %s", String.valueOf(e.getMessage())));
            return 0;
        }
    }
}