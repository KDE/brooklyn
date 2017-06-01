package bots;

import bots.messages.BotImgMessage;
import bots.messages.BotMessage;
import bots.messages.BotTextMessage;
import org.apache.http.client.utils.URIBuilder;
import org.javatuples.Triplet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public interface Bot {
    String EVERY_CHANNEL = "*";

    static void sendMessage(BotMessage message, List<Triplet<Bot, String, String>> sendToList,
                            String channelFrom) {
        for (Triplet<Bot, String, String> sendTo : sendToList) {
            if (sendTo.getValue2().equals(channelFrom) || channelFrom.equals(EVERY_CHANNEL)) {
                if (message instanceof BotImgMessage)
                    sendTo.getValue0().sendMessage((BotImgMessage) message, sendTo.getValue1());
                else if (message instanceof BotTextMessage)
                    sendTo.getValue0().sendMessage((BotTextMessage) message, sendTo.getValue1());
                else
                    System.err.println("Type of message not valid");
            }
        }
    }

    static String storeFile(final byte[] data, final String fileExtension,
                            Map<String, String> webserverConfig) throws URISyntaxException, IOException {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        final byte[] hash = digest.digest(data);
        final String encoded = Base64.getEncoder().encodeToString(hash)
                .replace(File.separator, ""); // It prevents to create useless directories

        final String filename = encoded + '.' + fileExtension;
        final String contentFolder = webserverConfig.get("content-folder");

        final DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
        final Date date = new Date();
        final String folder = dateFormat.format(date);

        String baseLocalPath;
        if (contentFolder.substring(contentFolder.length() - 1).equals(File.separator))
            baseLocalPath = contentFolder + folder;
        else
            baseLocalPath = contentFolder + File.separator + folder;
        if (!contentFolder.substring(contentFolder.length() - 1).equals(File.separator))
            baseLocalPath += File.separator;

        // Create the directory if not exist
        final File directory = new File(baseLocalPath);
        directory.mkdirs();

        final File file = new File(baseLocalPath + File.separator + filename);
        if (!file.exists()) {
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            fos.write(data);
            try {
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        final URIBuilder builder = new URIBuilder(webserverConfig.get("base-url"));
        builder.setPath(dateFormat.format(date) + '/' + filename);
        return builder.toString();
    }

    static List<Triplet<Bot, String, String[]>> askForUsers(
            final String channelFrom,
            final List<Triplet<Bot, String, String>> askToList) {
        final List<Triplet<Bot, String, String[]>> allUsers = new ArrayList<>(askToList.size());
        for (final Triplet<Bot, String, String> askTo : askToList) {
            if(askTo.getValue2().equals(channelFrom)) {
                final String[] users = askTo.getValue0().getUsers(askTo.getValue1());
                allUsers.add(new Triplet(askTo.getValue0(), askTo.getValue1(), users));
            }
        }

        return allUsers;
    }

    boolean init(final Map<String, String> configs, final String[] channels,
                 Map<String, String> webserverConfig);

    void addBridge(final Bot bot, final String channelTo, final String channelFrom);

    void sendMessage(final BotTextMessage msg, final String channelTo);

    void sendMessage(final BotImgMessage msg, final String channelTo);

    String[] getUsers(final String channel);
}
