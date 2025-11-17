package org.faded.worldvoice.ai;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import org.faded.worldvoice.integration.TensuraBridge;
import org.faded.worldvoice.service.SkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class WishActionExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(WishActionExecutor.class);

    private final SkillService skillService = new SkillService();

    public boolean execute(ServerPlayer player,
                           List<WishDecision.WishAction> actions,
                           boolean allowSkillGrants) {
        if (actions == null || actions.isEmpty()) {
            return false;
        }
        boolean applied = false;
        for (WishDecision.WishAction action : actions) {
            if (action == null || action.type() == null) continue;
            switch (action.type()) {
                case EP -> applied |= handleEp(player, action);
                case MAGICULES -> applied |= handleMagicules(player, action);
                case AURA -> applied |= handleAura(player, action);
                case SKILL -> applied |= handleSkill(player, action, allowSkillGrants);
                case EVOLUTION -> applied |= handleEvolution(player, action);
                case AWAKENING -> applied |= handleAwakening(player, action);
            }
        }
        return applied;
    }

    private boolean handleEp(ServerPlayer player, WishDecision.WishAction action) {
        return switch (action.operation()) {
            case ADD -> TensuraBridge.addEp(player, action.value());
            case SET -> TensuraBridge.setEp(player, action.value());
            default -> false;
        };
    }

    private boolean handleMagicules(ServerPlayer player, WishDecision.WishAction action) {
        return switch (action.operation()) {
            case ADD -> TensuraBridge.addMagicules(player, action.value());
            case SET -> TensuraBridge.setMagicules(player, action.value());
            default -> false;
        };
    }

    private boolean handleAura(ServerPlayer player, WishDecision.WishAction action) {
        return switch (action.operation()) {
            case ADD -> TensuraBridge.addAura(player, action.value());
            case SET -> TensuraBridge.setAura(player, action.value());
            default -> false;
        };
    }

    private boolean handleSkill(ServerPlayer player, WishDecision.WishAction action,
                                boolean allowGrants) {
        ResourceLocation id = action.targetId();
        if (id == null) return false;
        return switch (action.operation()) {
            case GRANT -> allowGrants && skillService.assign(player, id, false, true);
            case REVOKE -> skillService.revoke(player, id, "wish-action");
            default -> false;
        };
    }

    private boolean handleEvolution(ServerPlayer player, WishDecision.WishAction action) {
        ResourceLocation raceId = action.targetId();
        if (raceId == null) {
            LOGGER.debug("Wish evolution action missing target id.");
            return false;
        }
        return TensuraBridge.setRace(player, raceId, action.operation() != WishDecision.WishAction.ActionOperation.ADD);
    }

    private boolean handleAwakening(ServerPlayer player, WishDecision.WishAction action) {
        if (action.payload() == null || action.payload().isBlank()) {
            LOGGER.debug("Wish awakening action missing payload.");
            return false;
        }
        boolean flag = action.value() >= 0.5d || action.operation() == WishDecision.WishAction.ActionOperation.FORM;
        return TensuraBridge.setAwakening(player, action.payload(), flag);
    }
}
