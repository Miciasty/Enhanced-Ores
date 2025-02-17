package nsk.enhanced;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import javax.persistence.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "regions")
public class Region implements Listener {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = true)
    private String name;

    @Column(nullable = true)
    private double xA, yA, zA;
 
    @Column(nullable = true)
    private double xB, yB, zB;

    @Column(nullable = true)
    private String worldName;

    @Transient
    private UUID uuid;

    @Transient
    private File configFile;

    @Transient
    private FileConfiguration config;


    public Region() { /* Pusty konstruktor wymagany przez JPA */ }

    public Region(Player player, String name) {
        try {
            setWorld( player.getLocation().getWorld() );
            setUser(player.getUniqueId());
            setName(name);
            initializeConfig();
        } catch ( Exception e ) {
            PluginInstance.getInstance().getEnhancedLogger().severe(e.getMessage());
        }
    }

    public Region(Location pointA, Location pointB) {

        try {
            if (!pointA.getWorld().equals(pointB.getWorld())) {
                throw new IllegalArgumentException("pointA and pointB must be in the same world");
            } else {

                setWorld(pointA.getWorld());
                setPointA(pointA);
                setPointB(pointB);
                this.name = "region_" + pointA.getWorld().getName() + "_" + pointA.getBlockX() + "_" + pointA.getBlockZ();
                initializeConfig();
            }
        } catch (Exception e) {
            PluginInstance.getInstance().getEnhancedLogger().severe(e.getMessage());
        }
    }

    public void initializeConfig() {
        this.configFile = new File(PluginInstance.getInstance().getDataFolder(), "Regions" + File.separator + name + ".yml");
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                if (PluginInstance.getInstance().getDevmode()) {
                    PluginInstance.getInstance().getEnhancedLogger().config("Initializing <green>" + name + "</green> regional configuration file...");
                }
                createDefaultConfig();
            } catch (Exception e) {
                PluginInstance.getInstance().getEnhancedLogger().severe(e.getMessage());
            }
        } else {
            PluginInstance.getInstance().getEnhancedLogger().warning("Loading <green>" + name + "</green> regional configuration file!");
        }

        this.config = YamlConfiguration.loadConfiguration(configFile);

    }

    public void createDefaultConfig() {
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();

                File templateFile = new File(PluginInstance.getInstance().getDataFolder(), "Regions" + File.separator + "example.yml");
                if (!templateFile.exists()) {
                    templateFile.getParentFile().mkdirs();
                    PluginInstance.getInstance().saveResource("Regions" + File.separator + "example.yml", false);
                } else {
                    Files.copy(templateFile.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                config = YamlConfiguration.loadConfiguration(templateFile);

                if (PluginInstance.getInstance().getDevmode()) {
                    PluginInstance.getInstance().getEnhancedLogger().config("Creating regional configuration file for <green>" + name + ".yml");
                }

                //saveConfig();

            } catch (Exception e) {
                PluginInstance.getInstance().getEnhancedLogger().severe(e.getMessage());
            }
        }
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (Exception e) {
            PluginInstance.getInstance().getEnhancedLogger().severe(e.getMessage());
        }
    }

    public void loadConfiguration() {
        PluginInstance.getInstance().getEnhancedLogger().warning("Reloading <aqua>" + name + "</aqua> regional configuration...");
        initializeConfig();
    }

    public FileConfiguration getConfiguration() {
        return config;
    }

    // --- --- --- --- // Setter's / Getter's // --- --- --- --- //

    public void setUser(Player player) {
        uuid = player.getUniqueId();
    }
    public void setUser(UUID uuid) {
        this.uuid = uuid;
    }
    public UUID getUser() {
        return uuid;
    }

    public void resetUser() {
        uuid = null;
    }

    // --- --- --- --- // Setter's / Getter's // --- --- --- --- //

    public int getId() {
        return id;
    }

    public World getWorld() {
        return getPointA().getWorld();
    }

    public Location getPointA() {
        return new Location(Bukkit.getWorld(worldName), xA, yA, zA);
    }
    public String getPointAString() {
        return worldName + ", " + xA + ", " + yA + ", " + zA;
    }

    public Location getPointB() {
        return new Location(Bukkit.getWorld(worldName), xB, yB, zB);
    }
    public String getPointBString() {
        return worldName + ", " + xB + ", " + yB + ", " + zB;
    }

    public void setName(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }

    protected void setWorld(World world) {
        worldName = world.getName();
    }

    public void setPointA(Location pointA) {
        this.xA = pointA.getX();
        this.yA = pointA.getY();
        this.zA = pointA.getZ();
    }

    public void setPointB(Location pointB) {
        this.xB = pointB.getX();
        this.yB = pointB.getY();
        this.zB = pointB.getZ();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    // --- --- --- --- // Methods // --- --- --- --- //

    public boolean contains(Location location) {
        if (!location.getWorld().getName().equals(worldName)) {
            return false;
        }

        double minX = Math.min(xA, xB);
        double maxX = Math.max(xA, xB);
        double minY = Math.min(yA, yB);
        double maxY = Math.max(yA, yB);
        double minZ = Math.min(zA, zB);
        double maxZ = Math.max(zA, zB);

        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();

        return  x >= minX && x <= maxX &&
                y >= minY && y <= maxY &&
                z >= minZ && z <= maxZ;
    }


    public List<Block> getBlocks() {
        List<Block> blocks = new ArrayList<>();
        World world = getWorld();

        int minX = (int) Math.min(xA, xB);
        int maxX = (int) Math.max(xA, xB);
        int minY = (int) Math.min(yA, yB);
        int maxY = (int) Math.max(yA, yB);
        int minZ = (int) Math.min(zA, zB);
        int maxZ = (int) Math.max(zA, zB);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    blocks.add(world.getBlockAt(x, y, z));
                }
            }
        }

        return blocks;
    }

    public boolean overlaps(Region other) {
        if (!this.worldName.equals(other.worldName)) {
            return false;
        }

        return (
                this.contains(other.getPointA()) || this.contains(other.getPointB()) ||
                        other.contains(this.getPointA()) || other.contains(this.getPointB()));
    }

    public String longString() {

        StringBuilder builder = new StringBuilder();
        builder .append("\n")
                .append("Region ID: ").append(this.getId()).append("\n")
                .append("Name: ").append(this.getName()).append("\n")
                .append("World: ").append(this.getWorld().getName()).append("\n")
                .append("PointA:").append(" X: ").append(xA).append(" Y: ").append(yA).append(" Z: ").append(zA).append("\n")
                .append("PointB:").append(" X: ").append(xB).append(" Y: ").append(yB).append(" Z: ").append(zB).append("\n");

        return builder.toString();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder .append("Region ID: ").append(this.getId()).append(", ")
                .append("Name: ").append(this.getName()).append(", ")
                .append("World: ").append(this.getWorld().getName()).append(", ")
                .append("PointA:").append(" X: ").append(xA).append(" Y: ").append(yA).append(" Z: ").append(zA).append(", ")
                .append("PointB:").append(" X: ").append(xB).append(" Y: ").append(yB).append(" Z: ").append(zB);

        return builder.toString();
    }
}
