import java.util.ArrayList;

// Yannick Messinger, Jasmin Steiner, Markus Wagner

public class UdpMessage {

    private final String raw;
    public UdpMessage(String raw) {
        this.raw = raw.replace("\r\n", "");
    }

    // Chunks zu UdpMessage zusammenfuegen
    public static UdpMessage fromChunks(ArrayList<Chunk> chunks) {
        StringBuilder raw = new StringBuilder();
        for(Chunk chunk : chunks) {
            raw.append(chunk.getContent());
        }
        return new UdpMessage(raw.toString());
    }

    // UdpMessage in Chunks aufteilen
    public Chunk[] chunk() {
        int chunkCount = (int) Math.ceil(raw.length() / (double) Config.CHUNK_CONTENT_SIZE);
        Chunk[] chunks = new Chunk[chunkCount];
        for(int i = 0; i < chunkCount; i++) {
            int start = i*Config.CHUNK_CONTENT_SIZE;
            int end = start + Config.CHUNK_CONTENT_SIZE;
            String chunkString = raw.substring(start, Math.min(end, raw.length()));
            boolean isLast = i == chunkCount - 1;
            chunks[i] = new Chunk(i, chunkString, isLast);
        }
        return chunks;
    }

    public String getRaw() {
        return raw;
    }

    @Override
    public String toString() {
        return "UdpMessage{" +
                "raw='" + raw + '\'' +
                '}';
    }
}
