package com.simsek.simsekordu.entity.goal;

import com.simsek.simsekordu.data.HuntMode;
import com.simsek.simsekordu.registry.ModTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

/** Weapon and target rules shared by {@link HuntGoal} and the NPC. */
public final class HuntHelper {
    /** How far from the hunting centre a hunter looks for prey. */
    public static final int RADIUS = 24;
    /** In animal mode this many adults of every species are always left alive in the area. */
    public static final int MIN_ADULTS = 2;

    private HuntHelper() {
    }

    public static boolean isMeleeWeapon(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES));
    }

    public static boolean isBow(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BowItem;
    }

    public static boolean isArrow(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ItemTags.ARROWS);
    }

    /** Swords, axes, bows and arrows stay with the hunter when it empties its inventory into the storage. */
    public static boolean isHunterGear(ItemStack stack) {
        return isMeleeWeapon(stack) || isBow(stack) || isArrow(stack);
    }

    /** Attack damage the item adds in the main hand (used to pick the best sword or axe). */
    public static double meleeDamage(ItemStack stack) {
        double[] total = {0.0D};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                total[0] += modifier.amount();
            }
        });
        return total[0];
    }

    public static boolean isAnimalTarget(Entity entity) {
        return entity.getType().is(ModTags.HUNTABLE_ANIMALS);
    }

    /** Every {@link Monster} except creepers, which a hunter keeps away from instead. */
    public static boolean isMonsterTarget(Entity entity) {
        return entity instanceof Monster && !(entity instanceof Creeper);
    }

    /** Young, named (pets) and leashed animals are never hunted. */
    public static boolean isSpared(LivingEntity entity) {
        return entity.isBaby() || entity.hasCustomName() || (entity instanceof Mob mob && mob.isLeashed());
    }

    /**
     * Whether a soldier's arrow may hit {@code entity}: only what a hunter could kill. Everything else (players,
     * soldiers, villagers, pets, creepers, baby animals, item frames, ...) is flown through instead.
     */
    public static boolean mayShootAt(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        return isMonsterTarget(living) || (isAnimalTarget(living) && !isSpared(living));
    }

    public static boolean matchesMode(LivingEntity entity, HuntMode mode) {
        return (mode.includesAnimals() && isAnimalTarget(entity)) || (mode.includesMonsters() && isMonsterTarget(entity));
    }
}
