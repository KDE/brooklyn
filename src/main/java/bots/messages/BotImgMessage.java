package bots.messages;

public class BotImgMessage extends BotTextMessage {
    private final byte[] img;
    private final String fileExtension;

    public BotImgMessage(final BotTextMessage message, final String fileExstension, final byte[] img) {
        super(message, message.getText());
        this.img = img;
        this.fileExtension = fileExstension;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public byte[] getImg() {
        return img;
    }
}
