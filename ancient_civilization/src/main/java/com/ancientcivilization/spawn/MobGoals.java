package com.ancientcivilization.spawn;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;

import java.lang.reflect.Field;

/**
 * Reflection bridge to {@link Mob#goalSelector} and {@link Mob#targetSelector}, which
 * are {@code protected} in 1.21.1 and therefore not reachable from this package.
 * Fails silently (goal simply not added) if the field layout ever changes.
 */
public final class MobGoals {
    private static final Field GOAL_SELECTOR = field("goalSelector");
    private static final Field TARGET_SELECTOR = field("targetSelector");

    private MobGoals() {
    }

    private static Field field(String name) {
        try {
            Field f = Mob.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    public static void addGoal(Mob mob, int priority, Goal goal) {
        if (GOAL_SELECTOR == null) {
            return;
        }
        try {
            ((GoalSelector) GOAL_SELECTOR.get(mob)).addGoal(priority, goal);
        } catch (IllegalAccessException ignored) {
            // Field became inaccessible; skip.
        }
    }

    public static void addTargetGoal(Mob mob, int priority, Goal goal) {
        if (TARGET_SELECTOR == null) {
            return;
        }
        try {
            ((GoalSelector) TARGET_SELECTOR.get(mob)).addGoal(priority, goal);
        } catch (IllegalAccessException ignored) {
            // Field became inaccessible; skip.
        }
    }
}
