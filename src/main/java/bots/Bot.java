package bots;

import messages.BotDocumentMessage;
import messages.BotMessage;
import messages.BotTextMessage;
import models.MessageBuilder;
import models.MessagesModel;
import org.javatuples.Triplet;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public interface Bot {
    String EVERY_CHANNEL = "*";
    String LOCATION_TO_URL = "https://www.openstreetmap.org/?mlat=%s&&mlon=%s";

    static void sendMessage(BotMessage message, List<Triplet<Bot, String, String>> sendToList,
                            String channelFrom, Optional<MessageBuilder> optionalBuilder) {
        sendToList.stream()
                .filter(sendTo -> sendTo.getValue2().equals(channelFrom) || channelFrom.equals(Bot.EVERY_CHANNEL))
                .forEach(sendTo -> {
                    Optional<String> msgId;
                    if (message instanceof BotDocumentMessage) {
                        msgId = sendTo.getValue0().sendMessage(
                                (BotDocumentMessage) message, sendTo.getValue1());
                    } else if (message instanceof BotTextMessage) {
                        msgId = sendTo.getValue0().sendMessage(
                                (BotTextMessage) message, sendTo.getValue1());
                    } else {
                        System.err.println("Type of message not valid");
                        return;
                    }

                    if (optionalBuilder.isPresent()) {
                        optionalBuilder.get().append(sendTo.getValue0().getId(),
                                sendTo.getValue1(), msgId.orElse("-1"));
                    }
                });

        if (optionalBuilder.isPresent())
            optionalBuilder.get().saveHistory();
    }

    static void editMessage(BotTextMessage messageText, List<Triplet<Bot, String, String>> sendToList,
                            String channelFrom,
                            String messageId) {
        sendToList.stream()
                .filter(sendTo -> sendTo.getValue2().equals(channelFrom) || channelFrom.equals(Bot.EVERY_CHANNEL))
                .forEach(sendTo -> {
                    Optional<String> message = MessagesModel.getChildMessage(messageText.getBotFrom().getId(),
                            messageText.getChannelFrom(), messageId,
                            sendTo.getValue0().getId(), sendTo.getValue1());
                    if (message.isPresent()) {
                        sendTo.getValue0().editMessage(messageText, sendTo.getValue1(), message.get());
                    }
                });
    }

    static List<Triplet<Bot, String, String[]>> askForUsers(
            String channelFrom,
            List<Triplet<Bot, String, String>> askToList) {
        List<Triplet<Bot, String, String[]>> output = askToList.stream()
                .filter(askTo -> askTo.getValue2().equals(channelFrom))
                .map(askTo -> new Triplet<Bot, String, String[]>
                        (askTo.getValue0(), askTo.getValue1(),
                                askTo.getValue0().getUsers(askTo.getValue1())))
                .collect(Collectors.toList());

        return output;
    }

    static String messageFormatter(String botFrom,
                                   String channelFrom,
                                   String nicknameFrom,
                                   Optional<String> message) {
        if (message.isPresent())
            return String.format("%s/%s/%s: %s",
                    botFrom, channelFrom, nicknameFrom, message.get());

        return String.format("%s/%s/%s",
                botFrom, channelFrom, nicknameFrom);
    }

    boolean init(String botId, Map<String, String> configs, String[] channels);

    void addBridge(Bot bot, String channelTo, String channelFrom);

    Optional<String> sendMessage(BotTextMessage msg, String channelTo);

    Optional<String> sendMessage(BotDocumentMessage msg, String channelTo);

    void editMessage(BotTextMessage msg, String channelTo, String messageId);

    String[] getUsers(String channel);

    String getId();

    String channelIdToName(String channelId);
}
