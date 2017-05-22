package bots.messages;

public class BotTextMessage extends BotMessage {
    private String text;

    public BotTextMessage(BotMessage message, String text) {
        super(message.getNicknameFrom(), message.getChannelFrom(), message.getBotFrom());
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
