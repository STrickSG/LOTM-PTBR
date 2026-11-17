package de.jakob.lotm.beyonders.abilities.abyss.contract.builder;
import de.jakob.lotm.util.LotmText;


public enum ContractTermKind {
    PARTY_A(LotmText.reg("lotmcraft.text.party_a", "Party A"), false),
    PARTY_B(LotmText.reg("lotmcraft.text.party_b", "Party B"), false),
    GIVES(LotmText.reg("lotmcraft.text.gives", "Gives"), false),
    GETS(LotmText.reg("lotmcraft.text.gets", "Gets"), false),
    FOR(LotmText.reg("lotmcraft.text.for", "For"), false),
    FROM(LotmText.reg("lotmcraft.text.from", "From"), false),
    RETURNS(LotmText.reg("lotmcraft.text.returns", "Returns"), false),
    TO(LotmText.reg("lotmcraft.text.to", "To"), false),
    KILLS(LotmText.reg("lotmcraft.text.kills", "Kills"), false),
    VICTIM(LotmText.reg("lotmcraft.text.victim", "Victim"), true),
    BIND_LIFE_DEATH(LotmText.reg("lotmcraft.text.bind_life_death", "Bind Life & Death"), false),
    SOUL(LotmText.reg("lotmcraft.text.soul", "Soul"), false),
    RESOURCE(LotmText.reg("lotmcraft.text.resource", "Resource"), true),
    AMOUNT(LotmText.reg("lotmcraft.text.amount", "Amount"), true),
    TIME_UNIT(LotmText.reg("lotmcraft.text.time_unit", "Time Unit"), true),
    UNTIL(LotmText.reg("lotmcraft.text.until", "Until"), false),
    PASSES(LotmText.reg("lotmcraft.text.passes", "Passes"), false),
    DIGEST(LotmText.reg("lotmcraft.text.digest", "Digest"), false),
    ENTITY_TYPE(LotmText.reg("lotmcraft.text.entity_type", "Entity Type"), true);

    public final String label;
    public final boolean isInput;

    ContractTermKind(String label, boolean isInput) {
        this.label = label;
        this.isInput = isInput;
    }
}