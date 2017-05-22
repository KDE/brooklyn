package bots;

import bots.messages.BotTextMessage;

import java.util.Map;

public interface Bot {
    String EVERY_CHANNEL = "*";

    boolean init(final Map<String, String> configs, final String[] channels);

    void addBridge(final Bot bot, final String channelTo, final String channelFrom);
    void sendMessage(final BotTextMessage msg, final String channelTo);
}
