package messages;

public class BotDocumentMessage extends BotTextMessage {
    private final byte[] doc;
    private final String fileExtension;
    private final BotDocumentType type;
    private final String filename;

    public BotDocumentMessage(BotTextMessage message,
                              String filename, String fileExtension,
                              byte[] doc, BotDocumentType type) {
        super(message, message.getText());
        this.doc = doc;
        this.fileExtension = fileExtension;
        this.type = type;
        this.filename = filename;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public byte[] getDoc() {
        return doc;
    }

    public String getFilename() {
        return filename;
    }

    public BotDocumentType getDocumentType() {
        return type;
    }
}
