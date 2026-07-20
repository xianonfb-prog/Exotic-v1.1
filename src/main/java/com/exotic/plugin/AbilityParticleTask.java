package com.exotic.plugin;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Fast-interval (every 4 ticks) particle effects for the duration-based abilities.
 * Per design: Judgement and Deception get no continuous particles - only their
 * one-time activation bursts (handled in PassiveListener).
 */
public class AbilityParticleTask extends BukkitRunnable {

    private final CombatListener combat;
    private static final Particle.DustOptions GOLD_DUST = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.2f);
    private static final Particle.DustOptions LIGHT_BLUE_DUST = new Particle.DustOptions(Color.fromRGB(120, 210, 255), 1.1f);
    private static final Particle.DustOptions BLOOD_MOON_DUST = new Particle.DustOptions(Color.fromRGB(140, 10, 10), 1.3f);

    private int tick = 0;

    public AbilityParticleTask(CombatListener combat) {
        this.combat = combat;
    }

    @Override
    public void run() {
        tick += 4;
        long now = System.currentTimeMillis();
        double angle = Math.toRadians(tick * 12); // spiral rotation speed

        // Sword3 - Hypersonic: light blue particles circling in a spiral
        for (var entry : combat.hypersonicActive.entrySet()) {
            if (entry.getValue() <= now) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            spawnSpiral(player, LIGHT_BLUE_DUST, angle, 0.9);
        }

        // Sword5 - Imperial Decree: golden aura
        for (var entry : combat.decreeActive.entrySet()) {
            if (entry.getValue() <= now) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            player.getWorld().spawnParticle(Particle.DUST,
                    player.getLocation().add(0, 1.2, 0), 8, 0.4, 0.6, 0.4, 0, GOLD_DUST);
        }

        // Sword6 - Blood Moon: dark red spiral, wider than Hypersonic's
        for (var entry : combat.bloodMoonActive.entrySet()) {
            if (entry.getValue() <= now) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            spawnSpiral(player, BLOOD_MOON_DUST, angle, 1.4);
        }
    }

    private void spawnSpiral(Player player, Particle.DustOptions dust, double angle, double radius) {
        Location base = player.getLocation().add(0, 1.0, 0);
        for (int i = 0; i < 2; i++) {
            double a = angle + (i * Math.PI);
            double x = Math.cos(a) * radius;
            double z = Math.sin(a) * radius;
            double y = ((tick / 4) % 20) / 20.0; // slow vertical rise loop
            player.getWorld().spawnParticle(Particle.DUST, base.clone().add(x, y, z), 1, 0, 0, 0, 0, dust);
        }
    }
}
