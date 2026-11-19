package de.jakob.lotm.addons.rituals;

import de.jakob.lotm.beyonders.blasphemy.BlasphemyCardItem;
import de.jakob.lotm.beyonders.potions.RitualRecipeItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/** Rituais de avanço que são feitos na hora de beber a poção. */
public final class RitualChecks {
    private RitualChecks() {}

    /**
     * Para fazer o ritual é preciso conhecê-lo: ter no inventário o pergaminho deste ritual,
     * a Fórmula Completa desta Sequência, ou a Carta da Blasfêmia do Caminho com esta Sequência escolhida.
     * (Ler o ritual num mundo e fazê-lo em outro, sem o item, não vale.)
     */
    public static boolean knowsRitual(ServerPlayer player, String pathway, int sequence) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof RitualRecipeItem scroll && scroll.getPathway().equals(pathway) && scroll.getSequence() == sequence) return true;
            if (stack.getItem() instanceof de.jakob.lotm.beyonders.potions.CompleteFormulaItem formula && formula.getPathway().equals(pathway) && formula.getSequence() == sequence) return true;
            if (stack.getItem() instanceof BlasphemyCardItem card && card.getPathway().equals(pathway) && card.getSelectedSequence(stack) == sequence) return true;
        }
        return false;
    }

    public static boolean completedWhileDrinking(ServerPlayer player, String pathway, int sequence) {
        // Sol, Sequência 5 (Sacerdote da Luz): beber a poção em escuridão total, enterrado em gelo que não derrete
        if ("sun".equals(pathway) && sequence == 5) return buriedInPackedIceInDarkness(player);
        // Sol, Sequência 4 (Sem Sombra): as emoções guardadas no frasco voltam durante o avanço
        if ("sun".equals(pathway) && sequence == 4) return returnOwnEmotions(player);
        // Sol, Sequência 3 (Mentor da Justiça): manter o próprio Juramento de Justiça por 3 dias
        if ("sun".equals(pathway) && sequence == 3) return JusticeOath.upheld(player);
        // Sol, Sequência 2 (Buscador da Luz): item de Anjo do Sol com a sua memória, posto à sua frente
        if ("sun".equals(pathway) && sequence == 2) return memoryPlacedBefore(player);
        // Sol, Sequência 1 (Anjo Branco): ser adorado por 100 fiéis, contando você mesmo
        if ("sun".equals(pathway) && sequence == 1) return Believers.count(player) >= Believers.NEEDED;
        // Porta: Viajante, Feiticeiro dos Segredos, Errante, Andarilho dos Planos e Chave das Estrelas
        if ("door".equals(pathway) && sequence == 5) return DoorRituals.coordinates(player) >= DoorRituals.COORDINATES_NEEDED;
        if ("door".equals(pathway) && sequence == 4) return DoorRituals.sealed(player);
        if ("door".equals(pathway) && sequence == 3) return DoorRituals.records(player) >= DoorRituals.RECORDS_NEEDED;
        if ("door".equals(pathway) && sequence == 2) return DoorRituals.legends(player) >= DoorRituals.LEGENDS_NEEDED;
        if ("door".equals(pathway) && sequence == 1) return DoorRituals.starConnected(player);
        // Tolo: Marionetista, Feiticeiro Bizarro, Erudito de Outrora, Invocador de Milagres e Atendente de Mistérios
        if ("fool".equals(pathway) && sequence == 5) return FoolRituals.heardSong(player);
        if ("fool".equals(pathway) && sequence == 4) return FoolRituals.performed(player);
        if ("fool".equals(pathway) && sequence == 3) return FoolRituals.becameHistory(player);
        if ("fool".equals(pathway) && sequence == 2) return FoolRituals.miracleDone(player);
        if ("fool".equals(pathway) && sequence == 1) return FoolRituals.townDone(player);
        if ("error".equals(pathway) && sequence == 5) return ErrorRituals.inDreams(player);
        if ("error".equals(pathway) && sequence == 4) return ErrorRituals.sustained(player);
        if ("error".equals(pathway) && sequence == 3) return ErrorRituals.orderCollapsed(player);
        if ("error".equals(pathway) && sequence == 2) return ErrorRituals.identityTrusted(player);
        if ("error".equals(pathway) && sequence == 1) return ErrorRituals.disorderDone(player);
        if ("red_priest".equals(pathway) && sequence == 5) return RedPriestRituals.captured(player);
        if ("red_priest".equals(pathway) && sequence == 4) return RedPriestRituals.teamFormed(player);
        if ("red_priest".equals(pathway) && sequence == 3) return RedPriestRituals.warWon(player);
        if ("red_priest".equals(pathway) && sequence == 2) return RedPriestRituals.weatherBent(player);
        if ("red_priest".equals(pathway) && sequence == 1) return RedPriestRituals.nationConquered(player);
        // Demônia: Aflição, Desespero, Perpetuidade, Catástrofe e Apocalipse
        if ("demoness".equals(pathway) && sequence == 5) return DemonessRituals.burnedEnough(player);
        if ("demoness".equals(pathway) && sequence == 4) return DemonessRituals.infected(player) >= DemonessRituals.PLAGUE_NEEDED;
        if ("demoness".equals(pathway) && sequence == 3) return DemonessRituals.mirrorDone(player);
        if ("demoness".equals(pathway) && sequence == 2) return DemonessRituals.disasterActive(player);
        if ("demoness".equals(pathway) && sequence == 1) return DemonessRituals.apocalypseNow(player);
        // Tirano: Cantor do Oceano, Enterrador do Cataclismo, Rei do Mar, Calamidade e Deus do Trovão
        if ("tyrant".equals(pathway) && sequence == 5) return TyrantRituals.inSeaBelly(player);
        if ("tyrant".equals(pathway) && sequence == 4) return TyrantRituals.amidstCataclysm(player);
        if ("tyrant".equals(pathway) && sequence == 3) return TyrantRituals.seaSubdued(player);
        if ("tyrant".equals(pathway) && sequence == 2) return TyrantRituals.calamityHalted(player);
        if ("tyrant".equals(pathway) && sequence == 1) return TyrantRituals.angelsDisrupted(player) >= TyrantRituals.ANGELS_NEEDED;
        return false;
    }

    // gelo que não derrete: compactado ou azul (na novel, Derrick usou gelo azul)
    private static boolean isEverlastingIce(BlockState state) {
        return state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE);
    }

    /**
     * Escuridão total e corpo preso no gelo. Vale a cápsula de gelo que não derrete ou estar congelado por inteiro
     * (neve fina, poderes de gelo...); e vale o escuro de verdade ou os próprios olhos tomados pela escuridão.
     */
    private static boolean buriedInPackedIceInDarkness(ServerPlayer player) {
        Level level = player.level();
        // blocos ocupados pelo corpo do jogador
        Set<BlockPos> body = new HashSet<>();
        BlockPos.betweenClosedStream(player.getBoundingBox().deflate(1.0E-3)).forEach(pos -> body.add(pos.immutable()));
        if (body.isEmpty()) return false;

        boolean blind = player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS) || player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS);
        boolean capsule = true, dark = true;
        for (BlockPos pos : body) {
            // escuridão total: nenhuma luz do céu nem de blocos
            if (level.getBrightness(LightLayer.SKY, pos) > 0 || level.getBrightness(LightLayer.BLOCK, pos) > 0) dark = false;
            // enterrado: tudo encostado no corpo é gelo que não derrete
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!body.contains(next) && !isEverlastingIce(level.getBlockState(next))) capsule = false;
            }
        }
        return (capsule || player.isFullyFrozen()) && (dark || blind);
    }

    /** Item de Anjo do Sol com a memória do jogador, num suporte de item ou no chão à frente dele (até 4 blocos) */
    private static boolean memoryPlacedBefore(ServerPlayer player) {
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(4))) {
            ItemStack stack = entity instanceof ItemFrame frame ? frame.getItem()
                    : entity instanceof ItemEntity item ? item.getItem() : ItemStack.EMPTY;
            if (!JusticeOath.isSunAngelItem(stack) || !player.getUUID().equals(JusticeOath.memoryOwner(stack))) continue;
            Vec3 toItem = entity.position().subtract(player.position()).multiply(1, 0, 1);
            if (toItem.lengthSqr() < 1.0E-4 || toItem.normalize().dot(look) > 0) return true;
        }
        return false;
    }

    /** Procura no inventário um Frasco de Emoções com as emoções do próprio jogador e o gasta */
    private static boolean returnOwnEmotions(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (player.getUUID().equals(EmotionFlask.ownerOf(stack))) {
                stack.shrink(1);
                player.displayClientMessage(Component.translatable("lotmcraft.ritual.sun.4.returned").withColor(0xFFE29A), true);
                return true;
            }
        }
        return false;
    }
}
