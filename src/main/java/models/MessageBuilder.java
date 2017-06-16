package models;

import java.sql.*;
import java.util.LinkedList;
import java.util.List;

public class MessageBuilder {
    private static Connection database;
    private final int idFrom;
    private final List<Integer> idsTo = new LinkedList();

    public MessageBuilder(String botId, String channelId, String messageId) {
        this.idFrom = this.append(botId, channelId, messageId);
        this.idsTo.remove(Integer.valueOf(this.idFrom));
    }

    protected static void init(Connection database) {
        MessageBuilder.database = database;
    }

    public int append(String botId, String channelId, String messageId) {
        String sql = "INSERT INTO messages(bot,channel,message) VALUES(?,?,?)";
        try (final PreparedStatement pstmt = MessageBuilder.database.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, botId);
            pstmt.setString(2, channelId);
            pstmt.setString(3, messageId);
            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int newId = rs.getInt(1);
                    this.idsTo.add(newId);
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
        this.idsTo.forEach(idTo -> {
            try (final PreparedStatement pstmt = MessageBuilder.database.prepareStatement(sql)) {
                pstmt.setInt(1, this.idFrom);
                pstmt.setInt(2, idTo);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                System.err.println(e.getMessage());
            }
        });
    }
}
