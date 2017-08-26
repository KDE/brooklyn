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
import messages.BotDocumentType;
import messages.BotMessage;
import messages.BotTextMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javatuples.Triplet;
import org.kde.brooklyn.RocketChatAttachment;
import org.kde.brooklyn.RocketChatException;
import org.kde.brooklyn.RocketChatMessage;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class RocketChatBot implements Bot {
    private static final Logger logger = LogManager.getLogger(RocketChatBot.class.getSimpleName());

    private static final String USERNAME_KEY = "username";
    private static final String WEBSOCKET_URL_KEY = "websocket-url";
    private static final String FILE_UPLOAD_URL_KEY = "file-upload-url";
    private static final String PASSWORD_KEY = "password";
    private static final Pattern PATTERN = Pattern.compile("\\s+");
    private final BotsController botsController = new BotsController();
    private org.kde.brooklyn.RocketChatBot bot;

    private String botId;

    @Override
    public boolean init(final String botId, final Map<String, String> configs,
                        final String[] channels) {
        this.botId = botId;

        if (!configs.containsKey(WEBSOCKET_URL_KEY) ||
                !configs.containsKey(USERNAME_KEY) ||
                !configs.containsKey(PASSWORD_KEY) ||
                !configs.containsKey(FILE_UPLOAD_URL_KEY)) {
            logger.error("At least one key missing on Rocket.Chat bot params. ");
            return false;
        }

        final URI serverUri;
        try {
            serverUri = new URI(configs.get(WEBSOCKET_URL_KEY));
        } catch (URISyntaxException e) {
            logger.error("websocket-url is not a valid URL. ");
            return false;
        }
        final String username = configs.get(USERNAME_KEY);
        final String password = configs.get(PASSWORD_KEY);

        final URL fileUploadUrl;
        try {
            fileUploadUrl = new URL(configs.get(FILE_UPLOAD_URL_KEY));
        } catch (MalformedURLException e) {
            logger.error("file-upload-url is not a valid URL. ", e);
            return false;
        }

        try {
            this.bot = new org.kde.brooklyn.RocketChatBot(serverUri, fileUploadUrl, username, password, false) {
                @Override
                public void close() {
                }

                @Override
                protected void onMessageReceived(RocketChatMessage message) {
                    if (!username.equals(message.username))
                        RocketChatBot.this.onMessageReceived(message);
                }

                @Override
                protected void onMessageEdited(RocketChatMessage message) {
                    if (!username.equals(message.username))
                        RocketChatBot.this.onMessageEdited(message);
                }
            };
        } catch (RocketChatException e) {
            logger.error(e);
            return false;
        }

        if (!bot.isLogged())
            return false;

        for (String channel : channels) {
            bot.addRoom(channel);
        }

        return true;
    }

    @Override
    public void addBridge(Bot bot, String channelTo, String channelFrom) {
        botsController.addBridge(bot, channelTo, channelFrom);
    }

    private void onMessageReceived(RocketChatMessage message) {
        if (message.attachment.isPresent()) {
            final RocketChatAttachment attachment = message.attachment.get();

            final String[] filenameSplitted = attachment.title.split("\\.");
            String filename;
            final String extension;
            if (filenameSplitted.length > 1) {
                final StringBuilder builder = new StringBuilder(filenameSplitted[0]);
                for (int n = 1; n < filenameSplitted.length - 1; n++) {
                    builder.append('.').append(filenameSplitted[n]);
                }
                filename = builder.toString();
                extension = filenameSplitted[filenameSplitted.length - 1];
            } else {
                filename = attachment.title;
                extension = "";
            }

            final BotMessage botMessage = new BotMessage(message.username, message.roomId, this);
            final BotTextMessage botTextMessage = new BotTextMessage(botMessage, attachment.description.get());

            final BotDocumentMessage botDocumentMessage =
                    new BotDocumentMessage(botTextMessage,
                            filename, extension, attachment.data, BotDocumentType.OTHER);

            botsController.sendMessage(botDocumentMessage, message.roomId, Optional.of(message.id));
        } else {
            String[] textSpaceSplitted = PATTERN.split(message.msg);
            if (2 == textSpaceSplitted.length &&
                    textSpaceSplitted[0].equals("@" + message.username) &&
                    "users".equals(textSpaceSplitted[1])) {
                List<Triplet<Bot, String, List<String>>> users = botsController.askForUsers(message.roomId);
                users.forEach(channel -> {
                    final String channelName = channel.getValue0().getChannelName(channel.getValue1());
                    final StringBuilder output = new StringBuilder();
                    output.append(channel.getValue0().getClass().getSimpleName())
                            .append('/')
                            .append(channelName)
                            .append(": ");

                    channel.getValue2().forEach(userTo -> output.append(userTo).append(", "));

                    output.delete(output.length() - 2, output.length() - 1);
                    bot.sendMessage(output.toString(), message.roomId, Optional.empty());
                });
            } else {
                final BotMessage botMessage = new BotMessage(message.username, message.roomId, this);
                final BotTextMessage botTextMessage = new BotTextMessage(botMessage, message.msg);

                botsController.sendMessage(botTextMessage, message.roomId, Optional.of(message.id));
            }
        }
    }

    private void onMessageEdited(RocketChatMessage message) {
        final BotMessage botMessage = new BotMessage(message.username, message.roomId, this);

        String text = message.msg;
        if (message.attachment.isPresent()) {
            final RocketChatAttachment attachment = message.attachment.get();
            if (attachment.description.isPresent()) {
                text = attachment.description.get();
            }
        }

        final BotTextMessage botTextMessage = new BotTextMessage(botMessage, text);

        botsController.editMessage(botTextMessage, message.roomId, message.id);
    }

    @Override
    public Optional<String> sendMessage(BotTextMessage msg, String channelTo) {
        final String msgText = BotsController.messageFormatter(
                msg.getBotFrom(), msg.getChannelFrom(), msg.getNicknameFrom(),
                Optional.of(msg.getText()));

        final String msgId = bot.sendMessage(msgText, channelTo, Optional.empty());
        return Optional.of(msgId);
    }

    @Override
    public Optional<String> sendMessage(BotDocumentMessage msg, String channelTo) {
        String caption = BotsController.messageFormatter(
                msg.getBotFrom(), msg.getChannelFrom(),
                msg.getNicknameFrom(), Optional.ofNullable(msg.getText()));
        String filename = msg.getFilename() + '.' + msg.getFileExtension();

        final RocketChatAttachment attachment = new RocketChatAttachment();
        attachment.description = Optional.of(caption);
        attachment.title = filename;
        attachment.data = msg.getDoc();

        return Optional.of(bot.sendMessage(caption, attachment, channelTo, Optional.empty()));
    }

    @Override
    public void editMessage(BotTextMessage msg, String channelTo, String messageId) {
        final String text = BotsController.messageFormatter(msg.getBotFrom(),
                msg.getChannelFrom(), msg.getNicknameFrom(), Optional.of(msg.getText()));
        bot.updateMessage(text, messageId, channelTo);
    }

    @Override
    public List<String> getUsers(String channel) {
        return bot.getUsers(channel);
    }

    @Override
    public String getId() {
        return botId;
    }

    @Override
    public String getChannelName(String channelId) {
        return bot.getRoomName(channelId);
    }

    @Override
    public void close() throws Exception {
        bot.close();
    }
}
