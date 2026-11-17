package de.jakob.lotm.beyonders.abilities.abyss.contract.builder;
import de.jakob.lotm.util.LotmText;


public enum ContractTermKind {
    PARTY_A(LotmText.t("lotmcraft.text.party_a", "Party A"), false),
    PARTY_B(LotmText.t("lotmcraft.text.party_b", "Party B"), false),
    GIVES(LotmText.t("lotmcraft.text.gives", "Gives"), false),
    GETS(LotmText.t("lotmcraft.text.gets", "Gets"), false),
    FOR(LotmText.t("lotmcraft.text.for", "For"), false),
    FROM(LotmText.t("lotmcraft.text.from", "From"), false),
    RETURNS(LotmText.t("lotmcraft.text.returns", "Returns"), false),
    TO(LotmText.t("lotmcraft.text.to", "To"), false),
    KILLS(LotmText.t("lotmcraft.text.kills", "Kills"), false),
    VICTIM(LotmText.t("lotmcraft.text.victim", "Victim"), true),
    BIND_LIFE_DEATH(LotmText.t("lotmcraft.text.bind_life_death", "Bind Life & Death"), false),
    SOUL(LotmText.t("lotmcraft.text.soul", "Soul"), false),
    RESOURCE(LotmText.t("lotmcraft.text.resource", "Resource"), true),
    AMOUNT(LotmText.t("lotmcraft.text.amount", "Amount"), true),
    TIME_UNIT(LotmText.t("lotmcraft.text.time_unit", "Time Unit"), true),
    UNTIL(LotmText.t("lotmcraft.text.until", "Until"), false),
    PASSES(LotmText.t("lotmcraft.text.passes", "Passes"), false),
    DIGEST(LotmText.t("lotmcraft.text.digest", "Digest"), false),
    ENTITY_TYPE(LotmText.t("lotmcraft.text.entity_type", "Entity Type"), true);

    public final String label;
    public final boolean isInput;

    ContractTermKind(String label, boolean isInput) {
        this.label = label;
        this.isInput = isInput;
    }
}