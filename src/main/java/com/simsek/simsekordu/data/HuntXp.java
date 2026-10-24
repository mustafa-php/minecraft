package com.simsek.simsekordu.data;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;

/** XP a hunter earns per kill. */
public final class HuntXp {
    public static final int SMALL_ANIMAL = 2;
    public static final int ANIMAL = 3;
    public static final int MONSTER = 5;
    public static final int COMMON_MONSTER = 6;
    public static final int RARE_MONSTER = 12;
    public static final int OTHER = 1;

    private HuntXp() {
    }

    public static int xpFor(LivingEntity victim) {
        EntityType<?> type = victim.getType();
        if (type == EntityType.ENDERMAN || type == EntityType.WITCH) {
            return RARE_MONSTER;
        }
        if (victim instanceof Zombie || victim instanceof AbstractSkeleton || victim instanceof Spider) {
            return COMMON_MONSTER;
        }
        if (victim instanceof Monster) {
            return MONSTER;
        }
        if (type == EntityType.CHICKEN || type == EntityType.RABBIT) {
            return SMALL_ANIMAL;
        }
        if (victim instanceof Animal) {
            return ANIMAL;
        }
        return OTHER;
    }
}
