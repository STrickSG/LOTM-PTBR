package de.jakob.lotm.gui.custom.selection_gui;

import com.mojang.datafixers.util.Pair;
import de.jakob.lotm.network.packets.toServer.DiscernmentSelectedPacket;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class DiscernmentSelectionGui extends ButtonListGui<Pair<String, Integer>>{
    public DiscernmentSelectionGui(List<Pair<String, Integer>> items) {
        super(Component.translatableWithFallback("lotmcraft.text.select_pathway_and_sequence", "Select Pathway and Sequence"), items);
    }

    @Override
    protected Component getItemName(Pair<String, Integer> item) {
        return Component.translatableWithFallback("lotmcraft.text.path_seq", "Path: %s Seq: %s", String.valueOf(item.getFirst()), String.valueOf(item.getSecond()));
    }

    @Override
    protected void onItemSelected(Pair<String, Integer> item) {
        PacketDistributor.sendToServer(new DiscernmentSelectedPacket(item));
        minecraft.setScreen(null);
    }
}
