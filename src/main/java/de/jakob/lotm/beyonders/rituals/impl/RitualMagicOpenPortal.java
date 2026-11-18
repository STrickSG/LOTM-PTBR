package de.jakob.lotm.beyonders.rituals.impl;

import com.google.gson.JsonElement;
import de.jakob.lotm.beyonders.rituals.RitualManager;
import de.jakob.lotm.beyonders.rituals.RitualResultHandler;
import de.jakob.lotm.dimension.spirit_world.SpiritWorldHandler;
import de.jakob.lotm.entity.custom.ability_entities.PortalEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class RitualMagicOpenPortal implements RitualResultHandler {

    @Override
    public void perform(Map<String, Object> params, ServerPlayer player, BlockPos ritualCenter) {
        OpenPortalResult result = deserializeParams(params, OpenPortalResult.class);
        if (result == null) return;

        ServerLevel destinationLevel = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(result.destination())));

        if (destinationLevel == null) {
            return;
        }

        Vec3 portalPos = ritualCenter.getCenter().add(0, 2, 0);
        Vec3 destinationPos = result.destination.equals("lotmcraft:spirit_world") ? SpiritWorldHandler.getCoordinatesInSpiritWorld(portalPos, player.level()) : portalPos;

        // feito do lado de dentro, o ritual abre o caminho de volta para o Mundo de Cima (ex.: névoa da história)
        if (Boolean.TRUE.equals(result.return_when_inside()) && player.level() == destinationLevel) {
            destinationLevel = player.getServer().overworld();
            int y = destinationLevel.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(portalPos.x), (int) Math.floor(portalPos.z));
            destinationPos = new Vec3(portalPos.x, y, portalPos.z);
        } else if (result.destination.equals("lotmcraft:historical_void")) {
            // a névoa da história é plana: chega no chão dela
            int y = de.jakob.lotm.beyonders.abilities.fool.HistoricalVoidHidingAbility.findSafeY(destinationLevel, (int) Math.floor(portalPos.x), (int) Math.floor(portalPos.z));
            destinationPos = new Vec3(portalPos.x, y, portalPos.z);
        }
        BlockPos safeLocation = searchForSafeLocation(
                destinationLevel,
                BlockPos.containing(destinationPos),
                5
        );

        if(safeLocation == null) {
            safeLocation = BlockPos.containing(destinationPos);
            destinationLevel.setBlockAndUpdate(safeLocation, Blocks.AIR.defaultBlockState());
            destinationLevel.setBlockAndUpdate(safeLocation.above(), Blocks.AIR.defaultBlockState());
            destinationLevel.setBlockAndUpdate(safeLocation.below(), Blocks.END_STONE.defaultBlockState());
        }

        PortalEntity portal = new PortalEntity(
                portalPos,
                player.level(),
                destinationLevel,
                safeLocation.getCenter(),
                20 * 60
        );
        player.level().addFreshEntity(portal);
    }

    private BlockPos searchForSafeLocation(ServerLevel level, BlockPos startPos, int radius) {
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = startPos.offset(x, y, z);
                    if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir() && !level.getBlockState(pos.below()).isAir()) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    /** {@code return_when_inside}: feito dentro do destino, abre o portal de volta para o Mundo de Cima */
    public record OpenPortalResult(String destination, Boolean return_when_inside) { }

    public static <T> T deserializeParams(Map<String, Object> params, Class<T> type) {
        JsonElement json = RitualManager.GSON.toJsonTree(params);
        return RitualManager.GSON.fromJson(json, type);
    }
}
