package nsk.enhanced.Events;

import nsk.enhanced.PluginInstance;
import nsk.enhanced.Region;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;

public class OnPlayerInteractEvent implements Listener {

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        List<Region> regions = PluginInstance.getInstance().getRegions();

        if (event.getClickedBlock() != null) {
            Location location = event.getClickedBlock().getLocation();

            for (Region region : regions) {
                Player operator = PluginInstance.getInstance().getServer().getPlayer( region.getUser() );

                if (operator == player) {

                    Location previous;

                    switch (event.getAction()) {
                        case LEFT_CLICK_BLOCK:
                            previous = region.getPointA();
                            try {
                                region.setPointA(location);

                                PluginInstance.getInstance().saveEntityAsync(region)
                                        .exceptionally(e -> {
                                            region.setPointA(previous);
                                            throw new IllegalStateException("Query failed!", e);
                                        });
                            } catch (Exception e) {
                                PluginInstance.getInstance().consoleError(e);
                                PluginInstance.getInstance().playerWarning(e, player);
                            }
                            break;

                        case RIGHT_CLICK_BLOCK:
                            previous = region.getPointB();
                            try {
                                region.setPointB(location);

                                PluginInstance.getInstance().saveEntityAsync(region)
                                        .exceptionally(e -> {
                                            region.setPointB(previous);
                                            throw new IllegalStateException("Query failed!", e);
                                        });
                            } catch (Exception e) {
                                PluginInstance.getInstance().consoleError(e);
                                PluginInstance.getInstance().playerWarning(e, player);
                            }
                            break;
                    }

                    event.setCancelled(true);
                }
            }

        }

    }
}
