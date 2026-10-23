package com.simsek.simsekordu.entity.goal;

import com.simsek.simsekordu.entity.SimsekNpc;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

public class FollowOwnerGoal extends Goal {
    private final SimsekNpc npc;
    private final double speedModifier;
    private final double startDistanceSqr;
    private final double stopDistanceSqr;
    private Player owner;
    private int recalcTime;

    public FollowOwnerGoal(SimsekNpc npc, double speedModifier, float startDistance, float stopDistance) {
        this.npc = npc;
        this.speedModifier = speedModifier;
        this.startDistanceSqr = startDistance * startDistance;
        this.stopDistanceSqr = stopDistance * stopDistance;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Player player = this.npc.getOwner();
        if (player == null || player.isSpectator() || this.npc.distanceToSqr(player) < this.startDistanceSqr) {
            return false;
        }
        this.owner = player;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.owner.isAlive()
                && !this.owner.isSpectator()
                && !this.npc.getNavigation().isDone()
                && this.npc.distanceToSqr(this.owner) > this.stopDistanceSqr;
    }

    @Override
    public void start() {
        this.recalcTime = 0;
    }

    @Override
    public void stop() {
        this.owner = null;
        this.npc.getNavigation().stop();
    }

    @Override
    public void tick() {
        this.npc.getLookControl().setLookAt(this.owner, 10.0F, this.npc.getMaxHeadXRot());
        if (--this.recalcTime <= 0) {
            this.recalcTime = this.adjustedTickDelay(10);
            this.npc.getNavigation().moveTo(this.owner, this.speedModifier);
        }
    }
}
