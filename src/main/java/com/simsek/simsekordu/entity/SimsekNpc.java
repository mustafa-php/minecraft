package com.simsek.simsekordu.entity;

import com.simsek.simsekordu.SimsekOrdu;
import com.simsek.simsekordu.data.HuntMode;
import com.simsek.simsekordu.data.HuntXp;
import com.simsek.simsekordu.data.Job;
import com.simsek.simsekordu.data.MiningRegion;
import com.simsek.simsekordu.data.TaskStatus;
import com.simsek.simsekordu.data.NpcNames;
import com.simsek.simsekordu.data.Rank;
import com.simsek.simsekordu.entity.goal.FollowOwnerGoal;
import com.simsek.simsekordu.entity.goal.HuntGoal;
import com.simsek.simsekordu.entity.goal.HuntHelper;
import com.simsek.simsekordu.entity.goal.MineGoal;
import com.simsek.simsekordu.entity.goal.MiningHelper;
import com.simsek.simsekordu.menu.SimsekNpcMenu;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

public class SimsekNpc extends PathfinderMob implements RangedAttackMob {
    private static final String TAG_OWNER = "Owner";
    private static final String TAG_JOB = "Job";
    private static final String TAG_LEVEL = "Level";
    private static final String TAG_XP = "Xp";
    private static final String TAG_NPC_NAME = "NpcName";
    private static final String TAG_WAITING = "Waiting";
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_MINING_TASK = "MiningTask";
    private static final String TAG_TASK_MIN = "Min";
    private static final String TAG_TASK_MAX = "Max";
    private static final String TAG_TASK_STORAGE = "Storage";
    private static final String TAG_STORAGE = "StoragePos";
    private static final String TAG_TASK_REMAINING = "Remaining";
    private static final String TAG_HUNT_CENTER = "HuntCenter";
    private static final String TAG_HUNT_MODE = "HuntMode";
    private static final String TAG_HUNT_KILLS = "HuntKills";
    private static final String TAG_RETREATING = "Retreating";

    public static final int INVENTORY_SIZE = 9;

    /** A hunter below this fraction of its health falls back to rest until it is back at {@link #RESUME_AT}. */
    private static final float RETREAT_BELOW = 0.30F;
    private static final float RESUME_AT = 0.80F;
    /** Resting heals one health point every two seconds. */
    private static final int RETREAT_HEAL_INTERVAL = 40;
    /** Monsters this close make a resting hunter run instead of standing still. */
    private static final float RETREAT_FLEE_DISTANCE = 10.0F;

    private static final ResourceLocation RANK_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(SimsekOrdu.MODID, "rank_bonus");

    private static final EntityDataAccessor<Integer> DATA_JOB =
            SynchedEntityData.defineId(SimsekNpc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LEVEL =
            SynchedEntityData.defineId(SimsekNpc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_XP =
            SynchedEntityData.defineId(SimsekNpc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RANK =
            SynchedEntityData.defineId(SimsekNpc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_NPC_NAME =
            SynchedEntityData.defineId(SimsekNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(SimsekNpc.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_WAITING =
            SynchedEntityData.defineId(SimsekNpc.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_TASK_MIN =
            SynchedEntityData.defineId(SimsekNpc.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_TASK_MAX =
            SynchedEntityData.defineId(SimsekNpc.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_STORAGE =
            SynchedEntityData.defineId(SimsekNpc.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Integer> DATA_TASK_REMAINING =
            SynchedEntityData.defineId(SimsekNpc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TASK_STATUS =
            SynchedEntityData.defineId(SimsekNpc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_HUNT_CENTER =
            SynchedEntityData.defineId(SimsekNpc.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Integer> DATA_HUNT_MODE =
            SynchedEntityData.defineId(SimsekNpc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HUNT_KILLS =
            SynchedEntityData.defineId(SimsekNpc.class, EntityDataSerializers.INT);

    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    /** Number of players that currently have this NPC's menu open (server side only). */
    private int menuUsers;
    /** Notices already sent to the owner, so a recurring problem is only reported once (not persisted). */
    private final Set<String> sentNotices = new HashSet<>();
    /** True while a hunter is resting after it got badly hurt (see {@link #updateRetreat}); saved with the hunt data. */
    private boolean retreating;
    private int retreatHealTimer;
    /** Where the last victim died, until the hunt goal has looked for its drops (server side, not persisted). */
    @Nullable
    private BlockPos pendingLootCenter;

    public SimsekNpc(EntityType<? extends SimsekNpc> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.MINING_EFFICIENCY);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_JOB, Job.NONE.ordinal());
        builder.define(DATA_LEVEL, 1);
        builder.define(DATA_XP, 0);
        builder.define(DATA_RANK, Rank.ER.ordinal());
        builder.define(DATA_NPC_NAME, "");
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_WAITING, false);
        builder.define(DATA_TASK_MIN, Optional.empty());
        builder.define(DATA_TASK_MAX, Optional.empty());
        builder.define(DATA_STORAGE, Optional.empty());
        builder.define(DATA_TASK_REMAINING, 0);
        builder.define(DATA_TASK_STATUS, TaskStatus.NONE.ordinal());
        builder.define(DATA_HUNT_CENTER, Optional.empty());
        builder.define(DATA_HUNT_MODE, HuntMode.ANIMAL.ordinal());
        builder.define(DATA_HUNT_KILLS, 0);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        if (getNpcName().isEmpty()) {
            setNpcName(NpcNames.random(this.random));
        }
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new AvoidEntityGoal<>(this, Creeper.class, 8.0F, 1.0D, 1.35D) {
            @Override
            public boolean canUse() {
                return isHuntingActive() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return isHuntingActive() && super.canContinueToUse();
            }
        });
        // A hurt hunter that is resting must not stand still while monsters close in on it.
        this.goalSelector.addGoal(0, new AvoidEntityGoal<>(this, Monster.class, RETREAT_FLEE_DISTANCE, 1.0D, 1.3D) {
            @Override
            public boolean canUse() {
                return isRetreating() && isHuntingActive() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return isRetreating() && isHuntingActive() && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(1, new MineGoal(this));
        this.goalSelector.addGoal(1, new HuntGoal(this));
        this.goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.0D, 5.0F, 2.0F));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8D) {
            @Override
            public boolean canUse() {
                return !SimsekNpc.this.isHeldInPlace() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !SimsekNpc.this.isHeldInPlace() && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        boolean clientSide = this.level().isClientSide;
        if (getOwnerUuid() == null) {
            if (!clientSide) {
                setOwnerUuid(player.getUUID());
                player.displayClientMessage(Component.translatable("message.simsekordu.owner_set"), true);
            }
        } else if (isOwnedBy(player)) {
            if (!player.getItemInHand(hand).isEmpty()) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                openMenu(serverPlayer);
            }
        } else if (!clientSide) {
            player.displayClientMessage(Component.translatable("message.simsekordu.owned_by_other"), true);
        }
        return InteractionResult.sidedSuccess(clientSide);
    }

    private void openMenu(ServerPlayer player) {
        player.openMenu(
                new SimpleMenuProvider((containerId, playerInventory, p) -> new SimsekNpcMenu(containerId, playerInventory, this), getName()),
                buf -> buf.writeVarInt(getId()));
    }

    public Job getJob() {
        return Job.byId(this.entityData.get(DATA_JOB));
    }

    public void setJob(Job job) {
        this.entityData.set(DATA_JOB, job.ordinal());
    }

    public int getNpcLevel() {
        return this.entityData.get(DATA_LEVEL);
    }

    public int getXp() {
        return this.entityData.get(DATA_XP);
    }

    public Rank getRank() {
        return Rank.byId(this.entityData.get(DATA_RANK));
    }

    public String getNpcName() {
        return this.entityData.get(DATA_NPC_NAME);
    }

    public void setNpcName(String name) {
        this.entityData.set(DATA_NPC_NAME, name);
    }

    public static int xpForNextLevel(int level) {
        return 50 * level;
    }

    /** Adds XP and handles any level-ups (and rank promotions) it causes. Server side only. */
    public void addXp(int amount) {
        if (this.level().isClientSide || amount <= 0) {
            return;
        }
        Rank oldRank = getRank();
        int level = getNpcLevel();
        long xp = (long) getXp() + amount;
        int levelsGained = 0;
        while (xp >= xpForNextLevel(level)) {
            xp -= xpForNextLevel(level);
            level++;
            levelsGained++;
        }
        this.entityData.set(DATA_XP, (int) xp);
        if (levelsGained > 0) {
            this.entityData.set(DATA_LEVEL, level);
            onLevelUp(oldRank, Rank.fromLevel(level));
        }
    }

    private void onLevelUp(Rank oldRank, Rank newRank) {
        boolean promoted = newRank != oldRank;
        if (promoted) {
            this.entityData.set(DATA_RANK, newRank.ordinal());
        }
        applyRankBonuses();

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(promoted ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.HAPPY_VILLAGER,
                    getX(), getY() + getBbHeight() * 0.6D, getZ(), promoted ? 30 : 12, 0.4D, 0.6D, 0.4D, promoted ? 0.3D : 0.0D);
            serverLevel.playSound(null, blockPosition(),
                    promoted ? SoundEvents.PLAYER_LEVELUP : SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.NEUTRAL, 1.0F, 1.0F);
        }

        if (promoted) {
            // One message per rank gained, in case several ranks were skipped at once.
            for (Rank rank : Rank.values()) {
                if (rank.ordinal() > oldRank.ordinal() && rank.ordinal() <= newRank.ordinal()) {
                    messageOwner(Component.translatable("message.simsekordu.rank_up",
                            getNpcName(), Component.translatable(rank.getTranslationKey())));
                }
            }
        }
    }

    private void applyRankBonuses() {
        Rank rank = getRank();
        float oldMaxHealth = getMaxHealth();
        applyMultiplier(Attributes.MAX_HEALTH, rank.getMaxHealthMultiplier());
        applyMultiplier(Attributes.ATTACK_DAMAGE, rank.getAttackDamageMultiplier());
        applyMultiplier(Attributes.MOVEMENT_SPEED, rank.getMovementSpeedMultiplier());
        float healthGain = getMaxHealth() - oldMaxHealth;
        if (healthGain > 0.0F) {
            heal(healthGain);
        }
    }

    private void applyMultiplier(Holder<Attribute> attribute, double multiplier) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.addOrReplacePermanentModifier(new AttributeModifier(
                    RANK_MODIFIER_ID, multiplier - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    @Override
    public Component getName() {
        if (getNpcName().isEmpty()) {
            return super.getName();
        }
        return Component.translatable("entity.simsekordu.simsek_npc.label",
                Component.translatable(getRank().getTranslationKey()), getNpcName(), getNpcLevel());
    }

    @Override
    public boolean shouldShowName() {
        return true;
    }

    @Nullable
    public UUID getOwnerUuid() {
        return this.entityData.get(DATA_OWNER).orElse(null);
    }

    public void setOwnerUuid(@Nullable UUID uuid) {
        this.entityData.set(DATA_OWNER, Optional.ofNullable(uuid));
    }

    public boolean isOwnedBy(Player player) {
        return player.getUUID().equals(getOwnerUuid());
    }

    @Nullable
    public Player getOwner() {
        UUID ownerUuid = getOwnerUuid();
        return ownerUuid == null ? null : this.level().getPlayerByUUID(ownerUuid);
    }

    public SimpleContainer getInventory() {
        return this.inventory;
    }

    public boolean isWaiting() {
        return this.entityData.get(DATA_WAITING);
    }

    public void setWaiting(boolean waiting) {
        this.entityData.set(DATA_WAITING, waiting);
        if (waiting) {
            getNavigation().stop();
        }
    }

    /** True while the NPC must stand still: told to wait, or a player is using its menu. */
    public boolean isHeldInPlace() {
        return isWaiting() || this.menuUsers > 0;
    }

    public void addMenuUser() {
        this.menuUsers++;
        getNavigation().stop();
    }

    public void removeMenuUser() {
        this.menuUsers = Math.max(0, this.menuUsers - 1);
    }

    // ---- mining task ----------------------------------------------------------------------------

    @Nullable
    public MiningRegion getMiningRegion() {
        Optional<BlockPos> min = this.entityData.get(DATA_TASK_MIN);
        Optional<BlockPos> max = this.entityData.get(DATA_TASK_MAX);
        return min.isPresent() && max.isPresent() ? new MiningRegion(min.get(), max.get()) : null;
    }

    @Nullable
    public BlockPos getStoragePos() {
        return this.entityData.get(DATA_STORAGE).orElse(null);
    }

    public int getMiningRemaining() {
        return this.entityData.get(DATA_TASK_REMAINING);
    }

    public void setMiningRemaining(int remaining) {
        this.entityData.set(DATA_TASK_REMAINING, Math.max(0, remaining));
    }

    public TaskStatus getTaskStatus() {
        return TaskStatus.byId(this.entityData.get(DATA_TASK_STATUS));
    }

    public void setTaskStatus(TaskStatus status) {
        this.entityData.set(DATA_TASK_STATUS, status.ordinal());
    }

    /** Hands a region (and optionally a storage block) to this soldier, replacing any earlier task. Server side only. */
    public void assignMiningTask(MiningRegion region, @Nullable BlockPos storage) {
        this.entityData.set(DATA_TASK_MIN, Optional.of(region.min()));
        this.entityData.set(DATA_TASK_MAX, Optional.of(region.max()));
        if (storage != null) {
            this.entityData.set(DATA_STORAGE, Optional.of(storage));
        }
        this.sentNotices.clear();
        if (this.level() instanceof ServerLevel serverLevel) {
            setMiningRemaining(MiningHelper.countMineable(serverLevel, region, getStoragePos()).mineable());
        }
        setTaskStatus(TaskStatus.IDLE);
    }

    // ---- hunting task ---------------------------------------------------------------------------

    @Nullable
    public BlockPos getHuntCenter() {
        return this.entityData.get(DATA_HUNT_CENTER).orElse(null);
    }

    public HuntMode getHuntMode() {
        return HuntMode.byId(this.entityData.get(DATA_HUNT_MODE));
    }

    public void setHuntMode(HuntMode mode) {
        this.entityData.set(DATA_HUNT_MODE, mode.ordinal());
    }

    /** Total number of kills this soldier has made. */
    public int getHuntKills() {
        return this.entityData.get(DATA_HUNT_KILLS);
    }

    /** Hands a hunting centre (and optionally a storage block) to this soldier. Server side only. */
    public void assignHuntTask(BlockPos center, @Nullable BlockPos storage) {
        this.entityData.set(DATA_HUNT_CENTER, Optional.of(center));
        if (storage != null) {
            this.entityData.set(DATA_STORAGE, Optional.of(storage));
        }
        this.sentNotices.clear();
        setRetreating(false);
        setTaskStatus(TaskStatus.IDLE);
    }

    public void cancelHuntTask() {
        this.entityData.set(DATA_HUNT_CENTER, Optional.empty());
        setRetreating(false);
        this.pendingLootCenter = null;
        if (getMiningRegion() == null) {
            this.entityData.set(DATA_STORAGE, Optional.empty());
        }
        setTaskStatus(TaskStatus.NONE);
        this.sentNotices.clear();
    }

    /** Cancels the task the info tab shows for the current job: hunting for hunters, mining otherwise. */
    public void cancelCurrentTask() {
        if (getJob() == Job.HUNTER) {
            cancelHuntTask();
        } else {
            cancelMiningTask();
        }
    }

    private boolean isHuntingActive() {
        return getJob() == Job.HUNTER && getHuntCenter() != null && !isHeldInPlace();
    }

    /** True while this hunter rests: it fell below 30 % health and has not yet healed back up to 80 %. */
    public boolean isRetreating() {
        return this.retreating;
    }

    public void setRetreating(boolean retreating) {
        this.retreating = retreating;
        this.retreatHealTimer = 0;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        updateRetreat();
    }

    /**
     * Owns the resting state of a hunter so that it keeps healing no matter which goal is running (a fleeing hunter
     * must heal too): below 30 % health it starts resting, heals one point every two seconds and is done at 80 %.
     */
    private void updateRetreat() {
        if (getJob() != Job.HUNTER || getHuntCenter() == null) {
            this.retreating = false;
            this.retreatHealTimer = 0;
            return;
        }
        float fraction = getHealth() / getMaxHealth();
        if (!this.retreating) {
            if (fraction < RETREAT_BELOW) {
                setRetreating(true);
            }
            return;
        }
        if (fraction >= RESUME_AT) {
            setRetreating(false);
        } else if (++this.retreatHealTimer >= RETREAT_HEAL_INTERVAL) {
            this.retreatHealTimer = 0;
            heal(1.0F);
        }
    }

    /** Called when this soldier's attack (melee or arrow) killed {@code victim}: counts the kill and awards XP. */
    public void onHuntKill(LivingEntity victim) {
        this.entityData.set(DATA_HUNT_KILLS, getHuntKills() + 1);
        addXp(HuntXp.xpFor(victim));
        this.pendingLootCenter = victim.blockPosition();
    }

    /** The place of the last kill if its drops have not been looked for yet; clears it. */
    @Nullable
    public BlockPos consumePendingLootCenter() {
        BlockPos center = this.pendingLootCenter;
        this.pendingLootCenter = null;
        return center;
    }

    @Override
    public boolean canFireProjectileWeapon(ProjectileWeaponItem weapon) {
        return weapon instanceof BowItem;
    }

    /** Fires the first arrow of the inventory with the bow in the main hand; the spread depends on the rank. */
    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        ItemStack bow = getMainHandItem();
        ItemStack ammo = findArrows();
        if (!HuntHelper.isBow(bow) || ammo.isEmpty()) {
            return;
        }
        ItemStack arrowStack = ammo.copyWithCount(1);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, arrowStack, power, bow);
        if (bow.getItem() instanceof ProjectileWeaponItem weaponItem) {
            arrow = weaponItem.customArrow(arrow, arrowStack, bow);
        }
        // Rank bonuses: stronger arrows (as for melee hits) and a tighter spread.
        arrow.setBaseDamage(arrow.getBaseDamage() * getRank().getAttackDamageMultiplier());
        double dx = target.getX() - getX();
        double dy = target.getY(0.3333333333333333D) - arrow.getY();
        double dz = target.getZ() - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * 0.2D, dz, 1.6F, getRank().getBowInaccuracy());
        playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
        level().addFreshEntity(arrow);
        ammo.shrink(1);
        if (ammo.isEmpty()) {
            for (int i = 0; i < this.inventory.getContainerSize(); i++) {
                if (this.inventory.getItem(i) == ammo) {
                    this.inventory.setItem(i, ItemStack.EMPTY);
                }
            }
        }
        bow.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
    }

    /** The first stack of arrows in the inventory (the stack itself, not a copy), or empty. */
    public ItemStack findArrows() {
        for (ItemStack stack : this.inventory.getItems()) {
            if (HuntHelper.isArrow(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Reports the finished task to the owner and clears it. Server side only. */
    public void completeMiningTask() {
        messageOwner(Component.translatable("message.simsekordu.task_complete", getNpcName()));
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + getBbHeight() * 0.7D, getZ(),
                    16, 0.5D, 0.5D, 0.5D, 0.0D);
            serverLevel.playSound(null, blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
        cancelMiningTask();
    }

    public void cancelMiningTask() {
        this.entityData.set(DATA_TASK_MIN, Optional.empty());
        this.entityData.set(DATA_TASK_MAX, Optional.empty());
        if (getHuntCenter() == null) {
            this.entityData.set(DATA_STORAGE, Optional.empty());
        }
        setMiningRemaining(0);
        setTaskStatus(TaskStatus.NONE);
        this.sentNotices.clear();
    }

    /** Sends {@code message} to the owner if they are online. */
    public void messageOwner(Component message) {
        UUID ownerUuid = getOwnerUuid();
        if (ownerUuid != null && getServer() != null) {
            ServerPlayer owner = getServer().getPlayerList().getPlayer(ownerUuid);
            if (owner != null) {
                owner.sendSystemMessage(message);
            }
        }
    }

    /** Like {@link #messageOwner} but only the first time {@code noticeId} is used (until {@link #clearNotice}). */
    public void noticeOwnerOnce(String noticeId, Component message) {
        if (this.sentNotices.add(noticeId)) {
            messageOwner(message);
        }
    }

    /** Records {@code noticeId} as already sent without sending anything. */
    public void markNoticeSent(String noticeId) {
        this.sentNotices.add(noticeId);
    }

    public void clearNotice(String noticeId) {
        this.sentNotices.remove(noticeId);
    }

    public boolean isInventoryFull() {
        for (ItemStack stack : this.inventory.getItems()) {
            if (stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Puts {@code stack} into the inventory; whatever does not fit is dropped at the soldier's feet. */
    public void storeOrSpill(ItemStack stack) {
        ItemStack rest = this.inventory.addItem(stack);
        if (!rest.isEmpty()) {
            spawnAtLocation(rest);
        }
    }

    /**
     * The main hand only ever mirrors a stack that also sits in the inventory (the same object), so the tool
     * stays visible in the GUI and can never be duplicated. This is true while that is the case.
     */
    public boolean isMainHandMirrored() {
        ItemStack hand = getMainHandItem();
        if (hand.isEmpty()) {
            return false;
        }
        for (ItemStack stack : this.inventory.getItems()) {
            if (stack == hand) {
                return true;
            }
        }
        return false;
    }

    public void clearMirroredHand() {
        if (isMainHandMirrored()) {
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        // The hand only mirrors an inventory stack and the inventory is dropped in dropEquipment().
        clearMirroredHand();
        super.dropCustomDeathLoot(level, source, recentlyHit);
    }

    @Override
    public void die(DamageSource source) {
        stopRunningGoals();
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide) {
            stopRunningGoals();
        }
        super.remove(reason);
    }

    /** Lets running goals clean up after themselves (e.g. clear the block crack animation). */
    private void stopRunningGoals() {
        this.goalSelector.getAvailableGoals().stream().filter(WrappedGoal::isRunning).forEach(WrappedGoal::stop);
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack stack = this.inventory.getItem(i);
            if (!stack.isEmpty()) {
                spawnAtLocation(stack.copy());
                this.inventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID ownerUuid = getOwnerUuid();
        if (ownerUuid != null) {
            tag.putUUID(TAG_OWNER, ownerUuid);
        }
        tag.putBoolean(TAG_WAITING, isWaiting());
        tag.put(TAG_INVENTORY, ContainerHelper.saveAllItems(new CompoundTag(), this.inventory.getItems(), registryAccess()));
        tag.putString(TAG_JOB, getJob().name());
        tag.putInt(TAG_LEVEL, getNpcLevel());
        tag.putInt(TAG_XP, getXp());
        tag.putString(TAG_NPC_NAME, getNpcName());
        MiningRegion region = getMiningRegion();
        if (region != null) {
            CompoundTag task = new CompoundTag();
            task.put(TAG_TASK_MIN, NbtUtils.writeBlockPos(region.min()));
            task.put(TAG_TASK_MAX, NbtUtils.writeBlockPos(region.max()));
            task.putInt(TAG_TASK_REMAINING, getMiningRemaining());
            tag.put(TAG_MINING_TASK, task);
        }
        // The storage block is shared by the mining and the hunting task.
        BlockPos storage = getStoragePos();
        if (storage != null) {
            tag.put(TAG_STORAGE, NbtUtils.writeBlockPos(storage));
        }
        BlockPos huntCenter = getHuntCenter();
        if (huntCenter != null) {
            tag.put(TAG_HUNT_CENTER, NbtUtils.writeBlockPos(huntCenter));
        }
        tag.putString(TAG_HUNT_MODE, getHuntMode().name());
        tag.putInt(TAG_HUNT_KILLS, getHuntKills());
        // A hunter that is resting must not go back to work at half health just because its chunk was unloaded.
        tag.putBoolean(TAG_RETREATING, this.retreating);
        if (isMainHandMirrored()) {
            // Never persist the mirrored tool: the inventory already holds it, a second copy would duplicate it.
            ListTag hands = tag.getList("HandItems", 10);
            if (!hands.isEmpty()) {
                hands.set(0, new CompoundTag());
            }
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setOwnerUuid(tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null);
        this.entityData.set(DATA_WAITING, tag.getBoolean(TAG_WAITING));
        this.inventory.clearContent();
        ContainerHelper.loadAllItems(tag.getCompound(TAG_INVENTORY), this.inventory.getItems(), registryAccess());
        setJob(Job.byName(tag.getString(TAG_JOB)));
        int level = tag.contains(TAG_LEVEL) ? Math.max(1, tag.getInt(TAG_LEVEL)) : 1;
        this.entityData.set(DATA_LEVEL, level);
        this.entityData.set(DATA_XP, Math.max(0, tag.getInt(TAG_XP)));
        // Rank is derived from the level so it can never get out of sync with it.
        this.entityData.set(DATA_RANK, Rank.fromLevel(level).ordinal());
        setNpcName(tag.contains(TAG_NPC_NAME) ? tag.getString(TAG_NPC_NAME) : NpcNames.random(this.random));
        applyRankBonuses();
        readMiningTask(tag);
        readHuntTask(tag);
    }

    private void readMiningTask(CompoundTag tag) {
        this.sentNotices.clear();
        CompoundTag task = tag.getCompound(TAG_MINING_TASK);
        Optional<BlockPos> min = NbtUtils.readBlockPos(task, TAG_TASK_MIN);
        Optional<BlockPos> max = NbtUtils.readBlockPos(task, TAG_TASK_MAX);
        if (min.isPresent() && max.isPresent()) {
            this.entityData.set(DATA_TASK_MIN, min);
            this.entityData.set(DATA_TASK_MAX, max);
            setMiningRemaining(task.getInt(TAG_TASK_REMAINING));
            setTaskStatus(TaskStatus.IDLE);
        } else {
            this.entityData.set(DATA_TASK_MIN, Optional.empty());
            this.entityData.set(DATA_TASK_MAX, Optional.empty());
            setMiningRemaining(0);
            setTaskStatus(TaskStatus.NONE);
        }
        Optional<BlockPos> storage = NbtUtils.readBlockPos(tag, TAG_STORAGE);
        if (storage.isEmpty()) {
            // Saves from before the hunter kept the storage inside the mining task.
            storage = NbtUtils.readBlockPos(task, TAG_TASK_STORAGE);
        }
        this.entityData.set(DATA_STORAGE, storage);
    }

    private void readHuntTask(CompoundTag tag) {
        this.entityData.set(DATA_HUNT_CENTER, NbtUtils.readBlockPos(tag, TAG_HUNT_CENTER));
        setHuntMode(HuntMode.byName(tag.getString(TAG_HUNT_MODE)));
        this.entityData.set(DATA_HUNT_KILLS, Math.max(0, tag.getInt(TAG_HUNT_KILLS)));
        this.retreating = getHuntCenter() != null && tag.getBoolean(TAG_RETREATING);
        this.pendingLootCenter = null;
        if (getHuntCenter() != null && getTaskStatus() == TaskStatus.NONE) {
            setTaskStatus(TaskStatus.IDLE);
        }
    }
}
