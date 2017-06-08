import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

class Config {
    public static final String DEFAULT_FILENAME = "/etc/brooklyn/conf.yml";
    public static final String BOT_TYPE_KEY = "type";
    public static final String NAME_KEY = "name";
    public static final String BOT_KEY = "bot";
    private static final String BOTS_KEY = "bots";
    private static final String CHANNELS_KEY = "channels";
    private static final String BRIDGES_KEY = "bridges";
    private static final String WEBSERVER_KEY = "webserver";
    private static final String CONTENT_FOLDER_KEY = "content-folder";
    private static final String BASE_URL_KEY = "base-url";
    private static final String DATABASE_KEY = "db-uri";
    private final String fileName;
    private Map<String, Object> bots;
    private Map<String, Object> channels;
    private ArrayList<ArrayList<String>> bridges;
    private Map<String, String> webserver;
    private String dbUri;

    public Config(String configFileName) {
        bots = new LinkedHashMap<String, Object>();
        channels = new LinkedHashMap<String, Object>();
        bridges = new ArrayList();

        fileName = configFileName;
    }

    public void load() throws IOException {
        Yaml yaml = new Yaml();

        InputStream file = new FileInputStream(this.fileName);
        Object settingsTmp = yaml.load(file);
        if (!(settingsTmp instanceof Map))
            throw new IOException("File not formatted correctly");

        Map<String, Object> settings = (Map<String, Object>) settingsTmp;

        if (file != null) {
            try {
                file.close();
            } catch (IOException e) {
                // Should never happen
                e.printStackTrace();
            }
        }

        if (settings == null || !isValid(settings))
            throw new IOException("File not formatted correctly");

        bots = (Map<String, Object>) settings.get(BOTS_KEY);
        channels = (Map<String, Object>) settings.get(CHANNELS_KEY);
        bridges = (ArrayList) settings.get(BRIDGES_KEY);
        webserver = (Map<String, String>) settings.get(WEBSERVER_KEY);
    }

    public Map<String, Object> getBots() {
        return bots;
    }

    public Map<String, Object> getChannels() {
        return channels;
    }

    public ArrayList<ArrayList<String>> getBridges() {
        return bridges;
    }

    public Map<String, String> getWebserverConfig() {
        return webserver;
    }

    public String getDbUri() {
        return dbUri;
    }

    private boolean isValid(Map<String, Object> settings) {
        if (!settings.containsKey(BOTS_KEY) ||
                !settings.containsKey(CHANNELS_KEY) ||
                !settings.containsKey(BRIDGES_KEY) ||
                !settings.containsKey(WEBSERVER_KEY) ||
                !settings.containsKey(DATABASE_KEY))
            return false;

        if (!(settings.get(BOTS_KEY) instanceof Map) ||
                !(settings.get(CHANNELS_KEY) instanceof Map) ||
                !(settings.get(BRIDGES_KEY) instanceof ArrayList) ||
                !(settings.get(WEBSERVER_KEY) instanceof Map))
            return false;

        Map<String, Object> channels = (Map<String, Object>) settings.get(CHANNELS_KEY);
        Map<String, Object> bots = (Map<String, Object>) settings.get(BOTS_KEY);
        ArrayList bridges = (ArrayList) settings.get(BRIDGES_KEY);
        Map<String, String> webserver = (Map<String, String>) settings.get(WEBSERVER_KEY);
        this.dbUri = (String) settings.get(DATABASE_KEY);

        if (!isValidBots(bots))
            return false;

        if (!isValidChannels(channels, bots))
            return false;

        if (!isValidBridges(bridges, channels))
            return false;

        return isValidWebserverConfig(webserver);
    }

    private boolean isValidBots(Map<String, Object> bots) {
        for (Object obj : bots.entrySet()) {
            if (!(obj instanceof Map.Entry))
                return false;

            Map.Entry<String, Object> bot = (Map.Entry<String, Object>) obj;
            if (!(bot.getValue() instanceof Map))
                return false;

            Map<String, String> value = (Map<String, String>) bot.getValue();
            if (!value.containsKey(Config.BOT_TYPE_KEY))
                return false;
        }

        return true;
    }

    private boolean isValidChannels(Map<String, Object> channels, Map<String, Object> bots) {
        for (Object obj : channels.entrySet()) {
            if (!(obj instanceof Map.Entry))
                return false;

            Map.Entry<String, Object> channel = (Map.Entry<String, Object>) obj;
            if (!(channel.getValue() instanceof Map))
                return false;

            Map<String, String> value = (Map<String, String>) channel.getValue();
            if (!value.containsKey(BOT_KEY))
                return false;

            String bot = value.get(BOT_KEY);
            if (!bots.containsKey(bot))
                return false;

            if (!value.containsKey(NAME_KEY))
                return false;
        }

        return true;
    }

    private boolean isValidBridges(ArrayList bridges, Map<String, Object> channels) {
        for (Object obj : bridges) {
            if (!(obj instanceof ArrayList)) {
                ArrayList<String> bridge = (ArrayList<String>) obj;
                for (String channel : bridge) {
                    if (!channels.containsKey(channel))
                        return false;
                }
            }
        }

        return true;
    }

    private boolean isValidWebserverConfig(Map<String, String> webserver) {
        return webserver.containsKey(CONTENT_FOLDER_KEY) &&
                webserver.containsKey(BASE_URL_KEY);
    }
}
