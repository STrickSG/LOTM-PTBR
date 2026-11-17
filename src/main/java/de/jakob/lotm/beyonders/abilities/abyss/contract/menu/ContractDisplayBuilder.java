package de.jakob.lotm.beyonders.abilities.abyss.contract.menu;

import de.jakob.lotm.beyonders.abilities.abyss.contract.ContractData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import de.jakob.lotm.util.LotmText;

public final class ContractDisplayBuilder {

    private ContractDisplayBuilder() {
    }

    public static ContractDisplay build(ContractData data) {
        String kind = data.contractKind().isEmpty() ? "STANDARD" : data.contractKind().toUpperCase();
        String status = getStatus(data);
        return switch (kind) {
            case "KILL" -> buildKill(data, status);
            case "LIFE_DEATH" -> buildLifeDeath(data, status);
            default -> buildStandard(data, status);
        };
    }
    // STANDARD
    private static ContractDisplay buildStandard(ContractData data, String status) {
        ContractDisplay.Builder builder = ContractDisplay.builder(Component.translatableWithFallback("lotmcraft.text.contract_2", "[Contract] %s", String.valueOf(status)).withStyle(ChatFormatting.GOLD));
        String termB = describeTransfer(data, false);
        String termA = describeTransfer(data, true);
        if (termB != null) {
            builder.line(Component.literal(termB));
            builder.line(statusLine(data.termBFulfilled()));
        }

        if (termA != null) {
            if (termB != null) {builder.blank();}
            builder.line(Component.literal(termA));
            builder.line(statusLine(data.termAFulfilled()));
        }
        if (termB == null && termA == null) {builder.line(Component.translatableWithFallback("lotmcraft.text.no_obligations", "No obligations").withStyle(ChatFormatting.GRAY));}
        return builder.build();
    }
    // KILL
    private static ContractDisplay buildKill(ContractData data, String status) {
        ContractDisplay.Builder builder = ContractDisplay.builder(Component.translatableWithFallback("lotmcraft.text.killing_contract_2", "[Killing Contract] %s", String.valueOf(status)).withStyle(ChatFormatting.GOLD));
        String killer = data.isKillerPartyA() ? safeName(data.partyAName(), LotmText.t("lotmcraft.text.party_a", "Party A")) : safeName(data.partyBName(), LotmText.t("lotmcraft.text.party_b", "Party B"));
        String victim = safeName(data.victimName(), LotmText.t("lotmcraft.text.unknown", "Unknown"));
        builder.line(Component.translatableWithFallback("lotmcraft.text.kills_2", "%s kills %s", String.valueOf(killer), String.valueOf(victim)));
        builder.line(Component.translatableWithFallback("lotmcraft.text.status", "Status: ").append(Component.literal(data.killPaidOut() ? LotmText.t("lotmcraft.text.kill_complete", "Kill Complete") : LotmText.t("lotmcraft.text.pending_kill", "Pending Kill")).withStyle(data.killPaidOut() ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));
        if (data.termAType() != ContractData.TermType.NONE) {
            builder.blank();
            String payer = data.isKillerPartyA() ? safeName(data.partyBName(), LotmText.t("lotmcraft.text.party_b", "Party B")) : safeName(data.partyAName(), LotmText.t("lotmcraft.text.party_a", "Party A"));
            String reward = describeKillReward(data);
            builder.line(Component.translatableWithFallback("lotmcraft.text.paid_by", "Paid by: %s", String.valueOf(payer)).withStyle(ChatFormatting.AQUA));
            builder.line(Component.translatableWithFallback("lotmcraft.text.reward", "Reward: %s", String.valueOf(reward)));
            builder.line(Component.translatableWithFallback("lotmcraft.text.payment", "Payment: ").append(Component.literal(data.termAFulfilled() ? LotmText.t("lotmcraft.text.paid_2", "Paid") : LotmText.t("lotmcraft.text.pending", "Pending")).withStyle(data.termAFulfilled() ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));
        } else {
            builder.blank();
            builder.line(Component.translatableWithFallback("lotmcraft.text.no_payment", "No payment").withStyle(ChatFormatting.GRAY));
        }
        return builder.build();
    }

    private static String describeKillReward(ContractData data) {
        String payer = data.isKillerPartyA() ? safeName(data.partyBName(), LotmText.t("lotmcraft.text.party_b", "Party B")) : safeName(data.partyAName(), LotmText.t("lotmcraft.text.party_a", "Party A"));
        String killer = data.isKillerPartyA() ? safeName(data.partyAName(), LotmText.t("lotmcraft.text.party_a", "Party A")) : safeName(data.partyBName(), LotmText.t("lotmcraft.text.party_b", "Party B"));
        String reward = describeTerm(data, true, payer, killer);
        return payer + " → " + killer + ": " + reward;
    }
    // LIFE / DEATH
    private static ContractDisplay buildLifeDeath(ContractData data, String status) {
        ContractDisplay.Builder builder = ContractDisplay.builder(Component.translatableWithFallback("lotmcraft.text.life_or_death_contract", "[Life-or-Death Contract] %s", String.valueOf(status)).withStyle(ChatFormatting.GOLD));
        String partyA = safeName(data.partyAName(), LotmText.t("lotmcraft.text.party_a", "Party A"));
        String partyB = safeName(data.partyBName(), LotmText.t("lotmcraft.text.party_b", "Party B"));
        builder.line(Component.literal(partyA + " ↔ " + partyB));
        builder.line(Component.translatableWithFallback("lotmcraft.text.linked_lives", "Linked lives").withStyle(ChatFormatting.LIGHT_PURPLE));

        String termB = describeLifeDeathTransfer(data, false);
        String termA = describeLifeDeathTransfer(data, true);

        if (termB != null) {
            builder.blank();
            builder.line(Component.literal(termB));
            builder.line(statusLine(data.termBFulfilled()));
        }

        if (termA != null) {
            if (termB == null) {builder.blank();}
            else {builder.blank();}
            builder.line(Component.literal(termA));
            builder.line(statusLine(data.termAFulfilled()));
        }
        if (termA == null && termB == null) {
            builder.blank();
            builder.line(Component.translatableWithFallback("lotmcraft.text.no_exchange", "No exchange").withStyle(ChatFormatting.GRAY));
        }
        builder.blank();
        builder.line(Component.literal(formatDuration(data.lifeDeathDurationTicks())).withStyle(ChatFormatting.GRAY));
        return builder.build();
    }

    // TRANSFERS
    private static String describeTransfer(ContractData data, boolean termA) {
        ContractData.TermType type = termA ? data.termAType() : data.termBType();
        if (type == ContractData.TermType.NONE) {return null;}
        String recipient = termA ? safeName(data.partyAName(), LotmText.t("lotmcraft.text.party_a", "Party A")) : safeName(data.partyBName(), LotmText.t("lotmcraft.text.party_b", "Party B"));
        String description;
        if (type == ContractData.TermType.SOUL) {
            boolean ownerIsPartyA = termA ? data.termASoulOwnerIsPartyA() : data.termBSoulOwnerIsPartyA();
            String owner = ownerIsPartyA ? safeName(data.partyAName(), LotmText.t("lotmcraft.text.party_a", "Party A")) : safeName(data.partyBName(), LotmText.t("lotmcraft.text.party_b", "Party B"));
            description = LotmText.f("lotmcraft.text.s_soul_2", "%s's Soul", owner);
            return owner + " → " + recipient + ": " + description;
        }
        String sender = termA ? safeName(data.partyBName(), LotmText.t("lotmcraft.text.party_b", "Party B")) : safeName(data.partyAName(), LotmText.t("lotmcraft.text.party_a", "Party A"));
        description = describeTerm(data, termA, sender, recipient);
        return sender + " → " + recipient + ": " + description;
    }

    private static String describeLifeDeathTransfer(ContractData data, boolean termA) {
        ContractData.TermType type = termA ? data.termAType() : data.termBType();
        if (type == ContractData.TermType.NONE) {
            return null;
        }
        if (type == ContractData.TermType.SOUL) {
            boolean ownerIsPartyA = termA ? data.termASoulOwnerIsPartyA() : data.termBSoulOwnerIsPartyA();
            String owner = ownerIsPartyA ? safeName(data.partyAName(), LotmText.t("lotmcraft.text.party_a", "Party A")) : safeName(data.partyBName(), LotmText.t("lotmcraft.text.party_b", "Party B"));
            String recipient = ownerIsPartyA ? safeName(data.partyBName(), LotmText.t("lotmcraft.text.party_b", "Party B")) : safeName(data.partyAName(), LotmText.t("lotmcraft.text.party_a", "Party A"));
            return LotmText.f("lotmcraft.text.s_soul", "%s's Soul → %s", owner, recipient);
        }
        String sender = termA ? safeName(data.partyBName(), LotmText.t("lotmcraft.text.party_b", "Party B")) : safeName(data.partyAName(), LotmText.t("lotmcraft.text.party_a", "Party A"));
        String recipient = termA ? safeName(data.partyAName(), LotmText.t("lotmcraft.text.party_a", "Party A")) : safeName(data.partyBName(), LotmText.t("lotmcraft.text.party_b", "Party B"));
        return sender + " → " + recipient + ": " + describeTerm(data, termA, sender, recipient);
    }
    // TERM DESCRIPTION
    private static String describeTerm(ContractData data, boolean termA, String sender, String recipient) {
        ContractData.TermType type = termA ? data.termAType() : data.termBType();
        String item = termA ? data.termAItem() : data.termBItem();
        int count = termA ? data.termACount() : data.termBCount();
        return switch (type) {
            case ITEM -> count + "x " + ContractData.prettify(item);

            case DIGEST -> LotmText.f("lotmcraft.text.digestion_progress", "%s%% digestion progress", count);
            case SOUL -> {
                boolean ownerIsPartyA = termA ? data.termASoulOwnerIsPartyA() : data.termBSoulOwnerIsPartyA();
                String owner = ownerIsPartyA ? safeName(data.partyAName(), LotmText.t("lotmcraft.text.party_a", "Party A")) : safeName(data.partyBName(), LotmText.t("lotmcraft.text.party_b", "Party B"));
                yield LotmText.f("lotmcraft.text.s_soul_2", "%s's Soul", owner);
            }
            case RELEASE_SOUL -> LotmText.f("lotmcraft.text.the_soul_of", "the soul of %s", item);
            case NONE -> LotmText.t("lotmcraft.text.nothing", "nothing");
        };
    }
    // STATUS

    private static String getStatus(ContractData data) {
        if (!data.isActive() && data.isComplete()) {return LotmText.t("lotmcraft.text.fulfilled_2", "Fulfilled");}
        if (!data.isActive()) {return LotmText.t("lotmcraft.text.void", "Void");}
        return LotmText.t("lotmcraft.text.active", "Active");
    }

    private static Component statusLine(boolean fulfilled) {
        return Component.literal(fulfilled ? LotmText.t("lotmcraft.text.fulfilled_2", "Fulfilled") : LotmText.t("lotmcraft.text.pending", "Pending")).withStyle(fulfilled ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
    }
    // DURATION
    private static String formatDuration(long ticks) {
        if (ticks < 0) {return LotmText.t("lotmcraft.text.no_expiry", "No expiry");}
        long seconds = ticks / 20;
        if (seconds % 86400 == 0) {
            long days = seconds / 86400;
            return LotmText.f("lotmcraft.text.expires_in", "Expires in %s%s", days, (days == 1 ? LotmText.t("lotmcraft.text.day", " day") : LotmText.t("lotmcraft.text.days", " days")));
        }
        if (seconds % 3600 == 0) {
            long hours = seconds / 3600;
            return LotmText.f("lotmcraft.text.expires_in", "Expires in %s%s", hours, (hours == 1 ? LotmText.t("lotmcraft.text.hour", " hour") : LotmText.t("lotmcraft.text.hours", " hours")));
        }
        if (seconds % 60 == 0) {
            long minutes = seconds / 60;
            return LotmText.f("lotmcraft.text.expires_in", "Expires in %s%s", minutes, (minutes == 1 ? LotmText.t("lotmcraft.text.minute", " minute") : LotmText.t("lotmcraft.text.minutes", " minutes")));
        }
        return LotmText.f("lotmcraft.text.expires_in", "Expires in %s%s", seconds, (seconds == 1 ? LotmText.t("lotmcraft.text.second", " second") : LotmText.t("lotmcraft.text.seconds", " seconds")));
    }
    // UTIL
    private static String safeName(String name, String fallback) {
        return name == null || name.isBlank() ? fallback : name;
    }
}