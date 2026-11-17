package de.jakob.lotm.beyonders.abilities.abyss.contract.menu;

import de.jakob.lotm.gui.custom.text_display.AbilityMenuBuilder;
import de.jakob.lotm.gui.custom.text_display.ColoredTextDisplayScreen;
import de.jakob.lotm.gui.custom.text_display.TextColorHelper;
import net.minecraft.client.gui.screens.Screen;
import de.jakob.lotm.util.LotmText;

public class ContractAbilityMenus {

    public static ColoredTextDisplayScreen createContractAbilityMenu(Screen previousScreen) {
        return new AbilityMenuBuilder(LotmText.t("lotmcraft.text.outdated_contract", "Outdated Contract"), previousScreen)
                .header(LotmText.t("lotmcraft.text.overview", "Overview"), TextColorHelper.DARK_RED)
                .line(LotmText.t("lotmcraft.text.create_binding_contracts_with_other_players", "Create binding contracts with other players using the contract builder."), TextColorHelper.DARK_GREEN)
                .line(LotmText.t("lotmcraft.text.contracts_can_exchange_items_souls_digestion", "Contracts can exchange items, souls, digestion, life and death, or"), TextColorHelper.DARK_GREEN)
                .line(LotmText.t("lotmcraft.text.even_require_one_player_to_kill", "even require one player to kill another. Choose your terms carefully."), TextColorHelper.DARK_GREEN)

                .spacing()
                .header(LotmText.t("lotmcraft.text.how_to_use", "How to Use"), TextColorHelper.DARK_RED)
                .line(LotmText.t("lotmcraft.text.1_activate_the_ability", "1. Activate the ability"), TextColorHelper.DARK_GREEN)
                .line(LotmText.t("lotmcraft.text.2_use_the_contract_builder_to", "2. Use the Contract Builder to choose the contract terms"), TextColorHelper.DARK_GREEN)
                .line(LotmText.t("lotmcraft.text.3_assign_party_a_and_party", "3. Assign Party A and Party B when required"), TextColorHelper.DARK_GREEN)
                .line(LotmText.t("lotmcraft.text.4_the_assigned_target_receives_the", "4. The assigned target receives the contract proposal"), TextColorHelper.DARK_GREEN)
                .line(LotmText.t("lotmcraft.text.5_they_type_accept_or_decline", "5. They type 'accept' or 'decline' in chat"), TextColorHelper.DARK_GREEN)
                .line(LotmText.t("lotmcraft.text.6_once_accepted_a_contract_item", "6. Once accepted, a Contract Item records the agreement"), TextColorHelper.DARK_GREEN)

                .spacing()
                .header(LotmText.t("lotmcraft.text.contract_forms", "Contract Forms"), TextColorHelper.DARK_RED)
                .ability("<Name> gives <Name> <resource> for <Name> <resource>", LotmText.t("lotmcraft.text.standard_exchange", "Standard exchange"), TextColorHelper.DARK_RED, TextColorHelper.DARK_GREEN)
                .ability("<Name> gets <resource> for <Name> <resource>", LotmText.t("lotmcraft.text.same_as_give_reordered", "Same as GIVE, reordered"), TextColorHelper.DARK_RED, TextColorHelper.DARK_GREEN)
                .ability("<Name> returns <Name>'s soul to <Name> [for <resource>]", LotmText.t("lotmcraft.text.releases_a_soul_you_hold", "Releases a soul you hold"), TextColorHelper.DARK_RED, TextColorHelper.DARK_GREEN)
                .ability("<Name> returns <item> to <Name>", LotmText.t("lotmcraft.text.an_iou_using_your_soul_as", "An IOU using your soul as collateral"), TextColorHelper.DARK_RED, TextColorHelper.DARK_GREEN)
                .ability("<Name> kills <Name> for <resource> from <Name>", LotmText.t("lotmcraft.text.killing_contract", "Killing Contract"), TextColorHelper.DARK_RED, TextColorHelper.DARK_GREEN)
                .ability("<Name> binds life and death with <Name> for <resource> from <Name>", LotmText.t("lotmcraft.text.link_your_lives", "Link your lives"), TextColorHelper.DARK_RED, TextColorHelper.DARK_GREEN)
                .ability("... until <amount> days|minutes|seconds passes", LotmText.t("lotmcraft.text.optional_expiry_on_a_life_link", "Optional expiry on a life-link"), TextColorHelper.DARK_RED, TextColorHelper.DARK_GREEN)
                .ability("<Name> gives <Name> <amount> of digest for <resource>", LotmText.t("lotmcraft.text.trade_digestion_progress_for_a_50", "Trade digestion progress for a 50%% tax"), TextColorHelper.DARK_RED, TextColorHelper.DARK_GREEN)

                .spacing()
                .header(LotmText.t("lotmcraft.text.resources", "Resources"), TextColorHelper.DARK_RED)
                .line(LotmText.t("lotmcraft.text.an_item_resource_10_diamonds_1", "An item resource: '10 diamonds', '1 iron ingot'"), TextColorHelper.DARK_GREEN)
                .line(LotmText.t("lotmcraft.text.a_soul_must_name_its_owner", "A soul must name its owner: 'Alice's soul'"), TextColorHelper.DARK_GREEN)
                .line(LotmText.t("lotmcraft.text.souls_cannot_be_offered_without_identifying", "Souls cannot be offered without identifying their owner."), TextColorHelper.DARK_GREEN)

                .spacing()
                .header(LotmText.t("lotmcraft.text.how_souls_work", "How Souls Work"), TextColorHelper.DARK_RED)
                .line(LotmText.t("lotmcraft.text.a_soul_can_only_be_bound", "A soul can only be bound to one contract at a time."), TextColorHelper.DARK_GREEN)
                .line(LotmText.t("lotmcraft.text.once_bound_the_soul_remains_tied", "Once bound, the soul remains tied to the contract until fulfilled"), TextColorHelper.DARK_GREEN)
                .line(LotmText.t("lotmcraft.text.or_the_contract_is_voided_consuming", "or the contract is voided. Consuming a soul begins a drain on its owner."), TextColorHelper.DARK_GREEN)
                .line(LotmText.t("lotmcraft.text.the_soul_s_owner_does_not", "The soul's owner does not immediately die when their soul is consumed."), TextColorHelper.DARK_GREEN)
                .line(LotmText.t("lotmcraft.text.right_click_the_contract_item_to", "Right-click the Contract Item to perform an available soul action."), TextColorHelper.DARK_GREEN)

                .spacing()
                .header(LotmText.t("lotmcraft.text.completing_voiding", "Completing & Voiding"), TextColorHelper.DARK_RED)
                .line(LotmText.t("lotmcraft.text.item_terms_are_fulfilled_when_the", "Item terms are fulfilled when the required item is present with"), TextColorHelper.DARK_GREEN)
                .line(LotmText.t("lotmcraft.text.the_party_who_is_supposed_to", "the party who is supposed to receive it."), TextColorHelper.DARK_GREEN)
                .line(LotmText.t("lotmcraft.text.some_contracts_complete_automatically_once_all", "Some contracts complete automatically once all obligations are met."), TextColorHelper.DARK_GREEN)
                .line(LotmText.t("lotmcraft.text.a_sufficiently_powerful_player_may_forcibly", "A sufficiently powerful player may forcibly void a contract they do not own."), TextColorHelper.DARK_GREEN)
                .line(LotmText.t("lotmcraft.text.voiding_releases_pledged_souls_or_transfers", "Voiding releases pledged souls or transfers them to a pending"), TextColorHelper.DARK_GREEN)
                .line(LotmText.t("lotmcraft.text.release_contract_when_applicable", "release contract when applicable."), TextColorHelper.DARK_GREEN)

                .spacing()
                .separator()
                .spacing()
                .line(LotmText.t("lotmcraft.text.press_esc_to_close_this_menu", "Press ESC to close this menu"), TextColorHelper.DARK_GRAY)

                .build();
    }
}