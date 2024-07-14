package nsk.enhanced.Tags;

import nsk.enhanced.PluginInstance;
import org.bukkit.configuration.file.FileConfiguration;

public class Annotations {

    private static final FileConfiguration translations = PluginInstance.getInstance().getTranslationsFile();

    public static String getTag(String annotation) {

        String type;

        if (translations == null) {
            PluginInstance.getInstance().getLogger().info("No translations found");
            return type = "#AA0000";
        }

        switch(annotation) {
            case "error":

                type = translations.getString("EnhancedOres.tags.error", "#AA0000");

                if (type.charAt(0) != '#') {
                    return "#" + type;
                } else {
                    return type;
                }

            case "warning":

                type = translations.getString("EnhancedOres.tags.warning", "#AA0000");

                if (type.charAt(0) != '#') {
                    return "#" + type;
                } else {
                    return type;
                }

            case "success":

                type = translations.getString("EnhancedOres.tags.success", "#AA0000");

                if (type.charAt(0) != '#') {
                    return "#" + type;
                } else {
                    return type;
                }

            case "info":

                type = translations.getString("EnhancedOres.tags.info", "#AA0000");

                if (type.charAt(0) != '#') {
                    return "#" + type;
                } else {
                    return type;
                }

            default:
                return "#AA0000";
        }

    }

}
