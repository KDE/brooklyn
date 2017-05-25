import bots.Bot;
import bots.TelegramBot;

import java.io.IOException;
import java.util.*;

public class Application {
    public static void main(String[] args) throws InterruptedException {
        // TODO: find a way to replace this temporary fix
        TelegramBot.init();

        final Config conf;
        if(args.length < 1)
        conf = new Config(Config.DEFAULT_FILENAME);
        else
            conf = new Config(args[0]);

        try {
            conf.load();
        } catch (IOException e) {
            System.err.println(String.format("Error while loading config file: %s.", e.getMessage()));
            System.exit(1);
        }

        final Map<String, Object> channelsConfig = conf.getChannels();
        final Map<String, String> webserverConfig = conf.getWebserverConfig();

        final Map<String, Bot> bots = initBots(conf.getBots(), channelsConfig, webserverConfig);
        manageBridges(bots, channelsConfig, conf.getBridges());

        handleShutdown();
    }

    private static Map<String, Bot> initBots(final Map<String, Object> botsConfig,
                                             final Map<String, Object> channelsConfig,
                                             final Map<String, String> webserverConfig) {
        final Map<String, Bot> bots = new LinkedHashMap<String, Bot>();
        for (Map.Entry<String, Object> entry : botsConfig.entrySet()) {
            final Map<String, String> botConfig = (Map<String, String>) entry.getValue();
            try {
                final Object newClass = Class.forName(Bot.class.getPackage().getName() + "." + botConfig.get(Config.BOT_TYPE_KEY)).newInstance();
                if (newClass instanceof Bot) {
                    final Bot bot = (Bot) newClass;
                    final String[] channels = getChannelsName(entry.getKey(), channelsConfig);
                    if (bot.init(botConfig, channels, webserverConfig)) {
                        bots.put(entry.getKey(), bot);
                        System.out.println(String.format("Bot '%s' initialized.", entry.getKey()));
                    } else
                        System.err.println(String.format("Failed to init '%s' bot.", entry.getKey()));
                } else
                    System.err.println(String.format("'%s' is not a valid bot.", botConfig.get(Config.BOT_TYPE_KEY)));
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                System.err.println(String.format("Class of type '%s' can't be instantiated.", botConfig.get(Config.BOT_TYPE_KEY)));
            }
        }

        return bots;
    }

    private static String[] getChannelsName(final String botName,
                                            final Map<String, Object> channelsConfig) {
        final List<String> channels = new LinkedList<String>();
        for (Map.Entry<String, Object> entry : channelsConfig.entrySet()) {
            final Map<String, String> channelConfig = (Map<String, String>) entry.getValue();
            if (botName.equals(channelConfig.get(Config.BOT_KEY))) {
                if (channelConfig.containsKey(Config.NAME_KEY))
                    channels.add(channelConfig.get(Config.NAME_KEY));
            }
        }
        return channels.toArray(new String[channels.size()]);
    }

    private static void manageBridges(final Map<String, Bot> bots,
                                      final Map<String, Object> channelsConfig,
                                      final ArrayList<ArrayList<String>> bridgesConfig) {
        for (ArrayList<String> bridgeConfig : bridgesConfig) {
            for (String fromChannelId : bridgeConfig) {
                final String fromBotId = channelToBotId(fromChannelId, channelsConfig);
                if(fromBotId != null) {
                    final Bot fromBot = bots.get(fromBotId);
                    for (String toChannelId : bridgeConfig) {
                        final String toBotId = channelToBotId(toChannelId, channelsConfig);
                        if(toBotId != null) {
                            final Bot toBot = bots.get(toBotId);
                            final Map<String, String> toChannelConfig = (Map<String, String>) channelsConfig.get(toChannelId);
                            final Map<String, String> fromChannelConfig = (Map<String, String>) channelsConfig.get(fromChannelId);

                            if(fromChannelId != toChannelId)
                                fromBot.addBridge(toBot, toChannelConfig.get(Config.NAME_KEY), fromChannelConfig.get(Config.NAME_KEY));
                        }
                    }
                }
            }
        }
    }

    private static String channelToBotId(final String channelId,
                                         final Map<String, Object> channelsConfig) {
        for (Map.Entry<String, Object> entry : channelsConfig.entrySet()) {
            if (entry.getKey().equals(channelId)) {
                final Map<String, String> channelConfig = (Map<String, String>) entry.getValue();
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
