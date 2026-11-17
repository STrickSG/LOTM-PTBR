package de.jakob.lotm.beyonders.abilities.visionary.prophecy;

import de.jakob.lotm.gui.custom.text_display.AbilityMenuBuilder;
import de.jakob.lotm.gui.custom.text_display.ColoredTextDisplayScreen;
import de.jakob.lotm.gui.custom.text_display.TextColorHelper;
import net.minecraft.client.gui.screens.Screen;
import de.jakob.lotm.util.LotmText;

public class VisionaryAbilityMenus {

    public static ColoredTextDisplayScreen createStoryWritingAbilityMenu(Screen previousScreen) {
        return new AbilityMenuBuilder(LotmText.t("lotmcraft.text.story_writing", "Story Writing"), previousScreen)
                .header(LotmText.t("lotmcraft.text.overview", "Overview"), TextColorHelper.GOLD)
                .line(LotmText.t("lotmcraft.text.write_prophecies_into_existence_type_natural", "Write prophecies into existence. Type natural language commands"), TextColorHelper.WHITE)
                .line(LotmText.t("lotmcraft.text.to_create_permanent_story_based_effects", "to create permanent story-based effects on targets."), TextColorHelper.WHITE)

                .spacing()
                .header(LotmText.t("lotmcraft.text.how_to_use", "How to Use"), TextColorHelper.GOLD)
                .line(LotmText.t("lotmcraft.text.1_activate_the_ability", "1. Activate the ability"), TextColorHelper.WHITE)
                .line(LotmText.t("lotmcraft.text.2_type_in_chat_if_player", "2. Type in chat: if <player_name> <trigger> then <action>"), TextColorHelper.WHITE)
                .line(LotmText.t("lotmcraft.text.3_the_prophecy_is_recorded_and", "3. The prophecy is recorded and stored on the target (you can use yourself as the target)"), TextColorHelper.WHITE)

                .spacing()
                .header(LotmText.t("lotmcraft.text.available_triggers", "Available Triggers"), TextColorHelper.GOLD)
                .ability("instant", LotmText.t("lotmcraft.text.activates_immediately_when_created", "Activates immediately when created"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("on <x y z>", LotmText.t("lotmcraft.text.triggers_when_player_reaches_coordinates", "Triggers when player reaches coordinates"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("has <item>", LotmText.t("lotmcraft.text.triggers_when_player_picks_up_item", "Triggers when player picks up item"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("health <amount>", LotmText.t("lotmcraft.text.triggers_when_player_health_drops_below_2", "Triggers when player health drops below amount"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("sanity <amount>", LotmText.t("lotmcraft.text.triggers_when_sanity_drops_below_amount_2", "Triggers when sanity drops below amount"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("player <name>", LotmText.t("lotmcraft.text.triggers_when_another_player_is_nearby", "Triggers when another player is nearby"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("sealed", LotmText.t("lotmcraft.text.triggers_when_sealed_artifact_is_used", "Triggers when sealed artifact is used"), TextColorHelper.GOLD, TextColorHelper.WHITE)

                .spacing()
                .header(LotmText.t("lotmcraft.text.available_actions", "Available Actions"), TextColorHelper.GOLD)
                .ability("health <number>", LotmText.t("lotmcraft.text.damage_negative_or_heal_positive_only", "Damage (negative) or heal (positive) (only for Seq 0)"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("sanity <number>", LotmText.t("lotmcraft.text.drain_or_restore_sanity_only_for", "Drain or restore sanity (only for Seq 0)"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("teleport <x y z>", LotmText.t("lotmcraft.text.teleport_target_to_coordinates_only_for", "Teleport target to coordinates (only for Seq 0)"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("drop <item>", LotmText.t("lotmcraft.text.force_target_to_drop_item", "Force target to drop item"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("digest <number>", LotmText.t("lotmcraft.text.modify_digestion", "Modify digestion"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("health <number>", LotmText.t("lotmcraft.text.apply_or_remove_health", "Apply or remove health"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("sanity <number>", LotmText.t("lotmcraft.text.drain_or_restore_sanity_only_for", "Drain or restore sanity (only for Seq 0)"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("stun", LotmText.t("lotmcraft.text.stun_target", "Stun target"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("skill <name>", LotmText.t("lotmcraft.text.trigger_skill_effect", "Trigger skill effect"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("confusion", LotmText.t("lotmcraft.text.confuse_target", "Confuse target"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("seal", LotmText.t("lotmcraft.text.seal_target", "Seal target"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("unseal", LotmText.t("lotmcraft.text.unseal_target", "Unseal target"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("calamity <type>", LotmText.t("lotmcraft.text.trigger_calamity", "Trigger calamity"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("spawn <entity>", LotmText.t("lotmcraft.text.spawn_entity_at_target_only_for", "Spawn entity at target (only for Seq 0)"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("say <message>", LotmText.t("lotmcraft.text.target_says_message_in_chat", "Target says message in chat"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("weather <type>", LotmText.t("lotmcraft.text.change_weather_rain_thunder_clear_only", "Change weather (rain/thunder/clear) (only for Seq 0)"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("time <value>", LotmText.t("lotmcraft.text.change_world_time_seq_1_only", "Change world time (Seq 1+) (only for Seq 0)"), TextColorHelper.GOLD, TextColorHelper.WHITE)

                .spacing()
                .header(LotmText.t("lotmcraft.text.examples", "Examples"), TextColorHelper.GOLD)
                .line("if Steve instant then health -15", TextColorHelper.CYAN)
                .line("if Alex on 100 64 200 then teleport 0 100 0", TextColorHelper.CYAN)
                .line("if Diana health 3 then sanity -5", TextColorHelper.CYAN)
                .line("if Bob has minecraft:golden_apple then say You picked up forbidden fruit", TextColorHelper.CYAN)
                .line("if Eve player Frank then confusion", TextColorHelper.CYAN)
                .line("if Charlie sealed then spawn zombie", TextColorHelper.CYAN)

                .spacing()
                .separator()
                .spacing()
                .line(LotmText.t("lotmcraft.text.press_esc_to_close_this_menu", "Press ESC to close this menu"), TextColorHelper.DARK_GRAY)

                .build();
    }

    public static ColoredTextDisplayScreen createPsychologicalCueAbilityMenu(Screen previousScreen) {
        return new AbilityMenuBuilder(LotmText.t("lotmcraft.text.psychological_cue", "Psychological Cue"), previousScreen)
                .header(LotmText.t("lotmcraft.text.overview", "Overview"), TextColorHelper.GOLD)
                .line(LotmText.t("lotmcraft.text.plant_psychological_cues_in_nearby_players", "Plant psychological cues in nearby players' minds."), TextColorHelper.WHITE)
                .line(LotmText.t("lotmcraft.text.create_immediate_mental_effects_on_targets", "Create immediate mental effects on targets within your range. (or yourself)"), TextColorHelper.WHITE)

                .spacing()
                .header(LotmText.t("lotmcraft.text.how_to_use", "How to Use"), TextColorHelper.GOLD)
                .line(LotmText.t("lotmcraft.text.1_activate_the_ability", "1. Activate the ability"), TextColorHelper.WHITE)
                .line(LotmText.t("lotmcraft.text.2_type_in_chat_if_player", "2. Type in chat: if <player_name> <trigger> then <action>"), TextColorHelper.WHITE)
                .line(LotmText.t("lotmcraft.text.3_target_must_be_within_range", "3. Target must be within range or cue fails"), TextColorHelper.WHITE)
                .line(LotmText.t("lotmcraft.text.4_cannot_cue_significantly_stronger_targets", "4. Cannot cue significantly stronger targets"), TextColorHelper.WHITE)

                .spacing()
                .header(LotmText.t("lotmcraft.text.available_triggers", "Available Triggers"), TextColorHelper.GOLD)
                .ability("instant", LotmText.t("lotmcraft.text.activates_immediately_when_created_only_at", "Activates immediately when created (Only at Sequence 4 unlocked)"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("on <x y z>", LotmText.t("lotmcraft.text.triggers_when_player_reaches_coordinates", "Triggers when player reaches coordinates"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("has <item>", LotmText.t("lotmcraft.text.triggers_when_player_picks_up_item", "Triggers when player picks up item"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("health <amount>", LotmText.t("lotmcraft.text.triggers_when_player_health_drops_below", "Triggers when player health drops below amount (Only at Sequence 4 unlocked)"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("sanity <amount>", LotmText.t("lotmcraft.text.triggers_when_sanity_drops_below_amount", "Triggers when sanity drops below amount (Only at Sequence 4 unlocked)"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("player <name>", LotmText.t("lotmcraft.text.triggers_when_another_player_is_nearby_2", "Triggers when another player is nearby (Only at Sequence 6 unlocked)"), TextColorHelper.GOLD, TextColorHelper.WHITE)

                .spacing()
                .header(LotmText.t("lotmcraft.text.available_actions", "Available Actions"), TextColorHelper.GOLD)
                .ability("drop", LotmText.t("lotmcraft.text.target_drops_item", "Target drops item"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("stun", LotmText.t("lotmcraft.text.stuns_target_only_at_sequence_4", "Stuns target (Only at Sequence 4 unlocked)"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("confusion", LotmText.t("lotmcraft.text.confuses_target_only_at_sequence_4", "Confuses target (Only at Sequence 4 unlocked)"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("skill <skill>", LotmText.t("lotmcraft.text.makes_target_use_skill_only_at", "Makes target use skill (Only at Sequence 4 unlocked)"), TextColorHelper.GOLD, TextColorHelper.WHITE)
                .ability("say <message>", LotmText.t("lotmcraft.text.makes_target_say_something_in_chat", "Makes target say something in chat (Only at Sequence 4 unlocked)"), TextColorHelper.GOLD, TextColorHelper.WHITE)

                .spacing()
                .header(LotmText.t("lotmcraft.text.examples", "Examples"), TextColorHelper.GOLD)
                .line("if Steve on 0 64 0 then drop", TextColorHelper.CYAN)

                .spacing()
                .warning(LotmText.t("lotmcraft.text.target_must_be_within_distance_limit", "Target must be within distance limit or cue fails!"))
                .warning(LotmText.t("lotmcraft.text.attempting_to_cue_stronger_targets_triggers", "Attempting to cue stronger targets triggers 'Losing Control' debuff (25 seconds)"))

                .spacing()
                .separator()
                .spacing()
                .line(LotmText.t("lotmcraft.text.press_esc_to_close_this_menu", "Press ESC to close this menu"), TextColorHelper.DARK_GRAY)

                .build();
    }
}