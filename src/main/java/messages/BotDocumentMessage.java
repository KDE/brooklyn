package messages;

public class BotDocumentMessage extends BotTextMessage {
    private final byte[] doc;
    private final String fileExtension;

    public BotDocumentMessage(BotTextMessage message, String fileExtension, byte[] doc) {
        super(message, message.getText());
        this.doc = doc;
        this.fileExtension = fileExtension;
    }

    public String getFileExtension() {
        return this.fileExtension;
    }

    public byte[] getDoc() {
        return this.doc;
    }
}
