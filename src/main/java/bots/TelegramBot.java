package bots;

import core.BotsController;
import maps.OpenStreetMap;
import messages.BotDocumentMessage;
import messages.BotDocumentType;
import messages.BotMessage;
import messages.BotTextMessage;
import models.MessageBuilder;
import org.apache.commons.io.IOUtils;
import org.javatuples.Triplet;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.api.methods.GetFile;
import org.telegram.telegrambots.api.methods.send.*;
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageCaption;
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.api.objects.*;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

/* TODO: implement a way not to exceed bot messages limit
   Wait until this will be stable:
   https://github.com/rubenlagus/TelegramBots/pull/230#issuecomment-306409017
*/
public final class TelegramBot extends TelegramLongPollingBot implements Bot {
    private static final String USERNAME_KEY = "username";
    private static final String TOKEN_KEY = "token";
    private static final Pattern COMPILE = Pattern.compile("\\\\s+");

    private static TelegramBotsApi telegramBotsApi;
    // You can't retrieve users list, so it'll store users who wrote at least one time here
    private final HashSet<String> users = new LinkedHashSet<>();
    private final Map<Long, String> chats = new HashMap<>();
    private final BotsController botsController = new BotsController();
    private Map<String, String> configs = new LinkedHashMap<>(0);
    private String botId;

    public TelegramBot() {
        if (telegramBotsApi == null) {
            telegramBotsApi = new TelegramBotsApi();
        }
    }

    public static void init() {
        ApiContextInitializer.init();
    }

    @Override
    public boolean init(String botId, Map<String, String> configs, String[] channels) {
        this.configs = configs;

        try {
            telegramBotsApi.registerBot(this);
        } catch (TelegramApiRequestException e) {
            e.printStackTrace();
            return false;
        }

        this.botId = botId;

        return true;
    }

    /**
     * @return a list of {@literal Triplet<byte[] data, String filename, String fileExtension>}
     */
    private Triplet<byte[], String, String> downloadFromFileId(String fileId) throws TelegramApiException, IOException {
        GetFile getFile = new GetFile();
        getFile.setFileId(fileId);

        File file = getFile(getFile);
        URL fileUrl = new URL(file.getFileUrl(configs.get(TOKEN_KEY)));
        HttpURLConnection httpConn = (HttpURLConnection) fileUrl.openConnection();
        InputStream inputStream = httpConn.getInputStream();
        byte[] output = IOUtils.toByteArray(inputStream);

        String fileName = file.getFilePath();
        String[] fileNameSplitted = fileName.split("\\.");
        String extension = fileNameSplitted[fileNameSplitted.length - 1];
        String filenameWithoutExtension = fileName.substring(0, fileName.length() - extension.length() - 1);

        inputStream.close();
        httpConn.disconnect();

        return new Triplet(output, filenameWithoutExtension, extension);
    }

    private void onPrivateMessageReceived(long chatId) {
        SendMessage licenceMsg = new SendMessage();
        licenceMsg.setChatId(chatId);
        licenceMsg.setText(BotTextMessage.LICENSE_MESSAGE);
        try {
            sendMessage(licenceMsg);
        } catch (TelegramApiException e) {
            System.err.println("Error while sending the licence message");
            e.printStackTrace();
        }
    }

    private void onAttachmentReceived(BotMessage botMsg, Message message,
                                      String fileId, BotDocumentType type,
                                      Optional<MessageBuilder> builder) {
        try {
            Triplet<byte[], String, String> data = downloadFromFileId(fileId);

            BotTextMessage textMessage = new BotTextMessage(botMsg, message.getCaption());
            BotDocumentMessage documentMessage = new BotDocumentMessage(textMessage,
                    data.getValue1(), data.getValue2(), data.getValue0(), type);

            botsController.sendMessage(documentMessage,
                    Long.toString(message.getChatId()), builder);
        } catch (TelegramApiException | IOException e) {
            System.err.println("Error loading the media received");
            e.printStackTrace();
        }
    }

    private void onLocationReceived(BotMessage botMsg, Message message, double lat, double lng,
                                    Optional<MessageBuilder> builder) {
        Location location = message.getLocation();
        maps.Map worldMap = new OpenStreetMap(location.getLatitude(), location.getLongitude());
        String text = String.format("(%s, %s) -> ", location.getLatitude(),
                location.getLongitude()) + worldMap;

        BotTextMessage textMessage = new BotTextMessage(botMsg, text);
        botsController.sendMessage(textMessage, Long.toString(message.getChatId()), builder);
    }

    private void onContactReceived(BotMessage botMsg, Message message,
                                   Optional<MessageBuilder> builder) {
        Contact contact = message.getContact();
        StringBuilder text = new StringBuilder();
        if (null != contact.getFirstName()) {
            text.append(contact.getFirstName())
                    .append(' ');
        }
        if (null != contact.getLastName()) {
            text.append(contact.getLastName())
                    .append(' ');
        }
        if (null != contact.getPhoneNumber()) {
            text.append(contact.getPhoneNumber())
                    .append(' ');
        }

        BotTextMessage textMessage = new BotTextMessage(botMsg, text.toString());
        botsController.sendMessage(textMessage, Long.toString(message.getChatId()), builder);
    }

    private void onPlainTextReceived(Message message, BotMessage botMsg,
                                     Optional<MessageBuilder> builder) {
        String text = message.getText();

        if (text.startsWith("/users ")) {
            List<Triplet<Bot, String, List<String>>> users =
                    botsController.askForUsers(Long.toString(message.getChatId()));
            StringBuilder output = new StringBuilder();
            users.forEach(channel -> {
                output.append(channel.getValue0().getClass().getSimpleName())
                        .append('/')
                        .append(channel.getValue1())
                        .append(':')
                        .append(System.lineSeparator());

                channel.getValue2().forEach(userTo -> output.append(userTo).append(System.lineSeparator()));

                output.append(System.lineSeparator());
            });

            SendMessage messageToSend = new SendMessage()
                    .setChatId(Long.toString(message.getChatId()))
                    .setText(output.toString());
            try {
                sendMessage(messageToSend);
            } catch (TelegramApiException e) {
                System.err.println("Failed to send message from TelegramBot");
                e.printStackTrace();
            }
        } else {
            BotTextMessage textMessage = new BotTextMessage(botMsg, text);
            botsController.sendMessage(textMessage,
                    Long.toString(message.getChatId()), builder);
        }
    }

    private void onEditedReceived(Message message, Chat chat) {
        String messageId = Long.toString(message.getMessageId());

        String text;
        if (message.hasText())
            text = message.getText();
        else if (null != message.getCaption())
            text = message.getCaption();
        else
            text = "";

        String channelFrom = Long.toString(chat.getId());
        User user = message.getFrom();
        String authorNickname = user.getUserName();
        BotMessage bm = new BotMessage(authorNickname, channelFrom, this);
        BotTextMessage tm = new BotTextMessage(bm, text);

        botsController.editMessage(tm, channelFrom, messageId);
    }


    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            Message message = update.getMessage();
            long chatId = message.getChatId();

            Optional<MessageBuilder> messageBuilder = Optional.of(new MessageBuilder(getId(),
                    Long.toString(chatId), message.getMessageId().toString()));

            User user = message.getFrom();
            users.add(user.getUserName());

            Chat chat = message.getChat();
            chats.put(chat.getId(), chat.getTitle());

            String channelFrom = chat.getTitle();
            String authorNickname = user.getUserName();

            // If it is a private message, send him a "licence message"
            if (chat.isUserChat()) {
                onPrivateMessageReceived(chatId);
            } else {
                BotMessage botMsg = new BotMessage(authorNickname, channelFrom, this);

                // Send image
                if (message.hasPhoto()) {
                    List<PhotoSize> photos = message.getPhoto();
                    PhotoSize photo = photos.get(photos.size() - 1);
                    onAttachmentReceived(botMsg, message, photo.getFileId(),
                            BotDocumentType.IMAGE, messageBuilder);
                }

                // Send voice message
                else if (message.getVoice() != null) {
                    Voice voice = message.getVoice();
                    onAttachmentReceived(botMsg, message, voice.getFileId(),
                            BotDocumentType.AUDIO, messageBuilder);
                }

                // Send document
                else if (message.hasDocument()) {
                    Document document = message.getDocument();
                    onAttachmentReceived(botMsg, message, document.getFileId(),
                            BotDocumentType.OTHER, messageBuilder);
                }

                // Send videomessages
                else if (null != message.getVideoNote()) {
                    VideoNote video = message.getVideoNote();
                    onAttachmentReceived(botMsg, message, video.getFileId(),
                            BotDocumentType.VIDEO, messageBuilder);
                }

                // Send video
                else if (null != message.getVideo()) {
                    Video video = message.getVideo();
                    onAttachmentReceived(botMsg, message, video.getFileId(),
                            BotDocumentType.VIDEO, messageBuilder);
                }

                // Send audio
                else if (null != message.getAudio()) {
                    Audio audio = message.getAudio();
                    onAttachmentReceived(botMsg, message, audio.getFileId(),
                            BotDocumentType.AUDIO, messageBuilder);
                }

                // Send position
                else if (message.hasLocation()) {
                    Location location = message.getLocation();
                    onLocationReceived(botMsg, message, location.getLatitude(),
                            location.getLongitude(), messageBuilder);
                }

                // Send contact
                else if (null != message.getContact()) {
                    Contact contact = message.getContact();
                    onContactReceived(botMsg, message, messageBuilder);
                }

                // Send sticker
                else if (message.getSticker() != null) {
                    Sticker sticker = message.getSticker();
                    BotTextMessage textMessage = new BotTextMessage(botMsg, sticker.getEmoji());
                    botsController.sendMessage(textMessage, chat.getId().toString(), messageBuilder);
                }

                // Send plain text
                else if (message.hasText())
                    onPlainTextReceived(message, botMsg, messageBuilder);
            }
        }

        // Edit a message
        else if (update.hasEditedMessage()) {
            Message msgEdited = update.getEditedMessage();
            Chat chat = msgEdited.getChat();
            onEditedReceived(msgEdited, chat);
        }
    }

    @Override
    public String getBotUsername() {
        return configs.get(USERNAME_KEY);
    }

    @Override
    public String getBotToken() {
        return configs.get(TOKEN_KEY);
    }

    @Override
    public void addBridge(Bot bot, String channelTo, String channelFrom) {
        botsController.addBridge(bot, channelTo, channelFrom);
    }

    @Override
    public Optional<String> sendMessage(BotTextMessage msg, String channelTo) {
        SendMessage message = new SendMessage()
                .setChatId(channelTo)
                .setText(BotsController.messageFormatter(
                        msg.getBotFrom().getId(), msg.getChannelFrom(),
                        msg.getNicknameFrom(), Optional.ofNullable(msg.getText())));
        try {
            Message sentMessage = sendMessage(message);
            return Optional.of(sentMessage.getMessageId().toString());
        } catch (TelegramApiException e) {
            System.err.println(String.format("Failed to send message from %s to TelegramBot", msg.getBotFrom().getId()));
            e.printStackTrace();
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> sendMessage(BotDocumentMessage msg, String channelTo) {
        String caption = BotsController.messageFormatter(
                msg.getBotFrom().getId(), msg.getChannelFrom(),
                msg.getNicknameFrom(), Optional.ofNullable(msg.getText()));
        String filename = msg.getFilename() + '.' + msg.getFileExtension();

        try (InputStream docStream = new ByteArrayInputStream(msg.getDoc())) {
            Message sentMessage;
            switch (msg.getDocumentType()) {
                case IMAGE:
                    sentMessage = sendImage(caption, channelTo,
                            docStream, filename);
                    break;
                case AUDIO:
                    sentMessage = sendAudio(caption, channelTo,
                            docStream, filename);
                    break;
                case VIDEO:
                    sentMessage = sendVideo(caption, channelTo,
                            docStream, filename);
                    break;
                default:
                    sentMessage = sendDocument(caption, channelTo,
                            docStream, filename);
            }
            return Optional.of(sentMessage.getMessageId().toString());
        } catch (IOException | TelegramApiException e) {
            // IOException should never happens
            e.printStackTrace();
            System.err.println(String.format("Failed to send message from %s to TelegramBot",
                    msg.getBotFrom().getId()));
            return Optional.empty();
        }
    }

    private Message sendDocument(String caption, String channelTo, InputStream docStream,
                                 String filename)
            throws TelegramApiException {
        SendDocument message = new SendDocument()
                .setChatId(channelTo);
        message.setCaption(caption);

        message.setNewDocument(filename, docStream);
        return sendDocument(message);
    }

    private Message sendImage(String caption, String channelTo, InputStream docStream,
                              String filename)
            throws TelegramApiException {

        SendPhoto message = new SendPhoto()
                .setChatId(channelTo);
        message.setCaption(caption);

        message.setNewPhoto(filename, docStream);
        return sendPhoto(message);
    }

    private Message sendAudio(String caption, String channelTo, InputStream docStream,
                              String filename)
            throws TelegramApiException {

        SendAudio message = new SendAudio()
                .setChatId(channelTo);
        message.setCaption(caption);

        message.setNewAudio(filename, docStream);
        return sendAudio(message);
    }

    private Message sendVideo(String caption, String channelTo, InputStream docStream,
                              String fileExtension)
            throws TelegramApiException {

        SendVideo message = new SendVideo()
                .setChatId(channelTo);
        message.setCaption(caption);

        message.setNewVideo("audio." + fileExtension, docStream);
        return sendVideo(message);
    }

    @Override
    public void editMessage(BotTextMessage msg, String channelTo, String messageId) {
        String channelFromName = msg.getBotFrom().channelIdToName(msg.getChannelFrom());
        String messageText = BotsController.messageFormatter(
                msg.getBotFrom().getId(), channelFromName, msg.getNicknameFrom(),
                Optional.ofNullable(msg.getText()));
        EditMessageText text = new EditMessageText();
        text.setChatId(channelTo);
        text.setMessageId(Integer.parseInt(messageId));
        text.setText(messageText);

        try {
            this.editMessageText(text);
        } catch (TelegramApiException e) {
            System.out.println("Waring: message text not found, trying to edit that as a caption...");
            e.printStackTrace(System.out);

            EditMessageCaption caption = new EditMessageCaption();
            caption.setChatId(channelTo);
            caption.setMessageId(Integer.parseInt(messageId));
            caption.setCaption(messageText);

            try {
                editMessageCaption(caption);
            } catch (TelegramApiException e1) {
                System.err.println("Error while changing img caption.");
                e1.printStackTrace();
            }
        }
    }

    @Override
    public List<String> getUsers(String channel) {
        return new ArrayList(users);
    }

    @Override
    public String getId() {
        return this.botId;
    }

    @Override
    public String channelIdToName(String channelId) {
        return this.chats.get(Long.parseLong(channelId));
    }
}
