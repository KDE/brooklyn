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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A new way to manage RocketChat bots, using the official lib
 */
public class RocketChatAlphaBot implements Bot {
    private final BotsController botsController = new BotsController();
    private Logger logger;
    private String botId;

    @Override
    public boolean init(String botId, Map<String, String> configs, String[] channels) {
        logger = LogManager.getLogger(RocketChatAlphaBot.class.getSimpleName() + ":" + botId);

        this.botId = botId;
        return false;
    }

    @Override
    public String getChannelName(String channelId) {
        return channelId;
    }

    @Override
    public void addBridge(Bot bot, String channelTo, String channelFrom) {
        botsController.addBridge(bot, channelTo, channelFrom);
    }

    @Override
    public Optional<String> sendMessage(BotTextMessage msg, String channelTo) {
        return Optional.empty();
    }

    @Override
    public Optional<String> sendMessage(BotDocumentMessage msg, String channelTo) {
        return Optional.empty();
    }

    @Override
    public void editMessage(BotTextMessage msg, String channelTo, String messageId) {

    }

    @Override
    public List<String> getUsers(String channel) {
        return new ArrayList<>(0);
    }

    @Override
    public String getId() {
        return botId;
    }

    @Override
    public void close() throws Exception {

    }
}
