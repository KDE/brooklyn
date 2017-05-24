package bots;

import bots.messages.BotMessage;
import bots.messages.BotTextMessage;
import bots.messages.BotImgMessage;
import org.javatuples.Triplet;

import java.util.List;
import java.util.Map;

public interface Bot {
    String EVERY_CHANNEL = "*";

    boolean init(final Map<String, String> configs, final String[] channels);

    void addBridge(final Bot bot, final String channelTo, final String channelFrom);
    
    void sendMessage(final BotTextMessage msg, final String channelTo);
    void sendMessage(final BotImgMessage msg, final String channelTo);

    static void sendMessage(BotMessage message, List<Triplet<Bot, String, String>> sendToList,
                            String channelFrom) {
        for(Triplet<Bot, String, String> sendTo: sendToList) {
            if(sendTo.getValue2().equals(channelFrom)) {
                if(message instanceof BotImgMessage)
                    sendTo.getValue0().sendMessage((BotImgMessage)message, sendTo.getValue1());
                else if(message instanceof BotTextMessage)
                    sendTo.getValue0().sendMessage((BotTextMessage) message, sendTo.getValue1());
                else
                    System.err.println("Type of message not valid");
            }
        }
    }
}
