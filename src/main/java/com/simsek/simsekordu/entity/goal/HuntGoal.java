package com.simsek.simsekordu.entity.goal;

import com.simsek.simsekordu.data.HuntMode;
import com.simsek.simsekordu.data.Job;
import com.simsek.simsekordu.data.TaskStatus;
import com.simsek.simsekordu.entity.SimsekNpc;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A hunter looks for prey within {@link HuntHelper#RADIUS} blocks of its hunting centre (scanning every second, not
 * every tick), kills it with the best sword or axe it carries or, from further than 6 blocks, with a bow and arrows,
 * collects the drops, empties its inventory into the storage block when it is full (see {@link StorageRun}), and
 * falls back to the centre to heal when badly hurt.
 *
 * <p>In animal mode two adults of every species are always left alive in the area, and babies are never attacked.
 */
public class HuntGoal extends Goal {
    private static final int SCAN_INTERVAL = 20;
    private static final double CHASE_SPEED = 1.25D;
    /** Closer than this the last stretch to prey or loot is walked straight (see {@link #closeInDirectly}). */
    private static final double DIRECT_APPROACH_SQR = 3.0D * 3.0D;
    private static final int MELEE_COOLDOWN = 20;
    /** Switch to the bow beyond this distance and back to melee once the target is closer than {@link #RANGED_STOP}. */
    private static final double RANGED_START = 6.0D;
    private static final double RANGED_STOP = 5.0D;
    private static final double RANGED_MAX = 20.0D;
    private static final int AIM_TICKS = 20;
    private static final int SHOT_INTERVAL = 30;
    private static final int CHASE_TIMEOUT = 300;
    private static final int IGNORE_TICKS = 600;
    /** Prey that wandered this far outside the hunting radius is given up on. */
    private static final double AREA_MARGIN = 6.0D;
    private static final double RETURN_DISTANCE = 8.0D;
    private static final double CENTER_REACH = 5.0D;

    private static final double LOOT_RADIUS = 8.0D;
    private static final int LOOT_MAX_TICKS = 400;
    private static final int LOOT_ITEM_TIMEOUT = 120;
    private static final double PICKUP_DISTANCE_SQR = 2.25D;

    private static final double RETREAT_SPEED = 1.3D;

    private static final String NOTICE_NO_WEAPON = "no_weapon";

    private enum Phase {
        HUNTING,
        COLLECTING,
        STORAGE,
        RETREATING
    }

    private final SimsekNpc npc;
    private final BlockApproach centerApproach;
    private final StorageRun storageRun;
    private Phase phase = Phase.HUNTING;

    // hunting state
    @Nullable
    private LivingEntity target;
    private long nextScan;
    private final Map<UUID, Long> ignored = new HashMap<>();
    private boolean usingRanged;
    private int attackCooldown;
    private int aimTicks;
    private int chaseTicks;
    private long nextPathTick;

    // looting state
    private BlockPos lootCenter = BlockPos.ZERO;
    private long collectStart;
    private long nextLootScan;
    @Nullable
    private ItemEntity lootItem;
    private int lootItemTicks;
    private final Set<Integer> ignoredItems = new HashSet<>();

    public HuntGoal(SimsekNpc npc) {
        this.npc = npc;
        this.centerApproach = new BlockApproach(npc);
        this.storageRun = new StorageRun(npc, HuntHelper::isHunterGear);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        return !this.npc.level().isClientSide && this.npc.getJob() == Job.HUNTER
                && this.npc.getHuntCenter() != null && !this.npc.isHeldInPlace();
    }

    @Override
    public boolean canContinueToUse() {
        return this.npc.getJob() == Job.HUNTER && this.npc.getHuntCenter() != null && !this.npc.isHeldInPlace();
    }

    @Override
    public void start() {
        this.phase = this.npc.isRetreating() ? Phase.RETREATING : Phase.HUNTING;
        this.target = null;
        this.nextScan = 0L;
        this.ignored.clear();
        this.ignoredItems.clear();
        this.usingRanged = false;
        this.attackCooldown = 0;
        this.aimTicks = 0;
        this.lootItem = null;
        this.centerApproach.reset();
        this.npc.setTaskStatus(this.phase == Phase.RETREATING ? TaskStatus.RETREATING : TaskStatus.SEARCHING);
    }

    @Override
    public void stop() {
        this.target = null;
        this.lootItem = null;
        this.npc.setTarget(null);
        this.npc.getNavigation().stop();
        this.npc.clearMirroredHand();
        TaskStatus status = this.npc.getTaskStatus();
        if (status != TaskStatus.NONE && status != TaskStatus.IDLE) {
            // A resting hunter that was interrupted (it ran from a monster) is still resting.
            boolean resting = this.npc.isRetreating() && this.npc.getHuntCenter() != null;
            this.npc.setTaskStatus(resting ? TaskStatus.RETREATING : TaskStatus.IDLE);
        }
    }

    @Override
    public void tick() {
        if (!(this.npc.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos center = this.npc.getHuntCenter();
        if (center == null) {
            return;
        }
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
        // The NPC decides when to rest (see SimsekNpc#updateRetreat); the goal only follows.
        if (this.npc.isRetreating() && this.phase != Phase.RETREATING) {
            beginRetreat();
        } else if (!this.npc.isRetreating() && this.phase == Phase.RETREATING) {
            endRetreat();
        }
        switch (this.phase) {
            case HUNTING -> tickHunting(level, center);
            case COLLECTING -> tickCollecting(level);
            case STORAGE -> tickStorage(level);
            case RETREATING -> tickRetreating(level, center);
        }
    }

    // ---- hunting --------------------------------------------------------------------------------

    private void tickHunting(ServerLevel level, BlockPos center) {
        long now = level.getGameTime();
        if (now % SCAN_INTERVAL == 0) {
            updateWeaponNotice();
        }
        if (this.npc.isInventoryFull()) {
            beginStorage(level);
            return;
        }
        BlockPos killedAt = this.npc.consumePendingLootCenter();
        if (killedAt != null) {
            dropTarget();
            beginCollecting(level, killedAt);
            return;
        }
        if (this.target != null && !isStillValid(center)) {
            boolean died = !this.target.isAlive();
            BlockPos lastPosition = this.target.blockPosition();
            dropTarget();
            if (died) {
                beginCollecting(level, lastPosition);
                return;
            }
        }
        if (this.target == null) {
            if (now >= this.nextScan) {
                this.nextScan = now + SCAN_INTERVAL;
                this.target = findTarget(level, center, now);
                if (this.target != null) {
                    this.npc.setTarget(this.target);
                    this.chaseTicks = 0;
                    this.aimTicks = 0;
                    this.usingRanged = false;
                    this.npc.setTaskStatus(TaskStatus.HUNTING);
                }
            }
            if (this.target == null) {
                idle(level, center);
                return;
            }
        }
        fight(level, now);
    }

    private void idle(ServerLevel level, BlockPos center) {
        this.npc.setTaskStatus(TaskStatus.SEARCHING);
        this.npc.clearMirroredHand();
        if (this.centerApproach.inReach(center, RETURN_DISTANCE)) {
            this.npc.getNavigation().stop();
            this.centerApproach.reset();
        } else {
            this.centerApproach.approach(level, center, CENTER_REACH);
        }
    }

    private boolean isStillValid(BlockPos center) {
        LivingEntity current = this.target;
        if (current == null || !current.isAlive() || current.isRemoved()) {
            return false;
        }
        double limit = HuntHelper.RADIUS + AREA_MARGIN;
        return current.position().distanceToSqr(Vec3.atCenterOf(center)) <= limit * limit;
    }

    private void dropTarget() {
        this.target = null;
        this.npc.setTarget(null);
        this.npc.getNavigation().stop();
        this.attackCooldown = 0;
    }

    /** The nearest living thing the hunter may kill: right type for the mode, an adult in animal mode, enough left alive. */
    @Nullable
    private LivingEntity findTarget(ServerLevel level, BlockPos center, long now) {
        this.ignored.values().removeIf(expiry -> expiry <= now);
        HuntMode mode = this.npc.getHuntMode();
        Vec3 middle = Vec3.atCenterOf(center);
        double radiusSqr = (double) HuntHelper.RADIUS * HuntHelper.RADIUS;
        AABB area = new AABB(center).inflate(HuntHelper.RADIUS);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, area, entity ->
                entity.isAlive() && entity != this.npc && HuntHelper.matchesMode(entity, mode)
                        && entity.position().distanceToSqr(middle) <= radiusSqr);

        // Adults of every huntable species in the area, so that a minimum of them is left alone.
        Map<EntityType<?>, Integer> adults = new HashMap<>();
        for (LivingEntity entity : candidates) {
            if (HuntHelper.isAnimalTarget(entity) && !entity.isBaby()) {
                adults.merge(entity.getType(), 1, Integer::sum);
            }
        }

        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : candidates) {
            if (this.ignored.containsKey(entity.getUUID())) {
                continue;
            }
            if (HuntHelper.isAnimalTarget(entity)) {
                boolean protectedAnimal = HuntHelper.isSpared(entity)
                        || adults.getOrDefault(entity.getType(), 0) <= HuntHelper.MIN_ADULTS;
                if (protectedAnimal) {
                    continue;
                }
            }
            double distance = this.npc.distanceToSqr(entity);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    private void fight(ServerLevel level, long now) {
        LivingEntity current = this.target;
        if (current == null) {
            return;
        }
        double distance = this.npc.distanceTo(current);
        boolean lineOfSight = this.npc.getSensing().hasLineOfSight(current);
        this.npc.getLookControl().setLookAt(current, 30.0F, 30.0F);
        updateWeapon(distance);

        if (this.usingRanged) {
            if (distance <= RANGED_MAX && lineOfSight) {
                this.npc.getNavigation().stop();
                this.chaseTicks = 0;
                if (++this.aimTicks >= AIM_TICKS && this.attackCooldown <= 0) {
                    this.npc.performRangedAttack(current, 1.0F);
                    this.attackCooldown = SHOT_INTERVAL;
                    checkWeaponBroken();
                }
                return;
            }
            this.aimTicks = 0;
            chase(current, now);
            return;
        }

        this.aimTicks = 0;
        if (this.npc.isWithinMeleeAttackRange(current)) {
            this.npc.getNavigation().stop();
            this.chaseTicks = 0;
            if (this.attackCooldown <= 0) {
                this.attackCooldown = MELEE_COOLDOWN;
                this.npc.swing(InteractionHand.MAIN_HAND);
                if (this.npc.doHurtTarget(current)) {
                    damageWeapon(current);
                }
            }
            return;
        }
        chase(current, now);
    }

    private void chase(LivingEntity current, long now) {
        if (++this.chaseTicks > CHASE_TIMEOUT) {
            // Cannot get at it (fenced in, across water, ...): leave it alone for a while.
            this.ignored.put(current.getUUID(), now + IGNORE_TICKS);
            dropTarget();
            return;
        }
        if (closeInDirectly(current, CHASE_SPEED)) {
            return;
        }
        if (now >= this.nextPathTick) {
            this.nextPathTick = now + 10 + this.npc.getRandom().nextInt(6);
            this.npc.getNavigation().moveTo(current, CHASE_SPEED);
        }
    }

    /**
     * The path finder treats a target in the neighbouring block as reached, which can leave the hunter a block and a
     * half away from prey or an item it cannot hit or pick up yet. Close to a few blocks it therefore steers at the
     * target itself. Returns true when it did.
     */
    private boolean closeInDirectly(Entity target, double speed) {
        if (this.npc.distanceToSqr(target) > DIRECT_APPROACH_SQR || Math.abs(target.getY() - this.npc.getY()) > 1.5D) {
            return false;
        }
        this.npc.getNavigation().stop();
        this.npc.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), speed);
        return true;
    }

    // ---- weapons --------------------------------------------------------------------------------

    /** Picks sword/axe or bow for the current distance, hands it over to the main hand and keeps it there. */
    private void updateWeapon(double distance) {
        ItemStack bow = findBow();
        boolean rangedPossible = !bow.isEmpty() && !this.npc.findArrows().isEmpty();
        if (!rangedPossible) {
            this.usingRanged = false;
        } else if (this.usingRanged) {
            this.usingRanged = distance >= RANGED_STOP;
        } else {
            this.usingRanged = distance > RANGED_START;
        }
        ItemStack wanted = this.usingRanged ? bow : findBestMelee();
        if (this.npc.getMainHandItem() != wanted) {
            this.npc.setItemSlot(EquipmentSlot.MAINHAND, wanted);
        }
    }

    private ItemStack findBestMelee() {
        ItemStack best = ItemStack.EMPTY;
        double bestDamage = -1.0D;
        for (ItemStack stack : this.npc.getInventory().getItems()) {
            if (HuntHelper.isMeleeWeapon(stack)) {
                double damage = HuntHelper.meleeDamage(stack);
                if (damage > bestDamage) {
                    bestDamage = damage;
                    best = stack;
                }
            }
        }
        return best;
    }

    private ItemStack findBow() {
        for (ItemStack stack : this.npc.getInventory().getItems()) {
            if (HuntHelper.isBow(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Tells the owner (once) when the hunter has nothing to fight with and falls back to its bare hands. */
    private void updateWeaponNotice() {
        boolean armed = !findBestMelee().isEmpty() || (!findBow().isEmpty() && !this.npc.findArrows().isEmpty());
        if (armed) {
            this.npc.clearNotice(NOTICE_NO_WEAPON);
        } else {
            this.npc.noticeOwnerOnce(NOTICE_NO_WEAPON, Component.translatable("message.simsekordu.needs_weapon", this.npc.getNpcName()));
        }
    }

    /** A melee hit wears the weapon down like it would for a player (swords 1, axes 2); see {@code Player.attack}. */
    private void damageWeapon(LivingEntity victim) {
        ItemStack weapon = this.npc.getMainHandItem();
        if (!weapon.isEmpty()) {
            Item item = weapon.getItem();
            if (item.hurtEnemy(weapon, victim, this.npc)) {
                item.postHurtEnemy(weapon, victim, this.npc);
            }
        }
        checkWeaponBroken();
    }

    /** Cleans up after a weapon that has just broken: removes it from the inventory and tells the owner. */
    private void checkWeaponBroken() {
        ItemStack weapon = this.npc.getMainHandItem();
        if (!weapon.isEmpty()) {
            return;
        }
        // The stack that sat in the hand has been emptied in place; find and clear its inventory slot.
        Container inventory = this.npc.getInventory();
        boolean removed = false;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() && stack != ItemStack.EMPTY) {
                inventory.setItem(i, ItemStack.EMPTY);
                removed = true;
            }
        }
        if (removed) {
            this.npc.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            this.npc.messageOwner(Component.translatable("message.simsekordu.weapon_broke", this.npc.getNpcName()));
            if (findBestMelee().isEmpty() && (findBow().isEmpty() || this.npc.findArrows().isEmpty())) {
                this.npc.markNoticeSent(NOTICE_NO_WEAPON);
            }
        }
    }

    // ---- loot -----------------------------------------------------------------------------------

    private void beginCollecting(ServerLevel level, BlockPos killedAt) {
        this.phase = Phase.COLLECTING;
        this.lootCenter = killedAt;
        this.collectStart = level.getGameTime();
        this.nextLootScan = 0L;
        this.lootItem = null;
        this.lootItemTicks = 0;
        this.ignoredItems.clear();
        this.npc.setTaskStatus(TaskStatus.COLLECTING);
        this.npc.clearMirroredHand();
    }

    private void tickCollecting(ServerLevel level) {
        long now = level.getGameTime();
        if (this.npc.isInventoryFull()) {
            beginStorage(level);
            return;
        }
        if (now - this.collectStart > LOOT_MAX_TICKS) {
            endCollecting();
            return;
        }
        if (this.lootItem == null || !this.lootItem.isAlive()) {
            this.lootItem = null;
            if (now < this.nextLootScan) {
                return;
            }
            this.nextLootScan = now + 10;
            this.lootItem = findLoot(level);
            this.lootItemTicks = 0;
            if (this.lootItem == null) {
                // Nothing (more) to pick up.
                endCollecting();
                return;
            }
        }
        ItemEntity item = this.lootItem;
        this.npc.getLookControl().setLookAt(item, 30.0F, 30.0F);
        if (this.npc.distanceToSqr(item) <= PICKUP_DISTANCE_SQR) {
            if (!item.hasPickUpDelay()) {
                pickUp(level, item);
                this.lootItem = null;
            }
            return;
        }
        if (++this.lootItemTicks > LOOT_ITEM_TIMEOUT) {
            this.ignoredItems.add(item.getId());
            this.lootItem = null;
            return;
        }
        if (closeInDirectly(item, 1.1D)) {
            return;
        }
        if (!this.npc.getNavigation().isInProgress() || this.lootItemTicks % 10 == 1) {
            this.npc.getNavigation().moveTo(item, 1.1D);
        }
    }

    /** The nearest drop within reach of the last kill that the hunter can still carry. */
    @Nullable
    private ItemEntity findLoot(ServerLevel level) {
        AABB area = new AABB(this.lootCenter).inflate(LOOT_RADIUS);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, area, this::isCollectable);
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ItemEntity item : items) {
            double distance = this.npc.distanceToSqr(item);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = item;
            }
        }
        return best;
    }

    private boolean isCollectable(ItemEntity item) {
        // Items someone threw are theirs, and the NPC's own overflow must not be picked up again.
        return item.isAlive() && item.getOwner() == null && item.getTarget() == null
                && !this.ignoredItems.contains(item.getId()) && this.npc.getInventory().canAddItem(item.getItem());
    }

    private void pickUp(ServerLevel level, ItemEntity item) {
        ItemStack stack = item.getItem();
        int before = stack.getCount();
        ItemStack rest = this.npc.getInventory().addItem(stack.copy());
        int taken = before - rest.getCount();
        if (taken <= 0) {
            this.ignoredItems.add(item.getId());
            return;
        }
        this.npc.take(item, taken);
        level.playSound(null, this.npc.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.2F,
                (this.npc.getRandom().nextFloat() - this.npc.getRandom().nextFloat()) * 1.4F + 2.0F);
        if (rest.isEmpty()) {
            item.discard();
        } else {
            item.setItem(rest);
        }
    }

    private void endCollecting() {
        this.lootItem = null;
        this.npc.getNavigation().stop();
        this.phase = Phase.HUNTING;
        this.npc.setTaskStatus(TaskStatus.SEARCHING);
    }

    // ---- storage --------------------------------------------------------------------------------

    private void beginStorage(ServerLevel level) {
        dropTarget();
        this.lootItem = null;
        this.npc.clearMirroredHand();
        this.storageRun.begin(level);
        this.phase = Phase.STORAGE;
    }

    private void tickStorage(ServerLevel level) {
        switch (this.storageRun.tick(level)) {
            case WALKING -> this.npc.setTaskStatus(TaskStatus.TO_STORAGE);
            case FULL -> this.npc.setTaskStatus(TaskStatus.STORAGE_FULL);
            case NO_STORAGE -> this.npc.setTaskStatus(TaskStatus.NO_STORAGE);
            case UNREACHABLE -> this.npc.setTaskStatus(TaskStatus.UNREACHABLE);
            case DONE -> {
                this.phase = Phase.HUNTING;
                this.npc.setTaskStatus(TaskStatus.SEARCHING);
            }
        }
    }

    // ---- retreat --------------------------------------------------------------------------------

    private void beginRetreat() {
        dropTarget();
        this.lootItem = null;
        this.npc.clearMirroredHand();
        this.centerApproach.reset();
        this.phase = Phase.RETREATING;
        this.npc.setTaskStatus(TaskStatus.RETREATING);
    }

    private void endRetreat() {
        this.centerApproach.reset();
        this.phase = Phase.HUNTING;
        this.npc.setTaskStatus(TaskStatus.SEARCHING);
    }

    /** Falls back to the centre while the NPC itself heals (and flees from monsters, see SimsekNpc#registerGoals). */
    private void tickRetreating(ServerLevel level, BlockPos center) {
        this.npc.setTaskStatus(TaskStatus.RETREATING);
        if (this.centerApproach.inReach(center, CENTER_REACH)) {
            this.npc.getNavigation().stop();
        } else {
            this.centerApproach.approach(level, center, CENTER_REACH, RETREAT_SPEED);
        }
    }
}
