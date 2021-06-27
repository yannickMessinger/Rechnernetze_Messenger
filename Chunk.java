// Yannick Messinger, Jasmin Steiner, Markus Wagner

public class Chunk {

    private final int id;
    private final String content;
    private final boolean end;

    public Chunk(int id, String content, boolean end) {
        this.id = id;
        this.content = content;
        this.end = end;
    }

    public byte[] toPacket() {
        String str = String.format("CHUNK~%d~%d~%s\n", id, end ? 1 : 0, content);
        return str.getBytes();
    }

    public static boolean isChunk(String packetStr) {
        return packetStr.startsWith("CHUNK");
    }

    public static Chunk parse(String packetStr) {
        String[] contentComponents = packetStr.split("~");
        return new Chunk(Integer.parseInt(contentComponents[1]), contentComponents[3], contentComponents[2].equals("1"));
    }

    public int getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public boolean isEnd() {
        return end;
    }

    @Override
    public String toString() {
        return "Chunk{" +
                "id=" + id +
                ", content='" + content + '\'' +
                ", end=" + end +
                '}';
    }
}
