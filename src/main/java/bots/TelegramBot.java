package bots;

import bots.messages.BotImgMessage;
import bots.messages.BotMessage;
import bots.messages.BotTextMessage;
import org.apache.commons.io.IOUtils;
import org.javatuples.Triplet;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.api.methods.GetFile;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.methods.send.SendPhoto;
import org.telegram.telegrambots.api.objects.*;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public final class TelegramBot extends TelegramLongPollingBot implements Bot {
    private static final String USERNAME_KEY = "username";
    private static final String TOKEN_KEY = "token";

    private static TelegramBotsApi telegramBotsApi = null;
    private final List<Triplet<Bot, String, String>> sendToList = new LinkedList<>();
    private Map<String, String> configs;

    public TelegramBot() {
        configs = new LinkedHashMap<String, String>();

        if (telegramBotsApi == null) {
            telegramBotsApi = new TelegramBotsApi();
        }
    }

    public static void init() {
        ApiContextInitializer.init();
    }

    @Override
    public boolean init(final Map<String, String> botConfigs, final String[] channels) {
        configs = botConfigs;

        try {
            telegramBotsApi.registerBot(this);
        } catch (TelegramApiRequestException e) {
            return false;
        }
        return true;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            final Message msg = update.getMessage();
            final Chat chat = msg.getChat();
            final String channelFrom = chat.getTitle();
            final String authorNickname = msg.getFrom().getUserName();
            final BotMessage message = new BotMessage(authorNickname, channelFrom, this);

            final Message telegramMessage = update.getMessage();
            // Send image
            if(telegramMessage.hasPhoto()) {
                final List<PhotoSize> photos = telegramMessage.getPhoto();

                PhotoSize photo = photos.get(photos.size()-1);
                final GetFile file = new GetFile();
                file.setFileId(photo.getFileId());
                try {
                    final File img = getFile(file);
                    final URL imgUrl = new URL(img.getFileUrl(configs.get(TOKEN_KEY)));
                    final HttpURLConnection httpConn = (HttpURLConnection) imgUrl.openConnection();
                    final InputStream inputStream = httpConn.getInputStream();
                    final byte[] output = IOUtils.toByteArray(inputStream);

                    final String text = msg.getText();
                    final BotTextMessage textMessage = new BotTextMessage(message, text);
                    final BotImgMessage imgMessage = new BotImgMessage(textMessage, photo.getFilePath(), output);

                    for(Triplet<Bot, String, String> sendTo: sendToList) {
                        if(sendTo.getValue2().equals(chat.getId().toString()))
                            sendTo.getValue0().sendMessage(imgMessage, sendTo.getValue1());
                    }

                    inputStream.close();
                    httpConn.disconnect();
                } catch (TelegramApiException | IOException e) {
                    System.err.println("Error loading the img received");
                }
            }
            // Send plain text
            else if(telegramMessage.hasText()) {
                final String text = msg.getText();
                final BotTextMessage textMessage = new BotTextMessage(message, text);

                for(Triplet<Bot, String, String> sendTo: sendToList) {
                    if(sendTo.getValue2().equals(chat.getId().toString()))
                        sendTo.getValue0().sendMessage(textMessage, sendTo.getValue1());
                }
            }
            // Send sticker
            else if(telegramMessage.getSticker() != null) {
                final Sticker sticker = telegramMessage.getSticker();
                final BotTextMessage textMessage = new BotTextMessage(message, sticker.getEmoji());

                for(Triplet<Bot, String, String> sendTo: sendToList) {
                    if(sendTo.getValue2().equals(chat.getId().toString()))
                        sendTo.getValue0().sendMessage(textMessage, sendTo.getValue1());
                }
            }
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
    public void addBridge(final Bot bot, final String channelTo, final String channelFrom) {
        sendToList.add(Triplet.with(bot, channelTo, channelFrom));
    }

    @Override
    public void sendMessage(BotTextMessage msg, String channelTo) {
        final SendMessage message = new SendMessage()
                .setChatId(channelTo)
                .setText(String.format("%s/%s/%s: %s",
                        msg.getBotFrom().getClass().getSimpleName(), msg.getChannelFrom(),
                        msg.getNicknameFrom(), msg.getText()));
        try {
            sendMessage(message);
        } catch (TelegramApiException e) {
            System.err.println(String.format("Failed to send message from %s to TelegramBot", msg.getBotFrom().getClass().getSimpleName()));
            e.printStackTrace();
        }
    }

    @Override
    public void sendMessage(final BotImgMessage msg, final String channelTo) {
        final SendPhoto message = new SendPhoto()
                .setChatId(channelTo);

        if(msg.getText() != null)
                message.setCaption(String.format("%s/%s/%s: %s",
                        msg.getBotFrom().getClass().getSimpleName(), msg.getChannelFrom(),
                        msg.getNicknameFrom(), msg.getText()));
        else
            message.setCaption(String.format("%s/%s/%s",
                    msg.getBotFrom().getClass().getSimpleName(), msg.getChannelFrom(),
                    msg.getNicknameFrom()));

        final InputStream imageStream = new ByteArrayInputStream(msg.getImg());
        message.setNewPhoto(msg.getFilename(), imageStream);

        try {
            sendPhoto(message);
        } catch (TelegramApiException e) {
            System.err.println(String.format("Failed to send message from %s to TelegramBot", msg.getBotFrom().getClass().getSimpleName()));
            e.printStackTrace();
        }
    }
}
