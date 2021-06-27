import java.util.Random;

// Yannick Messinger, Jasmin Steiner, Markus Wagner

public class Config {
    public static final int SERVER_PORT = 6789;

    public static final int CHUNK_CONTENT_SIZE = 12;
    public static final double NETWORK_ERROR_PROBABILITY = 0.01;
    public static final int TIMEOUT_MS = 200;

    public static final int WINDOW_SIZE = 5;

    public static boolean isFakeNetworkError() {
        Random random = new Random();
        return random.nextDouble() < NETWORK_ERROR_PROBABILITY;
    }
}
