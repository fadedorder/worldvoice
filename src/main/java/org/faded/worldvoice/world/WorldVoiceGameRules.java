package org.faded.worldvoice.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import org.faded.worldvoice.core.SkillCategory;

import java.lang.reflect.Method;

public class WorldVoiceGameRules {
    private static GameRules.Key<GameRules.BooleanValue> TRULY_UNIQUE;
    private static GameRules.Key<GameRules.BooleanValue> TRULY_ULT;
    private static boolean registered = false;
    private static Method booleanFactory;

    public static void register() {
        ensureRegistered();
    }

    public static boolean isEnabled(ServerLevel level, SkillCategory cat) {
        ensureRegistered();
        GameRules.Key<GameRules.BooleanValue> key = cat == SkillCategory.UNIQUE ? TRULY_UNIQUE : TRULY_ULT;
        if (key == null) return true;
        GameRules.BooleanValue value = level.getGameRules().getRule(key);
        return value == null || value.get();
    }

    public static void enforce(ServerLevel level) {
        ensureRegistered();
    }

    private static GameRules.Key<GameRules.BooleanValue> registerBoolean(String name, boolean defaultValue) {
        try {
            if (booleanFactory == null) {
                for (Method m : GameRules.BooleanValue.class.getDeclaredMethods()) {
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class
                            && GameRules.Type.class.isAssignableFrom(m.getReturnType())) {
                        m.setAccessible(true);
                        booleanFactory = m;
                        break;
                    }
                }
                if (booleanFactory == null) {
                    throw new IllegalStateException("Could not locate GameRules.BooleanValue factory method");
                }
            }
            @SuppressWarnings("unchecked")
            GameRules.Type<GameRules.BooleanValue> type = (GameRules.Type<GameRules.BooleanValue>) booleanFactory.invoke(null, defaultValue);
            return GameRules.register(name, GameRules.Category.MISC, type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to register gamerule " + name, e);
        }
    }

    private static synchronized void ensureRegistered() {
        if (registered) return;
        TRULY_UNIQUE = registerBoolean("worldVoiceTrulyUnique", true);
        TRULY_ULT = registerBoolean("worldVoiceTrulyUlt", true);
        registered = true;
    }
}
