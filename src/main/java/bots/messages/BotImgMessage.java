package bots.messages;

import bots.messages.BotMessage;
import bots.messages.BotTextMessage;

public class BotImgMessage extends BotTextMessage {
    private final byte[] img;
    private final String filename;
    public BotImgMessage(final BotTextMessage message, final String filename, final byte[] img) {
        super(message, message.getText());
        this.img = img;
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
    }

    public byte[] getImg() {
        return img;
    }
}
