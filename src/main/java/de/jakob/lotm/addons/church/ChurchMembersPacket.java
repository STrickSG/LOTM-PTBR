package de.jakob.lotm.addons.church;

import de.jakob.lotm.LOTMCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;

/** Servidor -> cliente: os fiéis desta colônia, para as abas "Fiéis" e "Âncoras" do menu */
public record ChurchMembersPacket(BlockPos temple, List<Member> members, int total, int floorPercent, List<String> colonies, int flags)
        implements CustomPacketPayload {
    public ChurchMembersPacket(BlockPos temple, List<Member> members, int total, int floorPercent, List<String> colonies, boolean canEdit, boolean preachedToday) {
        this(temple, members, total, floorPercent, colonies, (canEdit ? 1 : 0) | (preachedToday ? 2 : 0));
    }

    public boolean canEdit() {
        return (flags & 1) != 0;
    }

    public boolean preachedToday() {
        return (flags & 2) != 0;
    }

    public record Member(UUID id, String name, String role) {
        static final StreamCodec<ByteBuf, Member> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Member::id, ByteBufCodecs.STRING_UTF8, Member::name, ByteBufCodecs.STRING_UTF8, Member::role, Member::new);
    }

    public static final Type<ChurchMembersPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LOTMCraft.MOD_ID, "church_members"));
    public static final StreamCodec<ByteBuf, ChurchMembersPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ChurchMembersPacket::temple,
            Member.CODEC.apply(ByteBufCodecs.list()), ChurchMembersPacket::members,
            ByteBufCodecs.VAR_INT, ChurchMembersPacket::total,
            ByteBufCodecs.VAR_INT, ChurchMembersPacket::floorPercent,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ChurchMembersPacket::colonies,
            ByteBufCodecs.VAR_INT, ChurchMembersPacket::flags,
            ChurchMembersPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ChurchMembersPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ChurchClient.members(packet));
    }
}
