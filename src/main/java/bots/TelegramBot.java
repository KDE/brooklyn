package bots;

import bots.messages.BotImgMessage;
import bots.messages.BotMessage;
import bots.messages.BotTextMessage;
import org.javatuples.Triplet;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Sticker;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

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
            final String channelFrom = msg.getChat().getTitle();
            final String authorNickname = msg.getFrom().getUserName();
            final BotMessage message = new BotMessage(authorNickname, channelFrom, this);

            final Message telegramMessage = update.getMessage();
            if(telegramMessage.hasText()) {
                // Send plain text
                final String text = msg.getText();
                final BotTextMessage textMessage = new BotTextMessage(message, text);

                for(Triplet<Bot, String, String> sendTo: sendToList) {
                    sendTo.getValue0().sendMessage(textMessage, sendTo.getValue1());
                }
            } else if(telegramMessage.getSticker() != null) {
                // Send sticker
                final Sticker sticker = telegramMessage.getSticker();
                final BotTextMessage textMessage = new BotTextMessage(message, sticker.getEmoji());

                for(Triplet<Bot, String, String> sendTo: sendToList) {
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
    public void sendMessage(BotImgMessage msg, String channelTo) {
        throw new NotImplementedException();
    }
}
