package models;

import org.javatuples.Triplet;

import java.sql.*;
import java.util.LinkedList;
import java.util.List;

public class MessagesModel {
    private static Connection database;

    public static void init(Connection database) throws SQLException {
        MessagesModel.database = database;
        MessageBuilder.init(database);

        String messageTableSql = "CREATE TABLE IF NOT EXISTS messages (\n"
                + "	id integer PRIMARY KEY AUTOINCREMENT,\n"
                + "	bot varchar(255) NOT NULL,\n"
                + "	channel varchar(255) NOT NULL,\n"
                + "	message varchar(255) NOT NULL,\n"
                + " CONSTRAINT UC_message UNIQUE (bot,channel,message)"
                + ");";

        String bridgeTableSql = "CREATE TABLE IF NOT EXISTS bridge (\n"
                + "fromId integer REFERENCES messages(id) ON DELETE CASCADE,\n"
                + "toId integer REFERENCES messages(id) ON DELETE CASCADE,\n"
                + "PRIMARY KEY(fromId, toId)"
                + ");";

        Statement createTables = database.createStatement();
        createTables.execute(messageTableSql);
        createTables.execute(bridgeTableSql);
        createTables.close();
    }

    public static void clean() {
        String deleteBridge = "DROP TABLE bridge;";
        String deleteMessages = "DROP TABLE messages;";
        try {
            Statement createTables = MessagesModel.database.createStatement();
            createTables.execute(deleteBridge);
            createTables.execute(deleteMessages);
            createTables.close();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    public static List<Triplet<String, String, String>> getChildMessages(String botId, String channelId, String messageId) {
        String query = "SELECT bot, channel, message \n"
                + "FROM messages INNER JOIN bridge ON messages.id = bridge.fromId \n"
                + "WHERE bridge.toId = \n"
                + "(SELECT id FROM messages WHERE bot=? AND channel=? AND message=?)\n"
                + ");";

        try (final PreparedStatement pstmt = MessagesModel.database.prepareStatement(query)) {
            pstmt.setString(1, botId);
            pstmt.setString(2, channelId);
            pstmt.setString(3, messageId);

            ResultSet rs = pstmt.executeQuery();
            List<Triplet<String, String, String>> result = new LinkedList();
            while (rs.next()) {
                result.add(new Triplet<>(rs.getString("bot"),
                        rs.getString("channel"), rs.getString("message")));
            }
            rs.close();
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return new LinkedList<>();
    }
}
