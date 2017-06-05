import bots.Bot;
import bots.TelegramBot;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoDatabase;
import models.MessageBuilder;

import java.io.IOException;
import java.util.*;

public final class Application {
    public static void main(String[] args) throws InterruptedException {
        // TODO: find a way to replace this temporary fix
        TelegramBot.init();

        Config conf = new Config(1 > args.length ? Config.DEFAULT_FILENAME : args[0]);

        try {
            conf.load();
        } catch (IOException e) {
            System.err.println(String.format("Error while loading config file: %s.", e.getMessage()));
            System.exit(1);
        }

        Map<String, Object> channelsConfig = conf.getChannels();
        Map<String, String> webserverConfig = conf.getWebserverConfig();

        // Init mongodb
        MongoClientURI connectionString = new MongoClientURI(conf.getMongoUri());
        MongoClient mongoClient = new MongoClient(connectionString);
        MongoDatabase database = mongoClient.getDatabase("brooklyn");
        MessageBuilder.init(database);

        Map<String, Bot> bots = Application.initBots(conf.getBots(), channelsConfig, webserverConfig);
        Application.manageBridges(bots, channelsConfig, conf.getBridges());

        Application.handleShutdown();
    }

    private static Map<String, Bot> initBots(Map<String, Object> botsConfig,
                                             Map<String, Object> channelsConfig,
                                             Map<String, String> webserverConfig) {
        int AVG_BOTS_N = 3;
        Map<String, Bot> bots = new LinkedHashMap<>(AVG_BOTS_N);
        for (Map.Entry<String, Object> entry : botsConfig.entrySet()) {
            Map<String, String> botConfig = (Map<String, String>) entry.getValue();
            try {
                Object newClass = Class.forName(Bot.class.getPackage().getName() + '.' + botConfig.get(Config.BOT_TYPE_KEY)).newInstance();
                if (newClass instanceof Bot) {
                    Bot bot = (Bot) newClass;
                    String[] channels = Application.getChannelsName(entry.getKey(), channelsConfig);
                    if (bot.init(botConfig, channels, webserverConfig)) {
                        bots.put(entry.getKey(), bot);
                        System.out.println(String.format("Bot '%s' initialized.", entry.getKey()));
                    } else
                        System.err.println(String.format("Failed to init '%s' bot.", entry.getKey()));
                } else
                    System.err.println(String.format("'%s' is not a valid bot.", botConfig.get(Config.BOT_TYPE_KEY)));
            } catch (Exception e) {
                System.err.println(String.format("Class of type '%s' can't be instantiated.", botConfig.get(Config.BOT_TYPE_KEY)));
                e.printStackTrace();
            }
        }

        return bots;
    }

    private static String[] getChannelsName(String botName,
                                            Map<String, Object> channelsConfig) {
        List<String> channels = new LinkedList<>();
        for (Map.Entry<String, Object> entry : channelsConfig.entrySet()) {
            Map<String, String> channelConfig = (Map<String, String>) entry.getValue();
            if (botName.equals(channelConfig.get(Config.BOT_KEY))) {
                if (channelConfig.containsKey(Config.NAME_KEY))
                    channels.add(channelConfig.get(Config.NAME_KEY));
            }
        }
        return channels.toArray(new String[channels.size()]);
    }

    private static void manageBridges(Map<String, Bot> bots,
                                      Map<String, Object> channelsConfig,
                                      ArrayList<ArrayList<String>> bridgesConfig) {
        for (Iterable<String> bridgeConfig : bridgesConfig) {
            for (String fromChannelId : bridgeConfig) {
                String fromBotId = Application.channelToBotId(fromChannelId, channelsConfig);
                if (fromBotId != null) {
                    Bot fromBot = bots.get(fromBotId);
                    for (String toChannelId : bridgeConfig) {
                        String toBotId = Application.channelToBotId(toChannelId, channelsConfig);
                        if (null != toBotId) {
                            Bot toBot = bots.get(toBotId);
                            Map<String, String> toChannelConfig = (Map<String, String>) channelsConfig.get(toChannelId);
                            Map<String, String> fromChannelConfig = (Map<String, String>) channelsConfig.get(fromChannelId);

                            if (!fromChannelId.equals(toChannelId))
                                fromBot.addBridge(toBot, toChannelConfig.get(Config.NAME_KEY), fromChannelConfig.get(Config.NAME_KEY));
                        }
                    }
                }
            }
        }
    }

    private static String channelToBotId(String channelId,
                                         Map<String, Object> channelsConfig) {
        for (Map.Entry<String, Object> entry : channelsConfig.entrySet()) {
            if (entry.getKey().equals(channelId)) {
                Map<String, String> channelConfig = (Map<String, String>) entry.getValue();
                return channelConfig.get(Config.BOT_KEY);
            }
        }

        return null;
    }

    private static void handleShutdown() throws InterruptedException {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Application terminated");
        }));

        // Wait forever unless the process is killed
        Thread.currentThread().join();
    }
}
