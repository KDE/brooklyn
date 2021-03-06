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

package models;

import org.apache.http.client.utils.URIBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

public class FileStorage {
    private static final Logger logger = LogManager.getLogger(FileStorage.class.getSimpleName());

    private static Map<String, String> webserverConfig;

    public static void init(Map<String, String> webserverConfig) {
        FileStorage.webserverConfig = webserverConfig;
    }

    public static String storeFile(byte[] data, String fileExtension) throws URISyntaxException, IOException {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Should never happens
            logger.warn(e);
        }

        byte[] hash = digest.digest(data);
        String encoded = Base64.getEncoder().encodeToString(hash)
                .replace(File.separator, ""); // It prevents to create useless directories

        String filename = encoded + '.' + fileExtension;
        String contentFolder = FileStorage.webserverConfig.get("content-folder");

        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
        Date date = new Date();
        String folder = dateFormat.format(date);

        String baseLocalPath;
        baseLocalPath = contentFolder.substring(contentFolder.length() - 1).equals(File.separator)
                ? contentFolder + folder
                : contentFolder + File.separator + folder;
        if (!contentFolder.substring(contentFolder.length() - 1).equals(File.separator))
            baseLocalPath += File.separator;

        // Create the directory if not exist
        File directory = new File(baseLocalPath);
        directory.mkdirs();

        File file = new File(baseLocalPath + File.separator + filename);
        if (!file.exists()) {
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data);
            } catch (FileNotFoundException e) {
                // Should never, ever happens
                logger.warn(e);
            }
        }

        URIBuilder builder = new URIBuilder(FileStorage.webserverConfig.get("base-url"));
        builder.setPath(dateFormat.format(date) + '/' + filename);
        return builder.toString();
    }
}
