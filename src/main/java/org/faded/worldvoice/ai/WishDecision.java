package org.faded.worldvoice.ai;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.List;

public record WishDecision(
        boolean granted,
        GrantType grantType,
        ResourceLocation skillId,
        ResourceLocation itemId,
        int itemCount,
        int boonSeconds,
        double backlashStrength,
        String narrative,
        String backlashNarrative,
        List<String> grantCommands,
        List<String> penaltyCommands,
        List<WishAction> actions
) {

    public static WishDecision deny(double backlash, String rationale, String backlashText) {
        return new WishDecision(false, GrantType.NONE, null, null, 0, 0, backlash,
                rationale, backlashText, List.of(), List.of(), List.of());
    }

    public static WishDecision boon(int seconds, double backlash, String rationale) {
        return new WishDecision(true, GrantType.BOON, null, null, 0, seconds, backlash,
                rationale, "", List.of(), List.of(), List.of());
    }

    public WishDecision withGrantOverride(boolean grant) {
        GrantType targetType = grant ? (grantType == GrantType.NONE ? GrantType.BOON : grantType) : GrantType.NONE;
        int seconds = boonSeconds > 0 ? boonSeconds : 60;
        return new WishDecision(grant, targetType, skillId, itemId,
                itemCount, seconds, backlashStrength, narrative, backlashNarrative, grantCommands, penaltyCommands, actions);
    }

    public List<String> grantCommands() {
        return grantCommands == null ? Collections.emptyList() : grantCommands;
    }

    public List<String> penaltyCommands() {
        return penaltyCommands == null ? Collections.emptyList() : penaltyCommands;
    }

    public List<WishAction> actions() {
        return actions == null ? List.of() : actions;
    }

    public enum GrantType {
        SKILL,
        ITEM,
        BOON,
        NONE
    }

    public record WishAction(ActionType type,
                             ActionOperation operation,
                             double value,
                             ResourceLocation targetId,
                             String payload) {

        public WishAction {
            value = Double.isNaN(value) ? 0.0d : value;
        }

        public enum ActionType {
            EP,
            MAGICULES,
            AURA,
            SKILL,
            EVOLUTION,
            AWAKENING
        }

        public enum ActionOperation {
            ADD,
            SET,
            GRANT,
            REVOKE,
            FORM,
            TOGGLE
        }
    }
}
