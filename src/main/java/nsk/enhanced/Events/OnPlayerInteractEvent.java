package nsk.enhanced.Events;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import nsk.enhanced.PluginInstance;
import nsk.enhanced.Region;
import nsk.enhanced.Tags.Annotations;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;
import java.util.UUID;

public class OnPlayerInteractEvent implements Listener {

    private FileConfiguration translations = PluginInstance.getInstance().getTranslationsFile();

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        List<Region> regions = PluginInstance.getInstance().getRegions();

        if (event.getClickedBlock() != null) {
            Location location = event.getClickedBlock().getLocation();

            for (Region region : regions) {

                if (region.getUser() != null) {
                    Player operator = PluginInstance.getInstance().getServer().getPlayer( region.getUser() );

                    if (operator == player) {

                        Location previous;

                        switch (event.getAction()) {
                            case LEFT_CLICK_BLOCK:
                                previous = region.getPointA();
                                try {
                                    region.setPointA(location);

                                    PluginInstance.getInstance().saveEntityAsync(region)
                                            .thenRun(() -> {
                                                Component pointA = MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.prefixPointA") + region.getPointAString(),
                                                        Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                                        Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                                        Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                                        Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") )));
                                                player.sendMessage(pointA);
                                            })
                                            .exceptionally(e -> {
                                                region.setPointA(previous);
                                                throw new IllegalStateException("Query failed!", e);
                                            });
                                } catch (Exception e) {
                                    PluginInstance.getInstance().getLogger().severe(e.getMessage());
                                }
                                break;

                            case RIGHT_CLICK_BLOCK:
                                if ( event.getHand() == EquipmentSlot.HAND )  {
                                    previous = region.getPointB();
                                    try {
                                        region.setPointB(location);

                                        PluginInstance.getInstance().saveEntityAsync(region)
                                                .thenRun(() -> {
                                                    Component pointB = MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.prefixPointB") + region.getPointBString(),
                                                            Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                                            Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                                            Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                                            Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") )));
                                                    player.sendMessage(pointB);
                                                })
                                                .exceptionally(e -> {
                                                    region.setPointB(previous);
                                                    throw new IllegalStateException("Query failed!", e);
                                                });
                                    } catch (Exception e) {
                                        PluginInstance.getInstance().getLogger().severe(e.getMessage());
                                    }
                                }
                                break;
                        }

                        event.setCancelled(true);
                    }
                }
            }

        }

    }
}
