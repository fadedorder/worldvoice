package org.faded.worldvoice.ai;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Set;

public final class WishBacklashCalculator {
    private static final Set<String> HIGH_INTENSITY_KEYWORDS = Set.of("power", "ultimate", "world", "army", "demon", "hero", "god");

    private WishBacklashCalculator() {}

    public static WishOutcome evaluate(ServerPlayer player, String wishText, double effectiveStrictness, double favorScore) {
        String normalized = wishText.toLowerCase(Locale.ROOT);
        double intensity = Math.min(1.0d, normalized.length() / 160.0d);
        for (String keyword : HIGH_INTENSITY_KEYWORDS) {
            if (normalized.contains(keyword)) {
                intensity = Math.min(1.0d, intensity + 0.2d);
            }
        }
        double favorClamped = Math.max(0.0d, Math.min(10.0d, favorScore));
        double strictness = Math.max(0.0d, Math.min(1.0d, effectiveStrictness));
        double leniency = 1.0d - strictness;
        double favorBoost = favorClamped * 0.03d;
        double grantProbability = Math.max(0.0d, Math.min(1.0d,
                (0.25d + leniency * 0.5d + favorBoost) - (intensity * (0.35d + strictness * 0.4d))));
        double backlash = Math.min(1.0d, intensity * (0.4d + strictness * 0.6d));
        return new WishOutcome(player, normalized, intensity, grantProbability, backlash);
    }

    public record WishOutcome(ServerPlayer player, String normalizedWish, double intensity,
                              double grantProbability, double backlashStrength) {

        public Component describe() {
            return Component.literal("The world hums with judgment (intensity "
                    + Math.round(intensity * 100) + "%, backlash "
                    + Math.round(backlashStrength * 100) + "%).").withStyle(ChatFormatting.DARK_PURPLE);
        }
    }
}
