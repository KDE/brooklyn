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
