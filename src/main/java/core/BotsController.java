package core;

import bots.Bot;
import messages.BotDocumentMessage;
import messages.BotMessage;
import messages.BotTextMessage;
import models.MessageBuilder;
import models.MessagesModel;
import org.javatuples.Triplet;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class BotsController {
    public static final String EVERY_CHANNEL = "*";

    // Triplet<Bot bot, String channelTo, String channelFrom>
    private final List<Triplet<Bot, String, String>> sendToList = new LinkedList<>();

    public static String messageFormatter(String botFrom,
                                          String channelFrom,
                                          String nicknameFrom,
                                          Optional<String> message) {
        if (message.isPresent())
            return String.format("%s/%s/%s: %s",
                    botFrom, channelFrom, nicknameFrom, message.get());

        return String.format("%s/%s/%s",
                botFrom, channelFrom, nicknameFrom);
    }

    public void addBridge(Bot bot, String channelTo, String channelFrom) {
        this.sendToList.add(Triplet.with(bot, channelTo, channelFrom));
    }

    public void editMessage(BotTextMessage messageText, String channelFrom, String messageId) {
        // TODO: fix this bug
        this.sendToList.stream()
                .filter(sendTo -> sendTo.getValue2().equals(channelFrom) || channelFrom.equals(BotsController.EVERY_CHANNEL))
                .forEach(sendTo -> {
                    Optional<String> message = MessagesModel.getChildMessage(messageText.getBotFrom().getId(),
                            messageText.getChannelFrom(), messageId,
                            sendTo.getValue0().getId(), sendTo.getValue1());
                    if (message.isPresent()) {
                        sendTo.getValue0().editMessage(messageText, sendTo.getValue1(), message.get());
                    }
                });
    }

    public void sendMessage(BotMessage message, String channelFrom,
                            Optional<MessageBuilder> optionalBuilder) {
        this.sendToList.stream()
                .filter(sendTo -> sendTo.getValue2().equals(channelFrom) || channelFrom.equals(BotsController.EVERY_CHANNEL))
                .forEach(sendTo -> {
                    Optional<String> msgId = sendTo.getValue0().sendMessage(
                            (BotDocumentMessage) message, sendTo.getValue1());

                    if (optionalBuilder.isPresent()) {
                        optionalBuilder.get().append(sendTo.getValue0().getId(),
                                sendTo.getValue1(), msgId.orElse(UUID.randomUUID().toString()));
                    }
                });

        if (optionalBuilder.isPresent())
            optionalBuilder.get().saveHistory();
    }

    /**
     * @return a list of {@literal Triplet<Bot bot, String channel, List<String> nicknames>}
     */
    public List<Triplet<Bot, String, List<String>>> askForUsers(String channelFrom) {
        return this.sendToList.stream()
                .filter(askTo -> askTo.getValue2().equals(channelFrom))
                .map(askTo -> new Triplet<>(askTo.getValue0(), askTo.getValue1(),
                                askTo.getValue0().getUsers(askTo.getValue1())))
                .collect(Collectors.toList());
    }
}
