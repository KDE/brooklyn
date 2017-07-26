/*
 * Copyright 2017 Davide Riva driva95@protonmail.com
 *
 * This is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

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

    public static String messageFormatter(Bot botFrom,
                                          String channelFrom,
                                          String nicknameFrom,
                                          Optional<String> message) {

        return message.map(s -> String.format("%s/%s/%s: %s",
                botFrom.getId(), botFrom.getChannelName(channelFrom), nicknameFrom, s)).orElseGet(() -> String.format("%s/%s/%s",
                botFrom.getId(), botFrom.getChannelName(channelFrom), nicknameFrom));
    }

    public void addBridge(Bot bot, String channelTo, String channelFrom) {
        this.sendToList.add(Triplet.with(bot, channelTo, channelFrom));
    }

    public void editMessage(BotTextMessage messageText, String channelFrom, String messageId) {
        this.sendToList.stream()
                .filter(sendTo -> sendTo.getValue2().equals(channelFrom) || channelFrom.equals(BotsController.EVERY_CHANNEL))
                .forEach(sendTo -> {
                    Optional<String> message = MessagesModel.getChildMessage(messageText.getBotFrom().getId(),
                            messageText.getChannelFrom(), messageId,
                            sendTo.getValue0().getId(), sendTo.getValue1());
                    message.ifPresent(id -> sendTo.getValue0().editMessage(messageText, sendTo.getValue1(), id));
                });
    }

    public void sendMessage(BotMessage message, String channelFrom,
                            Optional<String> messageId) {
        final MessageBuilder mb = messageId.map(id -> new MessageBuilder(message.getBotFrom().getId(), message.getChannelFrom(),
                id)).orElse(null);

        this.sendToList.stream()
                .filter(sendTo -> sendTo.getValue2().equals(channelFrom) || channelFrom.equals(BotsController.EVERY_CHANNEL))
                .forEach(sendTo -> {
                    Optional<String> msgId;
                    if (message instanceof BotDocumentMessage)
                        msgId = sendTo.getValue0().sendMessage(
                                (BotDocumentMessage) message, sendTo.getValue1());
                    else if (message instanceof BotTextMessage)
                        msgId = sendTo.getValue0().sendMessage(
                                (BotTextMessage) message, sendTo.getValue1());
                    else {
                        System.err.println("Error, message type not valid.");
                        return;
                    }

                    messageId.ifPresent(s -> mb.append(sendTo.getValue0().getId(),
                            sendTo.getValue1(), msgId.orElse(UUID.randomUUID().toString())));
                });

        messageId.ifPresent(s -> mb.saveHistory());
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
