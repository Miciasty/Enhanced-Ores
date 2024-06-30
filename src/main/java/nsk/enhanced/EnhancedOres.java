package nsk.enhanced;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import nsk.enhanced.Events.OnBlockEvent;
import nsk.enhanced.Events.OnPlayerInteractEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class EnhancedOres extends JavaPlugin implements Listener {

    private File configFile;
    private FileConfiguration config;
    private SessionFactory sessionFactory;

    private final List<Region> regions = new ArrayList<>();

    @Override
    public void onEnable() {

        loadConfiguration();
        sessionFactory = new Configuration().configure().buildSessionFactory();

        configureHibernate();
        loadRegionsFromDatabase();

        // --- --- --- --- // Events Managers & Listeners // --- --- --- --- //
        // Events Listeners
        OnPlayerInteractEvent onPlayerInteractEvent = new OnPlayerInteractEvent();
        OnBlockEvent onBlockInteractEvent = new OnBlockEvent();

        getServer().getPluginManager().registerEvents(onPlayerInteractEvent,this);
        getServer().getPluginManager().registerEvents(onBlockInteractEvent,this);

        this.getCommand("eo").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        Component EF_L1 = MiniMessage.miniMessage().deserialize("<gradient:#9953aa:#172d5d> [Enhanced Ores]");

        getServer().getConsoleSender().sendMessage(EF_L1);

        startAutoSaveTask();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- //

    private void loadConfiguration() {
        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        boolean isEnabled = config.getBoolean("EnhancedOres.settings.enabled");
        getLogger().info("Plugin enabled: " + isEnabled);
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

    private void configureHibernate() {
        try {
            Yaml yaml = new Yaml();
            try (InputStream in = getResource("config.yml")) {
                Map<String, Object> config = yaml.load(in);

                Map<String, String> databaseConfig = (Map<String, String>) config.get("database");

                Configuration cfg = new Configuration()
                        .setProperty("hibernate.dialect", "org.hibernate.dialect." + databaseConfig.get("dialect"))
                        .setProperty("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver")

                        .setProperty("hibernate.connection.url", "jdbc:msql://" + databaseConfig.get("address") + ":" + databaseConfig.get("port") + "/" + databaseConfig.get("database"))
                        .setProperty("hibernate.connection.username", databaseConfig.get("username"))
                        .setProperty("hibernate.connection.password", databaseConfig.get("password"))

                        .setProperty("hibernate.hbm2ddl.auto", "update")
                        .setProperty("hibernate.show_sql", "true")
                        .setProperty("hibernate.format_sql", "true");

                cfg.addAnnotatedClass(Region.class);

                cfg.buildSessionFactory();
            }
        } catch (Exception e) {
            PluginInstance.getInstance().consoleError(e);
        }
    }

    // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- //

    private void loadRegionsFromDatabase() {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<Region> query = builder.createQuery(Region.class);
            query.from(Region.class);

            List<Region> result = session.createQuery(query).getResultList();
            regions.addAll(result);

            session.getTransaction().commit();

        } catch (Exception e) {
            this.consoleError(e);
        }
    }

    private void startAutoSaveTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    saveAllEntitiesFromListAsync(regions)
                        .exceptionally(e -> {
                            throw new RuntimeException("Failed to save regions into the database", e);
                        }).get();
                } catch (Exception e) {
                    consoleError(e);
                }
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
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            session.saveOrUpdate(entity);
            session.getTransaction().commit();
        } catch (Exception e) {
            this.consoleError(e);
        }
    }
    public <T> CompletableFuture<Void> saveEntityAsync(T entity) {

        return CompletableFuture.runAsync(() -> {
            try (Session session = sessionFactory.openSession()) {
                session.beginTransaction();

                session.saveOrUpdate(entity);
                session.getTransaction().commit();
            } catch (Exception e) {
                this.consoleError(e);
            }

        });
    }
    public <T> CompletableFuture<Void> saveAllEntitiesFromListAsync(List<T> entities) {

        return CompletableFuture.runAsync(() -> {
            try (Session session = sessionFactory.openSession()) {
                session.beginTransaction();

                for (T entity : entities) {
                    session.saveOrUpdate(entity);
                }

                session.getTransaction().commit();
            } catch (Exception e) {
                this.consoleError(e);
            }
        });
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
            try (Session session = sessionFactory.openSession()) {
                session.beginTransaction();

                session.delete(entity);
                session.getTransaction().commit();
            } catch (Exception e) {
                this.consoleError(e);
            }
        });
    }
    public <T> CompletableFuture<Void> deleteAllEntitiesFromListAsync(List<T> entities) {

        return CompletableFuture.runAsync(() -> {
            try (Session session = sessionFactory.openSession()) {
                session.beginTransaction();

                for (T entity : entities) {
                    session.delete(entity);
                }

                session.getTransaction().commit();
            } catch (Exception e) {
                this.consoleError(e);
            }
        });
    }


    // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- //

    private ItemStack getEconomyItem() {
        String materialName = config.getString("EnhancedOres.economy-item.material", "GOLD_NUGGET");
        Material material = Material.getMaterial(materialName);

        if (material == null) {
            material = Material.GOLD_NUGGET;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String displaName = config.getString("EnhancedOres.economy-item.item-meta.display-name", "<!italic><gold>Złota moneta");
            meta.setDisplayName(displaName);

            List<String> lore = config.getStringList("EnhancedOres.economy-item.item-meta.lore");
            meta.setLore(lore);
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
        getLogger().log(Level.SEVERE, "Enhanced Ores error: ", e);
    }

    private static boolean isNumeric(String str) {
        return str != null && str.matches("[0-9]+");
    }


    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("eo")) {

            if (args.length == 0 && !sender.isOp()) {
                sender.sendMessage("You don't have permission to use this command :(");
                return false;
            }

            switch (args[0].toLowerCase()) {
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
                                        player.sendMessage(region.toString());
                                    }
                                }

                                if (n <= 0) {
                                    player.sendMessage("There are no regions in this place.");
                                }
                                break;

                            case "open":

                                if (args.length == 3) {

                                    if ( isNumeric( args[2] ) ) {
                                        for (Region region : regions) {
                                            if ( String.valueOf( region.getId() ).equals( args[2] ) ) {
                                                region.setUser(player);
                                                player.sendMessage("Your region session is now open.");
                                                break;
                                            }
                                        }

                                    } else {
                                        n = 0;

                                        for (Region region : regions) {
                                            if (region.getName().equalsIgnoreCase( args[2] )) {
                                                n++;
                                                region.setUser(player);
                                                player.sendMessage("Your region session is now open.");
                                                break;
                                            }
                                        }

                                        if (n <= 0) {
                                            try {
                                                Region region = new Region();
                                                region.setUser(player);

                                                player.sendMessage("This region does not exist.");
                                                player.sendMessage("Creating new region..");

                                                regions.add(region);
                                                saveEntityAsync(region)
                                                        .thenRun(() -> {
                                                            player.sendMessage("Your region session is now open.");
                                                        })
                                                        .exceptionally(e -> {
                                                            regions.remove(region);
                                                            throw new IllegalStateException("Query failed! ", e);
                                                        });
                                            } catch (Exception e) {
                                                player.sendMessage("An error occurred while saving new region. Region was not created");
                                                consoleError(e);
                                                break;
                                            }
                                        }
                                    }
                                }
                                break;

                            case "close":

                                for (Region region : regions) {
                                    if (region.getUser().equals(player.getUniqueId())) {
                                        region.resetUser();

                                        player.sendMessage("Your region session is now closed.");
                                        break;
                                    }
                                }

                                player.sendMessage("You don't have any open session.");
                                break;

                            case "remove":
                                if (args.length == 3) {

                                    try {
                                        for (Region region : regions) {
                                            if (    String.valueOf( region.getId() ).equals( args[2] ) ||
                                                    region.getName().equalsIgnoreCase(args[2])) {

                                                deleteEntityAsync(region)
                                                        .thenRun(() -> {
                                                            regions.remove(region);
                                                            player.sendMessage("Region is now removed.");
                                                        })
                                                        .exceptionally(e -> {
                                                            throw new IllegalStateException("Query failed! ", e);
                                                        });
                                                break;
                                            }
                                        }

                                    } catch (Exception e) {
                                        player.sendMessage("An error occurred while removing region. Region was not removed");
                                        consoleError(e);
                                    }
                                }
                                break;
                        }

                    } else {
                        sender.sendMessage("This command can only be executed by a player.");
                    }
                    return true;
            }
        }

        return false;
    }
}
