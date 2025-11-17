package org.faded.worldvoice.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import net.minecraft.resources.ResourceLocation;
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

public final class SkillDictionary {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillDictionary.class);
    private static final Gson GSON = new Gson();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private final Map<ResourceLocation, SkillMeta> entries = new LinkedHashMap<>();
    private long lastLoadedMs;

    public Optional<SkillMeta> lookup(ResourceLocation id) {
        return Optional.ofNullable(entries.get(id));
    }

    public Collection<SkillMeta> all() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public boolean contains(ResourceLocation id) {
        return entries.containsKey(id);
    }

    public boolean addEntry(SkillMeta meta) {
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
        for (SkillMeta meta : entries.values()) {
            array.add(encodeMeta(meta));
        }
        root.add("skills", array);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            PRETTY.toJson(root, writer);
        }
        LOGGER.info("Wrote {} skill dictionary entries to {}.", entries.size(), path);
    }

    public static SkillDictionary load(Path customPath, InputStream defaultStreamSupplier) {
        SkillDictionary dict = new SkillDictionary();
        boolean loaded = false;
        if (customPath != null && Files.exists(customPath)) {
            loaded = dict.tryLoad(customPath);
        }
        if (!loaded && defaultStreamSupplier != null) {
            loaded = dict.tryLoad(defaultStreamSupplier);
        }
        if (!loaded) {
            LOGGER.warn("Falling back to built-in Yggdrasil dictionary; custom file missing or invalid.");
        }
        return dict;
    }

    private boolean tryLoad(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(reader);
        } catch (IOException ex) {
            LOGGER.warn("Failed to load Yggdrasil dictionary from {}: {}", path, ex.toString());
            return false;
        }
    }

    private boolean tryLoad(InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return parse(reader);
        } catch (IOException ex) {
            LOGGER.warn("Failed to load embedded Yggdrasil dictionary: {}", ex.toString());
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
            LOGGER.error("Failed to parse Yggdrasil skill dictionary: {}", ex.getMessage());
            LOGGER.debug("Skill dictionary parse exception", ex);
            return false;
        }
        if (root == null || !root.has("skills")) return false;
        entries.clear();

        JsonElement skillsRoot = root.get("skills");
        if (skillsRoot.isJsonArray()) {
            parseArray(skillsRoot.getAsJsonArray());
        } else if (skillsRoot.isJsonObject()) {
            JsonObject skillsObj = skillsRoot.getAsJsonObject();
            if (skillsObj.has("unique") && skillsObj.get("unique").isJsonArray()) {
                parseArray(skillsObj.getAsJsonArray("unique"));
            }

        }
        lastLoadedMs = System.currentTimeMillis();
        LOGGER.info("Loaded {} Yggdrasil skill dictionary entries.", entries.size());
        return true;
    }

    private void parseArray(JsonArray arr) {
        for (JsonElement element : arr) {
            if (!element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();
            addEntry(obj);
        }
    }

    private void addEntry(JsonObject obj) {
        String rawId = optString(obj, "id", null);
        if (rawId == null) return;
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) return;

        String name = optString(obj, "name", prettifyId(id));
        int tier = clampInt(optInt(obj, "tier", 3), 1, 5);
        String notes = optString(obj, "notes", "");
        List<String> tags = parseStringArray(obj.get("tags"));
        ResourceLocation evolution = parseResourceLocation(obj, "evolution");
        List<ResourceLocation> combines = parseResourceLocationArray(obj.get("combines"));

        SkillMeta meta = new SkillMeta(id, name, tier, notes, tags, evolution, combines);
        entries.put(id, meta);
    }

    private JsonObject encodeMeta(SkillMeta meta) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", meta.id().toString());
        obj.addProperty("name", meta.name());
        obj.addProperty("tier", meta.tier());
        if (!meta.notes().isBlank()) obj.addProperty("notes", meta.notes());
        if (!meta.tags().isEmpty()) {
            JsonArray tags = new JsonArray();
            meta.tags().forEach(tags::add);
            obj.add("tags", tags);
        }
        if (meta.evolution() != null) {
            obj.addProperty("evolution", meta.evolution().toString());
        }
        if (!meta.combines().isEmpty()) {
            JsonArray combines = new JsonArray();
            meta.combines().forEach(id -> combines.add(id.toString()));
            obj.add("combines", combines);
        }
        return obj;
    }

    private static String optString(JsonObject obj, String key, String def) {
        try { return obj.has(key) ? obj.get(key).getAsString() : def; } catch (Exception e) { return def; }
    }

    private static int optInt(JsonObject obj, String key, int def) {
        try { return obj.has(key) ? obj.get(key).getAsInt() : def; } catch (Exception e) { return def; }
    }

    private static ResourceLocation parseResourceLocation(JsonObject obj, String key) {
        if (!obj.has(key)) return null;
        try {
            String s = obj.get(key).getAsString();
            return s == null ? null : ResourceLocation.tryParse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> parseStringArray(JsonElement el) {
        if (el == null || !el.isJsonArray()) return List.of();
        List<String> list = new ArrayList<>();
        for (JsonElement e : el.getAsJsonArray()) {
            if (e.isJsonPrimitive()) {
                String v = e.getAsString();
                if (v != null && !v.isBlank()) list.add(v.trim());
            }
        }
        return list;
    }

    private static List<ResourceLocation> parseResourceLocationArray(JsonElement el) {
        if (el == null || !el.isJsonArray()) return List.of();
        List<ResourceLocation> list = new ArrayList<>();
        for (JsonElement e : el.getAsJsonArray()) {
            if (e.isJsonPrimitive()) {
                ResourceLocation rl = ResourceLocation.tryParse(e.getAsString());
                if (rl != null) list.add(rl);
            }
        }
        return list;
    }

    public long getLastLoadedMs() {
        return lastLoadedMs;
    }

    public static String prettifyId(ResourceLocation id) {
        String path = id.getPath();
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1));
        }
        return sb.toString();
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public record SkillMeta(
            ResourceLocation id,
            String name,
            int tier,
            String notes,
            List<String> tags,
            ResourceLocation evolution,
            List<ResourceLocation> combines
    ) { }
}
