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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OnBlockEvent implements Listener {

    private FileConfiguration translations = PluginInstance.getInstance().getTranslationsFile();

    // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- //

    private boolean mayDrop(Player player, Material blockType) {
        Configuration config = PluginInstance.getInstance().getConfig();
        double drop_chance = config.getDouble("EnhancedOres.drop-chance");

        ItemStack tool = player.getInventory().getItemInMainHand();
        double toolDropChanceBonus = getToolDropChanceBonus(tool, blockType);

        if (drop_chance > 1) {
            drop_chance += toolDropChanceBonus;
            drop_chance = Math.min(drop_chance, 100.0);
        } else if (drop_chance < 0) {
            drop_chance = 0;
        } else {
            drop_chance = (drop_chance * 100) + toolDropChanceBonus;
        }

        drop_chance = Math.max(drop_chance, 0);
        drop_chance = Math.min(drop_chance, 100);

        Random random = new Random();
        return random.nextDouble() * 100 < drop_chance;
    }

    private double getToolDropChanceBonus(ItemStack tool, Material blocktype) {
        if (tool != null && tool.hasItemMeta()) {
            ItemMeta meta = tool.getItemMeta();
            if (meta.hasLore() && meta.getLore() != null) {

                FileConfiguration effectsConfig = PluginInstance.getInstance().getEffectsFile();
                ConfigurationSection oreTranslations = effectsConfig.getConfigurationSection("EnhancedOres.translation_ore");
                ConfigurationSection actionTranslations = effectsConfig.getConfigurationSection("EnhancedOres.translation_action");

                if (oreTranslations != null && actionTranslations != null) {
                    for (String lore : meta.getLore()) {
                        for (String oreKey : oreTranslations.getKeys(false)) {

                            String translatedOre = oreTranslations.getString(oreKey);

                            if (translatedOre != null && Material.matchMaterial(translatedOre) == blocktype) {

                                for (String actionKey : actionTranslations.getKeys(false)) {

                                    String translatedAction = actionTranslations.getString(actionKey);

                                    if (translatedAction != null) {

                                        String patternString = oreKey + " " + actionKey + " ([+-]\\d+(\\.\\d+)?)%";
                                        String patternString2 = actionKey + " " + oreKey + " ([+-]\\d+(\\.\\d+)?)%";

                                        Pattern pattern = Pattern.compile(patternString);
                                        Pattern pattern2 = Pattern.compile(patternString2);

                                        Matcher matcher1 = pattern.matcher(lore);
                                        Matcher matcher2 = pattern2.matcher(lore);

                                        boolean matchFound = false;
                                        Matcher matcher = null;
                                        if (matcher1.find()) {
                                            matcher = matcher1;
                                            matchFound = true;
                                        } else if (matcher2.find()) {
                                            matcher = matcher2;
                                            matchFound = true;
                                        }

                                        if ( matchFound ) {

                                            if (translatedAction.equals("drop rate")) {
                                                try {
                                                    return Double.parseDouble(matcher.group(1));
                                                } catch (NumberFormatException e) {
                                                    PluginInstance.getInstance().getEnhancedLogger().warning("Invalid tool drop chance: " + lore);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return 0;
    }

    // --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- --- //

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

                        if (this.mayDrop(player, block.getType())) {
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
