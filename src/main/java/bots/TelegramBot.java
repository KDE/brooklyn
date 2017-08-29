/*
 * Copyright 2017 Davide Riva driva95@protonmail.com
 *
 * This is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package bots;

import core.BotsController;
import maps.OpenStreetMap;
import messages.BotDocumentMessage;
import messages.BotDocumentType;
import messages.BotMessage;
import messages.BotTextMessage;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.api.methods.GetFile;
import org.telegram.telegrambots.api.methods.send.*;
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageCaption;
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.api.objects.*;
import org.telegram.telegrambots.api.objects.stickers.Sticker;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

// TODO: implement a way not to exceed bot messages limit
public class TelegramBot extends TelegramLongPollingBot implements Bot {
    private Logger logger;

    private static final String USERNAME_KEY = "username";
    private static final String TOKEN_KEY = "password";

    private static TelegramBotsApi telegramBotsApi;
    // You can't retrieve users list, so it'll store users who wrote at least one time here
    private final HashSet<Pair<String, String>> users = new LinkedHashSet<>();
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
        logger = LogManager.getLogger(TelegramBot.class.getSimpleName() + ":" + botId);

        this.configs = configs;

        try {
            telegramBotsApi.registerBot(this);
        } catch (TelegramApiRequestException e) {
            logger.error(e);
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

        File file = execute(getFile);
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

        return new Triplet<>(output, filenameWithoutExtension, extension);
    }

    private void onAttachmentReceived(BotMessage botMsg, Message message,
                                      String fileId, BotDocumentType type,
                                      String msgId) {
        try {
            Triplet<byte[], String, String> data = downloadFromFileId(fileId);

            BotTextMessage textMessage = new BotTextMessage(botMsg, message.getCaption());
            BotDocumentMessage documentMessage = new BotDocumentMessage(textMessage,
                    data.getValue1(), data.getValue2(), data.getValue0(), type);

            botsController.sendMessage(documentMessage,
                    Long.toString(message.getChatId()), Optional.of(msgId));
        } catch (TelegramApiException | IOException e) {
            logger.error("Error loading the media received. ", e);
        }
    }

    private void onLocationReceived(BotMessage botMsg, Message message, String messageId) {
        Location location = message.getLocation();
        maps.Map worldMap = new OpenStreetMap(location.getLatitude(), location.getLongitude());
        String text = String.format("(%s, %s) -> ", location.getLatitude(),
                location.getLongitude()) + worldMap.toUrl();

        BotTextMessage textMessage = new BotTextMessage(botMsg, text);
        botsController.sendMessage(textMessage, Long.toString(message.getChatId()),
                Optional.of(messageId));
    }

    private void onContactReceived(BotMessage botMsg, Message message,
                                   String messageId) {
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
        botsController.sendMessage(textMessage, Long.toString(message.getChatId()), Optional.of(messageId));
    }

    private void onPlainTextReceived(Message message, BotMessage botMsg,
                                     String messageId) {
        String text = message.getText();

        if (text.equals("/users")) {
            List<Triplet<Bot, String, List<String>>> users =
                    botsController.askForUsers(Long.toString(message.getChatId()));
            StringBuilder output = new StringBuilder();
            users.forEach(channel -> {
                final String channelName = channel.getValue0().getChannelName(channel.getValue1());

                output.append(channel.getValue0().getClass().getSimpleName())
                        .append('/')
                        .append(channelName)
                        .append(':')
                        .append(System.lineSeparator());

                channel.getValue2().forEach(userTo -> output.append(userTo).append(System.lineSeparator()));

                output.append(System.lineSeparator());
            });

            SendMessage messageToSend = new SendMessage()
                    .setChatId(Long.toString(message.getChatId()))
                    .setText(output.toString());
            try {
                execute(messageToSend);
            } catch (TelegramApiException e) {
                logger.error("Failed to send message from TelegramBot. ", e);
            }
        } else {
            BotTextMessage textMessage = new BotTextMessage(botMsg, text);
            botsController.sendMessage(textMessage,
                    Long.toString(message.getChatId()), Optional.of(messageId));
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
            final String messageId = message.getMessageId().toString();

            final User user = message.getFrom();
            final Optional<String> authorNickname = Optional.ofNullable(user.getUserName());
            final String author = authorNickname.orElseGet(() -> user.getFirstName() + " " + user.getLastName());

            if (null != message.getLeftChatMember())
                users.remove(new Pair(Long.toString(chatId), message.getLeftChatMember()));
            else
                authorNickname.ifPresent(nick -> users.add(new Pair(Long.toString(chatId), nick)));

            Chat chat = message.getChat();
            chats.put(chat.getId(), chat.getTitle());

            String channelFrom = chat.getId().toString();

            BotMessage botMsg = new BotMessage(author, channelFrom, this);

            // Send image
            if (message.hasPhoto()) {
                List<PhotoSize> photos = message.getPhoto();
                PhotoSize photo = photos.get(photos.size() - 1);
                onAttachmentReceived(botMsg, message, photo.getFileId(),
                        BotDocumentType.IMAGE, messageId);
            }

            // Send voice message
            else if (message.getVoice() != null) {
                Voice voice = message.getVoice();
                onAttachmentReceived(botMsg, message, voice.getFileId(),
                        BotDocumentType.AUDIO, messageId);
            }

            // Send document
            else if (message.hasDocument()) {
                Document document = message.getDocument();
                onAttachmentReceived(botMsg, message, document.getFileId(),
                        BotDocumentType.OTHER, messageId);
            }

            // Send videomessages
            else if (null != message.getVideoNote()) {
                VideoNote video = message.getVideoNote();
                onAttachmentReceived(botMsg, message, video.getFileId(),
                        BotDocumentType.VIDEO, messageId);
            }

            // Send video
            else if (null != message.getVideo()) {
                Video video = message.getVideo();
                onAttachmentReceived(botMsg, message, video.getFileId(),
                        BotDocumentType.VIDEO, messageId);
            }

            // Send audio
            else if (null != message.getAudio()) {
                Audio audio = message.getAudio();
                onAttachmentReceived(botMsg, message, audio.getFileId(),
                        BotDocumentType.AUDIO, messageId);
            }

            // Send position
            else if (message.hasLocation()) {
                Location location = message.getLocation();
                onLocationReceived(botMsg, message, messageId);
            }

            // Send contact
            else if (null != message.getContact())
                onContactReceived(botMsg, message, messageId);

            // Send sticker
            else if (message.getSticker() != null) {
                Sticker sticker = message.getSticker();
                BotTextMessage textMessage = new BotTextMessage(botMsg, sticker.getEmoji());
                botsController.sendMessage(textMessage, channelFrom,
                        Optional.of(messageId));
            }

            // Send plain text
            else if (message.hasText())
                onPlainTextReceived(message, botMsg, messageId);
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
                        msg.getBotFrom(), msg.getChannelFrom(),
                        msg.getNicknameFrom(), Optional.ofNullable(msg.getText())));
        try {
            Message sentMessage = execute(message);
            return Optional.of(sentMessage.getMessageId().toString());
        } catch (TelegramApiException e) {
            logger.error("Failed to send message from {} to TelegramBot",
                    msg.getBotFrom().getId(), e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> sendMessage(BotDocumentMessage msg, String channelTo) {
        String caption = BotsController.messageFormatter(
                msg.getBotFrom(), msg.getChannelFrom(),
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
            logger.warn("Failed to send message from {} to TelegramBot",
                    msg.getBotFrom().getId(), e);
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
        String messageText = BotsController.messageFormatter(
                msg.getBotFrom(), msg.getChannelFrom(), msg.getNicknameFrom(),
                Optional.ofNullable(msg.getText()));
        EditMessageText text = new EditMessageText();
        text.setChatId(channelTo);
        text.setMessageId(Integer.parseInt(messageId));
        text.setText(messageText);

        try {
            execute(text);
        } catch (TelegramApiException e) {
            logger.info("Message text not found, trying to edit that as a caption. ", e);

            EditMessageCaption caption = new EditMessageCaption();
            caption.setChatId(channelTo);
            caption.setMessageId(Integer.parseInt(messageId));
            caption.setCaption(messageText);

            try {
                execute(caption);
            } catch (TelegramApiException e1) {
                logger.error("Error while changing img caption. ", e1);
            }
        }
    }

    @Override
    public List<String> getUsers(String channel) {
        return users.stream()
                .filter(x -> x.getValue0().equals(channel))
                .map(Pair::getValue1)
                .collect(Collectors.toList());
    }

    @Override
    public String getId() {
        return this.botId;
    }

    @Override
    public String getChannelName(String channelId) {
        try {
            return this.chats.getOrDefault(Long.parseLong(channelId), channelId);
        } catch (NumberFormatException e) {
            return channelId;
        }
    }

    @Override
    public void close() throws Exception {
    }
}
