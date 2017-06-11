import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

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
        this.bots = new LinkedHashMap<String, Object>();
        this.channels = new LinkedHashMap<String, Object>();
        this.bridges = new ArrayList();

        this.fileName = configFileName;
    }

    public void load() throws IOException {
        Yaml yaml = new Yaml();

        InputStream file = new FileInputStream(fileName);
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

        if (settings == null || !this.isValid(settings))
            throw new IOException("File not formatted correctly");

        this.bots = (Map<String, Object>) settings.get(Config.BOTS_KEY);
        this.channels = (Map<String, Object>) settings.get(Config.CHANNELS_KEY);
        this.bridges = (ArrayList) settings.get(Config.BRIDGES_KEY);
        this.webserver = (Map<String, String>) settings.get(Config.WEBSERVER_KEY);
    }

    public Map<String, Object> getBots() {
        return this.bots;
    }

    public Map<String, Object> getChannels() {
        return this.channels;
    }

    public ArrayList<ArrayList<String>> getBridges() {
        return this.bridges;
    }

    public Map<String, String> getWebserverConfig() {
        return this.webserver;
    }

    public String getDbUri() {
        return this.dbUri;
    }

    private boolean isValid(Map<String, Object> settings) {
        if (!settings.containsKey(Config.BOTS_KEY) ||
                !settings.containsKey(Config.CHANNELS_KEY) ||
                !settings.containsKey(Config.BRIDGES_KEY) ||
                !settings.containsKey(Config.WEBSERVER_KEY) ||
                !settings.containsKey(Config.DATABASE_KEY))
            return false;

        if (!(settings.get(Config.BOTS_KEY) instanceof Map) ||
                !(settings.get(Config.CHANNELS_KEY) instanceof Map) ||
                !(settings.get(Config.BRIDGES_KEY) instanceof ArrayList) ||
                !(settings.get(Config.WEBSERVER_KEY) instanceof Map))
            return false;

        Map<String, Object> channels = (Map<String, Object>) settings.get(Config.CHANNELS_KEY);
        Map<String, Object> bots = (Map<String, Object>) settings.get(Config.BOTS_KEY);
        ArrayList bridges = (ArrayList) settings.get(Config.BRIDGES_KEY);
        Map<String, String> webserver = (Map<String, String>) settings.get(Config.WEBSERVER_KEY);
        dbUri = (String) settings.get(Config.DATABASE_KEY);

        if (!this.isValidBots(bots))
            return false;

        if (!this.isValidChannels(channels, bots))
            return false;

        if (!this.isValidBridges(bridges, channels))
            return false;

        return this.isValidWebserverConfig(webserver);
    }

    private boolean isValidBots(Map<String, Object> bots) {
        for (Object obj : bots.entrySet()) {
            if (!(obj instanceof Entry))
                return false;

            Entry<String, Object> bot = (Entry<String, Object>) obj;
            if (!(bot.getValue() instanceof Map))
                return false;

            Map<String, String> value = (Map<String, String>) bot.getValue();
            if (!value.containsKey(BOT_TYPE_KEY))
                return false;
        }

        return true;
    }

    private boolean isValidChannels(Map<String, Object> channels, Map<String, Object> bots) {
        for (Object obj : channels.entrySet()) {
            if (!(obj instanceof Entry))
                return false;

            Entry<String, Object> channel = (Entry<String, Object>) obj;
            if (!(channel.getValue() instanceof Map))
                return false;

            Map<String, String> value = (Map<String, String>) channel.getValue();
            if (!value.containsKey(Config.BOT_KEY))
                return false;

            String bot = value.get(Config.BOT_KEY);
            if (!bots.containsKey(bot))
                return false;

            if (!value.containsKey(Config.NAME_KEY))
                return false;
        }

        return true;
    }

    private boolean isValidBridges(ArrayList bridges, Map<String, Object> channels) {
        for (Object obj : bridges) {
            if (!(obj instanceof ArrayList)) {
                ArrayList<String> bridge = (ArrayList<String>) obj;
                for (Object channel : bridges) {
                    if (!channels.containsKey(channel))
                        return false;
                }
            }
        }

        return true;
    }

    private boolean isValidWebserverConfig(Map<String, String> webserver) {
        return webserver.containsKey(Config.CONTENT_FOLDER_KEY) &&
                webserver.containsKey(Config.BASE_URL_KEY);
    }
}
