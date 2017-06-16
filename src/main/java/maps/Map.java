package maps;

public abstract class Map {
    protected final double latitude;
    protected final double longitude;

    protected Map(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public abstract String toUrl();
}
