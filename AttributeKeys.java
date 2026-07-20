package com.exotic.plugin;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;

/**
 * Paper migrated Attribute from an enum with GENERIC_* constants to a
 * Registry-backed interface. This looks attributes up by their vanilla
 * registry key instead, which is stable across that migration.
 */
public final class AttributeKeys {
    private AttributeKeys() {}

    public static final Attribute ATTACK_DAMAGE = get("generic.attack_damage");
    public static final Attribute ATTACK_SPEED = get("generic.attack_speed");
    public static final Attribute MOVEMENT_SPEED = get("generic.movement_speed");
    public static final Attribute MAX_HEALTH = get("generic.max_health");
    public static final Attribute STEP_HEIGHT = get("generic.step_height");
    public static final Attribute ATTACK_KNOCKBACK = get("generic.attack_knockback");

    private static Attribute get(String key) {
        Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
        if (attribute == null) {
            throw new IllegalStateException("Unknown attribute key: " + key);
        }
        return attribute;
    }
}
