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
import models.FileStorage;
import org.apache.commons.io.IOUtils;
import org.javatuples.Triplet;
import org.kde.brooklyn.RocketChatAttachment;
import org.kde.brooklyn.RocketChatException;
import org.kde.brooklyn.RocketChatMessage;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class RocketChatBot implements Bot {
    private static final String USERNAME_KEY = "username";
    private static final String WEBSOCKET_URL_KEY = "websocket-url";
    private static final String FILE_UPLOAD_URL_KEY = "file-upload-url";
    private static final String PASSWORD_KEY = "password";
    private static final long WAIT_BEFORE_LOGIN = 2000;
    private static final Pattern PATTERN = Pattern.compile("\\s+");
    private final BotsController botsController = new BotsController();
    private Map<String, String> configs = new HashMap<>(0);
    private org.kde.brooklyn.RocketChatBot bot;

    private String botId;

    @Override
    public boolean init(final String botId, final Map<String, String> configs,
                        final String[] channels) {
        this.botId = botId;
        this.configs = configs;

        if (!configs.containsKey(WEBSOCKET_URL_KEY) ||
                !configs.containsKey(USERNAME_KEY) ||
                !configs.containsKey(PASSWORD_KEY))
            return false;

        final URI serverUri;
        try {
            serverUri = new URI(configs.get(WEBSOCKET_URL_KEY));
        } catch (URISyntaxException e) {
            return false;
        }
        final String username = configs.get(USERNAME_KEY);
        final String password = configs.get(PASSWORD_KEY);

        try {
            this.bot = new org.kde.brooklyn.RocketChatBot(serverUri, username, password) {
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
            e.printStackTrace();
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

    private byte[] downloadFromId(String id) throws IOException {
        final String baseUrl = configs.get(FILE_UPLOAD_URL_KEY);
        final String fileUrl;
        if (baseUrl.substring(baseUrl.length() - 2, baseUrl.length() - 1).equals("/"))
            fileUrl = baseUrl + id;
        else
            fileUrl = baseUrl + "/" + id;

        HttpURLConnection httpConn = (HttpURLConnection) new URL(fileUrl).openConnection();
        InputStream inputStream = httpConn.getInputStream();
        byte[] output = IOUtils.toByteArray(inputStream);

        inputStream.close();
        httpConn.disconnect();
        return output;
    }

    private void onMessageReceived(RocketChatMessage message) {
        if (message.attachment.isPresent()) {
            final RocketChatAttachment attachment = message.attachment.get();

            final String[] filenameSplitted = attachment.title.split(".");
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

            try {
                final byte[] data = downloadFromId(attachment.id + '/' + attachment.title);

                final BotMessage botMessage = new BotMessage(message.username, message.roomId, this);
                final BotTextMessage botTextMessage = new BotTextMessage(botMessage, message.msg);
                final BotDocumentMessage botDocumentMessage =
                        new BotDocumentMessage(botTextMessage,
                                filename, extension, data, BotDocumentType.OTHER);

                botsController.sendMessage(botDocumentMessage, message.roomId, Optional.of(message.id));
            } catch (IOException e) {
                System.err.println("Failed to download the attachment");
                e.printStackTrace();
            }
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
        final BotTextMessage botTextMessage = new BotTextMessage(botMessage, message.msg);

        botsController.editMessage(botTextMessage, message.roomId, message.id);
    }

    @Override
    public Optional<String> sendMessage(BotTextMessage msg, String channelTo) {
        final String alias = BotsController.messageFormatter(msg.getBotFrom().getId(),
                msg.getChannelFrom(), msg.getNicknameFrom(), Optional.empty());

        final String msgId = bot.sendMessage(msg.getText(), channelTo, Optional.of(alias));
        return Optional.of(msgId);
    }

    @Override
    public Optional<String> sendMessage(BotDocumentMessage msg, String channelTo) {
        try {
            final String fileUrl = FileStorage.storeFile(msg.getDoc(), msg.getFileExtension());
            final String id;
            if (null != msg.getText()) {
                id = bot.sendMessage(fileUrl + System.lineSeparator() + msg.getText(),
                        channelTo, Optional.of(msg.getNicknameFrom()));
            } else {
                id = bot.sendMessage(fileUrl,
                        channelTo, Optional.of(msg.getNicknameFrom()));
            }

            return Optional.of(id);
        } catch (URISyntaxException | IOException e) {
            System.err.println("Error while storing the doc");
            e.printStackTrace();
        }

        return Optional.empty();
    }

    @Override
    public void editMessage(BotTextMessage msg, String channelTo, String messageId) {
        bot.updateMessage(msg.getText(), messageId, channelTo);
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
