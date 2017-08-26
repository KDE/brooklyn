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

package bots;

import core.BotsController;
import messages.BotDocumentMessage;
import messages.BotMessage;
import messages.BotTextMessage;
import models.FileStorage;
import net.engio.mbassy.listener.Handler;
import net.engio.mbassy.listener.Invoke;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javatuples.Triplet;
import org.kitteh.irc.client.library.Client;
import org.kitteh.irc.client.library.element.Channel;
import org.kitteh.irc.client.library.element.User;
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent;
import org.kitteh.irc.client.library.feature.AuthManager;
import org.kitteh.irc.client.library.feature.auth.SaslPlain;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class IrcBot implements Bot {
    private static final Logger logger = LogManager.getLogger(IrcBot.class.getSimpleName());

    private static final String USERNAME_KEY = "username";
    private static final String HOST_KEY = "host";
    private static final String PASSWORD_KEY = "password";
    private static final Pattern COMPILE = Pattern.compile("[\r\n]");
    private static final Pattern PATTERN = Pattern.compile("\\s+");
    private final BotsController botsController = new BotsController();
    private final ResourceBundle resourceBundle = ResourceBundle.getBundle("resources");
    private Client client;
    private String botId;

    @Override
    public boolean init(String botId, Map<String, String> configs, String[] channels) {
        if (!configs.containsKey(USERNAME_KEY))
            return false;
        if (!configs.containsKey(HOST_KEY))
            return false;

        client = Client.builder()
                .nick(configs.get(USERNAME_KEY))
                .serverHost(configs.get(HOST_KEY))
                .listenInput(line -> logger.debug(botId + " [I]: " + line))
                .listenOutput(line -> logger.debug(botId + " [O]: " + line))
                .listenException(logger::error)
                .build();

        if (configs.containsKey(PASSWORD_KEY)) {
            AuthManager auth = client.getAuthManager();
            auth.addProtocol(new SaslPlain(client,
                    client.getIntendedNick(), configs.get(PASSWORD_KEY)));
        }

        client.getEventManager().registerEventListener(this);

        for (String channel : channels) {
            try {
                client.addChannel(channel);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid channel name '{}' on '{}'.",
                        channel,
                        configs.get(HOST_KEY), e);
            }
        }

        this.botId = botId;

        return true;
    }

    @Override
    public void addBridge(Bot bot, String channelTo, String channelFrom) {
        botsController.addBridge(bot, channelTo, channelFrom);
    }

    @Override
    public Optional<String> sendMessage(BotTextMessage msg, String channelTo) {
        String[] messagesWithoutNewline = COMPILE.split(msg.getText()); // IRC doesn't allow CR / LF
        for (String messageToken : messagesWithoutNewline) {
            client.sendMessage(channelTo, BotsController.messageFormatter(
                    msg.getBotFrom(), msg.getChannelFrom(),
                    msg.getNicknameFrom(), Optional.ofNullable(messageToken)));
        }

        // There aren't reasons to store IRC messages
        return Optional.empty();
    }

    @Handler(delivery = Invoke.Asynchronously)
    private void onMessageReceived(ChannelMessageEvent message) {
        String authorNickname = message.getActor().getNick();

        String text = message.getMessage();
        String channelFrom = message.getChannel().getName();

        String[] textSpaceSplitted = PATTERN.split(text);
        if (2 == textSpaceSplitted.length &&
                textSpaceSplitted[0].equals(client.getNick()) &&
                "users".equals(textSpaceSplitted[1])) {
            List<Triplet<Bot, String, List<String>>> users = botsController.askForUsers(channelFrom);
            users.forEach(channel -> {
                final String channelName = channel.getValue0().getChannelName(channel.getValue1());
                final StringBuilder output = new StringBuilder();
                output.append(channel.getValue0().getClass().getSimpleName())
                        .append('/')
                        .append(channelName)
                        .append(": ");

                channel.getValue2().forEach(userTo -> output.append(userTo).append(", "));

                output.delete(output.length() - 2, output.length() - 1);
                client.sendMessage(channelFrom, output.toString());
            });
        } else {
            BotMessage msg = new BotMessage(authorNickname, channelFrom, this);
            BotTextMessage textMessage = new BotTextMessage(msg, text);
            // An empty msgId is passed. There aren't reasons to store IRC messages
            botsController.sendMessage(textMessage, channelFrom, Optional.empty());
        }
    }

    @Override
    public Optional<String> sendMessage(BotDocumentMessage msg, String channelTo) {
        try {
            String fileUrl = FileStorage.storeFile(msg.getDoc(), msg.getFileExtension());
            if (msg.getText() != null) {
                String[] text = COMPILE.split(msg.getText());

                if (text.length == 1) {
                    this.client.sendMessage(channelTo, BotsController.messageFormatter(
                            msg.getBotFrom(), msg.getChannelFrom(), msg.getNicknameFrom(),
                            Optional.of(fileUrl + ' ' + text[0])));
                } else {
                    this.client.sendMessage(channelTo, BotsController.messageFormatter(
                            msg.getBotFrom(),
                            msg.getChannelFrom(),
                            msg.getNicknameFrom(),
                            Optional.ofNullable(fileUrl)));
                    for (String messageToken : text) {
                        client.sendMessage(channelTo, BotsController.messageFormatter(
                                msg.getBotFrom(), msg.getChannelFrom(),
                                msg.getNicknameFrom(), Optional.ofNullable(messageToken)));
                    }
                }
            } else {
                this.client.sendMessage(channelTo, BotsController.messageFormatter(
                        msg.getBotFrom(), msg.getChannelFrom(),
                        msg.getNicknameFrom(), Optional.ofNullable(fileUrl)));
            }
        } catch (URISyntaxException | IOException e) {
            logger.error("Error while storing the doc. ", e);
        }

        // There aren't reasons to store IRC messages
        return Optional.empty();
    }

    @Override
    public void editMessage(BotTextMessage msg, String channelTo, String messageId) {
        String[] messagesWithoutNewline = COMPILE.split(msg.getText()); // IRC doesn't allow CR / LF
        for (String messageToken : messagesWithoutNewline) {
            client.sendMessage(channelTo, BotsController.messageFormatter(
                    msg.getBotFrom(), msg.getChannelFrom(), msg.getNicknameFrom(),
                    Optional.of(MessageFormat.format(resourceBundle.getString("message-edited"), messageToken))));
        }
    }

    @Override
    public List<String> getUsers(String channel) {
        if (client.getChannel(channel).isPresent()) {
            Channel ircChannel = client.getChannel(channel).get();
            List<User> listOfUsers = ircChannel.getUsers();

            return listOfUsers.stream()
                    .filter(user -> !user.getNick().equals(client.getNick()))
                    .map(User::getNick)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>(0);
    }

    @Override
    public String getId() {
        return botId;
    }

    @Override
    public void close() throws Exception {
        client.shutdown();
    }
}
