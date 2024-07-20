package nsk.enhanced.System;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import nsk.enhanced.EnhancedOres;
import nsk.enhanced.PluginInstance;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.logging.*;

public class EnhancedLogger extends Logger {

    private EnhancedOres plugin;

    public EnhancedLogger(EnhancedOres plugin) {
        super(plugin.getName(), null);
        this.plugin = plugin;
        setParent(Bukkit.getLogger());
        setLevel(Level.ALL);

        setUseParentHandlers(false);

        for (Handler handler : getHandlers()) {
            removeHandler(handler);
        }
        EnhancedHandler enhancedHandler = new EnhancedHandler();
        enhancedHandler.setFormatter(new SimpleFormatter());
        addHandler(enhancedHandler);
    }

    private class EnhancedHandler extends Handler {
        @Override
        public void publish(LogRecord record) {
            if (!isLoggable(record)) return;

            String message = getFormatter().formatMessage(record);
            Level level = record.getLevel();

            Component casual = MiniMessage.miniMessage().deserialize("<gradient:#1f8eb2:#2dccff>[Enhanced Ores]</gradient> " + message);

            if (level == Level.SEVERE) {
                Component severe = MiniMessage.miniMessage().deserialize("<gradient:#b24242:#ff5f5f>[Enhanced Ores]</gradient> <#ffafaf>" + message);
                Bukkit.getConsoleSender().sendMessage(severe);
            } else if (level == Level.WARNING) {
                Component warning = MiniMessage.miniMessage().deserialize("<gradient:#b28724:#ffc234>[Enhanced Ores]</gradient> <#ffe099>" + message);
                Bukkit.getConsoleSender().sendMessage(warning);
            } else if (level == Level.FINE) {
                Component fine = MiniMessage.miniMessage().deserialize("<gradient:#3ca800:#56f000>[Enhanced Ores]</gradient> <#aaf77f>" + message);
                Bukkit.getConsoleSender().sendMessage(fine);
            }
            else {
                Bukkit.getConsoleSender().sendMessage(casual);
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() throws SecurityException {}

    }

}
