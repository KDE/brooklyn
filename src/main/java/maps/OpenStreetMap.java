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

package maps;

public final class OpenStreetMap extends Map {
    public OpenStreetMap(double latitude, double longitude) {
        super(latitude, longitude);
    }

    @Override
    public String toUrl() {
        return String.format("https://www.openstreetmap.org/?mlat=%s&&mlon=%s",
                this.latitude, this.longitude);
    }
}
