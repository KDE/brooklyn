package bots.messages;

import bots.Bot;

public class BotMessage {
    public BotMessage(final String nicknameFrom, final String channelFrom, final Bot botFrom) {
        this.nicknameFrom = nicknameFrom;
        this.channelFrom = channelFrom;
        this.botFrom = botFrom;
    }

    private final String nicknameFrom;
    private final String channelFrom;
    private final Bot botFrom;

    public String getNicknameFrom() {
        return nicknameFrom;
    }

    public String getChannelFrom() {
        return channelFrom;
    }

    public Bot getBotFrom() {
        return botFrom;
    }
}
