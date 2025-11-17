package org.faded.worldvoice.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Item dictionary mirroring the skill dictionary pattern.
 * Allows JSON5-driven metadata describing vanilla + modded items for AI prompts.
 */
public final class ItemDictionary {
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemDictionary.class);
    private static final Gson GSON = new Gson();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private final Map<ResourceLocation, ItemMeta> entries = new LinkedHashMap<>();
    private long lastLoadedMs;

    public Optional<ItemMeta> lookup(ResourceLocation id) {
        return Optional.ofNullable(entries.get(id));
    }

    public Collection<ItemMeta> all() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public boolean contains(ResourceLocation id) {
        return entries.containsKey(id);
    }

    public boolean addEntry(ItemMeta meta) {
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
        for (ItemMeta meta : entries.values()) {
            array.add(encodeMeta(meta));
        }
        root.add("items", array);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            PRETTY.toJson(root, writer);
        }
        LOGGER.info("Wrote {} item dictionary entries to {}.", entries.size(), path);
    }

    public long getLastLoadedMs() {
        return lastLoadedMs;
    }

    public static ItemDictionary load(Path customPath, InputStream fallbackStream) {
        ItemDictionary dict = new ItemDictionary();
        boolean loaded = false;
        if (customPath != null && Files.exists(customPath)) {
            LOGGER.info("Item dictionary: Custom file found at {}, attempting to load...", customPath.toAbsolutePath());
            loaded = dict.tryLoad(customPath);
            if (loaded) {
                LOGGER.info("Item dictionary: Successfully loaded from custom file");
            }
        } else if (customPath != null) {
            LOGGER.warn("Item dictionary: Custom file does NOT exist at {}", customPath.toAbsolutePath());
        }
        if (!loaded && fallbackStream != null) {
            LOGGER.warn("Item dictionary: Loading from embedded fallback resource (JAR file)");
            loaded = dict.tryLoad(fallbackStream);
        }
        if (!loaded) {
            LOGGER.error("Item dictionary: Failed to load from both custom path and fallback! Dictionary will be empty.");
        }
        return dict;
    }

    private boolean tryLoad(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(reader);
        } catch (IOException ex) {
            LOGGER.warn("Failed to load item dictionary from {}: {}", path, ex.toString());
            return false;
        }
    }

    private boolean tryLoad(InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return parse(reader);
        } catch (IOException ex) {
            LOGGER.warn("Failed to load embedded item dictionary: {}", ex.toString());
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
            LOGGER.error("Failed to parse item dictionary JSON: {}", ex.getMessage());
            LOGGER.debug("Item dictionary parse exception", ex);
            return false;
        }
        if (root == null || !root.has("items")) return false;
        entries.clear();
        JsonElement itemsElement = root.get("items");
        if (itemsElement.isJsonArray()) {
            parseItemsArray(itemsElement.getAsJsonArray());
        }
        lastLoadedMs = System.currentTimeMillis();
        LOGGER.info("Loaded {} item dictionary entries.", entries.size());
        return true;
    }

    private void parseItemsArray(JsonArray arr) {
        for (JsonElement element : arr) {
            if (!element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();
            addEntry(obj);
        }
    }

    private void addEntry(JsonObject obj) {
        ResourceLocation id = parseResourceLocation(obj, "id");
        if (id == null) return;
        String name = optString(obj, "name", prettifyId(id));
        String mod = optString(obj, "mod", id.getNamespace());
        String role = optString(obj, "role", "");
        String rarity = optString(obj, "rarity", "");
        List<String> tags = parseStringArray(obj.get("tags"));
        String notes = optString(obj, "notes", "");
        ItemMeta meta = new ItemMeta(id, name, mod, role, rarity, tags, notes);
        entries.put(id, meta);
    }

    private JsonObject encodeMeta(ItemMeta meta) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", meta.id().toString());
        obj.addProperty("name", meta.name());
        if (!meta.mod().isBlank()) obj.addProperty("mod", meta.mod());
        if (!meta.role().isBlank()) obj.addProperty("role", meta.role());
        if (!meta.rarity().isBlank()) obj.addProperty("rarity", meta.rarity());
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

    public static String prettifyId(ResourceLocation id) {
        String path = id.getPath();
        String[] tokens = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            if (token.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(token.charAt(0)));
            if (token.length() > 1) {
                sb.append(token.substring(1));
            }
        }
        return sb.toString();
    }

    public static ItemMeta autoDescribe(Item item) {
        if (item == null) return null;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) return null;
        String name = prettifyId(id);
        String mod = id.getNamespace();
        String role = inferRole(item, id);
        String rarity = inferRarity(item);
        List<String> tags = collectTags(item);
        return new ItemMeta(id, name, mod, role, rarity, tags, "");
    }

    private static String inferRole(Item item, ResourceLocation id) {
        if (item instanceof SwordItem || item instanceof TridentItem || item instanceof BowItem) return "weapon";
        if (item instanceof PickaxeItem || item instanceof ShovelItem) return "tool";
        if (item instanceof ThrowablePotionItem) return "consumable";
        if (item.isEdible()) return "food";
        if (item instanceof BlockItem) return "building";
        String path = id.getPath();
        if (path.contains("staff") || path.contains("wand")) return "magic";
        return "utility";
    }

    private static String inferRarity(Item item) {
        try {
            ItemStack stack = new ItemStack(item);
            return stack.getRarity().name().toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            return "common";
        }
    }

    private static List<String> collectTags(Item item) {
        List<String> tags = new ArrayList<>();
        try {
            item.builtInRegistryHolder().tags().map(TagKey::location).forEach(loc -> tags.add(loc.toString()));
        } catch (Exception ignored) {}
        FoodProperties foodProperties = item.getFoodProperties();
        if (foodProperties != null) {
            tags.add("nutrition_" + foodProperties.getNutrition());
            if (foodProperties.isMeat()) tags.add("meat");
            if (foodProperties.isFastFood()) tags.add("fast");
        }
        return tags.isEmpty() ? List.of() : List.copyOf(tags);
    }

    public record ItemMeta(
            ResourceLocation id,
            String name,
            String mod,
            String role,
            String rarity,
            List<String> tags,
            String notes
    ) { }
}
