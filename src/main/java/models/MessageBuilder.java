package models;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by davide on 05/06/17.
 */
public class MessageBuilder {
    private static MongoCollection<Document> messages;
    private final Document messageBundle = new Document();
    private final List<Document> history = new LinkedList<>();

    public static void init(MongoDatabase db) {
        if (messages == null)
            messages = db.getCollection("messages");
    }

    public void append(String botId, String messageId, String channelId) {
        Document message = new Document("bot", botId)
                .append("channel", channelId)
                .append("message", messageId);

        this.history.add(message);
    }

    public void saveHistory() {
        // Append array to messageBundle
        this.messageBundle.append("history", this.history);
        messages.insertOne(this.messageBundle);
    }
}
