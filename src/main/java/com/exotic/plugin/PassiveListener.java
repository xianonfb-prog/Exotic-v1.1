package com.exotic.plugin;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.raid.RaidStopEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PassiveListener implements Listener {

    private final ExoticPlugin plugin;
    private final CombatListener combat;

    // sword2: entities recently knocked airborne by a swarm cat - their next fall
    // damage ignores Feather Falling. player -> expiry epoch millis (per-victim keyed below)
    private final Map<UUID, Long> featherIgnoreUntil = new HashMap<>();

    public PassiveListener(ExoticPlugin plugin, CombatListener combat) {
        this.plugin = plugin;
        this.combat = combat;
    }

    // ---------------------------------------------------------------
    // Shift + Right-Click ability activation
    // ---------------------------------------------------------------
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();

        if (combat.frozen.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (!player.isSneaking()) return;

        String itemId = SwordUtil.getSwordId(player.getInventory().getItemInMainHand());
        if (itemId == null) return;

        ExoticItem item = ExoticItem.byId(itemId);
        if (item == null) return;

        if (plugin.cooldowns().isOnCooldown(player.getUniqueId(), itemId)) {
            long secs = plugin.cooldowns().remainingMs(player.getUniqueId(), itemId) / 1000;
            player.sendMessage(Component.text(item.styledName() + " is on cooldown: " + secs + "s", NamedTextColor.RED));
            return;
        }

        switch (itemId) {
            case "sword1" -> activateKarmicRetribution(player);
            case "sword2" -> activateKittySwarm(player);
            case "sword3" -> activateSpeedIsMySpecialty(player);
            case "sword4" -> activateLurker(player);
            case "sword5" -> activateImperialDecree(player);
            case "sword6" -> activateBloodMoon(player);
            case "tome1" -> activateIceAge(player);
        }
    }

    // ---------------------------------------------------------------
    // Plain Left-Click ability activation (Staff's beam, Hades' Lament's summon)
    // ---------------------------------------------------------------
    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        if (combat.frozen.contains(player.getUniqueId())) return;

        String itemId = SwordUtil.getSwordId(player.getInventory().getItemInMainHand());
        if (itemId == null) return;

        if (itemId.equals("tome1")) {
            activateAbsoluteZeroBeam(player);
        } else if (itemId.equals("sword6")) {
            activateSummonUndead(player);
        }
    }

    // Sword1 - Judgement: 25s invincibility + reflect
    private void activateKarmicRetribution(Player player) {
        plugin.cooldowns().trigger(player.getUniqueId(), "sword1");
        long until = System.currentTimeMillis() + 25_000L;
        combat.retributionActive.put(player.getUniqueId(), until);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.7f);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 40, 0.6, 1.0, 0.6, 0.05);
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 40, 0.6, 1.0, 0.6, 0.05);
        player.sendMessage(Component.text("Karmic Retribution activated! Invincible for 25 seconds.", NamedTextColor.GOLD));
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> combat.retributionActive.remove(player.getUniqueId()), 500L);
    }

    // Sword2 - Pretty Kitty Princess Blade: 14 cats, 4 dmg (nerfed from 16), Speed 2, 4 min cooldown
    private void activateKittySwarm(Player player) {
        plugin.cooldowns().trigger(player.getUniqueId(), "sword2");
        combat.swarmActive.put(player.getUniqueId(), System.currentTimeMillis() + 30_000L);
        player.sendMessage(Component.text("Kitty Swarm summoned!", NamedTextColor.LIGHT_PURPLE));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_CAT_PURR, 1f, 1.4f);
        player.getWorld().playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.6f);
        Location base = player.getLocation();

        base.getWorld().spawnParticle(Particle.HEART, base.clone().add(0, 1, 0), 40, 1.2, 1.0, 1.2, 0.1);

        for (int i = 0; i < 14; i++) {
            Cat cat = (Cat) base.getWorld().spawnEntity(base.clone().add(
                    (Math.random() - 0.5) * 3, 0, (Math.random() - 0.5) * 3), EntityType.CAT);
            cat.setOwner(player);
            cat.getAttribute(AttributeKeys.ATTACK_DAMAGE).setBaseValue(4.0);
            cat.getAttribute(AttributeKeys.MOVEMENT_SPEED).setBaseValue(0.35);
            cat.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1, false, false)); // Speed 2
            cat.setCustomName(player.getName() + "'s Kitty");
            cat.setCustomNameVisible(false);
            cat.getWorld().spawnParticle(Particle.HEART, cat.getLocation().add(0, 0.5, 0), 8, 0.3, 0.3, 0.3, 0.05);
            new SwarmCatAI(plugin, cat, player, 12.0, 600L, this).runTaskTimer(plugin, 0L, 5L);
        }
    }

    /** Called by SwarmCatAI whenever a cat lands a hit, to mark that target's next fall as Feather-Falling-ignoring. */
    public void markFeatherIgnore(LivingEntity target) {
        featherIgnoreUntil.put(target.getUniqueId(), System.currentTimeMillis() + 3000L);
    }

    // Sword3 - Hypersonic Devastator: Speed 7, 35s, auto-crit, immune to water slow, full-block step-up
    private void activateSpeedIsMySpecialty(Player player) {
        plugin.cooldowns().trigger(player.getUniqueId(), "sword3");
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 700, 6, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 700, 0, false, true));
        combat.hypersonicActive.put(player.getUniqueId(), System.currentTimeMillis() + 35_000L);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 1.8f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.8f, 1.6f);
        player.sendMessage(Component.text("\"Speed is my specialty.\"", NamedTextColor.AQUA));

        var stepHeight = player.getAttribute(AttributeKeys.STEP_HEIGHT);
        double originalStep = stepHeight != null ? stepHeight.getBaseValue() : 0.6;
        if (stepHeight != null) stepHeight.setBaseValue(1.0);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            combat.hypersonicActive.remove(player.getUniqueId());
            var step = player.getAttribute(AttributeKeys.STEP_HEIGHT);
            if (step != null) step.setBaseValue(originalStep);
        }, 700L);
    }

    // Sword4 - Deception: TRUE invisibility
    private void activateLurker(Player player) {
        plugin.cooldowns().trigger(player.getUniqueId(), "sword4");
        combat.lurkerActive.put(player.getUniqueId(), System.currentTimeMillis() + 25_000L);
        combat.trueInvisible.add(player.getUniqueId());
        VisibilityManager.hideFromEveryone(plugin, player);
        plugin.scoreboards().hideNameTag(player, 500L);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_VEX_AMBIENT, 0.5f, 0.5f);
        player.getWorld().playSound(player.getLocation(), Sound.AMBIENT_CAVE, 0.6f, 0.6f);
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.03);
        player.sendMessage(Component.text("You slip into the shadows.", NamedTextColor.DARK_RED));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            combat.trueInvisible.remove(player.getUniqueId());
            VisibilityManager.showToEveryone(plugin, player);
        }, 500L);
    }

    // Sword5 - Bane Of The Emperor: Imperial Decree
    private void activateImperialDecree(Player player) {
        plugin.cooldowns().trigger(player.getUniqueId(), "sword5");
        combat.decreeActive.put(player.getUniqueId(), System.currentTimeMillis() + 30_000L);
        player.sendMessage(Component.text("Imperial Decree issued. None shall stand against the throne.", NamedTextColor.RED));
        player.getWorld().playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 1f, 0.6f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 0.8f, 0.4f);

        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 1, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 600, 1, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 600, 2, false, true));

        for (Entity e : player.getNearbyEntities(12, 12, 12)) {
            boolean isEnemyPlayer = e instanceof Player p && !p.equals(player);
            boolean isHostile = e instanceof Monster;
            if (!isEnemyPlayer && !isHostile) continue;
            if (e instanceof LivingEntity le) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 240, 2, false, true));
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 240, 2, false, true));
                le.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 240, 2, false, true));
            }
        }
    }

    // Sword6 - Hades' Lament: Blood Moon (25s, +4 summon cap, 5s summon cooldown, lifesteal)
    private void activateBloodMoon(Player player) {
        plugin.cooldowns().trigger(player.getUniqueId(), "sword6");
        combat.bloodMoonActive.put(player.getUniqueId(), System.currentTimeMillis() + 25_000L);
        player.sendMessage(Component.text("Blood Moon rises.", NamedTextColor.DARK_RED));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.7f, 0.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 0.8f);
        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().add(0, 1, 0), 30, 0.5, 1.0, 0.5, 0.02);
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> combat.bloodMoonActive.remove(player.getUniqueId()), 500L);
    }

    // Sword6 - Summon Undead (base left-click ability)
    private void activateSummonUndead(Player player) {
        if (plugin.cooldowns().isOnCooldown(player.getUniqueId(), "sword6_summon")) return;

        boolean bloodMoon = combat.bloodMoonActive.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
        int cap = bloodMoon ? 9 : 5;
        long cooldownMs = bloodMoon ? 5000L : 10000L;

        long alive = countAliveSummons(player);
        if (alive >= cap) return;

        plugin.cooldowns().trigger(player.getUniqueId(), "sword6_summon", cooldownMs);
        new UndeadSummonTask(plugin, player, player.getLocation()).start();
    }

    private long countAliveSummons(Player owner) {
        long count = 0;
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (Entity e : world.getEntitiesByClass(LivingEntity.class)) {
                if (!e.getScoreboardTags().contains("exotic_hades_summon")) continue;
                String ownerStr = e.getPersistentDataContainer().get(CombatListener.HADES_OWNER, org.bukkit.persistence.PersistentDataType.STRING);
                if (ownerStr != null && ownerStr.equals(owner.getUniqueId().toString()) && !e.isDead()) count++;
            }
        }
        return count;
    }

    // Tome1 - Staff Of Absolute Zero: Ice Age (shift+right-click)
    private void activateIceAge(Player player) {
        plugin.cooldowns().trigger(player.getUniqueId(), "tome1");
        player.sendMessage(Component.text("Ice Age unleashed!", NamedTextColor.AQUA));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.8f);
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation(), 60, 5, 1, 5, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(20, 60, 140), 1.5f));
        for (Entity e : player.getNearbyEntities(15, 15, 15)) {
            if (e.equals(player)) continue;
            if (!(e instanceof LivingEntity le)) continue;
            new IceCageTask(plugin, combat, le, 120L).start();
        }
    }

    // Tome1 - Absolute Zero Beam (base left-click ability, gated by full attack charge)
    private void activateAbsoluteZeroBeam(Player player) {
        float cooldownProgress = player.getAttackCooldown();
        if (cooldownProgress < 0.95f) return; // must be fully charged

        org.bukkit.util.RayTraceResult trace = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), player.getEyeLocation().getDirection(), 20,
                entity -> entity instanceof LivingEntity && !entity.equals(player));

        Location eye = player.getEyeLocation();
        Location end = trace != null ? trace.getHitPosition().toLocation(player.getWorld()) : eye.clone().add(eye.getDirection().multiply(20));

        // Beam particle trail
        double distance = eye.distance(end);
        org.bukkit.util.Vector direction = end.toVector().subtract(eye.toVector()).normalize();
        for (double d = 0; d < distance; d += 0.5) {
            Location point = eye.clone().add(direction.clone().multiply(d));
            player.getWorld().spawnParticle(Particle.DUST, point, 2, 0, 0, 0, 0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(100, 200, 255), 1.0f));
        }
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.6f, 1.8f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1.4f);

        if (trace != null && trace.getHitEntity() instanceof LivingEntity target) {
            double dmg = 4.0 + Math.random() * 1.0; // 2-2.5 hearts, ignores armor entirely
            double newHealth = Math.max(0, target.getHealth() - dmg);
            target.setHealth(newHealth);
            target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(100, 200, 255), 1.2f));
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_HURT, 1f, 1.6f);
        }
    }

    // ---------------------------------------------------------------
    // Sword2 - Kitty Love: no fall damage; feather-falling ignored briefly after cat knockback
    // ---------------------------------------------------------------
    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

        if (event.getEntity() instanceof Player player && SwordUtil.hasSwordInInventory(player, SwordType.SWORD2)) {
            event.setCancelled(true);
            return;
        }

        Long until = featherIgnoreUntil.get(event.getEntity().getUniqueId());
        if (until != null && until > System.currentTimeMillis()) {
            featherIgnoreUntil.remove(event.getEntity().getUniqueId());
            event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, 0.0);
            event.setDamage(EntityDamageEvent.DamageModifier.MAGIC, 0.0); // covers Feather Falling's reduction path
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (event.getTo() == null) return;
        if (event.getTo().getBlock().getType() != Material.COBWEB) return;
        if (!SwordUtil.hasSwordInInventory(player, SwordType.SWORD2)) return;

        org.bukkit.util.Vector v = player.getVelocity();
        player.setVelocity(v.multiply(2.5).setY(Math.max(v.getY(), 0.1)));
    }

    // ---------------------------------------------------------------
    // Sword5 - Emperor's Charisma / Sword6 - Hades' summons don't target their owner
    // ---------------------------------------------------------------
    private static final List<EntityType> BOSS_TYPES = List.of(
            EntityType.ENDER_DRAGON, EntityType.WITHER, EntityType.WARDEN, EntityType.ELDER_GUARDIAN
    );

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player)) return;

        // Hades' Lament summons never attack their own owner
        if (event.getEntity().getScoreboardTags().contains("exotic_hades_summon")) {
            String ownerStr = event.getEntity().getPersistentDataContainer()
                    .get(CombatListener.HADES_OWNER, org.bukkit.persistence.PersistentDataType.STRING);
            if (ownerStr != null && ownerStr.equals(player.getUniqueId().toString())) {
                event.setCancelled(true);
            }
            return;
        }

        if (BOSS_TYPES.contains(event.getEntityType())) return;
        if (event.getEntity().getScoreboardTags().contains("exotic_summoned")) return;
        if (!(event.getEntity() instanceof Monster) && !(event.getEntity() instanceof Animals)) return;
        if (SwordUtil.hasSwordInInventory(player, SwordType.SWORD5)) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------
    // Block enchanting for unenchantable items (Hades' Lament, Staff of Absolute Zero)
    // ---------------------------------------------------------------
    @EventHandler
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (!SwordUtil.isEnchantable(event.getItem()) && SwordUtil.getSwordId(event.getItem()) != null) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------
    // Re-hide true-invisible players from anyone who joins mid-effect
    // ---------------------------------------------------------------
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        if (combat.trueInvisible.isEmpty()) return;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (combat.trueInvisible.contains(p.getUniqueId()) && !p.equals(joined)) {
                joined.hidePlayer(plugin, p);
            }
        }
    }

    // ---------------------------------------------------------------
    // Trial progress hooks
    // ---------------------------------------------------------------
    @EventHandler
    public void onEntityTame(EntityTameEvent event) {
        if (event.getEntityType() != EntityType.CAT) return;
        if (!(event.getOwner() instanceof Player player)) return;
        plugin.trials().progress(player, TrialSystem.ObjectiveType.CATS_TAMED, 1);
    }

    @EventHandler
    public void onVillagerDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        if (event.getEntityType() == EntityType.VILLAGER) {
            plugin.trials().progress(killer, TrialSystem.ObjectiveType.VILLAGER_KILLS, 1);
        }

        if (isUndeadType(event.getEntityType())) {
            plugin.trials().progress(killer, TrialSystem.ObjectiveType.UNDEAD_KILLS, 1);
        }
    }

    private boolean isUndeadType(EntityType type) {
        return type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED
                || type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.WITHER_SKELETON
                || type == EntityType.ZOMBIE_VILLAGER || type == EntityType.PHANTOM
                || type == EntityType.ZOMBIFIED_PIGLIN;
    }

    @EventHandler
    public void onPotionConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return;
        PotionType baseType = meta.getBasePotionType();
        if (baseType == null) return;

        Player player = event.getPlayer();
        String name = baseType.name();
        if (name.contains("SPEED") || name.contains("SWIFTNESS")) {
            plugin.trials().progress(player, TrialSystem.ObjectiveType.SPEED_POTIONS_DRUNK, 1);
        } else if (name.contains("INVISIBILITY")) {
            plugin.trials().progress(player, TrialSystem.ObjectiveType.INVIS_POTIONS_DRUNK, 1);
        }
    }

    @EventHandler
    public void onRaidStop(RaidStopEvent event) {
        if (event.getRaid().getStatus() != org.bukkit.Raid.RaidStatus.VICTORY) return;
        for (UUID heroId : event.getRaid().getHeroes()) {
            Player player = org.bukkit.Bukkit.getPlayer(heroId);
            if (player != null) {
                plugin.trials().progress(player, TrialSystem.ObjectiveType.RAID_WON, 1);
            }
        }
    }

    @EventHandler
    public void onIcePickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack picked = event.getItem().getItemStack();
        if (picked.getType() != Material.ICE) return;
        plugin.trials().progress(player, TrialSystem.ObjectiveType.ICE_BLOCKS_COLLECTED, picked.getAmount());
    }

    // ---------------------------------------------------------------
    // Prevent renaming exotic swords (anvil)
    // ---------------------------------------------------------------
    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getItem(0);
        if (left != null && SwordUtil.getSwordId(left) != null) {
            event.setResult(null);
        }
    }
}
