package de.jakob.lotm.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.attachments.UniquenessComponent;
import de.jakob.lotm.entity.custom.uniqueness.UniquenessEntity;
import de.jakob.lotm.network.PacketHandler;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class UniquenessCommand {

    private static final SuggestionProvider<CommandSourceStack> PATHWAY_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(BeyonderData.implementedPathways, builder);
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("uniqueness")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("check")
                        .then(Commands.argument("pathway", StringArgumentType.string())
                                .suggests(PATHWAY_SUGGESTIONS)
                                .executes(context -> {
                                    String pathway = StringArgumentType.getString(context, "pathway");
                                    return checkHolder(context.getSource(), pathway);
                                })
                        )
                )
                .then(Commands.literal("remove")
                        .then(Commands.argument("pathway", StringArgumentType.string())
                                .suggests(PATHWAY_SUGGESTIONS)
                                .executes(context -> {
                                    String pathway = StringArgumentType.getString(context, "pathway");
                                    return removeUniqueness(context.getSource(), pathway);
                                })
                        )
                )
                .then(Commands.literal("spawn")
                        .then(Commands.argument("pathway", StringArgumentType.string())
                                .suggests(PATHWAY_SUGGESTIONS)
                                .executes(context -> {
                                    String pathway = StringArgumentType.getString(context, "pathway");

                                    if (UniquenessEntity.existsInWorld(context.getSource().getLevel(), pathway)) return 0;

                                    if (UniquenessEntity.anyPlayerHoldsUniqueness(context.getSource().getLevel(), pathway)) return 0;

                                    if (BeyonderData.playerMap != null && BeyonderData.playerMap.count(pathway, 0) > 0) return 0;

                                    UniquenessEntity.trySpawn(context.getSource().getLevel(), context.getSource().getPosition().add(0, -2, 0), pathway);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("spawn_empty")
                            .executes(context -> {
                                String pathway = "";

                                UniquenessEntity uniqueness = new UniquenessEntity(context.getSource().getLevel(), context.getSource().getPosition(), pathway);
                                uniqueness.setPos(context.getSource().getPosition());
                                context.getSource().getLevel().addFreshEntity(uniqueness);
                                return 1;
                            })
                )
        );
    }

    private static int checkHolder(CommandSourceStack source, String pathway) {
        if (!BeyonderData.implementedPathways.contains(pathway)) {
            source.sendFailure(Component.translatableWithFallback("lotmcraft.text.unknown_or_unimplemented_pathway", "Unknown or unimplemented pathway: %s", String.valueOf(pathway)));
            return 0;
        }

        ServerLevel level = source.getServer().overworld();

        // Check if an entity exists in the world
        boolean entityExists = UniquenessEntity.existsInWorld(level, pathway);

        // Check which player holds it
        List<ServerPlayer> holders = source.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> {
                    UniquenessComponent comp = p.getData(ModAttachments.UNIQUENESS_COMPONENT);
                    return comp.hasUniqueness() && pathway.equalsIgnoreCase(comp.getUniquenessPathway());
                })
                .toList();

        if (holders.isEmpty()) {
            if (entityExists) {
                source.sendSuccess(() -> Component.translatableWithFallback("lotmcraft.text.the_uniqueness_is_currently_in_the", "The %s uniqueness is currently in the world (not held by any player).", String.valueOf(pathway)), false);
            } else {
                source.sendSuccess(() -> Component.translatableWithFallback("lotmcraft.text.the_uniqueness_is_not_currently_in", "The %s uniqueness is not currently in the world and not held by any player.", String.valueOf(pathway)), false);
            }
        } else {
            for (ServerPlayer holder : holders) {
                int killCount = holder.getData(ModAttachments.UNIQUENESS_COMPONENT).getKillCount();
                source.sendSuccess(() -> Component.translatableWithFallback("lotmcraft.text.holds_the_uniqueness_kill_count", "%s holds the %s uniqueness. Kill count: %s", String.valueOf(holder.getName().getString()), String.valueOf(pathway), String.valueOf(killCount)), false);
            }
        }

        return 1;
    }

    private static int removeUniqueness(CommandSourceStack source, String pathway) {
        if (!BeyonderData.implementedPathways.contains(pathway)) {
            source.sendFailure(Component.translatableWithFallback("lotmcraft.text.unknown_or_unimplemented_pathway", "Unknown or unimplemented pathway: %s", String.valueOf(pathway)));
            return 0;
        }

        List<ServerPlayer> holders = source.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> {
                    UniquenessComponent comp = p.getData(ModAttachments.UNIQUENESS_COMPONENT);
                    return comp.hasUniqueness() && pathway.equalsIgnoreCase(comp.getUniquenessPathway());
                })
                .toList();

        if (holders.isEmpty()) {
            source.sendFailure(Component.translatableWithFallback("lotmcraft.text.no_online_player_currently_holds_the", "No online player currently holds the %s uniqueness.", String.valueOf(pathway)));
            return 0;
        }

        for (ServerPlayer holder : holders) {
            UniquenessComponent comp = holder.getData(ModAttachments.UNIQUENESS_COMPONENT);
            comp.setHasUniqueness(false);
            comp.setUniquenessPathway("");
            BeyonderData.playerMap.setUniqueness(holder, "none");
            PacketHandler.syncUniquenessToPlayer(holder);
            source.sendSuccess(() -> Component.translatableWithFallback("lotmcraft.text.removed_uniqueness_from", "Removed uniqueness from %s", String.valueOf(holder.getName().getString())), false);
        }

        return 1;
    }
}
