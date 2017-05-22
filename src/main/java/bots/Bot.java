package bots;

import bots.messages.BotTextMessage;
import bots.messages.BotImgMessage;

import java.util.Map;

public interface Bot {
    String EVERY_CHANNEL = "*";

    boolean init(final Map<String, String> configs, final String[] channels);

    void addBridge(final Bot bot, final String channelTo, final String channelFrom);
    void sendMessage(final BotTextMessage msg, final String channelTo);
    void sendMessage(final BotImgMessage msg, final String channelTo);
}
