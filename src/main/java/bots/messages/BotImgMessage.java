package bots.messages;

import bots.messages.BotMessage;
import bots.messages.BotTextMessage;

public class BotImgMessage extends BotTextMessage {
    private final Byte[] img;
    public BotImgMessage(final BotTextMessage message, final Byte[] img, final String description) {
        super(message, message.getText());
        this.img = img;
    }
}
