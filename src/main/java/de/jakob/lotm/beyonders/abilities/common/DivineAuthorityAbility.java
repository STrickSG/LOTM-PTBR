package de.jakob.lotm.beyonders.abilities.common;

import de.jakob.lotm.addons.church.ColonistBeyonders;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.beyonders.abilities.error.StealCharacteristicAbility;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.FxHandler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * Autoridade Divina: como a Autoridade Angelical, uma habilidade com sub-habilidades, mas só para quem é Deus
 * (Sequência 0) de algum Caminho. Por enquanto: Extrair Característica.
 */
public class DivineAuthorityAbility extends SelectableAbility {
    private static final int GOLD = 0xFFD970;

    public DivineAuthorityAbility(String id) {
        super(id, 5.0f);
        this.canBeUsedByNPC = false;
        this.canBeCopied = false;
        this.cannotBeStolen = true;
        this.canBeReplicated = false;
        this.canBeUsedInArtifact = false;
        this.doesNotIncreaseDigestion = true;
    }

    @Override
    protected float getSpiritualityCost() {
        return 1000.0f;
    }

    @Override
    public Map<String, Integer> getRequirements() {
        Map<String, Integer> reqs = new HashMap<>();
        for (String pathway : BeyonderData.pathways) reqs.put(pathway, 0);
        return reqs;
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{"ability.lotmcraft.divine_authority.extract_characteristic"};
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int abilityIndex) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        if (abilityIndex == 0) extract((ServerLevel) level, player);
    }

    /**
     * Arranca a característica da Sequência atual de um Beyonder mais fraco: ela vai para o Deus e o alvo cai uma
     * Sequência. Quem estava na Sequência 9 não aguenta e morre.
     */
    private void extract(ServerLevel level, ServerPlayer god) {
        LivingEntity target = AbilityUtil.getTargetEntity(god, 40, 2, false, true);
        if (target == null || !BeyonderData.isBeyonder(target)) {
            message(god, "ability.lotmcraft.divine_authority.no_target");
            return;
        }
        int targetSeq = BeyonderData.getSequence(target);
        if (targetSeq <= BeyonderData.getSequence(god)) {
            message(god, "ability.lotmcraft.divine_authority.too_strong");
            return;
        }
        boolean dies = targetSeq >= 9;
        // colono Beyonder: a Sequência guardada cai junto (ou some, se ele morre) antes de mexer na entidade
        ColonistBeyonders.regress(level.getServer(), target);
        ItemStack characteristic = StealCharacteristicAbility.extractCharacteristic(target, targetSeq);
        if (characteristic.isEmpty()) {
            message(god, "ability.lotmcraft.divine_authority.failed");
            return;
        }
        if (target instanceof de.jakob.lotm.entity.custom.BeyonderNPCEntity npc) npc.syncSequenceFromData();
        FxHandler.playOnEntity(level, "characteristic_theft", target, target.getBbHeight() * 0.5, Math.max(1, target.getBbHeight() / 1.6));
        level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY(0.6), target.getZ(), 40, 0.4, 0.6, 0.4, 0.08);
        level.playSound(null, target.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.5f, 0.6f);
        if (!god.getInventory().add(characteristic)) god.drop(characteristic, false);
        message(god, "ability.lotmcraft.divine_authority.extracted", target.getName());
        if (target instanceof ServerPlayer victim)
            victim.displayClientMessage(Component.translatable("ability.lotmcraft.divine_authority.victim").withColor(GOLD), false);
        if (dies) {
            // a característica já saiu: o corpo não solta outra ao morrer
            target.getPersistentData().putBoolean("LotmCharacteristicTaken", true);
            target.hurt(level.damageSources().magic(), Float.MAX_VALUE);
            // colonos e outros protegidos não morrem só com dano: sem a característica, o corpo não aguenta
            if (target.isAlive()) {
                target.setHealth(0);
                target.die(level.damageSources().magic());
            }
        }
    }

    private static void message(ServerPlayer player, String key, Object... args) {
        AbilityUtil.sendActionBar(player, Component.translatable(key, args).withColor(GOLD));
    }
}
