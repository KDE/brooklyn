package bots;

import messages.BotDocumentMessage;
import messages.BotMessage;
import messages.BotTextMessage;
import models.FileStorage;
import net.engio.mbassy.listener.Handler;
import net.engio.mbassy.listener.Invoke;
import org.javatuples.Triplet;
import org.kitteh.irc.client.library.Client;
import org.kitteh.irc.client.library.element.Channel;
import org.kitteh.irc.client.library.element.User;
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent;
import org.kitteh.irc.client.library.event.helper.ChannelUserListChangeEvent;
import org.kitteh.irc.client.library.event.helper.ChannelUserListChangeEvent.Change;
import org.kitteh.irc.client.library.feature.AuthManager;
import org.kitteh.irc.client.library.feature.auth.SaslPlain;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Pattern;

public final class IrcBot implements Bot {
    private static final String USERNAME_KEY = "username";
    private static final String HOST_KEY = "host";
    private static final String PASSWORD_KEY = "password";
    private static final Pattern COMPILE = Pattern.compile("[\r\n]");
    private final List<Triplet<Bot, String, String>> sendToList = new LinkedList<>();
    private final Collection<String> usersParticipating = new LinkedHashSet<>();
    private Map<String, String> webserverConfig;
    private Client client;
    private String botId;

    @Override
    public boolean init(String botId, Map<String, String> configs, String[] channels) {
        if (!configs.containsKey(IrcBot.USERNAME_KEY))
            return false;
        if (!configs.containsKey(IrcBot.HOST_KEY))
            return false;

        this.client = Client.builder().nick(configs.get(IrcBot.USERNAME_KEY))
                .serverHost(configs.get(IrcBot.HOST_KEY)).build();
        if (configs.containsKey(IrcBot.PASSWORD_KEY)) {
            AuthManager auth = this.client.getAuthManager();
            auth.addProtocol(new SaslPlain(this.client,
                    this.client.getIntendedNick(), configs.get(IrcBot.PASSWORD_KEY)));
        }

        this.client.getEventManager().registerEventListener(this);

        for (String channel : channels) {
            try {
                this.client.addChannel(channel);
            } catch (IllegalArgumentException e) {
                System.err.println(String.format("Invalid channel name '%s' on '%s'.", channel, configs.get(IrcBot.HOST_KEY)));
                e.printStackTrace();
            }
        }

        this.botId = botId;

        return true;
    }

    @Override
    public void addBridge(Bot bot, String channelTo, String channelFrom) {
        this.sendToList.add(Triplet.with(bot, channelTo, channelFrom));
    }

    @Override
    public String sendMessage(BotTextMessage msg, String channelTo) {
        String[] messagesWithoutNewline = IrcBot.COMPILE.split(msg.getText()); // IRC doesn't allow CR / LF
        for (String messageToken : messagesWithoutNewline) {
            this.client.sendMessage(channelTo, String.format("%s/%s/%s: %s",
                    msg.getBotFrom().getClass().getSimpleName(), msg.getChannelFrom(), msg.getNicknameFrom(), messageToken));
        }

        // There aren't reasons to store IRC messages
        return null;
    }

    @Handler(delivery = Invoke.Asynchronously)
    private void onMessageReceived(ChannelMessageEvent message) {
        String authorNickname = message.getActor().getNick();
        this.usersParticipating.add(authorNickname);

        String channelFrom = message.getChannel().getName();
        String text = message.getMessage();

        String[] textSpaceSplitted = text.split("\\s+");
        if (2 == textSpaceSplitted.length &&
                textSpaceSplitted[0].equals(this.client.getNick()) &&
                textSpaceSplitted[1].equals("users")) {
            List<Triplet<Bot, String, String[]>> users = Bot.askForUsers(channelFrom, this.sendToList);
            for (Triplet<Bot, String, String[]> channel : users) {
                StringBuilder output = new StringBuilder();
                output.append(channel.getValue0().getClass().getSimpleName())
                        .append("/")
                        .append(channel.getValue1())
                        .append(": ");

                for (String userTo : channel.getValue2()) {
                    output.append(userTo).append(", ");
                }
                output.delete(output.length() - 2, output.length() - 1);
                this.client.sendMessage(channelFrom, output.toString());
            }
        } else {
            BotMessage msg = new BotMessage(authorNickname, channelFrom, this);
            BotTextMessage textMessage = new BotTextMessage(msg, text);
            // A new, useless msg builder is passed. There aren't reasons to store IRC messages
            Bot.sendMessage(textMessage, this.sendToList, channelFrom, null);
        }
    }

    @Handler
    public void onJoin(ChannelUserListChangeEvent event) {
        String authorNickname = event.getUser().getNick();
        if (!authorNickname.equals(this.client.getNick())) {
            Optional<Channel> channelFrom = event.getAffectedChannel();
            Change change = event.getChange();

            String channelFromName;
            if (channelFrom.isPresent())
                channelFromName = channelFrom.get().getName();
            else
                channelFromName = Bot.EVERY_CHANNEL;

            String message;
            if (0 == change.compareTo(Change.JOIN))
                message = String.format("%s joined the channel", authorNickname);
            else {
                // Send a notification only if the user has sent at least one message
                if (!this.usersParticipating.contains(authorNickname))
                    return;

                this.usersParticipating.remove(authorNickname);
                if (channelFrom.isPresent())
                    message = String.format("%s leaved the channel", authorNickname);
                else
                    message = String.format("%s leaved", authorNickname);
            }

            BotMessage msg = new BotMessage(authorNickname, channelFromName, this);
            BotTextMessage textMessage = new BotTextMessage(msg, message);

            // A new, useless msg builder is passed. There aren't reasons to store IRC messages
            Bot.sendMessage(textMessage, this.sendToList, channelFromName, null);
        }
    }

    @Override
    public String sendMessage(BotDocumentMessage msg, String channelTo) {
        try {
            String fileUrl = FileStorage.storeFile(msg.getDoc(), msg.getFileExtension());
            if (msg.getText() != null) {
                client.sendMessage(channelTo, String.format("%s/%s/%s: %s %s",
                        msg.getBotFrom().getClass().getSimpleName(), msg.getChannelFrom(), msg.getNicknameFrom(),
                        fileUrl, msg.getText()));

            } else {
                client.sendMessage(channelTo, String.format("%s/%s/%s: %s",
                        msg.getBotFrom().getClass().getSimpleName(), msg.getChannelFrom(), msg.getNicknameFrom(), fileUrl));
            }
        } catch (URISyntaxException | IOException e) {
            System.err.println("Error while storing the doc");
            e.printStackTrace();
        }

        // There aren't reasons to store IRC messages
        return null;
    }

    @Override
    public void deleteMessage(String messageId, String channelId) {
        return; // You can't delete messages on IRC
    }

    @Override
    public String[] getUsers(String channel) {
        Channel ircChannel = this.client.getChannel(channel).get();
        List<User> listOfUsers = ircChannel.getUsers();
        List<String> output = new ArrayList<>(listOfUsers.size());
        for(User user: listOfUsers) {
            String nick = user.getNick();
            if (!nick.equals(this.client.getNick()))
                output.add(nick);
        }

        return output.toArray(new String[output.size()]);
    }

    @Override
    public String getId() {
        return this.botId;
    }
}
