package maps;

public class OpenStreetMap extends Map {
    public OpenStreetMap(double latitude, double longitude) {
        super(latitude, longitude);
    }

    @Override
    public String toUrl() {
        return String.format("https://www.openstreetmap.org/?mlat=%s&&mlon=%s",
                this.latitude, this.longitude);
    }
}
