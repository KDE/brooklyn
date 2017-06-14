package messages;

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
        return this.fileExtension;
    }

    public byte[] getDoc() {
        return this.doc;
    }

    public BotDocumentType getDocumentType() {
        return this.type;
    }
}
