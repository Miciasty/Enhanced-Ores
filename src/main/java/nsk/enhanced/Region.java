package nsk.enhanced;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import javax.persistence.*;
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


    public Region() { /* Pusty konstruktor wymagany przez JPA */ }

    public Region(Player player) {
        try {
            setWorld( player.getLocation().getWorld() );
            setUser(player.getUniqueId());
        } catch ( Exception e ) {
            PluginInstance.getInstance().getLogger().severe(e.getMessage());
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

            }
        } catch (Exception e) {
            PluginInstance.getInstance().getLogger().severe(e.getMessage());
        }
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


    // --- --- --- --- // Methods // --- --- --- --- //

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

    @Override
    public String toString() {

        StringBuilder builder = new StringBuilder();
        builder .append("\n")
                .append("Region ID: ").append(this.getId()).append("\n")
                .append("Name: ").append(this.getName()).append("\n")
                .append("World: ").append(this.getWorld().getName()).append("\n")
                .append("PointA:").append(" X: ").append(xA).append(" Y: ").append(yA).append(" Z: ").append(zA).append("\n")
                .append("PointB:").append(" X: ").append(xB).append(" Y: ").append(yB).append(" Z: ").append(zB).append("\n");

        return builder.toString();
    }
}
