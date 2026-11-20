package de.jakob.lotm.addons.church;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import de.jakob.lotm.LOTMCraft;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.addons.rituals.Believers;
import java.util.UUID;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cliente -> servidor: consagrar o templo a si mesmo (fundando a Igreja/Seita com o nome escolhido) ou renomear */
public record ChurchActionPacket(BlockPos temple, String action, String name, String kind) implements CustomPacketPayload {
    public static final Type<ChurchActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "church_action"));
    public static final StreamCodec<ByteBuf, ChurchActionPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ChurchActionPacket::temple,
            ByteBufCodecs.STRING_UTF8, ChurchActionPacket::action,
            ByteBufCodecs.STRING_UTF8, ChurchActionPacket::name,
            ByteBufCodecs.STRING_UTF8, ChurchActionPacket::kind,
            ChurchActionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ChurchActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) act(player, packet);
        });
    }

    private static void act(ServerPlayer player, ChurchActionPacket packet) {
        if (player.distanceToSqr(packet.temple().getCenter()) > 64 * 64) return;
        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(player.level(), packet.temple());
        if (colony == null || !(colony.getServerBuildingManager().getBuilding(packet.temple()) instanceof ChurchBuilding temple)) return;
        Churches churches = Churches.get(player.server);
        String name = packet.name().strip();
        if (name.length() > Churches.MAX_NAME) name = name.substring(0, Churches.MAX_NAME);

        switch (packet.action()) {
            case "consecrate" -> {
                if (!BeyonderData.isBeyonder(player) || BeyonderData.getSequence(player) > Churches.MAX_SEQUENCE) {
                    say(player, Component.translatable("lotmcraft.church.not_worthy", Churches.MAX_SEQUENCE), 0xFF7A7A);
                    return;
                }
                String model = ChurchModels.pathwayOf(temple.getBlueprintPath());
                if (model != null && !model.equals(BeyonderData.getPathway(player))) {
                    say(player, Component.translatable("lotmcraft.church.wrong_model", Component.translatable("lotm.pathway." + model)), 0xFF7A7A);
                    return;
                }
                if (temple.getGod() != null && !temple.getGod().equals(player.getUUID())) {
                    say(player, Component.translatable("lotmcraft.church.already_consecrated"), 0xFF7A7A);
                    return;
                }
                if (churches.info(player.getUUID()) == null) {
                    if (name.isEmpty()) {
                        say(player, Component.translatable("lotmcraft.church.need_name"), 0xFF7A7A);
                        return;
                    }
                    String kind = Churches.SECT.equals(packet.kind()) ? Churches.SECT : Churches.CHURCH;
                    churches.found(player, name, kind, BeyonderData.getPathway(player));
                }
                temple.setGod(player.getUUID());
                player.serverLevel().sendParticles(ParticleTypes.END_ROD, packet.temple().getX() + 0.5, packet.temple().getY() + 1.2, packet.temple().getZ() + 0.5, 40, 0.5, 0.8, 0.5, 0.05);
                player.serverLevel().playSound(null, packet.temple(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.3f);
                say(player, Component.translatable("lotmcraft.church.consecrated", churches.info(player.getUUID()).name()), 0xFFE29A);
            }
            case "rename" -> {
                if (!player.getUUID().equals(temple.getGod()) || name.isEmpty()) return;
                churches.rename(player.getUUID(), name);
                Churches.temples(player.server, player.getUUID()).forEach(ChurchBuilding::markDirty);
                say(player, Component.translatable("lotmcraft.church.renamed", name), 0xFFE29A);
            }
            case "preach" -> preach(player, colony, temple);
            case "members" -> sendMembers(player, colony, temple);
            case "role" -> setRole(player, colony, temple, packet.name(), packet.kind());
            default -> { }
        }
    }

    /** A pregação: uma vez por dia em cada templo, o Deus prega e quem está perto tem o triplo de chance de crer */
    private static void preach(ServerPlayer player, IColony colony, ChurchBuilding temple) {
        if (!player.getUUID().equals(temple.getGod())) return;
        long day = player.server.overworld().getDayTime() / 24000L;
        if (temple.getLastPreachDay() == day) {
            say(player, Component.translatable("lotmcraft.church.preach.already"), 0xFF7A7A);
            return;
        }
        temple.setLastPreachDay(day);
        int converted = ChurchConversion.dailyRoll(player.server, colony, 3.0, player, 24);
        player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(1.2), player.getZ(), 30, 1.2, 0.6, 1.2, 0.02);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.5f, 0.8f);
        say(player, Component.translatable("lotmcraft.church.preach.done", converted), 0xFFE29A);
        sendMembers(player, colony, temple);
    }

    static void sendMembers(ServerPlayer player, IColony colony, ChurchBuilding temple) {
        UUID god = temple.getGod();
        var members = new java.util.ArrayList<ChurchMembersPacket.Member>();
        var colonies = new java.util.ArrayList<String>();
        int total = 0, floor = 0;
        boolean preached = false;
        if (god != null) {
            var all = Believers.members(player.server, god);
            total = all.size();
            for (var citizen : colony.getCitizenManager().getCitizens()) {
                if (all.contains(citizen.getUUID()))
                    members.add(new ChurchMembersPacket.Member(citizen.getUUID(), Believers.originalName(player.server, citizen), Believers.roleOf(player.server, citizen.getUUID())));
            }
            for (var other : IColonyManager.getInstance().getAllColonies()) {
                int n = 0;
                for (var citizen : other.getCitizenManager().getCitizens()) if (all.contains(citizen.getUUID())) n++;
                if (n > 0) colonies.add(other.getName() + ": " + n);
            }
            ServerPlayer godPlayer = player.server.getPlayerList().getPlayer(god);
            if (godPlayer != null) floor = Math.round(Believers.anchorFloor(godPlayer) * 1000);
            preached = temple.getLastPreachDay() == player.server.overworld().getDayTime() / 24000L;
        }
        members.sort(java.util.Comparator.comparingInt((ChurchMembersPacket.Member m) -> -ChurchRoles.of(Churches.CHURCH).indexOf(m.role())).thenComparing(ChurchMembersPacket.Member::name));
        de.jakob.lotm.network.PacketHandler.sendToPlayer(player, new ChurchMembersPacket(temple.getPosition(), members, total, floor, colonies,
                player.getUUID().equals(god), preached));
    }

    /** O Deus dá um cargo a um fiel desta colônia (respeitando o limite do cargo) */
    private static void setRole(ServerPlayer player, IColony colony, ChurchBuilding temple, String citizenId, String role) {
        UUID god = temple.getGod();
        if (god == null || !player.getUUID().equals(god)) return;
        Churches.Info info = Churches.get(player.server).info(god);
        if (info == null || !ChurchRoles.of(info.kind()).contains(role)) return;
        UUID id;
        try { id = UUID.fromString(citizenId); } catch (IllegalArgumentException e) { return; }
        var citizen = colony.getCitizenManager().getCitizens().stream().filter(c -> c.getUUID().equals(id)).findFirst().orElse(null);
        if (citizen == null || !god.equals(Believers.godOfCitizen(player.server, id))) return;
        int limit = ChurchRoles.limit(role);
        if (limit != Integer.MAX_VALUE) {
            long holders;
            if (limit < 0) holders = colony.getCitizenManager().getCitizens().stream()
                    .filter(c -> !c.getUUID().equals(id) && god.equals(Believers.godOfCitizen(player.server, c.getUUID())) && role.equals(Believers.roleOf(player.server, c.getUUID()))).count();
            else holders = Believers.members(player.server, god).stream().filter(m -> !m.equals(id) && role.equals(Believers.roleOf(player.server, m))).count();
            if (holders >= Math.abs(limit)) {
                say(player, Component.translatable("lotmcraft.church.role.full", Component.translatable("lotmcraft.church.role." + role)), 0xFF7A7A);
                sendMembers(player, colony, temple);
                return;
            }
        }
        Believers.setRole(player.server, god, Churches.godName(player.server, god), citizen, role);
        sendMembers(player, colony, temple);
    }

    private static void say(ServerPlayer player, Component text, int color) {
        player.displayClientMessage(text.copy().withColor(color), true);
    }
}
