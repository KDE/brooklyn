package bots;

import net.engio.mbassy.listener.Handler;
import net.engio.mbassy.listener.Invoke;
import org.javatuples.Triplet;
import org.kitteh.irc.client.library.Client;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.kitteh.irc.client.library.element.Channel;
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent;
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent;
import org.kitteh.irc.client.library.event.helper.ChannelUserListChangeEvent;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public final class IrcBot implements Bot {
    private static final String USERNAME_KEY = "username";
    private static final String HOST_KEY = "host";
    private final List<Triplet<Bot, String, String>> sendToList = new LinkedList<>();
    private Client client;

    @Override
    public boolean init(final Map<String, String> configs, final String[] channels) {
        if (!configs.containsKey(USERNAME_KEY))
            return false;
        if (!configs.containsKey(HOST_KEY))
            return false;

        client = Client.builder().nick(configs.get(USERNAME_KEY))
                .serverHost(configs.get(HOST_KEY)).build();
        client.getEventManager().registerEventListener(this);

        for (String channel : channels) {
            try {
                client.addChannel(channel);
            } catch (IllegalArgumentException e) {
                System.err.println(String.format("Invalid channel name '%s' on '%s'.", channel, configs.get(HOST_KEY)));
            }
        }

        return true;
    }

    @Override
    public void addBridge(final Bot bot, final String channelTo, final String channelFrom) {
        sendToList.add(Triplet.with(bot, channelTo, channelFrom));
    }

    @Override
    public void sendMessage(final String text, final String channelTo, String channelFrom, Bot botFrom, String authorNick) {
        String[] messagesWithoutNewline = text.split("[\r\n]"); // IRC doesn't allow CR / LF
        for(String messageToken : messagesWithoutNewline) {
            client.sendMessage(channelTo, String.format("%s/%s/%s: %s",
                    botFrom.getClass().getSimpleName(), channelFrom, authorNick, messageToken));
        }
    }

    @Handler(delivery = Invoke.Asynchronously)
    private void onMessageReceived(final ChannelMessageEvent message) {
        final String channelFrom = message.getChannel().getName();
        final String authorNickname = message.getActor().getNick();
        final String text = message.getMessage();

        for(Triplet<Bot, String, String> sendTo: sendToList) {
            sendTo.getValue0().sendMessage(text, sendTo.getValue1(), channelFrom, this, authorNickname);
        }
    }

    @Handler
    public void onJoin(final ChannelUserListChangeEvent event) {
        final String authorNickname = event.getUser().getNick();
        if(!authorNickname.equals(client.getNick())) {
            final Optional<Channel> channelFrom = event.getAffectedChannel();
            final ChannelUserListChangeEvent.Change change = event.getChange();
            for(Triplet<Bot, String, String> sendTo: sendToList) {
                final String message;
                if (change.compareTo(ChannelUserListChangeEvent.Change.JOIN) == 0)
                    message = String.format("%s joined the channel", authorNickname);
                else {
                    if(channelFrom.isPresent())
                        message = String.format("%s leaved the channel", authorNickname);
                    else
                        message = String.format("%s leaved", authorNickname);
                }

                final String channelFromName;
                if(channelFrom.isPresent())
                    channelFromName = channelFrom.get().getName();
                else
                    channelFromName = EVERY_CHANNEL;

                sendTo.getValue0().sendMessage(message, sendTo.getValue1(), channelFromName, this, authorNickname);
            }
        }
    }
}
