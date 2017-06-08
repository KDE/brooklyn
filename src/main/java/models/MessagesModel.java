package models;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Created by davide on 08/06/17.
 */
public class MessagesModel {
    private static Connection database;

    public static void init(Connection database) {
        MessagesModel.database = database;
        MessageBuilder.init(database);

        // TODO: move "create tables" here
    }

    public static void clean() {
        String deleteBridge = "DROP TABLE bridge;";
        String deleteMessages = "DROP TABLE messages;";
        try {
            Statement createTables = MessagesModel.database.createStatement();
            createTables.execute(deleteBridge);
            createTables.execute(deleteMessages);
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }
}
