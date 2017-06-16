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

// TODO: implement a way not to exceed bot messages limit
public final class TelegramBot extends TelegramLongPollingBot implements Bot {
    private static final String USERNAME_KEY = "username";
    private static final String TOKEN_KEY = "token";

    private static TelegramBotsApi telegramBotsApi;
    // You can't retrieve users list, so it'll store users who wrote at least one time here
    private final Collection<String> users = new LinkedHashSet<>();
    private final Map<Long, String> chats = new HashMap<>();
    BotsController botsController = new BotsController();
    private Map<String, String> configs = new LinkedHashMap<String, String>(0);
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
    public boolean init(String botId, Map<String, String> botConfigs, String[] channels) {
        configs = botConfigs;

        try {
            telegramBotsApi.registerBot(this);
        } catch (TelegramApiRequestException e) {
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

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            Message msg = update.getMessage();
            User user = msg.getFrom();
            users.add(user.getUserName());

            Optional<MessageBuilder> builder = Optional.of(new MessageBuilder(getId(),
                    msg.getChatId().toString(), msg.getMessageId().toString()));

            if (update.hasMessage()) {
                Chat chat = msg.getChat();
                this.chats.put(chat.getId(), chat.getTitle());

                String channelFrom = chat.getTitle();
                String authorNickname = user.getUserName();
                BotMessage message = new BotMessage(authorNickname, channelFrom, this);

                Message telegramMessage = update.getMessage();
                // Send image
                if (telegramMessage.hasPhoto()) {
                    List<PhotoSize> photos = telegramMessage.getPhoto();

                    PhotoSize photo = photos.get(photos.size() - 1);
                    try {
                        Triplet<byte[], String, String> data = downloadFromFileId(photo.getFileId());

                        String text = msg.getCaption();
                        BotTextMessage textMessage = new BotTextMessage(message, text);
                        BotDocumentMessage imgMessage = new BotDocumentMessage(textMessage,
                                data.getValue1(), data.getValue2(), data.getValue0(), BotDocumentType.IMAGE);

                        botsController.sendMessage(imgMessage, chat.getId().toString(), builder);
                    } catch (TelegramApiException | IOException e) {
                        System.err.println("Error loading the img received");
                        e.printStackTrace();
                    }
                }

                // Send voice message
                else if (telegramMessage.getVoice() != null) {
                    Voice voice = telegramMessage.getVoice();
                    try {
                        Triplet<byte[], String, String> data = downloadFromFileId(voice.getFileId());

                        String text = msg.getText();
                        BotTextMessage textMessage = new BotTextMessage(message, text);
                        BotDocumentMessage docMessage = new BotDocumentMessage(textMessage,
                                data.getValue1(), data.getValue2(), data.getValue0(), BotDocumentType.VIDEO);
                        botsController.sendMessage(docMessage, chat.getId().toString(), builder);
                    } catch (TelegramApiException | IOException e) {
                        System.err.println("Error loading the voice message received");
                        e.printStackTrace();
                    }
                }

                // Send document
                else if (telegramMessage.hasDocument()) {
                    Document document = telegramMessage.getDocument();

                    try {
                        Triplet<byte[], String, String> data = downloadFromFileId(document.getFileId());

                        String text = msg.getText();
                        BotTextMessage textMessage = new BotTextMessage(message, text);
                        BotDocumentMessage docMessage = new BotDocumentMessage(textMessage,
                                data.getValue1(), data.getValue2(), data.getValue0(), BotDocumentType.OTHER);
                        botsController.sendMessage(docMessage, chat.getId().toString(), builder);
                    } catch (TelegramApiException | IOException e) {
                        System.err.println("Error loading the img received");
                        e.printStackTrace();
                    }
                }

                // Send videomessages
                else if (null != telegramMessage.getVideoNote()) {
                    VideoNote video = telegramMessage.getVideoNote();

                    try {
                        Triplet<byte[], String, String> data = downloadFromFileId(video.getFileId());

                        String text = msg.getCaption();
                        BotTextMessage textMessage = new BotTextMessage(message, text);
                        BotDocumentMessage docMessage = new BotDocumentMessage(textMessage,
                                data.getValue1(), data.getValue2(),
                                data.getValue0(), BotDocumentType.VIDEO);
                        botsController.sendMessage(docMessage, chat.getId().toString(), builder);
                    } catch (TelegramApiException | IOException e) {
                        System.err.println("Error loading the video received");
                        e.printStackTrace();
                    }
                }

                // Send video
                else if (null != telegramMessage.getVideo()) {
                    Video video = telegramMessage.getVideo();

                    try {
                        Triplet<byte[], String, String> data = downloadFromFileId(video.getFileId());

                        String text = msg.getCaption();
                        BotTextMessage textMessage = new BotTextMessage(message, text);
                        BotDocumentMessage docMessage = new BotDocumentMessage(textMessage,
                                data.getValue1(), data.getValue2(),
                                data.getValue0(), BotDocumentType.VIDEO);
                        botsController.sendMessage(docMessage, chat.getId().toString(), builder);
                    } catch (TelegramApiException | IOException e) {
                        System.err.println("Error loading the video received");
                        e.printStackTrace();
                    }
                }

                // Send position
                else if (telegramMessage.hasLocation()) {
                    Location location = telegramMessage.getLocation();
                    maps.Map worldMap = new OpenStreetMap(location.getLatitude(), location.getLongitude());
                    String text = String.format("(%s, %s) -> ", location.getLatitude(),
                            location.getLongitude()) + worldMap;

                    BotTextMessage textMessage = new BotTextMessage(message, text);
                    botsController.sendMessage(textMessage, chat.getId().toString(), builder);
                } else if (null != telegramMessage.getContact()) {
                    Contact contact = telegramMessage.getContact();
                    StringBuilder text = new StringBuilder();
                    if (null != contact.getFirstName()) {
                        text.append(contact.getFirstName())
                                .append(" ");
                    }
                    if (null != contact.getLastName()) {
                        text.append(contact.getLastName())
                                .append(" ");
                    }
                    if (null != contact.getPhoneNumber()) {
                        text.append(contact.getPhoneNumber())
                                .append(" ");
                    }

                    BotTextMessage textMessage = new BotTextMessage(message, text.toString());
                    botsController.sendMessage(textMessage, chat.getId().toString(), builder);
                }

                // Send audio
                else if (null != telegramMessage.getAudio()) {
                    Audio audio = telegramMessage.getAudio();

                    try {
                        Triplet<byte[], String, String> data = downloadFromFileId(audio.getFileId());

                        String text = msg.getCaption();
                        BotTextMessage textMessage = new BotTextMessage(message, text);
                        BotDocumentMessage docMessage = new BotDocumentMessage(textMessage,
                                data.getValue1(), data.getValue2(),
                                data.getValue0(), BotDocumentType.AUDIO);
                        botsController.sendMessage(docMessage, chat.getId().toString(), builder);
                    } catch (TelegramApiException | IOException e) {
                        System.err.println("Error loading the audio received");
                        e.printStackTrace();
                    }
                }

                // Send plain text
                else if (telegramMessage.hasText()) {
                    String text = msg.getText();

                    String[] commandSplitted = text.split("\\\\s+");
                    if (text.startsWith("/users")) {
                        List<Triplet<Bot, String, String[]>> users = botsController.askForUsers(chat.getId().toString());
                        StringBuilder output = new StringBuilder();
                        users.forEach(channel -> {
                            output.append(channel.getValue0().getClass().getSimpleName())
                                    .append('/')
                                    .append(channel.getValue1())
                                    .append(':')
                                    .append(System.lineSeparator());

                            for (String userTo : channel.getValue2()) {
                                output.append(userTo).append(System.lineSeparator());
                            }

                            output.append(System.lineSeparator());
                        });

                        SendMessage messageToSend = new SendMessage()
                                .setChatId(chat.getId())
                                .setText(output.toString());
                        try {
                            sendMessage(messageToSend);
                        } catch (TelegramApiException e) {
                            System.err.println("Failed to send message from TelegramBot");
                            e.printStackTrace();
                        }
                    } else {
                        BotTextMessage textMessage = new BotTextMessage(message, text);
                        botsController.sendMessage(textMessage, chat.getId().toString(), builder);
                    }
                }
                // Send sticker
                else if (telegramMessage.getSticker() != null) {
                    Sticker sticker = telegramMessage.getSticker();
                    BotTextMessage textMessage = new BotTextMessage(message, sticker.getEmoji());
                    botsController.sendMessage(textMessage, chat.getId().toString(), builder);
                }
            }
        } else if (update.hasEditedMessage()) {
            Message msg = update.getEditedMessage();
            String messageId = Integer.toString(msg.getMessageId());

            String text;
            if (msg.hasText())
                text = msg.getText();
            else if (null != msg.getCaption())
                text = msg.getCaption();
            else
                text = "";

            Chat chat = msg.getChat();
            String channelFrom = Long.toString(chat.getId());
            User user = msg.getFrom();
            String authorNickname = user.getUserName();
            BotMessage bm = new BotMessage(authorNickname, channelFrom, this);
            BotTextMessage tm = new BotTextMessage(bm, text);

            botsController.editMessage(tm, channelFrom, messageId);
        }
    }

    @Override
    public String getBotUsername() {
        if (configs.containsKey(USERNAME_KEY))
            return configs.get(USERNAME_KEY);
        else
            return null;
    }

    @Override
    public String getBotToken() {
        if (configs.containsKey(TOKEN_KEY))
            return configs.get(TOKEN_KEY);
        else
            return null;
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
        String filename = msg.getFilename() + "." + msg.getFileExtension();

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
    public String[] getUsers(String channel) {
        return users.toArray(new String[users.size()]);
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
