package bots.messages;

public class BotTextMessage extends BotMessage {
    private final String text;

    public BotTextMessage(final BotMessage message, final String text) {
        super(message.getNicknameFrom(), message.getChannelFrom(), message.getBotFrom());
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
