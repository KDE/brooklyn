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

import messages.BotDocumentMessage;
import messages.BotTextMessage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A new way to manage RocketChat bots, using the official lib
 */
public class RocketChatAlphaBot implements Bot {
    @Override
    public boolean init(String botId, Map<String, String> configs, String[] channels) {
        return false;
    }

    @Override
    public void addBridge(Bot bot, String channelTo, String channelFrom) {

    }

    @Override
    public Optional<String> sendMessage(BotTextMessage msg, String channelTo) {
        return null;
    }

    @Override
    public Optional<String> sendMessage(BotDocumentMessage msg, String channelTo) {
        return null;
    }

    @Override
    public void editMessage(BotTextMessage msg, String channelTo, String messageId) {

    }

    @Override
    public List<String> getUsers(String channel) {
        return null;
    }

    @Override
    public String getId() {
        return null;
    }

    @Override
    public void close() throws Exception {

    }
}
