package nsk.enhanced;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import nsk.enhanced.Events.OnBlockEvent;
import nsk.enhanced.Events.OnPlayerInteractEvent;
import nsk.enhanced.Tags.Annotations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.yaml.snakeyaml.Yaml;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class EnhancedOres extends JavaPlugin implements Listener, CommandExecutor {

    private File configFile;
    private FileConfiguration config;

    private File translationsFile;
    private FileConfiguration translations;

    private SessionFactory sessionFactory;

    private final List<Region> regions = new ArrayList<>();
    private final List<Miner> miners = new ArrayList<>();

    @Override
    public void onEnable() {

        PluginInstance.setInstance(this);

        getLogger().info("Loading configuration...");
        loadConfiguration();
        getLogger().info("Loading translations...");
        loadTranslations();

        configureHibernate();
        loadRegionsFromDatabase();

        // --- --- --- --- // Events Managers & Listeners // --- --- --- --- //
        // Events Listeners
        OnPlayerInteractEvent onPlayerInteractEvent = new OnPlayerInteractEvent();
        OnBlockEvent onBlockInteractEvent = new OnBlockEvent();

        getServer().getPluginManager().registerEvents(onPlayerInteractEvent, this);
        getLogger().info("onPlayerInteractEvent registered");
        getServer().getPluginManager().registerEvents(onBlockInteractEvent, this);
        getLogger().info("onBlockInteractEvent registered");

        PluginCommand command = this.getCommand("eo");
        if (command != null) {
            command.setExecutor(this);
            getLogger().info("Commands registered");
        } else {
            getLogger().info("Command 'eo' is not registered");
        }

        getServer().getPluginManager().registerEvents(this, this);

        Component EF_L1 = MiniMessage.miniMessage().deserialize("<gradient:#9953aa:#172d5d>[Enhanced Ores]");

        getServer().getConsoleSender().sendMessage(EF_L1);

        startAutoSaveTask();
        startMinerCheckTask();

        getLogger().info("AutoSave loop started");
    }

    @Override
    public void onDisable() {
        getLogger().info("Preparing to save all regions...");
        int maxAttempts = 9;

        try {
            saveAllEntitiesWithRetry(regions, maxAttempts).thenAccept(result -> {
                if (result) {
                    getLogger().info("Saved all regions successfully");
                } else {
                    getLogger().info("Saved all regions failed after " + maxAttempts + " attempts");
                }
            }).get();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to save all regions after " + maxAttempts + " attempts ", e);
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
                    if (currentTime - miner.timestamp > 3000) {
                        iterator.remove();

                        Player player = Bukkit.getPlayer(miner.uuid);
                        if (player != null && player.isOnline()) {
                            Component message = MiniMessage.miniMessage().deserialize("<gold>Your cooldown passed.");
                            player.sendMessage(message);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private static class Miner {
        private final UUID uuid;
        private final long timestamp;

        public Miner(UUID uuid, long timestamp) {
            this.uuid = uuid;
            this.timestamp = timestamp;
        }
    }

    public void addMiner(Player player) {
        miners.add(new Miner(player.getUniqueId(), System.currentTimeMillis()));
    }

    public boolean isMiner(Player player) {
        for (Miner miner : miners) {
            if (miner.uuid == player.getUniqueId()) {
                return true;
            }
        }

        return false;
    }

    // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- //

    private void loadConfiguration() {
        getLogger().info("Loading configuration...");
        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        boolean isEnabled = config.getBoolean("EnhancedOres.settings.enabled");
        getLogger().info("Config enabled: " + isEnabled);
    }

    private FileConfiguration getConfigFile() {
        return config;
    }
    private void saveConfigFile() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to save config file", e);
        }
    }

    private void loadTranslations() {
        getLogger().info("Loading translations...");
        translationsFile = new File(getDataFolder(), "translations.yml");
        if (!translationsFile.exists()) {
            translationsFile.getParentFile().mkdirs();
            saveResource("translations.yml", false);
        }

        translations = YamlConfiguration.loadConfiguration(translationsFile);

        boolean isEnabled = translations.getBoolean("EnhancedOres.settings.enabled");
        getLogger().info("Translations enabled: " + isEnabled);
    }

    public FileConfiguration getTranslationsFile() {
        return translations;
    }

    private void configureHibernate() {
        getLogger().info("Configuring Hibernate...");
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
            getLogger().log(Level.SEVERE, "Could not create session factory", e);
        }
        getLogger().info("Hibernate loaded");
    }

    // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- //

    private void loadRegionsFromDatabase() {
        getLogger().info("Loading regions from database...");
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<Region> query = builder.createQuery(Region.class);
            query.from(Region.class);

            List<Region> result = session.createQuery(query).getResultList();
            regions.addAll(result);

            session.getTransaction().commit();

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not load regions from database", e);
        }
        getLogger().info("Regions loaded");
    }

    private void startAutoSaveTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    getLogger().info("Starting auto save task");
                    saveAllEntitiesFromListAsync(regions)
                        .exceptionally(e -> {
                            throw new RuntimeException("Failed to save regions into the database", e);
                        }).get();
                } catch (Exception e) {
                    consoleError(e);
                }
                getLogger().info("Auto save task completed");
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
        getLogger().info("Preparing to save entity: " + entity.getClass().getSimpleName());
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            getLogger().info("Saving entity: " + entity.getClass().getSimpleName());
            session.saveOrUpdate(entity);
            session.getTransaction().commit();
            getLogger().info("Saved entity: " + entity.getClass().getSimpleName());
        } catch (Exception e) {
            this.consoleError(e);
            getLogger().info("Saving entity failed");
        }
    }
    public <T> CompletableFuture<Void> saveEntityAsync(T entity) {

        return CompletableFuture.runAsync(() -> {
            getLogger().info("Saving entity: " + entity);
            try (Session session = sessionFactory.openSession()) {
                getLogger().info("Opened Hibernate session for entity");
                session.beginTransaction();
                getLogger().info("Transaction begun");

                session.saveOrUpdate(entity);
                getLogger().info("Entity saveOrUpdate called");

                session.getTransaction().commit();
                getLogger().info("Transaction committed");
                session.close();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to save entity", e);
                this.consoleError(e);
            }

        });
    }
    public <T> CompletableFuture<Void> saveAllEntitiesFromListAsync(List<T> entities) {

        return CompletableFuture.runAsync(() -> {
            getLogger().info("Saving entities from the list: " + entities);
            try (Session session = sessionFactory.openSession()) {
                session.beginTransaction();

                for (T entity : entities) {
                    session.saveOrUpdate(entity);
                }

                session.getTransaction().commit();
                session.close();
            } catch (Exception e) {
                this.consoleError(e);
            }
        });
    }
    public <T> CompletableFuture<Boolean> saveAllEntitiesWithRetry(List<T> entities, int maxAttempts) {

        return saveAllEntitiesFromListAsync(entities).handle((result, ex) -> {
            if (ex == null) {
                return CompletableFuture.completedFuture(true);
            } else if (maxAttempts > 1) {
                getLogger().log(Level.WARNING, "Save failed, retrying... Attempts left: " + maxAttempts);
                return saveAllEntitiesWithRetry(entities, maxAttempts - 1);
            } else {
                getLogger().log(Level.SEVERE, "Save failed after maximum attempts: " + ex);
                return CompletableFuture.completedFuture(false);
            }
        }).thenCompose(result -> result);

    }

    private <T> void deleteEntity(T entity) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            session.delete(entity);
            session.getTransaction().commit();
        } catch (Exception e) {
            this.consoleError(e);
        }
    }
    public <T> CompletableFuture<Void> deleteEntityAsync(T entity) {

        return CompletableFuture.runAsync(() -> {
            getLogger().info("Deleting entity: " + entity);
            try (Session session = sessionFactory.openSession()) {
                session.beginTransaction();

                session.delete(entity);
                session.getTransaction().commit();
                session.close();
            } catch (Exception e) {
                this.consoleError(e);
            }
        });
    }
    public <T> CompletableFuture<Void> deleteAllEntitiesFromListAsync(List<T> entities) {

        return CompletableFuture.runAsync(() -> {
            getLogger().info("Deleting entities from the list: " + entities);
            try (Session session = sessionFactory.openSession()) {
                session.beginTransaction();

                for (T entity : entities) {
                    session.delete(entity);
                }

                session.getTransaction().commit();
                session.close();
            } catch (Exception e) {
                this.consoleError(e);
            }
        });
    }


    // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- //

    public ItemStack getEconomyItem() {
        String materialName = config.getString("EnhancedOres.economy-item.material", "GOLD_NUGGET");
        Material material = Material.getMaterial(materialName);

        if (material == null) {
            material = Material.GOLD_NUGGET;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String displaName = config.getString("EnhancedOres.economy-item.item-meta.display-name", "<!italic><gold>Złota moneta");
            Component displayNameComponent = MiniMessage.miniMessage().deserialize(displaName);
            meta.displayName(displayNameComponent);

            List<String> loreStrings = config.getStringList("EnhancedOres.economy-item.item-meta.lore");
            List<Component> loreComponents = new ArrayList<>();

            for (String lore : loreStrings) {
                loreComponents.add(MiniMessage.miniMessage().deserialize(lore));
            }
            meta.lore(loreComponents);
            item.setItemMeta(meta);
        }

        return item;
    }

    public List<Material> getOres() {
        List<String> materialName = config.getStringList("EnhancedOres.ores");
        List<Material> materials = new ArrayList<>();

        for (String material : materialName) {
            materials.add(Material.getMaterial( material.toUpperCase() + "_ORE" ));
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
        getLogger().log(Level.SEVERE, "Error: ", e);
    }

    private static boolean isNumeric(String str) {
        return str != null && str.matches("[0-9]+");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("eo")) {

            if (args.length == 0) {
                // plugin.yml -> command usage will do the work :)
                return false;
            }

            if (!sender.isOp()) {
                Component message = MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.permissionDenied"),
                        Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                        Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                        Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                        Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") )));
                sender.sendMessage(message);
                return false;
            }

            switch (args[0].toLowerCase()) {

                case "list":
                    StringBuilder list = new StringBuilder();
                    list.append("\n");
                    list.append("Regions: \n");

                    for (Region region : regions) {
                        list.append("[").append(region.getId()).append("] - ");
                        list.append(region.getName()).append("]\n");
                    }

                    if (sender instanceof Player) {
                        sender.sendMessage(list.toString());
                    } else {
                        getLogger().info(list.toString());
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
                                        Component pointA = MiniMessage.miniMessage().deserialize("<green> - " + translations.getString("EnhancedOres.messages.prefixPointA") + region.getPointAString(),
                                                Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                                Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                                Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                                Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") )));
                                        Component pointB = MiniMessage.miniMessage().deserialize("<green> - " + translations.getString("EnhancedOres.messages.prefixPointB") + region.getPointBString(),
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
                                    Component message = MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.notRegion"),
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
                                                region.setUser(player);
                                                Component message = MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.sessionOpen"),
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
                                                Component message = MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.sessionOpen"),
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
                                                Region region = new Region(player);
                                                region.setName( args[2] );
                                                region.setUser(player);

                                                player.sendMessage(MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.notExistRegion"),
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
                                                            player.sendMessage(MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.sessionOpen"),
                                                                    Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                                                    Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                                                    Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                                                    Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") ))));
                                                            getLogger().info("Region was successfully added to database.");
                                                        })
                                                        .exceptionally(e -> {
                                                            regions.remove(region);
                                                            getLogger().info(region.getName() + " has beed removed.");
                                                            throw new IllegalStateException("Query failed! ", e);
                                                        });
                                            } catch (Exception e) {
                                                player.sendMessage(MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.queryError"),
                                                        Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                                        Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                                        Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                                        Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") ))));
                                                consoleError(e);
                                                break;
                                            }
                                        }
                                    }
                                } else {
                                    player.sendMessage(MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.emptyArgument"),
                                            Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                            Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                            Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                            Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") ))));
                                }
                                break;

                            case "close":

                                try {
                                    for (Region region : regions) {
                                        if (region.getUser().equals(player.getUniqueId())) {
                                            region.resetUser();

                                            player.sendMessage(MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.sessionClosed"),
                                                    Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                                    Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                                    Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                                    Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") ))));
                                            break;
                                        }
                                    }
                                } catch (Exception e) {
                                    player.sendMessage(MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.noSession"),
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
                                                        .thenRun(() -> {
                                                            regions.remove(region);
                                                            player.sendMessage(MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.removedRegion"),
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
                                            player.sendMessage(MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.notExistRegion"),
                                                    Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                                    Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                                    Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                                    Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") ))));
                                        }

                                    } catch (Exception e) {
                                        player.sendMessage(MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.queryError"),
                                                Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                                Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                                Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                                Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") ))));
                                        consoleError(e);
                                    }
                                } else {
                                    player.sendMessage(MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.emptyArgument"),
                                            Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                            Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                            Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                            Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") ))));
                                }
                                break;
                        }

                    } else {
                        sender.sendMessage("[!] This command can only be executed by a player [!]");
                    }
                    return true;
            }
        }

        return false;
    }
}
