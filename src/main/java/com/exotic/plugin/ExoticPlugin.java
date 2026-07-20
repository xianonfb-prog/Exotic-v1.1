package com.exotic.plugin;

import org.bukkit.plugin.java.JavaPlugin;

public class ExoticPlugin extends JavaPlugin {

    private CooldownManager cooldownManager;
    private TrialSystem trialSystem;
    private ScoreboardManager scoreboardManager;
    private PersistenceManager persistenceManager;

    @Override
    public void onEnable() {
        cooldownManager = new CooldownManager();
        trialSystem = new TrialSystem(this);
        scoreboardManager = new ScoreboardManager(this);
        persistenceManager = new PersistenceManager(this);

        CombatListener combat = new CombatListener(this);
        getServer().getPluginManager().registerEvents(combat, this);
        getServer().getPluginManager().registerEvents(new PassiveListener(this, combat), this);
        getServer().getPluginManager().registerEvents(new SoulboundListener(this), this);

        CommandHandler handler = new CommandHandler(this);
        getCommand("exotic").setExecutor(handler);
        getCommand("exotic").setTabCompleter(handler);

        new PassiveTickTask(this, combat).runTaskTimer(this, 20L, 20L);
        new AbilityParticleTask(combat).runTaskTimer(this, 0L, 4L);

        // Restore trials/cooldowns from disk (survives restarts)
        persistenceManager.load();

        // Autosave every 5 minutes in case of a crash between restarts
        getServer().getScheduler().runTaskTimer(this, () -> persistenceManager.save(), 6000L, 6000L);

        getLogger().info("Exotic enabled - 6 swords + Staff of Absolute Zero loaded.");
    }

    @Override
    public void onDisable() {
        if (persistenceManager != null) persistenceManager.save();
        getLogger().info("Exotic disabled.");
    }

    public CooldownManager cooldowns() { return cooldownManager; }
    public TrialSystem trials() { return trialSystem; }
    public ScoreboardManager scoreboards() { return scoreboardManager; }
}
