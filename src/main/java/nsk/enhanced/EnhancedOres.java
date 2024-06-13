package nsk.enhanced;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import nsk.enhanced.Events.OnBlockInteractEvent;
import nsk.enhanced.Events.OnPlayerInteractEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
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
import java.util.ArrayList;
import java.util.List;
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

        loadRegionsFromDatabase();

        // --- --- --- --- // Events Managers & Listeners // --- --- --- --- //
        // Events Listeners
        OnPlayerInteractEvent onPlayerInteractEvent = new OnPlayerInteractEvent();
        OnBlockInteractEvent onBlockInteractEvent = new OnBlockInteractEvent();
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

    public void loadConfiguration() {
        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        boolean isEnabled = config.getBoolean("EnhancedOres.settings.enabled");
        getLogger().info("Plugin enabled: " + isEnabled);
    }
    public FileConfiguration getConfigFile() {
        return config;
    }

    public void saveConfigFile() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to save config file", e);
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
                //saveAllFactionsAsync(EnhancedOres.this);
                try {
                    saveAllEntitiesFromListAsync(regions)
                        .exceptionally(e -> {
                            throw new RuntimeException("Failed to save region into the database", e);
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
}
