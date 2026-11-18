package de.jakob.lotm.addons.rituals;

import de.jakob.lotm.beyonders.potions.PotionItemHandler;
import de.jakob.lotm.beyonders.potions.BeyonderCharacteristicItemHandler;
import de.jakob.lotm.item.ModItems;
import de.jakob.lotm.util.BeyonderData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.function.Supplier;

/**
 * Rituais de avanço que já estão ligados e o que cada um precisa.
 * O pergaminho de ritual e a Carta da Blasfêmia mostram isso; a verificação fica em RitualChecks.
 */
public final class AdvancementRituals {
    private AdvancementRituals() {}

    /** Ritual para avançar até a Sequência {@code sequence} do Caminho {@code pathway}. */
    public record Ritual(String pathway, int sequence, Supplier<List<ItemStack>> materials) {
        /** Nome do pergaminho deste ritual, ex.: priest_of_light_ritual */
        public String id() {
            return BeyonderData.pathwayInfos.get(pathway).sequenceNames()[sequence] + "_ritual";
        }

        /** Começo das chaves de tradução com a explicação do ritual (…1, …2, …) */
        public String textKey() {
            return "lotmcraft.ritual." + pathway + "." + sequence + ".";
        }
    }

    private static final List<Ritual> RITUALS = List.of(
            // Sol, Sequência 5 (Sacerdote da Luz): em escuridão total, com o corpo enterrado em gelo que não derrete
            new Ritual("sun", 5, () -> List.of(
                    new ItemStack(Blocks.PACKED_ICE, 10),
                    new ItemStack(PotionItemHandler.PRIEST_OF_LIGHT_POTION.get()))),
            // Sol, Sequência 4 (Sem Sombra): extrair as emoções mais fortes antes e devolvê-las durante o avanço
            new Ritual("sun", 4, () -> List.of(
                    new ItemStack(Items.GLASS_BOTTLE),
                    EmotionFlask.example(),
                    new ItemStack(PotionItemHandler.UNSHADOWED.get()))),
            // Sol, Sequência 3 (Mentor da Justiça): definir a própria justiça e mantê-la sem nenhuma violação
            new Ritual("sun", 3, () -> List.of(
                    new ItemStack(Items.WRITABLE_BOOK),
                    new ItemStack(PotionItemHandler.JUSTICE_MENTOR_POTION.get()))),
            // Sol, Sequência 2 (Buscador da Luz): ligar a memória da sua justiça a um item de Anjo do Sol e pô-lo à sua frente
            new Ritual("sun", 2, () -> List.of(
                    new ItemStack(Items.WRITTEN_BOOK),
                    new ItemStack(BeyonderCharacteristicItemHandler.selectCharacteristicOfPathwayAndSequence("sun", 2)),
                    new ItemStack(PotionItemHandler.LIGHTSEEKER_POTION.get()))),
            // Sol, Sequência 1 (Anjo Branco): ser adorado por 100 fiéis, incluindo você mesmo
            new Ritual("sun", 1, () -> List.of(
                    new ItemStack(ModItems.SACRED_REGISTER.get()),
                    new ItemStack(Items.VILLAGER_SPAWN_EGG, 99),
                    new ItemStack(PotionItemHandler.WHITE_ANGEL_POTION.get()))),
            // Porta, Sequência 5 (Viajante): coordenadas em 4 pontos do Mundo Espiritual, muito longe uns dos outros
            new Ritual("door", 5, () -> List.of(
                    new ItemStack(Items.LODESTONE, 4),
                    new ItemStack(PotionItemHandler.TRAVELER_POTION.get()))),
            // Porta, Sequência 4 (Feiticeiro dos Segredos): selar uma criatura hostil de nível semideus
            new Ritual("door", 4, () -> List.of(
                    new ItemStack(Items.CHAIN),
                    new ItemStack(PotionItemHandler.SECRETS_SORCERER_POTION.get()))),
            // Porta, Sequência 3 (Errante): registrar 3 projeções do Mundo Astral no Mundo Espiritual, sem esquecer
            new Ritual("door", 3, () -> List.of(
                    new ItemStack(Items.WRITABLE_BOOK),
                    new ItemStack(PotionItemHandler.WANDERER_POTION.get()))),
            // Porta, Sequência 2 (Andarilho dos Planos): deixar lendas sobre você em 9 lugares fora do planeta
            new Ritual("door", 2, () -> List.of(
                    new ItemStack(Items.LECTERN, 9),
                    new ItemStack(Items.WRITTEN_BOOK, 9),
                    new ItemStack(PotionItemHandler.PLANESWALKER_POTION.get()))),
            // Porta, Sequência 1 (Chave das Estrelas): pousar num planeta pesado que gira e emite sinais e ligar-se a ele
            new Ritual("door", 1, () -> List.of(
                    new ItemStack(Items.LODESTONE),
                    new ItemStack(PotionItemHandler.KEY_OF_STARS_POTION.get()))),
            // Tolo, Sequência 5 (Marionetista): beber a poção em meio ao canto de sereias
            new Ritual("fool", 5, () -> List.of(
                    new ItemStack(Items.NAUTILUS_SHELL),
                    new ItemStack(PotionItemHandler.MARIONETTIST_POTION.get()))),
            // Tolo, Sequência 4 (Feiticeiro Bizarro): matar um semideus num grande espetáculo e beber no fim
            new Ritual("fool", 4, () -> List.of(
                    new ItemStack(Items.FIREWORK_ROCKET),
                    new ItemStack(Items.VILLAGER_SPAWN_EGG, 30),
                    new ItemStack(PotionItemHandler.BIZARRO_SORCERER_POTION.get()))),
            // Tolo, Sequência 3 (Erudito de Outrora): separado da realidade, virar história
            new Ritual("fool", 3, () -> List.of(
                    new ItemStack(Items.CLOCK),
                    new ItemStack(PotionItemHandler.SCHOLAR_OF_YORE_POTION.get()))),
            // Tolo, Sequência 2 (Invocador de Milagres): devolver ao presente um pedaço da história que ficou para trás
            new Ritual("fool", 2, () -> List.of(
                    new ItemStack(Items.RECOVERY_COMPASS),
                    new ItemStack(PotionItemHandler.MIRACLE_INVOKER_POTION.get()))),
            // Tolo, Sequência 1 (Atendente de Mistérios): uma cidade só de marionetes, cada uma com seu destino
            new Ritual("fool", 1, () -> List.of(
                    new ItemStack(Items.NAME_TAG, 50),
                    new ItemStack(PotionItemHandler.ATTENDANT_OF_MYSTERIES_POTION.get()))),
            // Erro, Sequência 5 (Ladrão de Sonhos): ser figura-chave nos sonhos de 30 pessoas que dormem na hora de beber
            new Ritual("error", 5, () -> List.of(
                    new ItemStack(Items.RED_BED),
                    new ItemStack(PotionItemHandler.DREAM_STEALER_POTION.get()))),
            // Erro, Sequência 4 (Parasita): 9 alvos oferecem de boa vontade tudo o que têm para sustentá-lo
            new Ritual("error", 4, () -> List.of(
                    new ItemStack(Items.CHEST, 9),
                    new ItemStack(PotionItemHandler.PARASITE_POTION.get()))),
            // Erro, Sequência 3 (Mentor do Engano): sem violência, usar brechas nas regras para derrubar a ordem de uma vila
            new Ritual("error", 3, () -> List.of(
                    new ItemStack(Items.WRITABLE_BOOK),
                    new ItemStack(PotionItemHandler.MENTOR_OF_DECEIT_POTION.get()))),
            // Erro, Sequência 2 (Cavalo de Troia do Destino): tomar a identidade de alguém sem poderes e ganhar a confiança dos dele
            new Ritual("error", 2, () -> List.of(
                    new ItemStack(Items.NAME_TAG),
                    new ItemStack(PotionItemHandler.TROJAN_HORSE_OF_DESTINY_POTION.get()))),
            // Erro, Sequência 1 (Verme do Tempo): mergulhar uma cidade em desordem temporal por pelo menos 7 dias
            new Ritual("error", 1, () -> List.of(
                    new ItemStack(Items.CLOCK, 12),
                    new ItemStack(Items.ITEM_FRAME, 12),
                    new ItemStack(PotionItemHandler.WORM_OF_TIME_POTION.get()))),
            // Sacerdote Vermelho, Sequência 5 (Ceifador): capturar um alvo mais forte e beber diante dele
            new Ritual("red_priest", 5, () -> List.of(
                    new ItemStack(Items.CHAIN),
                    new ItemStack(PotionItemHandler.REAPER_POTION.get()))),
            // Sacerdote Vermelho, Sequência 4 (Cavaleiro de Sangue de Ferro): uma equipe de trinta, forte e unida
            new Ritual("red_priest", 4, () -> List.of(
                    new ItemStack(Items.IRON_CHESTPLATE, 30),
                    new ItemStack(PotionItemHandler.IRON_BLOODED_KNIGHT_POTION.get()))),
            // Sacerdote Vermelho, Sequência 3 (Bispo da Guerra): liderar a equipe e vencer uma força inimiga poderosa
            new Ritual("red_priest", 3, () -> List.of(
                    new ItemStack(Items.CROSSBOW),
                    new ItemStack(PotionItemHandler.WAR_BISHOP_POTION.get()))),
            // Sacerdote Vermelho, Sequência 2 (Bruxo do Clima): mudar à força o clima de uma região, sem ajuda, num instante
            new Ritual("red_priest", 2, () -> List.of(
                    new ItemStack(Items.LIGHTNING_ROD),
                    new ItemStack(PotionItemHandler.WEATHER_WARLOCK_POTION.get()))),
            // Sacerdote Vermelho, Sequência 1 (Conquistador): conquistar uma nação que tema, obedeça e reverencie
            new Ritual("red_priest", 1, () -> List.of(
                    new ItemStack(Items.RED_BANNER),
                    new ItemStack(PotionItemHandler.CONQUEROR_POTION.get())))
    );

    public static List<Ritual> all() {
        return RITUALS;
    }

    public static Ritual get(String pathway, int sequence) {
        for (Ritual ritual : RITUALS) {
            if (ritual.pathway().equals(pathway) && ritual.sequence() == sequence) return ritual;
        }
        return null;
    }
}
