package org.faded.worldvoice.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SkillConfigDefaults {

    public static final int DEFAULT_CAPACITY = 1;

    private static final List<String> UNIQUE = List.of(
            "tensura:absolute_severance=" + DEFAULT_CAPACITY,
            "tensura:berserk=" + DEFAULT_CAPACITY,
            "tensura:berserker=" + DEFAULT_CAPACITY,
            "tensura:bewilder=" + DEFAULT_CAPACITY,
            "tensura:chef=" + DEFAULT_CAPACITY,
            "tensura:chosen_one=" + DEFAULT_CAPACITY,
            "tensura:commander=" + DEFAULT_CAPACITY,
            "tensura:cook=" + DEFAULT_CAPACITY,
            "tensura:creator=" + DEFAULT_CAPACITY,
            "tensura:degenerate=" + DEFAULT_CAPACITY,
            "tensura:engorger=" + DEFAULT_CAPACITY,
            "tensura:envy=" + DEFAULT_CAPACITY,
            "tensura:falsifier=" + DEFAULT_CAPACITY,
            "tensura:fighter=" + DEFAULT_CAPACITY,
            "tensura:fusionist=" + DEFAULT_CAPACITY,
            "tensura:gourmand=" + DEFAULT_CAPACITY,
            "tensura:gourmet=" + DEFAULT_CAPACITY,
            "tensura:great_sage=" + DEFAULT_CAPACITY,
            "tensura:greed=" + DEFAULT_CAPACITY,
            "tensura:guardian=" + DEFAULT_CAPACITY,
            "tensura:healer=" + DEFAULT_CAPACITY,
            "tensura:infinity_prison=" + DEFAULT_CAPACITY,
            "tensura:lust=" + DEFAULT_CAPACITY,
            "tensura:martial_master=" + DEFAULT_CAPACITY,
            "tensura:mathematician=" + DEFAULT_CAPACITY,
            "tensura:merciless=" + DEFAULT_CAPACITY,
            "tensura:murderer=" + DEFAULT_CAPACITY,
            "tensura:musician=" + DEFAULT_CAPACITY,
            "tensura:observer=" + DEFAULT_CAPACITY,
            "tensura:oppressor=" + DEFAULT_CAPACITY,
            "tensura:predator=" + DEFAULT_CAPACITY,
            "tensura:pride=" + DEFAULT_CAPACITY,
            "tensura:reflector=" + DEFAULT_CAPACITY,
            "tensura:researcher=" + DEFAULT_CAPACITY,
            "tensura:royal_beast=" + DEFAULT_CAPACITY,
            "tensura:reaper=" + DEFAULT_CAPACITY,
            "tensura:reverser=" + DEFAULT_CAPACITY,
            "tensura:seer=" + DEFAULT_CAPACITY,
            "tensura:severer=" + DEFAULT_CAPACITY,
            "tensura:shadow_striker=" + DEFAULT_CAPACITY,
            "tensura:sloth=" + DEFAULT_CAPACITY,
            "tensura:sniper=" + DEFAULT_CAPACITY,
            "tensura:spearhead=" + DEFAULT_CAPACITY,
            "tensura:suppressor=" + DEFAULT_CAPACITY,
            "tensura:survivor=" + DEFAULT_CAPACITY,
            "tensura:traveler=" + DEFAULT_CAPACITY,
            "tensura:thrower=" + DEFAULT_CAPACITY,
            "tensura:tuner=" + DEFAULT_CAPACITY,
            "tensura:unyielding=" + DEFAULT_CAPACITY,
            "tensura:usurper=" + DEFAULT_CAPACITY,
            "tensura:villain=" + DEFAULT_CAPACITY,
            "tensura:wrath=" + DEFAULT_CAPACITY
    );

    private SkillConfigDefaults() {
    }

    public static List<? extends String> uniqueDefaults() {
        return new ArrayList<>(UNIQUE);
    }

    public static List<? extends String> ultimateDefaults() {
        return new ArrayList<>();
    }

    public static List<? extends String> manualDefaults() {
        return new ArrayList<>();
    }
}
