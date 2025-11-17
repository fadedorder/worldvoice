package org.faded.worldvoice.quest.requirement;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Registry for quest requirement types
 */
public class QuestRequirementType {
    private static final Map<String, Function<JsonObject, QuestRequirement>> JSON_DESERIALIZERS = new HashMap<>();
    private static final Map<String, Function<FriendlyByteBuf, QuestRequirement>> NETWORK_DESERIALIZERS = new HashMap<>();

    public static void init() {

        JSON_DESERIALIZERS.put("ep", EPRequirement::fromJson);
        NETWORK_DESERIALIZERS.put("ep", EPRequirement::fromNetwork);


        JSON_DESERIALIZERS.put("race", RaceRequirement::fromJson);
        NETWORK_DESERIALIZERS.put("race", RaceRequirement::fromNetwork);


        JSON_DESERIALIZERS.put("skill", SkillRequirement::fromJson);
        NETWORK_DESERIALIZERS.put("skill", SkillRequirement::fromNetwork);


        JSON_DESERIALIZERS.put("awakening", AwakeningRequirement::fromJson);
        NETWORK_DESERIALIZERS.put("awakening", AwakeningRequirement::fromNetwork);


        JSON_DESERIALIZERS.put("quest_completed", CompletedQuestRequirement::fromJson);
        NETWORK_DESERIALIZERS.put("quest_completed", CompletedQuestRequirement::fromNetwork);
    }

    public static QuestRequirement fromJson(JsonObject json) {
        String type = json.get("type").getAsString();
        Function<JsonObject, QuestRequirement> deserializer = JSON_DESERIALIZERS.get(type);
        if (deserializer == null) {
            throw new IllegalArgumentException("Unknown requirement type: " + type);
        }
        return deserializer.apply(json);
    }

    public static QuestRequirement fromNetwork(FriendlyByteBuf buf) {
        String type = buf.readUtf();
        Function<FriendlyByteBuf, QuestRequirement> deserializer = NETWORK_DESERIALIZERS.get(type);
        if (deserializer == null) {
            throw new IllegalArgumentException("Unknown requirement type: " + type);
        }
        return deserializer.apply(buf);
    }

    public static void toNetwork(QuestRequirement requirement, FriendlyByteBuf buf) {
        buf.writeUtf(requirement.getType());
        requirement.toNetwork(buf);
    }
}
