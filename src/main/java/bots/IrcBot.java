package bots;

import core.BotsController;
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
    private final Collection<String> usersParticipating = new LinkedHashSet<>();
    private final BotsController botsController = new BotsController();
    private Map<String, String> webserverConfig;
    private Client client;
    private String botId;

    @Override
    public boolean init(String botId, Map<String, String> configs, String[] channels) {
        if (!configs.containsKey(USERNAME_KEY))
            return false;
        if (!configs.containsKey(HOST_KEY))
            return false;

        client = Client.builder().nick(configs.get(USERNAME_KEY))
                .serverHost(configs.get(HOST_KEY)).build();
        if (configs.containsKey(PASSWORD_KEY)) {
            AuthManager auth = client.getAuthManager();
            auth.addProtocol(new SaslPlain(client,
                    client.getIntendedNick(), configs.get(PASSWORD_KEY)));
        }

        client.getEventManager().registerEventListener(this);

        for (String channel : channels) {
            try {
                client.addChannel(channel);
            } catch (IllegalArgumentException e) {
                System.err.println(String.format("Invalid channel name '%s' on '%s'.", channel, configs.get(HOST_KEY)));
                e.printStackTrace();
            }
        }

        this.botId = botId;

        return true;
    }

    @Override
    public void addBridge(Bot bot, String channelTo, String channelFrom) {
        botsController.addBridge(bot, channelTo, channelFrom);
    }

    @Override
    public Optional<String> sendMessage(BotTextMessage msg, String channelTo) {
        String[] messagesWithoutNewline = COMPILE.split(msg.getText()); // IRC doesn't allow CR / LF
        for (String messageToken : messagesWithoutNewline) {
            client.sendMessage(channelTo, BotsController.messageFormatter(
                    msg.getBotFrom().getId(), msg.getChannelFrom(),
                    msg.getNicknameFrom(), Optional.ofNullable(messageToken)));
        }

        // There aren't reasons to store IRC messages
        return Optional.empty();
    }

    @Handler(delivery = Invoke.Asynchronously)
    private void onMessageReceived(ChannelMessageEvent message) {
        String authorNickname = message.getActor().getNick();
        usersParticipating.add(authorNickname);

        String channelFrom = message.getChannel().getName();
        String text = message.getMessage();

        String[] textSpaceSplitted = text.split("\\s+");
        if (2 == textSpaceSplitted.length &&
                textSpaceSplitted[0].equals(client.getNick()) &&
                textSpaceSplitted[1].equals("users")) {
            List<Triplet<Bot, String, String[]>> users = botsController.askForUsers(channelFrom);
            users.forEach(channel -> {
                StringBuilder output = new StringBuilder();
                output.append(channel.getValue0().getClass().getSimpleName())
                        .append("/")
                        .append(channel.getValue1())
                        .append(": ");

                for (String userTo : channel.getValue2()) {
                    output.append(userTo).append(", ");
                }
                output.delete(output.length() - 2, output.length() - 1);
                client.sendMessage(channelFrom, output.toString());
            });
        } else {
            BotMessage msg = new BotMessage(authorNickname, channelFrom, this);
            BotTextMessage textMessage = new BotTextMessage(msg, text);
            // An empty msg builder is passed. There aren't reasons to store IRC messages
            botsController.sendMessage(textMessage, channelFrom, Optional.empty());
        }
    }

    @Handler
    public void onJoin(ChannelUserListChangeEvent event) {
        String authorNickname = event.getUser().getNick();
        if (!authorNickname.equals(client.getNick())) {
            Optional<Channel> channelFrom = event.getAffectedChannel();
            ChannelUserListChangeEvent.Change change = event.getChange();

            final String channelFromName;
            if (channelFrom.isPresent())
                channelFromName = channelFrom.get().getName();
            else
                channelFromName = BotsController.EVERY_CHANNEL;

            final String message;
            if (0 == change.compareTo(ChannelUserListChangeEvent.Change.JOIN))
                message = String.format("%s joined the channel", authorNickname);
            else {
                // Send a notification only if the user has sent at least one message
                if (!usersParticipating.contains(authorNickname))
                    return;

                usersParticipating.remove(authorNickname);
                if (channelFrom.isPresent())
                    message = String.format("%s leaved the channel", authorNickname);
                else
                    message = String.format("%s leaved", authorNickname);
            }

            BotMessage msg = new BotMessage(authorNickname, channelFromName, this);
            BotTextMessage textMessage = new BotTextMessage(msg, message);

            // A new, useless msg builder is passed. There aren't reasons to store IRC messages
            botsController.sendMessage(textMessage, channelFromName, Optional.empty());
        }
    }

    @Override
    public Optional<String> sendMessage(BotDocumentMessage msg, String channelTo) {
        try {
            String fileUrl = FileStorage.storeFile(msg.getDoc(), msg.getFileExtension());
            if (msg.getText() != null) {
                String[] text = COMPILE.split(msg.getText());

                if (text.length == 1) {
                    this.client.sendMessage(channelTo, BotsController.messageFormatter(
                            msg.getBotFrom().getId(), msg.getChannelFrom(), msg.getNicknameFrom(),
                            Optional.ofNullable(fileUrl + " " + text[0])));
                } else {
                    this.client.sendMessage(channelTo, BotsController.messageFormatter(
                            msg.getBotFrom().getId(),
                            msg.getChannelFrom(),
                            msg.getNicknameFrom(),
                            Optional.ofNullable(fileUrl)));
                    for (String messageToken : text) {
                        client.sendMessage(channelTo, BotsController.messageFormatter(
                                msg.getBotFrom().getId(), msg.getChannelFrom(),
                                msg.getNicknameFrom(), Optional.ofNullable(messageToken)));
                    }
                }
            } else {
                this.client.sendMessage(channelTo, BotsController.messageFormatter(
                        msg.getBotFrom().getId(), msg.getChannelFrom(),
                        msg.getNicknameFrom(), Optional.ofNullable(fileUrl)));
            }
        } catch (URISyntaxException | IOException e) {
            System.err.println("Error while storing the doc");
            e.printStackTrace();
        }

        // There aren't reasons to store IRC messages
        return Optional.empty();
    }

    @Override
    public void editMessage(BotTextMessage msg, String channelTo, String messageId) {
        String channelName = msg.getBotFrom().channelIdToName(msg.getChannelFrom());
        String[] messagesWithoutNewline = COMPILE.split(msg.getText()); // IRC doesn't allow CR / LF
        for (String messageToken : messagesWithoutNewline) {
            client.sendMessage(channelTo, BotsController.messageFormatter(
                    msg.getBotFrom().getId(), channelName, msg.getNicknameFrom(),
                    Optional.of("(edited): " + messageToken)));
        }
    }

    @Override
    public String[] getUsers(String channel) {
        Channel ircChannel = client.getChannel(channel).get();
        List<User> listOfUsers = ircChannel.getUsers();
        List<String> output = new ArrayList<>(listOfUsers.size());
        listOfUsers.stream()
                .filter(user -> !user.getNick().equals(client.getNick()))
                .map(user -> user.getNick())
                .forEach(nick -> output.add(nick));

        return output.toArray(new String[output.size()]);
    }

    @Override
    public String getId() {
        return botId;
    }

    @Override
    public String channelIdToName(String channelId) {
        // On IRC channelId and channelName are the same thing
        return channelId;
    }
}
