package nsk.enhanced.Events;

import nsk.enhanced.PluginInstance;
import nsk.enhanced.Region;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.List;

public class OnBlockInteractEvent implements Listener {

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {

        Player player = event.getPlayer();
        Block block = event.getBlock();

        Location location = block.getLocation();

        List<Region> regions = PluginInstance.getInstance().getRegions();

        for (Region region : regions) {
            if (region.contains(location)) {



            }
        }

    }

}
