import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

// Yannick Messinger, Jasmin Steiner, Markus Wagner

public class Message {
    private final MessageType type;
    private final Map<String, String> body;

    public Message(MessageType type, HashMap<String, String> body) {
        this.type = type;
        this.body = body;
    }

    //String in Message-Objekt parsen
    //Form: MESSAGE_TYPE;key=value,key=value
    public static Message parse(String raw) {
       if (raw == null || raw.isEmpty()) {
           return null;
       }
       String[] components = raw.split(";");

       boolean invalidMessage = true;
       for (MessageType mt : MessageType.values()) {
           if (mt.toString().equals(components[0])) {
               invalidMessage = false;
               break;
           }
       }

       if (!invalidMessage) {

           MessageType type = MessageType.valueOf(components[0]);
           HashMap<String, String> body = new HashMap<>();

           if (components.length >= 2) {
               for (String keyValuePair : components[1].split(",")) {
                   String[] kvComponents = keyValuePair.split("=");
                   if (kvComponents.length >= 2) {
                       body.put(kvComponents[0], kvComponents[1]);
                   }
               }
           }

           return new Message(type, body);
       } else {
           return null;
       }
    }

    //Message in String umwandeln
    //Form: MESSAGE_TYPE;key=value,key=value
    public String serialize() {
        return String.format("%s;%s\r\n", type, body.entrySet().stream()
                .map(e -> String.format("%s=%s", e.getKey(), e.getValue()))
                .collect(Collectors.joining(",")));
    }

    public MessageType getType() {
        return type;
    }

    public Map<String, String> getBody() {
        return body;
    }

}
