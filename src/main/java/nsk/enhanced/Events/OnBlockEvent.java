package nsk.enhanced.Events;

import nsk.enhanced.PluginInstance;
import nsk.enhanced.Region;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.List;

public class OnBlockEvent implements Listener {

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {

        Player player = event.getPlayer();
        List<Region> regions = PluginInstance.getInstance().getRegions();

        for (Region region : regions) {
            Player operator = PluginInstance.getInstance().getServer().getPlayer( region.getUser() );

            if (operator == player) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        List<Region> regions = PluginInstance.getInstance().getRegions();

        for (Region region : regions) {
            Player operator = PluginInstance.getInstance().getServer().getPlayer( region.getUser() );

            if (operator == player) {
                event.setCancelled(true);
            }
        }
    }

}
