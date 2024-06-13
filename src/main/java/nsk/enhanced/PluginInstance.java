package nsk.enhanced;

public class PluginInstance {


    private static EnhancedOres instance;
    public static EnhancedOres getInstance() {
        return instance;
    }
    public static void setInstance(EnhancedOres in) {
        instance = in;
    }

}
