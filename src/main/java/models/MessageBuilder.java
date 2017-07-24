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
import java.util.LinkedList;
import java.util.List;

public class MessageBuilder {
    private static Connection database;
    private final int idFrom;
    private final List<Integer> idsTo = new LinkedList<>();

    public MessageBuilder(String botId, String channelId, String messageId) {
        idFrom = append(botId, channelId, messageId);
        idsTo.remove(Integer.valueOf(idFrom));
    }

    protected static void init(Connection database) {
        MessageBuilder.database = database;
    }

    public int append(String botId, String channelId, String messageId) {
        String sql = "INSERT INTO messages(bot,channel,message) VALUES(?,?,?)";
        try (final PreparedStatement pstmt = database.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, botId);
            pstmt.setString(2, channelId);
            pstmt.setString(3, messageId);
            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int newId = rs.getInt(1);
                    idsTo.add(newId);
                    return newId;
                } else
                    return -1;
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return -1;
        }
    }

    public void saveHistory() {
        String sql = "INSERT INTO bridge(fromId,toId) VALUES(?,?)";
        idsTo.forEach(idTo -> {
            try (final PreparedStatement pstmt = database.prepareStatement(sql)) {
                pstmt.setInt(1, idFrom);
                pstmt.setInt(2, idTo);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                System.err.println(e.getMessage());
            }
        });
    }
}
