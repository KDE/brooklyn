package messages;

import bots.Bot;

public class BotMessage {
    public static final String LICENSE_MESSAGE =
            "This software is released under the GNU AGPL license."
                    + System.lineSeparator()
                    + "https://phabricator.kde.org/source/brooklyn/";

    private final String nicknameFrom;
    private final String channelFrom;
    private final Bot botFrom;

    public BotMessage(String nicknameFrom, String channelFrom, Bot botFrom) {
        this.nicknameFrom = nicknameFrom;
        this.channelFrom = channelFrom;
        this.botFrom = botFrom;
    }

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
