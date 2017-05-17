package bots;

import java.util.Map;

public interface Bot {
    String EVERY_CHANNEL = "*";

    boolean init(final Map<String, String> configs, final String[] channels);

    void addBridge(final Bot bot, final String channelTo, final String channelFrom);
    void sendMessage(final String text, final String channelTo, final String channelFrom,
                     final Bot botFrom, final String authorNick);
}
