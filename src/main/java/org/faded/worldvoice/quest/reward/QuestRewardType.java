package org.faded.worldvoice.quest.reward;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;


public class QuestRewardType<T extends QuestReward> {
    private static final Map<ResourceLocation, QuestRewardType<?>> REGISTRY = new HashMap<>();


    public static final QuestRewardType<ItemReward> ITEM =
            register("item", ItemReward::fromJson, ItemReward::fromNetwork);

    public static final QuestRewardType<CommandReward> COMMAND =
            register("command", CommandReward::fromJson, CommandReward::fromNetwork);

    public static final QuestRewardType<SkillReward> SKILL =
            register("skill", SkillReward::fromJson, SkillReward::fromNetwork);

    public static final QuestRewardType<EPLevelsReward> EP_LEVELS =
            register("ep_levels", EPLevelsReward::fromJson, EPLevelsReward::fromNetwork);

    public static final QuestRewardType<MaxMagiculesReward> MAX_MAGICULES =
            register("max_magicules", MaxMagiculesReward::fromJson, MaxMagiculesReward::fromNetwork);

    public static final QuestRewardType<MaxAuraReward> MAX_AURA =
            register("max_aura", MaxAuraReward::fromJson, MaxAuraReward::fromNetwork);

    public static final QuestRewardType<SoulPointsReward> SOUL_POINTS =
            register("soul_points", SoulPointsReward::fromJson, SoulPointsReward::fromNetwork);

    public static final QuestRewardType<ResetCounterReward> RESET_COUNTER =
            register("reset_counter", ResetCounterReward::fromJson, ResetCounterReward::fromNetwork);

    public static final QuestRewardType<ExperienceReward> EXPERIENCE =
            register("experience", ExperienceReward::fromJson, ExperienceReward::fromNetwork);

    private final ResourceLocation id;
    private final JsonDeserializer<T> jsonDeserializer;
    private final NetworkDeserializer<T> networkDeserializer;

    private QuestRewardType(ResourceLocation id, JsonDeserializer<T> jsonDeserializer,
                            NetworkDeserializer<T> networkDeserializer) {
        this.id = id;
        this.jsonDeserializer = jsonDeserializer;
        this.networkDeserializer = networkDeserializer;
    }

    public ResourceLocation getId() {
        return id;
    }

    public T fromJson(JsonObject json) {
        return jsonDeserializer.deserialize(json);
    }

    public T fromNetwork(FriendlyByteBuf buf) {
        return networkDeserializer.deserialize(buf);
    }

    public static <T extends QuestReward> QuestRewardType<T> register(
            String name, JsonDeserializer<T> jsonDeserializer, NetworkDeserializer<T> networkDeserializer) {
        ResourceLocation id = new ResourceLocation("arcclient", name);
        QuestRewardType<T> type = new QuestRewardType<>(id, jsonDeserializer, networkDeserializer);
        REGISTRY.put(id, type);
        return type;
    }

    public static QuestRewardType<?> getType(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static void init() {

    }

    @FunctionalInterface
    public interface JsonDeserializer<T extends QuestReward> {
        T deserialize(JsonObject json);
    }

    @FunctionalInterface
    public interface NetworkDeserializer<T extends QuestReward> {
        T deserialize(FriendlyByteBuf buf);
    }
}
