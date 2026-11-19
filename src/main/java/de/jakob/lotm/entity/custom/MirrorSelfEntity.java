package de.jakob.lotm.entity.custom;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/**
 * O seu eu do espelho (Demônia, ritual da Sequência 3). Mora no Mundo do Espelho, tem a sua aparência e a sua força.
 * Fica neutro até ser atacado. Depois do ritual, vencido, encantado ou reconciliado, acompanha você.
 */
public class MirrorSelfEntity extends PathfinderMob {
    /** 0 = livre, 1 = escravizado, 2 = apaixonado, 3 = reconciliado */
    public static final int WILD = 0, ENSLAVED = 1, INFATUATED = 2, RECONCILED = 3;

    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(MirrorSelfEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(MirrorSelfEntity.class, EntityDataSerializers.INT);

    /** Tempo em paz ao lado do original (para a reconciliação) */
    public int peaceTicks;
    /** O original o atacou alguma vez (não há mais reconciliação) */
    public boolean attackedByOwner;
    /** Alguém além do original o feriu, ou o original usou um Artefato Selado (não vale vencer sozinho) */
    public boolean helped;

    public MirrorSelfEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
        builder.define(STATE, WILD);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(2, new FollowOriginalGoal(this));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 12f));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Nullable
    public UUID getOwner() {
        return entityData.get(OWNER).orElse(null);
    }

    public void setOwner(UUID owner) {
        entityData.set(OWNER, Optional.of(owner));
    }

    public int getState() {
        return entityData.get(STATE);
    }

    public void setState(int state) {
        entityData.set(STATE, state);
        if (state != WILD) setTarget(null);
    }

    @Nullable
    public Player getOwnerPlayer() {
        UUID owner = getOwner();
        return owner == null ? null : level().getPlayerByUUID(owner);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (getOwner() != null) tag.putUUID("MirrorOwner", getOwner());
        tag.putInt("MirrorState", getState());
        tag.putInt("MirrorPeace", peaceTicks);
        tag.putBoolean("MirrorAttacked", attackedByOwner);
        tag.putBoolean("MirrorHelped", helped);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("MirrorOwner")) setOwner(tag.getUUID("MirrorOwner"));
        setState(tag.getInt("MirrorState"));
        peaceTicks = tag.getInt("MirrorPeace");
        attackedByOwner = tag.getBoolean("MirrorAttacked");
        helped = tag.getBoolean("MirrorHelped");
    }

    /** Depois do ritual (vencido, encantado ou reconciliado), anda ao lado do original */
    private static final class FollowOriginalGoal extends Goal {
        private final MirrorSelfEntity self;

        FollowOriginalGoal(MirrorSelfEntity self) {
            this.self = self;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            Player owner = self.getOwnerPlayer();
            return self.getState() != WILD && owner != null && self.distanceToSqr(owner) > 16;
        }

        @Override
        public void tick() {
            Player owner = self.getOwnerPlayer();
            if (owner == null) return;
            if (self.distanceToSqr(owner) > 32 * 32) self.teleportTo(owner.getX(), owner.getY(), owner.getZ());
            else self.getNavigation().moveTo(owner, 1.1);
        }
    }
}
