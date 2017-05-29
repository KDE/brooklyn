import bots.Bot;
import bots.TelegramBot;

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
            final Map<String, String> botConfig = (Map<String, String>) entry.getValue();
            try {
                final Object newClass = Class.forName(Bot.class.getPackage().getName() + '.' + botConfig.get(Config.BOT_TYPE_KEY)).newInstance();
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
            } catch (final Exception e) {
                System.err.println(String.format("Class of type '%s' can't be instantiated.", botConfig.get(Config.BOT_TYPE_KEY)));
                e.printStackTrace();
            }
        }

        return bots;
    }

    private static String[] getChannelsName(final String botName,
                                            final Map<String, Object> channelsConfig) {
        final List<String> channels = new LinkedList<>();
        for (final Map.Entry<String, Object> entry : channelsConfig.entrySet()) {
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
        for (final Iterable<String> bridgeConfig : bridgesConfig) {
            for (final String fromChannelId : bridgeConfig) {
                final String fromBotId = channelToBotId(fromChannelId, channelsConfig);
                if (fromBotId != null) {
                    final Bot fromBot = bots.get(fromBotId);
                    for (final String toChannelId : bridgeConfig) {
                        final String toBotId = channelToBotId(toChannelId, channelsConfig);
                        if (null != toBotId) {
                            final Bot toBot = bots.get(toBotId);
                            final Map<String, String> toChannelConfig = (Map<String, String>) channelsConfig.get(toChannelId);
                            final Map<String, String> fromChannelConfig = (Map<String, String>) channelsConfig.get(fromChannelId);

                            if (!fromChannelId.equals(toChannelId))
                                fromBot.addBridge(toBot, toChannelConfig.get(Config.NAME_KEY), fromChannelConfig.get(Config.NAME_KEY));
                        }
                    }
                }
            }
        }
    }

    private static String channelToBotId(final String channelId,
                                         final Map<String, Object> channelsConfig) {
        for (final Map.Entry<String, Object> entry : channelsConfig.entrySet()) {
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
