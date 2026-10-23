package com.simsek.simsekordu.entity;

import com.simsek.simsekordu.SimsekOrdu;
import com.simsek.simsekordu.data.Job;
import com.simsek.simsekordu.data.NpcNames;
import com.simsek.simsekordu.data.Rank;
import com.simsek.simsekordu.entity.goal.FollowOwnerGoal;
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

    @Nullable
    private UUID ownerUuid;

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
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide) {
            if (this.ownerUuid == null) {
                this.ownerUuid = player.getUUID();
                player.displayClientMessage(Component.translatable("message.simsekordu.owner_set"), true);
            } else if (this.ownerUuid.equals(player.getUUID())) {
                player.displayClientMessage(Component.translatable("message.simsekordu.already_yours"), true);
            } else {
                player.displayClientMessage(Component.translatable("message.simsekordu.owned_by_other"), true);
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
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
            onLevelUp();
        }
    }

    private void onLevelUp() {
        Rank oldRank = getRank();
        Rank newRank = Rank.fromLevel(getNpcLevel());
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

        if (promoted && this.ownerUuid != null && getServer() != null) {
            ServerPlayer owner = getServer().getPlayerList().getPlayer(this.ownerUuid);
            if (owner != null) {
                owner.sendSystemMessage(Component.translatable("message.simsekordu.rank_up",
                        getNpcName(), Component.translatable(newRank.getTranslationKey())));
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
        return this.ownerUuid;
    }

    @Nullable
    public Player getOwner() {
        return this.ownerUuid == null ? null : this.level().getPlayerByUUID(this.ownerUuid);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.ownerUuid != null) {
            tag.putUUID(TAG_OWNER, this.ownerUuid);
        }
        tag.putString(TAG_JOB, getJob().name());
        tag.putInt(TAG_LEVEL, getNpcLevel());
        tag.putInt(TAG_XP, getXp());
        tag.putString(TAG_NPC_NAME, getNpcName());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.ownerUuid = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
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
