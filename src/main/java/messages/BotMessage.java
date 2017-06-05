package messages;

import bots.Bot;

public class BotMessage {
    private final String nicknameFrom;
    private final String channelFrom;
    private final Bot botFrom;

    public BotMessage(String nicknameFrom, String channelFrom, Bot botFrom) {
        this.nicknameFrom = nicknameFrom;
        this.channelFrom = channelFrom;
        this.botFrom = botFrom;
    }

    public String getNicknameFrom() {
        return this.nicknameFrom;
    }

    public String getChannelFrom() {
        return this.channelFrom;
    }

    public Bot getBotFrom() {
        return this.botFrom;
    }
}
