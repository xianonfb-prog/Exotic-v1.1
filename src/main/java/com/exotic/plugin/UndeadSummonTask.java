package com.exotic.plugin;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

public class UndeadSummonTask extends BukkitRunnable {

    private static final long RISE_TICKS = 60; // 3 seconds
    private static final Random RANDOM = new Random();

    private final ExoticPlugin plugin;
    private final Player owner;
    private final Location location;
    private final EntityType type;
    private long elapsed = 0;

    public UndeadSummonTask(ExoticPlugin plugin, Player owner, Location location) {
        this.plugin = plugin;
        this.owner = owner;
        this.location = location;
        this.type = RANDOM.nextBoolean() ? EntityType.SKELETON : EntityType.ZOMBIE;
    }

    public void start() {
        location.getWorld().playSound(location, Sound.ENTITY_ZOMBIE_INFECT, 1f, 0.6f);
        runTaskTimer(plugin, 0L, 5L);
    }

    @Override
    public void run() {
        elapsed += 5;
        location.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, location.clone().add(0, 0.2, 0),
                12, 0.3, 0.1, 0.3, 0, location.getBlock().getRelative(0, -1, 0).getBlockData());
        location.getWorld().spawnParticle(Particle.SOUL, location.clone().add(0, 0.5, 0), 3, 0.2, 0.3, 0.2, 0.01);

        if (elapsed >= RISE_TICKS) {
            spawnFinal();
            cancel();
        }
    }

    private void spawnFinal() {
        if (!owner.isOnline()) return;
        LivingEntity mob = (LivingEntity) location.getWorld().spawnEntity(location, type);

        double health = type == EntityType.SKELETON ? 70.0 : 100.0;
        double damage = type == EntityType.SKELETON ? 19.0 : 14.0;

        mob.getAttribute(AttributeKeys.MAX_HEALTH).setBaseValue(health);
        mob.setHealth(health);
        mob.getAttribute(AttributeKeys.ATTACK_DAMAGE).setBaseValue(damage);
        mob.getAttribute(AttributeKeys.MOVEMENT_SPEED).setBaseValue(0.35); // "very fast"
        mob.getAttribute(AttributeKeys.ATTACK_KNOCKBACK).setBaseValue(0.0); // no knockback

        mob.getPersistentDataContainer().set(CombatListener.HADES_OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());
        mob.addScoreboardTag("exotic_summoned");
        mob.addScoreboardTag("exotic_hades_summon");
        mob.setCustomName(owner.getName() + "'s " + (type == EntityType.SKELETON ? "Skeleton" : "Zombie"));
        mob.setCustomNameVisible(false);
        mob.setRemoveWhenFarAway(false);

        location.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, location.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
        location.getWorld().playSound(location, Sound.ENTITY_ZOMBIE_AMBIENT, 1f, 0.5f);
    }
}
