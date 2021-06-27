// Yannick Messinger, Jasmin Steiner, Markus Wagner

public class Acknowledgement {

    private final int id;

    public Acknowledgement(int id) {
        this.id = id;
    }

    public byte[] toPacket() {
        String str = String.format("ACK;%d\n", id);
        return str.getBytes();
    }

    public static boolean isAcknowledgement(String packetStr) {
        return packetStr.startsWith("ACK");
    }

    public static Acknowledgement parse(String packetStr) {
        String[] stringComponents = packetStr.split(";");
        return new Acknowledgement(Integer.parseInt(stringComponents[1]));
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return "Acknowledgement{" +
                "id=" + id +
                '}';
    }
}
