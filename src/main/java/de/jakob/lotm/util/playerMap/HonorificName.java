package de.jakob.lotm.util.playerMap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedList;
import java.util.List;
import de.jakob.lotm.util.LotmText;

public record HonorificName(LinkedList<String> lines) {
    static public final String NBT_LINES = "honorific_name_lines";

    static public final HonorificName EMPTY = new HonorificName(new LinkedList<>());

    static public LinkedList<String> getMustHaveWords(String path){
        return new LinkedList<String>(
                switch (path){
                    case "fool" -> List.of("History", "Mystery", "Bizarreness", "Change", "Wishes", "Miracles", "Drama", "Performances", "Fools");
                    case "error" -> List.of("Time", "Deceit", "Trickery", "Loopholes", "Tampering", "Exploitation", "Bugs", "Theft", "Errors");
                    case "door" -> List.of("Space", "Stars", "Cosmos", "Worlds", "Concealment", "Seals", "Travels", "Chronicles", "Doors");
                    case "visionary" -> List.of("Mind", "Dreams", "Visions", "Fantasies", "Imagination", "Discernment", "Psyche", "Coincidence", "Arrangements");
                    case "sun" -> List.of( "Light", "Order", "Holiness", "Contracts", "Justice", "Energy", "Sun", "Daytime", "Inextinguishable");
                    case "tyrant" -> List.of("Storms", "Calamities", "Seas", "Skies", "Tsunamis", "Undersea", "Creatures", "Tyranny", "Wrath");
                    case "white_tower" -> List.of("Knowledge", "Observation", "Reasoning", "Cognition", "Wisdom", "Reason", "Omniscience", "Learning", "Madness");
                    case "hanged_man" -> List.of("Degeneration", "Corruption", "Mutation", "Shadows", "Darkness", "Whispers", "Sacrifice", "Depravity", "Words");
                    case "darkness" -> List.of("Darkness", "Dreams", "Silence", "Horror", "Repose", "Misfortune", "Concealment", "Stars", "Night");
                    case "death" -> List.of("Death", "Undeath", "Spirits", "Underworld", "Pallor", "Styx", "Dead", "Eternal", "Rest");
                    case "twilight_giant" -> List.of("Twilight", "Decay", "Glory", "Passage","Time", "Dawn", "Dusk", "Power", "Combat");
                    case "demoness" -> List.of("Chaos", "Catastrophes","Mirror", "Disasters", "Apocalypse", "Strife", "Plagues", "Primordium", "Femininity");
                    case "red_priest" -> List.of("War", "Calamity", "Iron","Blood", "Battlefield", "Strife", "Chaos", "Destruction", "Masculinity");
                    case "hermit" -> List.of("Knowledge", "Information", "Symbols", "Magic", "Data", "Numbers", "Education", "Stories", "Mysticism");
                    case "paragon" -> List.of("Technology", "Logic", "Machinery", "Physics", "Civilizations", "Structure", "Essence", "Education", "Enlightenment");
                    case "wheel_of_fortune" -> List.of("Fate", "Luck", "Lucky", "Misfortune", "Fortune", "River", "Calamities", "Probability", "Madness", "Chaos");
                    case "mother" -> List.of("Earth", "Fertility", "Reproduction", "Desolation", "Nature", "Origin", "Life", "Harvest", "Bounty");
                    case "moon" -> List.of("Moon", "Fertility", "Reproduction", "Spirituality", "Beauty", "Proliferation", "Moonlight", "Silver", "Crimson");
                    case "abyss" -> List.of("Devils", "Evil", "Desires", "Corruption", "Blood", "Malice", "Blathers", "Corrosion", "Filth");
                    case "chained" -> List.of("Chains", "Temperance", "Deviants", "Restraint", "Indulgence", "Unity", "Binding", "Curses", "Thorns");
                    case "black_emperor" -> List.of("Disorder", "Distortion", "Exploitation", "Entropy", "Abolition", "Frenzy", "Domination", "Black", "Shadow");
                    case "justiciar" -> List.of("Laws", "Rules", "Balance", "Discipline", "Justice", "Judgement", "Order", "White", "Punishment");
                    default -> List.of();
        });
    }

    /** As mesmas palavras em português. Qualquer uma das duas línguas vale no nome honorífico. */
    static public LinkedList<String> getMustHaveWordsPt(String path){
        return new LinkedList<String>(
                switch (path){
                    case "fool" -> List.of("História", "Mistério", "Bizarrice", "Mudança", "Desejos", "Milagres", "Drama", "Atuações", "Tolos");
                    case "error" -> List.of("Tempo", "Engano", "Trapaça", "Brechas", "Adulteração", "Exploração", "Bugs", "Roubo", "Erros");
                    case "door" -> List.of("Espaço", "Estrelas", "Cosmos", "Mundos", "Ocultação", "Selos", "Viagens", "Crônicas", "Portas");
                    case "visionary" -> List.of("Mente", "Sonhos", "Visões", "Fantasias", "Imaginação", "Discernimento", "Psique", "Coincidência", "Arranjos");
                    case "sun" -> List.of("Luz", "Ordem", "Santidade", "Contratos", "Justiça", "Energia", "Sol", "Dia", "Inextinguível");
                    case "tyrant" -> List.of("Tempestades", "Calamidades", "Mares", "Céus", "Tsunamis", "Profundezas", "Criaturas", "Tirania", "Ira");
                    case "white_tower" -> List.of("Conhecimento", "Observação", "Raciocínio", "Cognição", "Sabedoria", "Razão", "Onisciência", "Aprendizado", "Loucura");
                    case "hanged_man" -> List.of("Degeneração", "Corrupção", "Mutação", "Sombras", "Escuridão", "Sussurros", "Sacrifício", "Depravação", "Palavras");
                    case "darkness" -> List.of("Escuridão", "Sonhos", "Silêncio", "Horror", "Repouso", "Infortúnio", "Ocultação", "Estrelas", "Noite");
                    case "death" -> List.of("Morte", "Não-morte", "Espíritos", "Submundo", "Palidez", "Estige", "Mortos", "Eterno", "Descanso");
                    case "twilight_giant" -> List.of("Crepúsculo", "Decadência", "Glória", "Passagem", "Tempo", "Alvorada", "Anoitecer", "Poder", "Combate");
                    case "demoness" -> List.of("Caos", "Catástrofes", "Espelho", "Desastres", "Apocalipse", "Discórdia", "Pragas", "Primórdio", "Feminilidade");
                    case "red_priest" -> List.of("Guerra", "Calamidade", "Ferro", "Sangue", "Batalha", "Discórdia", "Caos", "Destruição", "Masculinidade");
                    case "hermit" -> List.of("Conhecimento", "Informação", "Símbolos", "Magia", "Dados", "Números", "Educação", "Histórias", "Misticismo");
                    case "paragon" -> List.of("Tecnologia", "Lógica", "Máquinas", "Física", "Civilizações", "Estrutura", "Essência", "Educação", "Iluminação");
                    case "wheel_of_fortune" -> List.of("Destino", "Sorte", "Sortudo", "Infortúnio", "Fortuna", "Rio", "Calamidades", "Probabilidade", "Loucura", "Caos");
                    case "mother" -> List.of("Terra", "Fertilidade", "Reprodução", "Desolação", "Natureza", "Origem", "Vida", "Colheita", "Fartura");
                    case "moon" -> List.of("Lua", "Fertilidade", "Reprodução", "Espiritualidade", "Beleza", "Proliferação", "Luar", "Prata", "Carmesim");
                    case "abyss" -> List.of("Demônios", "Mal", "Desejos", "Corrupção", "Sangue", "Malícia", "Balbucios", "Corrosão", "Imundície");
                    case "chained" -> List.of("Correntes", "Temperança", "Desviantes", "Contenção", "Indulgência", "Unidade", "Amarras", "Maldições", "Espinhos");
                    case "black_emperor" -> List.of("Desordem", "Distorção", "Exploração", "Entropia", "Abolição", "Frenesi", "Dominação", "Negro", "Sombra");
                    case "justiciar" -> List.of("Leis", "Regras", "Equilíbrio", "Disciplina", "Justiça", "Julgamento", "Ordem", "Branco", "Punição");
                    default -> List.of();
        });
    }

    /** As palavras para mostrar a quem está escrevendo: em português, se o jogo estiver em português */
    static public LinkedList<String> getMustHaveWordsFor(String path, String languageCode){
        return languageCode != null && languageCode.startsWith("pt") ? getMustHaveWordsPt(path) : getMustHaveWords(path);
    }

    /** Sem acento, em minúsculas e sem o "s" do plural: "Misterio" vale como "Mistério" e "Tolo" como "Tolos" */
    private static String plain(String s){
        String p = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
        return p.length() > 3 && p.endsWith("s") ? p.substring(0, p.length() - 1) : p;
    }

    public static int MAX_LENGTH = 200;

    public boolean contains(String str) {return lines.contains(str);}

    public static boolean validate(String path, LinkedList<String> list){
        var mustHave = new java.util.HashSet<String>();
        getMustHaveWords(path).forEach(w -> mustHave.add(plain(w)));
        getMustHaveWordsPt(path).forEach(w -> mustHave.add(plain(w)));

        for (var str : list){
            var words = List.of(str.split("[ ,.;:!?]"));
            if(words.stream().noneMatch(input -> mustHave.contains(plain(input))))
                return false;
        }

        return true;
    }

    public boolean isEmpty(){
        return lines.isEmpty();
    }

    public HonorificName addLine(String str){
        LinkedList<String> result = new LinkedList<>(lines);
        result.add(str);

        return new HonorificName(result);
    }

    public String getAllInfo(){
        return !lines.isEmpty()?
                (LotmText.f("lotmcraft.text.line_1_line_2_line_3", "\n  Line 1: %s\n  Line 2: %s\n  Line 3: %s%s%s", lines.get(0), lines.get(1), lines.get(2), ((lines.size() >= 4) ? (LotmText.f("lotmcraft.text.line_4", "\n  Line 4: %s", lines.get(3))) : ""), ((lines.size() == 5) ? (LotmText.f("lotmcraft.text.line_5", "\n  Line 5: %s", lines.get(4))) : "")))
                : LotmText.t("lotmcraft.text.none", "None");
    }

    public CompoundTag toNBT(){
        CompoundTag tag = new CompoundTag();

        ListTag list = new ListTag();
        for (var value : lines) {
            list.add(StringTag.valueOf(value));
        }

        tag.put(NBT_LINES, list);

        return tag;
    }

    static public HonorificName fromNBT(CompoundTag tag){
        LinkedList<String> list = new LinkedList<>();

        if (tag.contains(NBT_LINES, Tag.TAG_LIST)) {
            ListTag listTag = tag.getList(NBT_LINES, Tag.TAG_STRING);

            for (var obj : listTag) {
                list.add(obj.getAsString());
            }
        }

        return new HonorificName(list);
    }

    public static HonorificName fromNetwork(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        LinkedList<String> list = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            list.add(buf.readUtf(MAX_LENGTH));
        }
        return new HonorificName(list);
    }

    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeVarInt(lines.size());
        for (String line : lines) {
            buf.writeUtf(line, MAX_LENGTH);
        }
    }
}
