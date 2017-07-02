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

package models;

import java.sql.*;
import java.util.Optional;

public class MessagesModel {
    private static Connection database;

    public static void init(Connection database) throws SQLException {
        MessagesModel.database = database;
        MessageBuilder.init(database);

        String messageTableSql = "CREATE TABLE IF NOT EXISTS messages (\n"
                + "	id integer PRIMARY KEY AUTOINCREMENT,\n"
                + "	bot varchar(255) NOT NULL,\n"
                + "	channel varchar(255) NOT NULL,\n"
                + "	message varchar(36) NOT NULL,\n"
                + " CONSTRAINT UC_message UNIQUE (bot,channel,message)"
                + ");";

        String bridgeTableSql = "CREATE TABLE IF NOT EXISTS bridge (\n"
                + "fromId integer REFERENCES messages(id) ON DELETE CASCADE,\n"
                + "toId integer REFERENCES messages(id) ON DELETE CASCADE,\n"
                + "PRIMARY KEY(fromId, toId)"
                + ");";

        try (Statement createTables = database.createStatement()) {
            createTables.execute(messageTableSql);
            createTables.execute(bridgeTableSql);
        }
    }

    public static void clean() {
        String deleteBridge = "DROP TABLE bridge;";
        String deleteMessages = "DROP TABLE messages;";
        try (Statement createTables = database.createStatement()) {
            createTables.execute(deleteBridge);
            createTables.execute(deleteMessages);
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    public static Optional<String> getChildMessage(String botIdFrom, String channelIdFrom, String messageIdFrom,
                                                   String botIdTo, String channelIdTo) {
        String query = "SELECT toMessages.message \n"
                + "FROM ((messages fromMessages INNER JOIN bridge ON fromMessages.id = bridge.fromId) \n"
                + "INNER JOIN messages toMessages ON toMessages.id = bridge.toId)"
                + "WHERE fromMessages.bot = ? AND fromMessages.channel = ? AND fromMessages.message = ? \n"
                + "AND toMessages.bot = ? AND toMessages.channel = ? \n"
                + "LIMIT 1;";

        Optional<String> output = Optional.empty();
        try (final PreparedStatement pstmt = database.prepareStatement(query)) {
            pstmt.setString(1, botIdFrom);
            pstmt.setString(2, channelIdFrom);
            pstmt.setString(3, messageIdFrom);
            pstmt.setString(4, botIdTo);
            pstmt.setString(5, channelIdTo);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next())
                    output = Optional.ofNullable(rs.getString("message"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return output;
    }
}
