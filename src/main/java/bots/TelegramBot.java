package bots;

import org.javatuples.Triplet;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;

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
        if (update.hasMessage() && update.getMessage().hasText()) {
            final Message msg = update.getMessage();
            final String channelFrom = msg.getChat().getUserName();
            final String authorNickname = msg.getFrom().getUserName();
            final String text = msg.getText();

            for(Triplet<Bot, String, String> sendTo: sendToList) {
                sendTo.getValue0().sendMessage(text, sendTo.getValue1(), channelFrom, this, authorNickname);
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
    public void sendMessage(final String text, final String channelTo, String channelFrom, Bot botFrom, String authorNick) {
        final SendMessage message = new SendMessage()
                .setChatId(channelTo)
                .setText(String.format("%s/%s/%s: %s",
                        botFrom.getClass().getSimpleName(), channelFrom, authorNick, text));

        try {
            sendMessage(message);
        } catch (TelegramApiException e) {
            System.err.println(String.format("Failed to send message from %s to TelegramBot", botFrom.getClass().getSimpleName()));
            e.printStackTrace();
        }
    }
}
