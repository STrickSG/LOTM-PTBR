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
                    new ItemStack(PotionItemHandler.CONQUEROR_POTION.get()))),
            // Demônia, Sequência 5 (Demônia da Aflição): queimar sem parar e sobreviver sem enlouquecer
            new Ritual("demoness", 5, () -> List.of(
                    new ItemStack(Items.CAMPFIRE),
                    new ItemStack(PotionItemHandler.DEMONESS_OF_AFFLICTION_POTION.get()))),
            // Demônia, Sequência 4 (Demônia do Desespero): envolver muita gente numa praga
            new Ritual("demoness", 4, () -> List.of(
                    new ItemStack(Items.FERMENTED_SPIDER_EYE),
                    new ItemStack(PotionItemHandler.DEMONESS_OF_DESPAIR_POTION.get()))),
            // Demônia, Sequência 3 (Demônia da Perpetuidade): escravizar, encantar ou reconciliar-se com o eu do espelho
            new Ritual("demoness", 3, () -> List.of(
                    new ItemStack(Items.GLASS_PANE),
                    new ItemStack(PotionItemHandler.DEMONESS_OF_UNAGING_POTION.get()))),
            // Demônia, Sequência 2 (Demônia da Catástrofe): participar de um desastre e beber durante ele
            new Ritual("demoness", 2, () -> List.of(
                    new ItemStack(Items.TNT),
                    new ItemStack(PotionItemHandler.DEMONESS_OF_CATASTROPHE_POTION.get()))),
            // Demônia, Sequência 1 (Demônia do Apocalipse): fim de uma era, apocalipse em curso ou queda de uma divindade
            new Ritual("demoness", 1, () -> List.of(
                    new ItemStack(Items.DRAGON_HEAD),
                    new ItemStack(PotionItemHandler.DEMONESS_OF_APOCALYPSE_POTION.get()))),
            // Tirano, Sequência 5 (Cantor do Oceano): beber na barriga de um gigante do mar
            new Ritual("tyrant", 5, () -> List.of(
                    new ItemStack(Items.NAUTILUS_SHELL),
                    new ItemStack(PotionItemHandler.OCEAN_SONGSTER_POTION.get()))),
            // Tirano, Sequência 4 (Enterrador do Cataclismo): causar terremoto e tsunami e beber no meio deles
            new Ritual("tyrant", 4, () -> List.of(
                    new ItemStack(Items.WATER_BUCKET),
                    new ItemStack(PotionItemHandler.CATACLYSMIC_INTERRER_POTION.get()))),
            // Tirano, Sequência 3 (Rei do Mar): uma torre de pedra do fundo do mar e todo o mar em volta subjugado
            new Ritual("tyrant", 3, () -> List.of(
                    new ItemStack(Items.STONE_BRICKS, 16),
                    new ItemStack(PotionItemHandler.SEA_KING_POTION.get()))),
            // Tirano, Sequência 2 (Calamidade): causar sozinho um desastre e pará-lo diante dos que sobraram
            new Ritual("tyrant", 2, () -> List.of(
                    new ItemStack(Items.SKELETON_SKULL),
                    new ItemStack(PotionItemHandler.CALAMITY_POTION.get()))),
            // Tirano, Sequência 1 (Deus do Trovão): com raios, atrapalhar a ascensão de três Anjos
            new Ritual("tyrant", 1, () -> List.of(
                    new ItemStack(Items.LIGHTNING_ROD),
                    new ItemStack(PotionItemHandler.THUNDER_GOD_POTION.get()))),
            // Escuridão, Sequência 5 (Bruxo Espiritual): no Mundo Espiritual, fundir-se com o seu verdadeiro eu
            new Ritual("darkness", 5, () -> List.of(
                    new ItemStack(Items.ENDER_EYE),
                    new ItemStack(PotionItemHandler.SPIRIT_WARLOCK_POTION.get()))),
            // Escuridão, Sequência 4 (Vigia da Noite): um círculo que acalma as almas, com sangue de linhagem divina
            new Ritual("darkness", 4, () -> List.of(
                    new ItemStack(Items.CANDLE, 8),
                    new ItemStack(PotionItemHandler.NIGHTWATCHER_POTION.get()))),
            // Escuridão, Sequência 3 (Bispo do Horror): encarar a morte de verdade
            new Ritual("darkness", 3, () -> List.of(
                    new ItemStack(Items.WITHER_ROSE),
                    new ItemStack(PotionItemHandler.HORROR_BISHOP_POTION.get()))),
            // Escuridão, Sequência 2 (Servo da Ocultação): isolado, no escuro e em silêncio
            new Ritual("darkness", 2, () -> List.of(
                    new ItemStack(Items.BLACK_WOOL),
                    new ItemStack(PotionItemHandler.SERVANT_OF_CONCEALMENT_POTION.get()))),
            // Escuridão, Sequência 1 (Cavaleiro do Infortúnio): ser o infortúnio de um Arcanjo ou de uma civilização
            new Ritual("darkness", 1, () -> List.of(
                    new ItemStack(Items.BLACK_BANNER),
                    new ItemStack(PotionItemHandler.KNIGHT_OF_MISFORTUNE_POTION.get()))),
            // Visionário, Sequência 5 (Caminhante dos Sonhos): contrato com uma criatura do Mundo Espiritual, a pena e uma emoção forte
            new Ritual("visionary", 5, () -> List.of(
                    new ItemStack(Items.FEATHER),
                    new ItemStack(PotionItemHandler.DREAMWALKER_POTION.get()))),
            // Visionário, Sequência 4 (Manipulador): ressoar com as emoções de uma multidão
            new Ritual("visionary", 4, () -> List.of(
                    new ItemStack(Items.BELL),
                    new ItemStack(PotionItemHandler.MANIPULATOR_POTION.get()))),
            // Visionário, Sequência 3 (Tecelão de Sonhos): muita gente com o mesmo sonho numa noite
            new Ritual("visionary", 3, () -> List.of(
                    new ItemStack(Items.PHANTOM_MEMBRANE),
                    new ItemStack(PotionItemHandler.DREAM_WEAVER_POTION.get()))),
            // Visionário, Sequência 2 (Discernidor): deixar a sua marca no subconsciente de muita gente
            new Ritual("visionary", 2, () -> List.of(
                    new ItemStack(Items.SPYGLASS),
                    new ItemStack(PotionItemHandler.DISCERNER_POTION.get()))),
            // Visionário, Sequência 1 (Autor): um romance com pessoas reais, e o destino delas seguindo a história
            new Ritual("visionary", 1, () -> List.of(
                    new ItemStack(Items.WRITABLE_BOOK),
                    new ItemStack(PotionItemHandler.AUTHOR_POTION.get()))),
            // Roda da Fortuna, Sequência 5 (Vencedor): um mês em azar, sem nenhuma sorte
            new Ritual("wheel_of_fortune", 5, () -> List.of(
                    new ItemStack(Items.COBWEB),
                    new ItemStack(PotionItemHandler.WINNER_POTION.get()))),
            // Roda da Fortuna, Sequência 4 (Mago do Infortúnio): no fundo do azar, sobreviver a uma calamidade sem preparo
            new Ritual("wheel_of_fortune", 4, () -> List.of(
                    new ItemStack(Items.ANVIL),
                    new ItemStack(PotionItemHandler.MISFORTUNE_MAGE_POTION.get()))),
            // Roda da Fortuna, Sequência 3 (Caminhante do Caos): fixar um farol que o destino vai alcançar
            new Ritual("wheel_of_fortune", 3, () -> List.of(
                    new ItemStack(Items.LODESTONE),
                    new ItemStack(PotionItemHandler.CHAOSWALKER_POTION.get()))),
            // Roda da Fortuna, Sequência 2 (Adivinho): uma revelação sobre uma nação que acontece como foi vista
            new Ritual("wheel_of_fortune", 2, () -> List.of(
                    new ItemStack(Items.AMETHYST_SHARD),
                    new ItemStack(PotionItemHandler.SOOTHSAYER_POTION.get()))),
            // Roda da Fortuna, Sequência 1 (Serpente de Mercúrio): tirar todas as memórias, beber e devolvê-las
            new Ritual("wheel_of_fortune", 1, () -> List.of(
                    new ItemStack(Items.BOOK),
                    new ItemStack(PotionItemHandler.SNAKE_OF_MERCURY_POTION.get())))
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
