package nsk.enhanced;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import nsk.enhanced.Events.OnBlockEvent;
import nsk.enhanced.Events.OnPlayerInteractEvent;
import nsk.enhanced.System.EnhancedLogger;
import nsk.enhanced.Tags.Annotations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class EnhancedOres extends JavaPlugin implements Listener, CommandExecutor {

    private EnhancedLogger enhancedLogger;

    private File configFile;
    private FileConfiguration config;

    private File translationsFile;
    private FileConfiguration translations;

    private File effectsFile;
    private FileConfiguration effects;

    private SessionFactory sessionFactory;

    private final List<Region> regions = new ArrayList<>();
    private final List<Miner> miners = new ArrayList<>();

    private boolean devmove = false;

    @Override
    public void onEnable() {

        PluginInstance.setInstance(this);

        enhancedLogger = new EnhancedLogger(this);

        loadConfiguration();
        loadTranslations();
        loadEffects();

        createRegionsFolder();

        configureHibernate();
        loadRegionsFromDatabase();

        // --- --- --- --- // Events Managers & Listeners // --- --- --- --- //
        // Events Listeners
        OnPlayerInteractEvent onPlayerInteractEvent = new OnPlayerInteractEvent();
        OnBlockEvent onBlockInteractEvent = new OnBlockEvent();


        try {
            getServer().getPluginManager().registerEvents(onPlayerInteractEvent, this);
            enhancedLogger.fine("onPlayerInteractEvent registered");
        } catch (Exception e) {
            enhancedLogger.severe("Registration onPlayerInteractEvent failed. - " + e.getMessage());
        }

        try {
            getServer().getPluginManager().registerEvents(onBlockInteractEvent, this);
            enhancedLogger.fine("onBlockInteractEvent registered");
        } catch (Exception e) {
            enhancedLogger.severe("Registration onBlockInteractEvent failed. - " + e.getMessage());
        }

        PluginCommand command = this.getCommand("eo");
        if (command != null) {
            command.setExecutor(this);
            enhancedLogger.fine("Commands registered");
        } else {
            enhancedLogger.severe("Command 'eo' is not registered");
        }

        getServer().getPluginManager().registerEvents(this, this);

        startAutoSaveTask();
        startMinerCheckTask();

        enhancedLogger.info("AutoSave loop started");
    }

    @Override
    public void onDisable() {
        enhancedLogger.warning("Preparing to save all regions...");
        int maxAttempts = 9;

        try {
            saveAllEntitiesWithRetry(regions, maxAttempts).thenAccept(result -> {
                if (result) {
                    enhancedLogger.fine("Saved all regions successfully");
                } else {
                    enhancedLogger.severe("Saved all regions failed after " + maxAttempts + " attempts");
                }
            }).get();
        } catch (Exception e) {
            enhancedLogger.severe("Failed to save all regions after " + maxAttempts + " attempts. - " + e);
        }
    }

    private void startMinerCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                Iterator<Miner> iterator = miners.iterator();

                while (iterator.hasNext()) {
                    Miner miner = iterator.next();
                    if (currentTime - miner.timestamp > config.getDouble("EnhancedOres.cooldown")) {
                        iterator.remove();

                        Player player = Bukkit.getPlayer(miner.uuid);
                        if (player != null && player.isOnline()) {

                            Component message = MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.onCooldownPass", "<error>'onCooldownPass' not found!"),
                                    Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                    Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                    Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                    Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") )));
                            player.sendActionBar(message);

                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private static class Miner {
        private final UUID uuid;
        private final long timestamp;
        private final Location location;

        public Miner(UUID uuid, long timestamp, Location location) {
            this.uuid = uuid;
            this.timestamp = timestamp;
            this.location = location;
        }
    }

    public void addMiner(Player player, Block block) {
        miners.add(new Miner(player.getUniqueId(), System.currentTimeMillis(), block.getLocation()));
    }

    public boolean isMiner(Player player, Block block) {
        for (Miner miner : miners) {
            if (miner.uuid.equals(player.getUniqueId()) && miner.location.equals(block.getLocation())) {
                return true;
            }
        }

        return false;
    }

    // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- //

    private void loadConfiguration() {
        enhancedLogger.warning("Loading configuration...");
        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        //boolean isEnabled = config.getBoolean("EnhancedOres.settings.enabled");
        //enhancedLogger.info("Config enabled: " + isEnabled);
    }
    private FileConfiguration getConfigFile() {
        return config;
    }
    private void saveConfigFile() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            enhancedLogger.log(Level.SEVERE, "Failed to save config file", e);
        }
    }

    private void loadTranslations() {
        enhancedLogger.warning("Loading translations...");
        translationsFile = new File(getDataFolder(), "translations.yml");
        if (!translationsFile.exists()) {
            translationsFile.getParentFile().mkdirs();
            saveResource("translations.yml", false);
        }

        translations = YamlConfiguration.loadConfiguration(translationsFile);

        //boolean isEnabled = translations.getBoolean("EnhancedOres.settings.enabled");
        //enhancedLogger.info("Translations enabled: " + isEnabled);
    }
    public FileConfiguration getTranslationsFile() {
        return translations;
    }

    private void loadEffects() {
        enhancedLogger.warning("Loading effects...");
        effectsFile = new File(getDataFolder(), "effects.yml");
        if (!effectsFile.exists()) {
            effectsFile.getParentFile().mkdirs();
            saveResource("effects.yml", false);
        }

        effects = YamlConfiguration.loadConfiguration(effectsFile);

        //boolean isEnabled = translations.getBoolean("EnhancedOres.settings.enabled");
        //enhancedLogger.info("Translations enabled: " + isEnabled);
    }
    public FileConfiguration getEffectsFile() {
        return effects;
    }

    private void createRegionsFolder() {
        File regionsFolder = new File(getDataFolder(), "Regions");
        if (!regionsFolder.exists()) {
            regionsFolder.mkdirs();
        }

    }

    // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- //

    private void reloadConfiguration() {
        try {
            loadTranslations();
            loadConfiguration();
            loadEffects();
            enhancedLogger.fine("Reloaded configuration");
            enhancedLogger.info("Preparing to reload all regional configurations.");

            for (Region region : regions) {
                try {
                    region.loadConfiguration();
                } catch (Exception e) {
                    enhancedLogger.severe("Failed to reload regional configuration: " + region.getName() + " " + e.getMessage());
                }
            }

        } catch (Exception e) {
            enhancedLogger.severe("Failed to reload configuration. - " + e);
        }
    }

    // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- //

    private void configureHibernate() {
        enhancedLogger.warning("Configuring Hibernate...");
        try {
            String dialect  = config.getString("EnhancedOres.database.dialect");

            String address  = config.getString("EnhancedOres.database.address");
            String port     = config.getString("EnhancedOres.database.port");
            String database = config.getString("EnhancedOres.database.database");

            String username = config.getString("EnhancedOres.database.username");
            String password = config.getString("EnhancedOres.database.password");

            String show_sql     = config.getString("EnhancedOres.hibernate.show_sql");
            String format_sql   = config.getString("EnhancedOres.hibernate.format_sql");
            String sql_comments = config.getString("EnhancedOres.hibernate.sql_comments");

            Configuration cfg = new Configuration()
                    .setProperty("hibernate.dialect", "org.hibernate.dialect." + dialect)
                    .setProperty("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver")

                    .setProperty("hibernate.connection.url", "jdbc:mysql://" + address + ":" + port + "/" + database)
                    .setProperty("hibernate.connection.username", username)
                    .setProperty("hibernate.connection.password", password)

                    .setProperty("hibernate.hbm2ddl.auto", "update")
                    .setProperty("hibernate.show_sql", show_sql)
                    .setProperty("hibernate.format_sql", format_sql)
                    .setProperty("hibernate.use_sql_comments", sql_comments);

            cfg.addAnnotatedClass(Region.class);

            if (cfg.buildSessionFactory() != null) {
                sessionFactory = cfg.buildSessionFactory();
            } else {
                throw new IllegalStateException("Could not create session factory");
            }

        } catch (Exception e) {
            enhancedLogger.severe("Could not create session factory - " + e.getMessage());
        }
        enhancedLogger.fine("Hibernate loaded");
    }

    // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- //

    private void loadRegionsFromDatabase() {
        enhancedLogger.warning("Loading regions from database...");
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<Region> query = builder.createQuery(Region.class);
            query.from(Region.class);

            List<Region> result = session.createQuery(query).getResultList();
            for (Region region : result) {
                region.initializeConfig();
            }
            regions.addAll(result);

            session.getTransaction().commit();

        } catch (Exception e) {
            enhancedLogger.severe("Could not load regions from database - " + e.getMessage());
        }
        enhancedLogger.fine("Regions loaded");
    }

    private void startAutoSaveTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    enhancedLogger.warning("Starting auto save task");
                    saveAllEntitiesFromListAsync(regions)
                        .exceptionally(e -> {
                            throw new RuntimeException("Failed to save regions into the database", e);
                        }).get();
                } catch (Exception e) {
                    enhancedLogger.severe("Failed to start auto save task - " + e.getMessage());
                }
                enhancedLogger.fine("Auto save task completed");
            }
        }.runTaskTimerAsynchronously(this,0L, 20L * 60 * 15);
    }

    // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- //
    /*
                            888888     88b 88     888888     88     888888     Yb  dP
                            88__       88Yb88       88       88       88        YbdP
                            88""       88 Y88       88       88       88         8P
                            888888     88  Y8       88       88       88         dP
    */
    // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- //

    private <T> void saveEntity(T entity) {
        enhancedLogger.warning("Preparing to save entity: " + entity.getClass().getSimpleName());
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            enhancedLogger.info("Saving entity: " + entity.getClass().getSimpleName());
            session.saveOrUpdate(entity);
            session.getTransaction().commit();
            enhancedLogger.fine("Saved entity: " + entity.getClass().getSimpleName());
        } catch (Exception e) {
            enhancedLogger.severe("Saving entity failed - " + e.getMessage());
        }
    }
    public <T> CompletableFuture<Void> saveEntityAsync(T entity) {

        return CompletableFuture.runAsync(() -> {
            enhancedLogger.warning("Saving entity: " + entity);
            try (Session session = sessionFactory.openSession()) {
                session.beginTransaction();

                enhancedLogger.info("Saving entity: " + entity.getClass().getSimpleName());
                session.saveOrUpdate(entity);
                session.getTransaction().commit();
                enhancedLogger.fine("Saved entity: " + entity.getClass().getSimpleName());
                session.close();
            } catch (Exception e) {
                enhancedLogger.severe("Saving entity failed - " + e.getMessage());
            }

        });
    }
    public <T> CompletableFuture<Void> saveAllEntitiesFromListAsync(List<T> entities) {

        return CompletableFuture.runAsync(() -> {
            enhancedLogger.warning("Saving entities from the list: " + entities);
            try (Session session = sessionFactory.openSession()) {
                session.beginTransaction();

                for (T entity : entities) {
                    enhancedLogger.info("Saving entity: " + entity.getClass().getSimpleName());
                    session.saveOrUpdate(entity);
                }

                session.getTransaction().commit();
                enhancedLogger.fine("Saved entities from the list: " + entities);
                session.close();
            } catch (Exception e) {
                enhancedLogger.severe("Saving entities failed - " + e.getMessage());
            }
        });
    }
    public <T> CompletableFuture<Boolean> saveAllEntitiesWithRetry(List<T> entities, int maxAttempts) {

        return saveAllEntitiesFromListAsync(entities).handle((result, ex) -> {
            if (ex == null) {
                return CompletableFuture.completedFuture(true);
            } else if (maxAttempts > 1) {
                enhancedLogger.warning("Save failed, retrying... Attempts left: " + maxAttempts);
                return saveAllEntitiesWithRetry(entities, maxAttempts - 1);
            } else {
                enhancedLogger.severe("Save failed after maximum attempts: " + ex);
                return CompletableFuture.completedFuture(false);
            }
        }).thenCompose(result -> result);

    }

    private <T> void deleteEntity(T entity) {
        enhancedLogger.warning("Preparing to delete entity: " + entity.getClass().getSimpleName());
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            enhancedLogger.info("Deleting entity: " + entity.getClass().getSimpleName());
            session.delete(entity);
            session.getTransaction().commit();
            enhancedLogger.fine("Deleted entity: " + entity.getClass().getSimpleName());
        } catch (Exception e) {
            enhancedLogger.severe("Deleting entity failed - " + e.getMessage());
        }
    }
    public <T> CompletableFuture<Void> deleteEntityAsync(T entity) {

        return CompletableFuture.runAsync(() -> {
            enhancedLogger.warning("Preparing to delete entity: " + entity.getClass().getSimpleName());
            try (Session session = sessionFactory.openSession()) {
                session.beginTransaction();

                enhancedLogger.info("Deleting entity: " + entity.getClass().getSimpleName());
                session.delete(entity);
                session.getTransaction().commit();
                enhancedLogger.fine("Deleted entity: " + entity.getClass().getSimpleName());
                session.close();
            } catch (Exception e) {
                enhancedLogger.severe("Deleting entity failed - " + e.getMessage());
            }
        });
    }
    public <T> CompletableFuture<Void> deleteAllEntitiesFromListAsync(List<T> entities) {

        return CompletableFuture.runAsync(() -> {
            enhancedLogger.warning("Preparing to delete entities from the list: " + entities);
            try (Session session = sessionFactory.openSession()) {
                session.beginTransaction();

                for (T entity : entities) {
                    enhancedLogger.info("Deleting entity: " + entity.getClass().getSimpleName());
                    session.delete(entity);
                }

                session.getTransaction().commit();
                enhancedLogger.fine("Deleted entities from the list: " + entities);
                session.close();
            } catch (Exception e) {
                enhancedLogger.severe("Deleting entities failed - " + e.getMessage());
            }
        });
    }


    // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- //

    public ItemStack getEconomyItem(Region region, String key) {
        FileConfiguration regionConfig = region.getConfiguration();
        String materialName = regionConfig.getString("Drops." + key + ".material", "GOLD_NUGGET");
        Material material = Material.getMaterial(materialName);

        if (material == null) {
            material = Material.GOLD_NUGGET;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String displaName = regionConfig.getString("Drops." + key + ".item-meta.display-name", "<!italic><gold>Złota moneta");
            Component displayNameComponent = MiniMessage.miniMessage().deserialize("<!italic>" + displaName);
            meta.displayName(displayNameComponent);

            List<String> loreStrings = regionConfig.getStringList("Drops." + key + ".item-meta.lore");
            List<Component> loreComponents = new ArrayList<>();

            for (String lore : loreStrings) {
                loreComponents.add(MiniMessage.miniMessage().deserialize("<!italic>" + lore));
            }
            meta.lore(loreComponents);
            item.setItemMeta(meta);
        }

        return item;
    }

    public List<Material> getOres(Region region, String key) {
        FileConfiguration regionConfig = region.getConfiguration();
        List<String> materialName = regionConfig.getStringList("Drops." + key + ".ores");
        List<Material> materials = new ArrayList<>();

        for (String material : materialName) {
            materials.add(Material.getMaterial( material.toUpperCase() ));
        }

        return materials;
    }

    public List<Region> getRegions() {
        return regions;
    }

    // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- //

    public void playerWarning(Exception e, Player player) {
        player.sendMessage(e.getMessage());
    }

    public void consoleError(Exception e) {
        enhancedLogger.log(Level.SEVERE, "Error: ", e);
    }

    private static boolean isNumeric(String str) {
        return str != null && str.matches("[0-9]+");
    }

    private String getHelp() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<gold>EnhancedOres Usage:</gold>\n")
                .append(" - /eo list - List all regions.\n")
                .append(" - /eo reload - Reload configuration.\n")
                .append(" - /eo region <green>[argument]</green> - Manage regions.\n")
                .append("   - <green><hover:show_text:'<yellow>/eo region check</yellow><gray> - Checks if region contain location.</gray>'>[check]</hover></green> - Hover me for more info.\n")
                .append("   - <green><hover:show_text:'<yellow>/eo region open [id/name]</yellow><gray> - Open/Create region session.</gray>'>[open] [id/name]</hover></green> - Hover me for more info.\n")
                .append("   - <green><hover:show_text:'<yellow>/eo region close</yellow><gray> - Close all player`s sessions.</gray>'>[close]</hover></green> - Hover me for more info.\n")
                .append("   - <green><hover:show_text:'<yellow>/eo region remove [id/name]</yellow><gray> - Remove region.</gray>'>[remove] [id/name]</hover></green> - Hover me for more info.");

        return stringBuilder.toString();
    }

    public boolean getDevmode() {
        return devmove;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("eo")) {

            if (args.length == 0) {
                Component help = MiniMessage.miniMessage().deserialize(getHelp());
                sender.sendMessage(help);
                return false;
            }

            if (!sender.isOp()) {
                Component message = MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.permissionDenied", "<error>'permissionDenied' not found!"),
                        Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                        Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                        Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                        Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") )));
                sender.sendMessage(message);
                return true;
            }

            switch (args[0].toLowerCase()) {

                case "devmode":

                    String dev;

                    if (devmove) {
                        devmove = false;
                        dev = "is now disabled.";
                    } else {
                        devmove = true;
                        dev = "is now enabled.";
                    }

                    Component status = MiniMessage.miniMessage().deserialize(("<gradient:#b28724:#ffc234>[Enhanced Ores]</gradient> <#ffe099>" + dev),
                            Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                            Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                            Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                            Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") )));

                    sender.sendMessage(status);

                    break;

                case "help":

                    Component help = MiniMessage.miniMessage().deserialize(getHelp());
                    sender.sendMessage(help);

                    break;

                case "reload":
                    reloadConfiguration();

                    Component reload = MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.configReloadSuccess", "<error>'configReloadSuccess' not found!"),
                            Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                            Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                            Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                            Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") )));
                    sender.sendMessage(reload);

                    break;

                case "list":
                    StringBuilder list = new StringBuilder();
                    list.append("\n");
                    list.append("Regions: \n");

                    for (Region region : regions) {
                        list.append("[").append(region.getId()).append("] - ");
                        list.append(region.getName()).append("\n");
                    }

                    if (sender instanceof Player) {
                        sender.sendMessage(list.toString());
                    } else {
                        enhancedLogger.info(list.toString());
                    }

                    break;

                case "region":
                    if (sender instanceof Player) {
                        Player player = (Player) sender;

                        int n;

                        switch (args[1].toLowerCase()) {

                            case "check":

                                Location l = player.getLocation();
                                n = 0;

                                for (Region region : regions) {
                                    if (region.contains(l)) {
                                        n++;
                                        Component message = MiniMessage.miniMessage().deserialize("<green>ID: <gray>" + region.getId() + "</gray>, Name: <gray>" + region.getName());
                                        Component pointA = MiniMessage.miniMessage().deserialize("<green> - " + translations.getString("EnhancedOres.messages.prefixPointA", "<error>'prefixPointA' not found!") + region.getPointAString(),
                                                Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                                Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                                Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                                Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") )));
                                        Component pointB = MiniMessage.miniMessage().deserialize("<green> - " + translations.getString("EnhancedOres.messages.prefixPointB", "<error>'prefixPointB' not found!") + region.getPointBString(),
                                                Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                                Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                                Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                                Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") )));

                                        player.sendMessage(message);
                                        if (region.getUser() != null) {
                                            Component user = MiniMessage.miniMessage().deserialize("<green> - session: <gold>" + Bukkit.getPlayer( region.getUser() ).getName());
                                            player.sendMessage(user);
                                        }
                                        player.sendMessage(pointA);
                                        player.sendMessage(pointB);
                                    }
                                }

                                if (n <= 0) {
                                    Component message = MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.notRegion", "<error>'notRegion' not found!"),
                                            Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                            Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                            Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                            Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") )));
                                    player.sendMessage(message);
                                }
                                break;

                            case "open":

                                if (args.length == 3) {

                                    if ( isNumeric( args[2] ) ) {
                                        for (Region region : regions) {
                                            if ( String.valueOf( region.getId() ).equals( args[2] ) ) {

                                                for (Region region2 : regions) {
                                                    if (region2.getUser() != null && region2.getUser().equals(player.getUniqueId())) {
                                                        enhancedLogger.info("User has already active session");
                                                        region2.resetUser();
                                                        enhancedLogger.info("Removed user from previous session.");
                                                    }
                                                }

                                                region.setUser(player);
                                                Component message = MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.sessionOpen", "<error>'sessionOpen' not found!"),
                                                        Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                                        Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                                        Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                                        Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") )));
                                                player.sendMessage(message);
                                                break;
                                            }
                                        }

                                    } else {
                                        n = 0;

                                        for (Region region : regions) {
                                            if (region.getName().equalsIgnoreCase( args[2] )) {
                                                n++;
                                                region.setUser(player);
                                                Component message = MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.sessionOpen", "<error>'sessionOpen' not found!"),
                                                        Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                                        Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                                        Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                                        Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") )));
                                                player.sendMessage(message);
                                                break;
                                            }
                                        }

                                        if (n <= 0) {
                                            try {

                                                for (Region region : regions) {
                                                    if (region.getUser() != null && region.getUser().equals(player.getUniqueId())) {
                                                        region.resetUser();
                                                    }
                                                }

                                                Region region = new Region(player, args[2]);
                                                region.setUser(player);

                                                player.sendMessage(MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.notExistRegion", "<error>'notExistRegion' not found!"),
                                                        Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                                        Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                                        Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                                        Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") ))));
                                                player.sendMessage(MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.creatingRegion") + region.getName(),
                                                        Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                                        Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                                        Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                                        Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") ))));

                                                regions.add(region);

                                                saveEntityAsync(region)
                                                        .thenAccept(success -> {
                                                            player.sendMessage(MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.sessionOpen", "<error>'sessionOpen' not found!"),
                                                                    Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                                                    Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                                                    Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                                                    Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") ))));

                                                            region.createDefaultConfig();
                                                        })
                                                        .exceptionally(e -> {
                                                            regions.remove(region);
                                                            throw new IllegalStateException("Query failed! ", e);
                                                        });
                                            } catch (Exception e) {
                                                player.sendMessage(MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.queryError", "<error>'queryError' not found!"),
                                                        Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                                        Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                                        Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                                        Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") ))));
                                                enhancedLogger.severe(e.getMessage());
                                                break;
                                            }
                                        }
                                    }
                                } else {
                                    player.sendMessage(MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.emptyArgument", "<error>'emptyArgument' not found!"),
                                            Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                            Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                            Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                            Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") ))));
                                }
                                break;

                            case "close":

                                try {

                                    for (Region region : regions) {
                                        if (region.getUser() != null && region.getUser().equals(player.getUniqueId())) {
                                            region.resetUser();

                                            player.sendMessage(MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.sessionClosed", "<error>'sessionClosed' not found!"),
                                                    Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                                    Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                                    Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                                    Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") ))));
                                            break;
                                        }
                                    }

                                } catch (Exception e) {
                                    player.sendMessage(MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.noSession", "<error>'noSession' not found!"),
                                            Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                            Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                            Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                            Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") ))));
                                }

                                break;

                            case "remove":
                                if (args.length == 3) {
                                    try {
                                        int x = 0;
                                        for (Region region : regions) {
                                            if (    String.valueOf( region.getId() ).equals( args[2] ) ||
                                                    region.getName().equalsIgnoreCase(args[2])) {
                                                x++;

                                                deleteEntityAsync(region)
                                                        .thenAccept(success -> {
                                                            regions.remove(region);

                                                            File configFile = new File(getDataFolder(), "Regions" + File.separator + region.getName() + ".yml");

                                                            if (configFile.exists()) {
                                                                if (configFile.delete()) {
                                                                    enhancedLogger.fine("Successfully deleted regional configuration " + region.getName());
                                                                } else {
                                                                    enhancedLogger.severe("Failed to delete regional configuration " + region.getName());
                                                                }
                                                            }

                                                            player.sendMessage(MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.removedRegion", "<error>'removedRegion' not found!"),
                                                                    Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                                                    Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                                                    Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                                                    Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") ))));
                                                        })
                                                        .exceptionally(e -> {
                                                            throw new IllegalStateException("Query failed! ", e);
                                                        });
                                                break;
                                            }
                                        }

                                        if (x == 0) {
                                            player.sendMessage(MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.notExistRegion", "<error>'notExistRegion' not found!"),
                                                    Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                                    Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                                    Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                                    Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") ))));
                                        }

                                    } catch (Exception e) {
                                        player.sendMessage(MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.queryError", "<error>'queryError' not found!"),
                                                Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                                Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                                Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                                Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") ))));
                                        enhancedLogger.severe(e.getMessage());
                                    }
                                } else {
                                    player.sendMessage(MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.emptyArgument", "<error>'emptyArgument' not found!"),
                                            Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                            Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                            Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                            Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") ))));
                                }
                                break;

                            default:
                                Component help2 = MiniMessage.miniMessage().deserialize(getHelp());
                                sender.sendMessage(help2);
                                return true;
                        }

                    } else {
                        sender.sendMessage("[!] This command can only be executed by a player [!]");
                    }
                    return true;

                default:
                    Component help2 = MiniMessage.miniMessage().deserialize(getHelp());
                    sender.sendMessage(help2);
                    return true;
            }
            return true;
        }

        return false;
    }


    public EnhancedLogger getEnhancedLogger() {
        return enhancedLogger;
    }
}
