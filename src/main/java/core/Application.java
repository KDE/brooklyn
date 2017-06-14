package core;

import bots.Bot;
import bots.TelegramBot;
import models.FileStorage;
import models.MessagesModel;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.Map.Entry;

public final class Application {
    private static Connection database;

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

        Application.initDatabase(conf.getDbUri());

        Map<String, String> webserverConfig = conf.getWebserverConfig();
        FileStorage.init(webserverConfig);

        Map<String, Bot> bots = initBots(conf.getBots(), channelsConfig, webserverConfig);
        manageBridges(bots, channelsConfig, conf.getBridges());

        handleShutdown();
    }

    // TODO: move this into a model
    private static void initDatabase(String dbUri) {
        try {
            Application.database = DriverManager.getConnection(dbUri);
            MessagesModel.init(Application.database);
        } catch (SQLException e) {
            System.err.println("Error loading the database");
            e.printStackTrace();
        }
    }

    private static Map<String, Bot> initBots(Map<String, Object> botsConfig,
                                             Map<String, Object> channelsConfig,
                                             Map<String, String> webserverConfig) {
        int AVG_BOTS_N = 3;
        Map<String, Bot> bots = new LinkedHashMap<>(AVG_BOTS_N);
        botsConfig.entrySet().forEach(entry -> {
            Map<String, String> botConfig = (Map<String, String>) entry.getValue();
            try {
                Object newClass = Class.forName(Bot.class.getPackage().getName() + '.' + botConfig.get(Config.BOT_TYPE_KEY)).newInstance();
                if (newClass instanceof Bot) {
                    Bot bot = (Bot) newClass;
                    String[] channels = Application.getChannelsName(entry.getKey(), channelsConfig);
                    if (bot.init(entry.getKey(), botConfig, channels)) {
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
        });

        return bots;
    }

    private static String[] getChannelsName(String botName,
                                            Map<String, Object> channelsConfig) {
        List<String> channels = new LinkedList<>();
        channelsConfig.entrySet().forEach(entry -> {
            Map<String, String> channelConfig = (Map<String, String>) entry.getValue();
            if (botName.equals(channelConfig.get(Config.BOT_KEY))) {
                if (channelConfig.containsKey(Config.NAME_KEY))
                    channels.add(channelConfig.get(Config.NAME_KEY));
            }
        });

        return channels.toArray(new String[channels.size()]);
    }

    private static void manageBridges(Map<String, Bot> bots,
                                      Map<String, Object> channelsConfig,
                                      ArrayList<ArrayList<String>> bridgesConfig) {
        bridgesConfig.forEach(bridgeConfig -> {
            bridgeConfig.forEach(fromChannelId -> {
                Optional<String> fromBotId = Application.channelToBotId(fromChannelId, channelsConfig);
                if (fromBotId.isPresent()) {
                    Bot fromBot = bots.get(fromBotId.get());
                    bridgeConfig.forEach(toChannelId -> {
                        Optional<String> toBotId = Application.channelToBotId(toChannelId, channelsConfig);
                        if (toBotId.isPresent()) {
                            Bot toBot = bots.get(toBotId.get());
                            Map<String, String> toChannelConfig = (Map<String, String>) channelsConfig.get(toChannelId);
                            Map<String, String> fromChannelConfig = (Map<String, String>) channelsConfig.get(fromChannelId);

                            if (!fromChannelId.equals(toChannelId))
                                fromBot.addBridge(toBot, toChannelConfig.get(Config.NAME_KEY), fromChannelConfig.get(Config.NAME_KEY));
                        }
                    });
                }
            });
        });
    }

    private static Optional<String> channelToBotId(String channelId,
                                                   Map<String, Object> channelsConfig) {
        for (Entry<String, Object> entry : channelsConfig.entrySet()) {
            if (entry.getKey().equals(channelId)) {
                Map<String, String> channelConfig = (Map<String, String>) entry.getValue();
                return Optional.ofNullable(channelConfig.get(Config.BOT_KEY));
            }
        }

        return Optional.empty();
    }

    private static void handleShutdown() throws InterruptedException {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                MessagesModel.clean();
                Application.database.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            System.out.println("core.Application terminated");
        }));

        // Wait forever unless the process is killed
        Thread.currentThread().join();
    }
}
