package nsk.enhanced.Events;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import nsk.enhanced.PluginInstance;
import nsk.enhanced.Region;
import nsk.enhanced.Tags.Annotations;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Random;

public class OnBlockEvent implements Listener {

    private FileConfiguration translations = PluginInstance.getInstance().getTranslationsFile();

    private boolean mayDrop() {
        Configuration config = PluginInstance.getInstance().getConfig();
        double drop_chance = config.getDouble("EnhancedOres.drop-chance");

        if (drop_chance > 1) {
            drop_chance = Math.min(drop_chance, 100.0);
        } else if (drop_chance < 0) {
            drop_chance = 0;
        } else {
            drop_chance *= 100;
        }

        if (drop_chance > 100) {
            drop_chance = 100;
        }

        Random random = new Random();
        return random.nextDouble() * 100 < drop_chance;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {

        Player player = event.getPlayer();
        Block block = event.getBlock();

        List<Region> regions = PluginInstance.getInstance().getRegions();

        for (Region region : regions) {

            if (region.getUser() != null) {

                Player operator = PluginInstance.getInstance().getServer().getPlayer( region.getUser() );

                if (player == operator) {
                    event.setCancelled(true);
                }
            }

            if (region.contains(block.getLocation())) {

                List<Material> materials = PluginInstance.getInstance().getOres();
                if (materials.contains( block.getType() )) {

                    if (PluginInstance.getInstance().isMiner(player, block)){
                        Component message = MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.onCooldown", "<error>'onCooldown' not found!"),
                                Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") )));
                        player.sendActionBar(message);
                        event.setCancelled(true);
                    } else {

                        PluginInstance.getInstance().addMiner(player, block);

                        if (this.mayDrop()) {
                            ItemStack economyitem = PluginInstance.getInstance().getEconomyItem();
                            player.getInventory().addItem(economyitem);

                            Component message = MiniMessage.miniMessage().deserialize(translations.getString("EnhancedOres.messages.onCoinFound", "<error>'onCoinFound' not found!"),
                                    Placeholder.styling("error", TextColor.fromHexString( Annotations.getTag("error") )),
                                    Placeholder.styling("warning", TextColor.fromHexString( Annotations.getTag("warning") )),
                                    Placeholder.styling("success", TextColor.fromHexString( Annotations.getTag("success") )),
                                    Placeholder.styling("info", TextColor.fromHexString( Annotations.getTag("info") )));
                            player.sendMessage(message);
                        }
                    }

                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        List<Region> regions = PluginInstance.getInstance().getRegions();

        for (Region region : regions) {

            if (region.getUser() != null) {
                Player operator = PluginInstance.getInstance().getServer().getPlayer( region.getUser() );

                if (operator == player) {
                    event.setCancelled(true);
                }
            }
        }
    }

}
