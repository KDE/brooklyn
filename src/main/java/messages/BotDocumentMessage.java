package messages;

// TODO: insert an enum to indicate the type of document
public class BotDocumentMessage extends BotTextMessage {
    private final byte[] doc;
    private final String fileExtension;
    private final BotDocumentType type;

    public BotDocumentMessage(BotTextMessage message,
                              String fileExtension, byte[] doc, BotDocumentType type) {
        super(message, message.getText());
        this.doc = doc;
        this.fileExtension = fileExtension;
        this.type = type;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public byte[] getDoc() {
        return doc;
    }

    public BotDocumentType getDocumentType() {
        return type;
    }
}
