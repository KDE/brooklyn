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
import messages.BotTextMessage;
import org.kde.brooklyn.RocketChatException;
import org.kde.brooklyn.RocketChatMessage;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RocketChatBot implements Bot {
    private static final String USERNAME_KEY = "username";
    private static final String HOST_KEY = "host";
    private static final String PASSWORD_KEY = "password";

    private final BotsController botsController = new BotsController();
    private org.kde.brooklyn.RocketChatBot bot;

    private String botId;

    @Override
    public boolean init(final String botId, final Map<String, String> configs,
                        final String[] channels) {
        this.botId = botId;

        if (!configs.containsKey(HOST_KEY) ||
                !configs.containsKey(USERNAME_KEY) ||
                !configs.containsKey(PASSWORD_KEY))
            return false;

        final URI serverUri;
        try {
            serverUri = new URI(configs.get(HOST_KEY));
        } catch (URISyntaxException e) {
            return false;
        }
        final String username = configs.get(USERNAME_KEY);
        final String password = configs.get(PASSWORD_KEY);

        try {
            this.bot = new org.kde.brooklyn.RocketChatBot(serverUri, username, password) {
                @Override
                protected void onMessageReceived(RocketChatMessage message) {
                    RocketChatBot.this.onMessageReceived(message);
                }

                @Override
                protected void onMessageEdited(RocketChatMessage message) {
                    onMessageEdited(message);
                }
            };
        } catch (RocketChatException e) {
            e.printStackTrace();
            return false;
        }

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
        // TODO: implement this
    }

    private void onMessageEdited(RocketChatMessage message) {
        // TODO: implement this
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
        // TODO: implement this
        return Optional.empty();
    }

    @Override
    public void editMessage(BotTextMessage msg, String channelTo, String messageId) {
        bot.updateMessage(msg.getText(), messageId, channelTo);
    }

    @Override
    public List<String> getUsers(String channel) {
        // TODO: implement this
        return new ArrayList<>(0);
    }

    @Override
    public String getId() {
        return botId;
    }

    @Override
    public String channelIdToName(String channelId) {
        // TODO: implement this
        return channelId;
    }
}
