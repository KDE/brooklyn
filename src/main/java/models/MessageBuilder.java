package models;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.javatuples.Pair;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by davide on 05/06/17.
 */
public class MessageBuilder {
    private static MongoCollection<Document> messages;
    private final List<Pair<String, Document>> history;

    public MessageBuilder(MongoDatabase db) {
        if (MessageBuilder.messages == null)
            MessageBuilder.messages = db.getCollection("messages");

        this.history = new LinkedList<>();
    }

    public void append(String botId, String messageId, String channelId) {
        Document message = new Document("channel", channelId)
                .append("message", messageId);
        this.history.add(new Pair(botId, message));
    }

    public void saveHistory() {
        Document messageBundle = new Document();
        for (Pair<String, Document> message : this.history) {
            messageBundle.append(message.getValue0(), message.getValue1());
        }
        MessageBuilder.messages.insertOne(messageBundle);
    }
}
