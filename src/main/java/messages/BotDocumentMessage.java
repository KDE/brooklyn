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

package messages;

public class BotDocumentMessage extends BotTextMessage {
    private final byte[] doc;
    private final String fileExtension;
    private final BotDocumentType type;
    private final String filename;

    public BotDocumentMessage(BotTextMessage message,
                              String filename, String fileExtension,
                              byte[] doc, BotDocumentType type) {
        super(message, message.getText());
        this.doc = doc;
        this.fileExtension = fileExtension;
        this.type = type;
        this.filename = filename;
    }

    public String getFileExtension() {
        return this.fileExtension;
    }

    public byte[] getDoc() {
        return this.doc;
    }

    public String getFilename() {
        return this.filename;
    }

    public BotDocumentType getDocumentType() {
        return this.type;
    }
}
