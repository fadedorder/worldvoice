package org.faded.worldvoice.quest.objective;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for quest objective types
 */
public class QuestObjectiveType<T extends QuestObjective> {
    private static final Map<ResourceLocation, QuestObjectiveType<?>> REGISTRY = new HashMap<>();


    public static final QuestObjectiveType<KillMobObjective> KILL_MOB =
            register("kill_mob", KillMobObjective::fromJson, KillMobObjective::fromNetwork);

    public static final QuestObjectiveType<CollectItemObjective> COLLECT_ITEM =
            register("collect_item", CollectItemObjective::fromJson, CollectItemObjective::fromNetwork);

    public static final QuestObjectiveType<EPRequirement> EP_REQUIREMENT =
            register("ep_requirement", EPRequirement::fromJson, EPRequirement::fromNetwork);

    public static final QuestObjectiveType<EPGainRequirement> EP_GAIN_REQUIREMENT =
            register("ep_gain_requirement", EPGainRequirement::fromJson, EPGainRequirement::fromNetwork);

    public static final QuestObjectiveType<StatRequirement> MAX_AURA_REQUIREMENT =
            register("max_aura_requirement", json -> StatRequirement.fromJson(json, "max_aura"),
                    buf -> StatRequirement.fromNetwork(buf, "max_aura"));

    public static final QuestObjectiveType<StatRequirement> MAX_MP_REQUIREMENT =
            register("max_mp_requirement", json -> StatRequirement.fromJson(json, "max_mp"),
                    buf -> StatRequirement.fromNetwork(buf, "max_mp"));

    public static final QuestObjectiveType<SkillRequirement> SKILL_REQUIREMENT =
            register("skill_requirement", SkillRequirement::fromJson, SkillRequirement::fromNetwork);

    public static final QuestObjectiveType<SkillMasteryRequirement> SKILL_MASTERY_REQUIREMENT =
            register("skill_mastery_requirement", SkillMasteryRequirement::fromJson, SkillMasteryRequirement::fromNetwork);

    private final ResourceLocation id;
    private final JsonDeserializer<T> jsonDeserializer;
    private final NetworkDeserializer<T> networkDeserializer;

    private QuestObjectiveType(ResourceLocation id, JsonDeserializer<T> jsonDeserializer,
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

    public static <T extends QuestObjective> QuestObjectiveType<T> register(
            String name, JsonDeserializer<T> jsonDeserializer, NetworkDeserializer<T> networkDeserializer) {
        ResourceLocation id = new ResourceLocation("arcclient", name);
        QuestObjectiveType<T> type = new QuestObjectiveType<>(id, jsonDeserializer, networkDeserializer);
        REGISTRY.put(id, type);
        return type;
    }

    public static QuestObjectiveType<?> getType(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static void init() {

    }

    @FunctionalInterface
    public interface JsonDeserializer<T extends QuestObjective> {
        T deserialize(JsonObject json);
    }

    @FunctionalInterface
    public interface NetworkDeserializer<T extends QuestObjective> {
        T deserialize(FriendlyByteBuf buf);
    }
}
