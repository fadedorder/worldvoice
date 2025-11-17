package org.faded.worldvoice.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mob dictionary describing AI-relevant metadata for entities.
 */
public final class MobDictionary {
    private static final Logger LOGGER = LoggerFactory.getLogger(MobDictionary.class);
    private static final Gson GSON = new Gson();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private final Map<ResourceLocation, MobMeta> entries = new LinkedHashMap<>();
    private long lastLoadedMs;

    public Optional<MobMeta> lookup(ResourceLocation id) {
        return Optional.ofNullable(entries.get(id));
    }

    public Collection<MobMeta> all() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public boolean contains(ResourceLocation id) {
        return entries.containsKey(id);
    }

    public boolean addEntry(MobMeta meta) {
        if (meta == null || meta.id() == null) return false;
        return entries.putIfAbsent(meta.id(), meta) == null;
    }

    public void write(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (MobMeta meta : entries.values()) {
            array.add(encodeMeta(meta));
        }
        root.add("mobs", array);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            PRETTY.toJson(root, writer);
        }
        LOGGER.info("Wrote {} mob dictionary entries to {}.", entries.size(), path);
    }

    public long getLastLoadedMs() {
        return lastLoadedMs;
    }

    public static MobDictionary load(Path customPath, InputStream fallbackStream) {
        MobDictionary dict = new MobDictionary();
        boolean loaded = false;
        if (customPath != null && Files.exists(customPath)) {
            loaded = dict.tryLoad(customPath);
        }
        if (!loaded && fallbackStream != null) {
            loaded = dict.tryLoad(fallbackStream);
        }
        if (!loaded) {
            LOGGER.warn("Falling back to embedded mob dictionary; custom file missing or invalid.");
        }
        return dict;
    }

    private boolean tryLoad(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(reader);
        } catch (IOException ex) {
            LOGGER.warn("Failed to load mob dictionary from {}: {}", path, ex.toString());
            return false;
        }
    }

    private boolean tryLoad(InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return parse(reader);
        } catch (IOException ex) {
            LOGGER.warn("Failed to load embedded mob dictionary: {}", ex.toString());
            return false;
        }
    }

    private boolean parse(BufferedReader reader) throws IOException {
        JsonReader jsonReader = new JsonReader(reader);
        jsonReader.setLenient(true);
        JsonObject root;
        try {
            root = GSON.fromJson(jsonReader, JsonObject.class);
        } catch (JsonParseException ex) {
            LOGGER.error("Failed to parse mob dictionary JSON: {}", ex.getMessage());
            LOGGER.debug("Mob dictionary parse exception", ex);
            return false;
        }
        if (root == null || !root.has("mobs")) return false;
        entries.clear();
        JsonElement mobsElement = root.get("mobs");
        if (mobsElement.isJsonArray()) {
            parseMobsArray(mobsElement.getAsJsonArray());
        }
        lastLoadedMs = System.currentTimeMillis();
        LOGGER.info("Loaded {} mob dictionary entries.", entries.size());
        return true;
    }

    private void parseMobsArray(JsonArray arr) {
        for (JsonElement element : arr) {
            if (!element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();
            addEntry(obj);
        }
    }

    private void addEntry(JsonObject obj) {
        ResourceLocation id = parseResourceLocation(obj, "id");
        if (id == null) return;
        String name = optString(obj, "name", ItemDictionary.prettifyId(id));
        String mod = optString(obj, "mod", id.getNamespace());
        String category = optString(obj, "category", "");
        double difficulty = optDouble(obj, "difficulty", 1.0d);
        List<String> tags = parseStringArray(obj.get("tags"));
        String notes = optString(obj, "notes", "");
        MobMeta meta = new MobMeta(id, name, mod, category, difficulty, tags, notes);
        entries.put(id, meta);
    }

    private JsonObject encodeMeta(MobMeta meta) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", meta.id().toString());
        obj.addProperty("name", meta.name());
        if (!meta.mod().isBlank()) obj.addProperty("mod", meta.mod());
        if (!meta.category().isBlank()) obj.addProperty("category", meta.category());
        obj.addProperty("difficulty", meta.difficulty());
        if (!meta.tags().isEmpty()) {
            JsonArray tags = new JsonArray();
            meta.tags().forEach(tags::add);
            obj.add("tags", tags);
        }
        if (!meta.notes().isBlank()) obj.addProperty("notes", meta.notes());
        return obj;
    }

    private static ResourceLocation parseResourceLocation(JsonObject obj, String key) {
        if (!obj.has(key)) return null;
        try {
            String raw = obj.get(key).getAsString();
            return raw == null ? null : ResourceLocation.tryParse(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String optString(JsonObject obj, String key, String def) {
        try {
            return obj.has(key) ? obj.get(key).getAsString() : def;
        } catch (Exception ex) {
            return def;
        }
    }

    private static double optDouble(JsonObject obj, String key, double def) {
        try {
            return obj.has(key) ? obj.get(key).getAsDouble() : def;
        } catch (Exception ex) {
            return def;
        }
    }

    private static List<String> parseStringArray(JsonElement element) {
        if (element == null || !element.isJsonArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonElement e : element.getAsJsonArray()) {
            if (e.isJsonPrimitive()) {
                String value = e.getAsString();
                if (value != null && !value.isBlank()) {
                    out.add(value.trim());
                }
            }
        }
        return out;
    }

    public static MobMeta autoDescribe(EntityType<?> type) {
        if (type == null) return null;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (id == null) return null;
        String name = ItemDictionary.prettifyId(id);
        String mod = id.getNamespace();
        String category = type.getCategory().getName();
        double difficulty = inferDifficulty(type.getCategory(), type);
        List<String> tags = collectTags(type);
        return new MobMeta(id, name, mod, category, difficulty, tags, "");
    }

    private static double inferDifficulty(MobCategory category, EntityType<?> type) {
        double base = switch (category) {
            case MONSTER -> 3.0d;
            case WATER_CREATURE, WATER_AMBIENT -> 1.5d;
            case CREATURE -> 1.0d;
            case AMBIENT -> 0.5d;
            case MISC -> 1.0d;
            default -> 1.0d;
        };
        if (type.fireImmune()) base += 0.5d;
        if (type.canSpawnFarFromPlayer()) base += 0.25d;
        return Math.max(0.1d, base);
    }

    private static List<String> collectTags(EntityType<?> type) {
        List<String> tags = new ArrayList<>();
        if (type.fireImmune()) tags.add("fire_immune");
        if (!type.canSpawnFarFromPlayer()) tags.add("close_range");
        if (type.getCategory() == MobCategory.MONSTER) tags.add("hostile");
        if (!type.canSummon()) tags.add("natural_only");
        return tags.isEmpty() ? List.of() : List.copyOf(tags);
    }

    public record MobMeta(
            ResourceLocation id,
            String name,
            String mod,
            String category,
            double difficulty,
            List<String> tags,
            String notes
    ) { }
}
