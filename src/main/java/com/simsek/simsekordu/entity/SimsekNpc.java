package com.simsek.simsekordu.entity;

import com.simsek.simsekordu.SimsekOrdu;
import com.simsek.simsekordu.data.Job;
import com.simsek.simsekordu.data.NpcNames;
import com.simsek.simsekordu.data.Rank;
import com.simsek.simsekordu.entity.goal.FollowOwnerGoal;
import com.simsek.simsekordu.menu.SimsekNpcMenu;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

public class SimsekNpc extends PathfinderMob {
    private static final String TAG_OWNER = "Owner";
    private static final String TAG_JOB = "Job";
    private static final String TAG_LEVEL = "Level";
    private static final String TAG_XP = "Xp";
    private static final String TAG_NPC_NAME = "NpcName";
    private static final String TAG_WAITING = "Waiting";
    private static final String TAG_INVENTORY = "Inventory";

    public static final int INVENTORY_SIZE = 9;

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

    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    /** Number of players that currently have this NPC's menu open (server side only). */
    private int menuUsers;

    public SimsekNpc(EntityType<? extends SimsekNpc> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
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

        UUID ownerUuid = getOwnerUuid();
        if (promoted && ownerUuid != null && getServer() != null) {
            ServerPlayer owner = getServer().getPlayerList().getPlayer(ownerUuid);
            if (owner != null) {
                // One message per rank gained, in case several ranks were skipped at once.
                for (Rank rank : Rank.values()) {
                    if (rank.ordinal() > oldRank.ordinal() && rank.ordinal() <= newRank.ordinal()) {
                        owner.sendSystemMessage(Component.translatable("message.simsekordu.rank_up",
                                getNpcName(), Component.translatable(rank.getTranslationKey())));
                    }
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
    }
}
