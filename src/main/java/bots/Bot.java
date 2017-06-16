package bots;

import messages.BotDocumentMessage;
import messages.BotTextMessage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface Bot {
    boolean init(String botId, Map<String, String> configs, String[] channels);

    void addBridge(Bot bot, String channelTo, String channelFrom);

    Optional<String> sendMessage(BotTextMessage msg, String channelTo);

    Optional<String> sendMessage(BotDocumentMessage msg, String channelTo);

    void editMessage(BotTextMessage msg, String channelTo, String messageId);

    List<String> getUsers(String channel);

    String getId();

    String channelIdToName(String channelId);
}
