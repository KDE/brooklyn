package bots.messages;

import bots.messages.BotMessage;
import bots.messages.BotTextMessage;

public class BotImgMessage extends BotTextMessage {
    private final byte[] img;
    public BotImgMessage(final BotTextMessage message, final byte[] img) {
        super(message, message.getText());
        this.img = img;
    }
}
