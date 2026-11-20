package de.jakob.lotm.addons.church;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import de.jakob.lotm.network.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** O altar da Igreja: fica dentro do templo e abre o menu dele (onde, mais tarde, os colonos recebem as funções) */
public class ChurchAltarBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(3, 3, 3, 13, 11, 13), Block.box(0, 11, 0, 16, 14, 16));

    public ChurchAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ChurchBuilding temple = nearestTemple(level, pos);
        if (temple == null) {
            player.displayClientMessage(Component.translatable("lotmcraft.church.altar.no_temple").withColor(0xFFB070), true);
            return InteractionResult.CONSUME;
        }
        PacketHandler.sendToPlayer((ServerPlayer) player, new OpenChurchPacket(temple.getPosition()));
        return InteractionResult.CONSUME;
    }

    /** O templo da colônia onde o altar está, o mais perto dele */
    public static ChurchBuilding nearestTemple(Level level, BlockPos pos) {
        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        if (colony == null) return null;
        ChurchBuilding best = null;
        double bestDist = Double.MAX_VALUE;
        for (IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
            if (!(building instanceof ChurchBuilding temple)) continue;
            double d = temple.getPosition().distSqr(pos);
            if (d < bestDist) { bestDist = d; best = temple; }
        }
        return best;
    }
}
