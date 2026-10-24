package com.simsek.simsekordu.entity;

import com.simsek.simsekordu.SimsekOrdu;
import com.simsek.simsekordu.entity.goal.HuntHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * Keeps soldiers and players from hurting each other and credits a soldier with the kills it makes:
 * <ul>
 *   <li>players (creative mode excepted) and other soldiers cannot hurt a soldier,</li>
 *   <li>a soldier cannot hurt a player or another soldier, and its arrows only hit what a hunter may kill.</li>
 * </ul>
 * Damage that has no attacker (fall, fire, void, /kill) and damage from other mobs is not affected.
 */
@EventBusSubscriber(modid = SimsekOrdu.MODID)
public final class CombatEvents {
    private CombatEvents() {
    }

    /** Whether {@code attacker} may not hurt {@code victim}. */
    public static boolean isFriendlyFire(Entity attacker, Entity victim) {
        if (attacker == null || attacker == victim) {
            return false;
        }
        boolean victimIsSoldier = victim instanceof SimsekNpc;
        if (victimIsSoldier && attacker instanceof SimsekNpc) {
            return true;
        }
        if (victimIsSoldier && attacker instanceof Player player) {
            return !player.getAbilities().instabuild;
        }
        return attacker instanceof SimsekNpc && (victim instanceof Player || victim instanceof SimsekNpc);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (isFriendlyFire(event.getSource().getEntity(), event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Stops a player's swing at a soldier altogether (no hit sound, no damage to the weapon). */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (isFriendlyFire(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
        }
    }

    /**
     * A soldier's arrow only hits what a hunter may kill; it flies through players, other soldiers, creepers, villagers,
     * pets and the like. A player's arrow flies through soldiers.
     */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) {
            return;
        }
        Entity owner = event.getProjectile().getOwner();
        if (isFriendlyFire(owner, hit.getEntity()) || (owner instanceof SimsekNpc && !HuntHelper.mayShootAt(hit.getEntity()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (event.getSource().getEntity() instanceof SimsekNpc killer && !(victim instanceof Player)
                && !(victim instanceof SimsekNpc) && !killer.level().isClientSide) {
            killer.onHuntKill(victim);
        }
    }
}
