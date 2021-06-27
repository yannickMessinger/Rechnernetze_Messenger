import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

// Yannick Messinger, Jasmin Steiner, Markus Wagner

public class Client {
    private static Socket clientSocket;
    private static DataOutputStream outToServer;

    private static BufferedReader inFromUser;
    private static boolean usernameRegistered;
    private static boolean passwordRegistered;
    private static boolean loginSuccessful;
    private static String currentUsername;
    private static boolean outstandingRequest;
    private static boolean activeChat;
    private static Message latestMessage;

    private static DatagramSocket udpSocket;
    private static InetAddress udpAddress;
    private static InetAddress curChatAddress;
    private static int curChatPort;

    private static int curChunkId;
    private static Chunk[] outgoingChunks;
    private static boolean[] receivedAcks;
    private static int travellingChunks;
    private static int lastAckId;
    private static boolean mediaSending;

    private static boolean allChunksReceived;
    private static ArrayList<Chunk> incomingChunks;


    public static void main(String[] argv) throws Exception {

// ---- Initialisierung ------------------------------------------------------------------------------------------------
        clientSocket = new Socket("localhost", Config.SERVER_PORT);
        outToServer = new DataOutputStream(clientSocket.getOutputStream());
        BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

        inFromUser = new BufferedReader(new InputStreamReader(System.in));
        usernameRegistered = false;
        passwordRegistered = false;
        loginSuccessful = false;
        currentUsername = "";
        outstandingRequest = false;
        activeChat = false;
        latestMessage = null;

        udpSocket = new DatagramSocket();
        udpAddress = InetAddress.getByName("localhost");
        curChatAddress = null;
        curChatPort = -1;

        curChunkId = 0;
        travellingChunks = 0;
        lastAckId = -1;
        mediaSending = false;

        allChunksReceived = false;
        incomingChunks = new ArrayList<>();

// ---- Beim Starten entweder anmelden oder registrieren noetig --------------------------------------------------------
        String eingabe = "";

        while (!eingabe.equals("a") && !eingabe.equals("r")) {
            System.out.println("Moechtest du dich anmelden (a) oder registrieren (r)?");
            eingabe = inFromUser.readLine();
        }

        if (eingabe.equals("a")) {
            // Anmeldung
            while (!loginSuccessful) {
                System.out.println("Gib deinen Namen ein:");
                String username = inFromUser.readLine();
                currentUsername = username;

                System.out.println("Gib dein Passwort ein:");
                String password = inFromUser.readLine();

                HashMap<String, String> body = new HashMap<>();
                body.put("username", username);
                body.put("password", password);
                Message sendMessage = new Message(MessageType.SEND_LOGIN_DATA, body);
                outToServer.writeBytes(sendMessage.serialize());
                String inputMessage = inFromServer.readLine();
                //System.out.println("FROM SERVER: " + inputMessage);
                handleMessage(Message.parse(inputMessage));
            }

        } else {
            // Registrierung
            // Username eingeben
            String username = "";
            while (!usernameRegistered) {
                System.out.println("Gib einen Namen ein (mindestens 3 Zeichen):");
                username = inFromUser.readLine();
                currentUsername = username;

                HashMap<String, String> body = new HashMap<>();
                body.put("username", username);
                Message sendMessage = new Message(MessageType.SEND_USERNAME, body);
                outToServer.writeBytes(sendMessage.serialize());
                String inputMessage = inFromServer.readLine();
                //System.out.println("FROM SERVER: " + inputMessage);
                handleMessage(Message.parse(inputMessage));
            }

            //Password eingeben
            String password = "";
            while (!passwordRegistered) {
                System.out.println("Gib ein Passwort ein (mindestens 3 Zeichen):");
                password = inFromUser.readLine();
                HashMap<String, String> body = new HashMap<>();
                body.put("password", password);
                Message sendMessage = new Message(MessageType.SEND_PASSWORD, body);
                outToServer.writeBytes(sendMessage.serialize());
                String inputMessage = inFromServer.readLine();
                //System.out.println("FROM SERVER: " + inputMessage);
                handleMessage(Message.parse(inputMessage));
            }

            //Nach Registrierung automatisch anmelden
            HashMap<String, String> body = new HashMap<>();
            body.put("username", username);
            body.put("password", password);
            Message sendMessage = new Message(MessageType.SEND_LOGIN_DATA, body);
            outToServer.writeBytes(sendMessage.serialize());
            String inputMessage = inFromServer.readLine();
            handleMessage(Message.parse(inputMessage));

        }

// ---- Ueberprueft dauerhaft, ob eine Meldung vom Server kommt ----------------------------------------------------------
        Thread inFromServerThread = new Thread(() -> {
            String iM;
            try {
                while (true) {
                    iM = inFromServer.readLine();
                    //System.out.println("FROM SERVER: " + iM);
                    latestMessage = Message.parse(iM);
                    handleMessage(latestMessage);
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }

        });
        inFromServerThread.start();

// ---- Ueberprueft dauerhaft, die Eingabe vom User ----------------------------------------------------------
        Thread inFromUserThread = new Thread(() -> {
            System.out.println();
            System.out.println("Du hast folgende Eingabemoeglichkeiten:");
            System.out.println("  active -- Zeigt alle aktive User an");
            System.out.println("  chat   -- Chat mit einem aktiven User starten");
            System.out.println("  logout -- Abmelden");
            System.out.println();

            try {

                while (true) {
                    // Naechste Zeile einlesen
                    String consoleInput = inFromUser.readLine();

                    // Wenn sich der User in einem aktiven Chat befindet
                    if (activeChat) {
                        if (!mediaSending) {
                            // Chat schliessen
                            if (consoleInput.equals("close")) {
                                HashMap<String, String> body = new HashMap<>();
                                body.put("username", currentUsername);
                                Message sendMessage = new Message(MessageType.CLOSE_CHAT, body);
                                UdpMessage sendUdpMessage = new UdpMessage(sendMessage.serialize());
                                sendUdpMessage(sendUdpMessage);

                                activeChat = false;
                                curChatAddress = null;
                                curChatPort = -1;
                                System.out.println("Chat closed");

                            // File senden
                            } else if (consoleInput.equals("loadMedia")) {
                                FileDialog dialog = new FileDialog((Frame) null, "Select File to Open");
                                dialog.setMode(FileDialog.LOAD);
                                dialog.setVisible(true);
                                if (dialog.getFiles().length > 0) {
                                    File file = dialog.getFiles()[0];
                                    byte[] fileBytes = Files.readAllBytes(file.toPath());
                                    HashMap<String, String> body = new HashMap<>();
                                    StringBuilder bytesString = new StringBuilder();
                                    for (byte b : fileBytes) {
                                        bytesString.append(b).append("|");
                                    }
                                    body.put("bytes", bytesString.toString());
                                    body.put("filename", file.getName());
                                    Message sendMessage = new Message(MessageType.MEDIA_MESSAGE, body);
                                    UdpMessage sendUdpMessage = new UdpMessage(sendMessage.serialize());
                                    System.out.println("Die Datei wird gesendet... Bitte habe etwas Geduld");
                                    mediaSending = true;
                                    sendUdpMessage(sendUdpMessage);
                                }

                            // Chatnachricht senden
                            } else {
                                HashMap<String, String> body = new HashMap<>();
                                body.put("content", consoleInput);
                                Message sendMessage = new Message(MessageType.CHAT_MESSAGE, body);
                                UdpMessage sendUdpMessage = new UdpMessage(sendMessage.serialize());
                                sendUdpMessage(sendUdpMessage);
                            }
                        }

                    // "Standardeingaben" (logout/active/chat)
                    } else if (!outstandingRequest) {
                        switch (consoleInput) {
                            case "logout": {
                                HashMap<String, String> body = new HashMap<>();
                                body.put("username", currentUsername);
                                Message sendMessage = new Message(MessageType.LOGOUT, body);
                                outToServer.writeBytes(sendMessage.serialize());
                                break;
                            }
                            case "active": {
                                HashMap<String, String> body = new HashMap<>();
                                Message sendMessage = new Message(MessageType.GET_ACTIVE_USERS, body);
                                outToServer.writeBytes(sendMessage.serialize());
                                break;
                            }
                            case "chat": {
                                System.out.println("Mit wem moechtest du chatten?");
                                String usernameInput = inFromUser.readLine();

                                HashMap<String, String> body = new HashMap<>();
                                body.put("fromUser", currentUsername);
                                body.put("toUser", usernameInput);
                                Message sendMessage = new Message(MessageType.CHAT_REQUEST, body);
                                outToServer.writeBytes(sendMessage.serialize());
                                System.out.println("Wartet darauf, dass " + usernameInput + " die Anfrage annimmt....");
                                break;
                            }
                            default:
                                System.out.println();
                                System.out.println("Du hast folgende Eingabemoeglichkeiten:");
                                System.out.println("  active -- Zeigt alle aktive User an");
                                System.out.println("  chat   -- Chat mit einem aktiven User starten");
                                System.out.println("  logout -- Abmelden");
                                System.out.println();
                                break;
                        }

                    // Wenn j oder n erwartet wird
                    } else {
                        String fromUser = latestMessage.getBody().get("fromUser");
                        String toUser = latestMessage.getBody().get("toUser");
                        while (!consoleInput.equals("j") && !consoleInput.equals("n")) {
                            System.out.println("Annehmen? Ja (j) oder Nein (n)?");
                            consoleInput = inFromUser.readLine();
                        }
                        if (consoleInput.equals("j")) {
                            HashMap<String, String> body = new HashMap<>();
                            body.put("fromUser", fromUser);
                            body.put("toUser", toUser);
                            body.put("udpAddress", udpAddress.getHostAddress());
                            body.put("udpPort", String.valueOf(udpSocket.getLocalPort()));
                            Message sendMessage = new Message(MessageType.CONFIRM_CHAT_REQUEST, body);
                            outToServer.writeBytes(sendMessage.serialize());
                        } else {
                            HashMap<String, String> body = new HashMap<>();
                            body.put("fromUser", fromUser);
                            body.put("toUser", toUser);
                            Message sendMessage = new Message(MessageType.DECLINE_CHAT_REQUEST, body);
                            outToServer.writeBytes(sendMessage.serialize());
                        }
                        outstandingRequest = false;
                    }

                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        });
        inFromUserThread.start();

// ---- UDP-"Logik" ----------------------------------------------------------------------------------------------------
        udpSocket.setSoTimeout(Config.TIMEOUT_MS);

        // ueberprueft udpSocket auf eingehende Packete
        Thread udpThread = new Thread(() -> {

            while (true) {
                byte[] receiveData = new byte[1024];
                try {
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    // Blockiert solange, bis ein Packet auf Socket ankommt oder Timeout eintritt
                    udpSocket.receive(receivePacket);
                    curChatAddress = receivePacket.getAddress();
                    curChatPort = receivePacket.getPort();

                    String messageString = new String(receivePacket.getData()).substring(0, receivePacket.getLength());
                    messageString = messageString.replace("\n", "");

                    // Wenn Chunk angekommen ist
                    // Es wird kein Acknowledgement zurueckgeschickt, wenn
                    // ein Chunk mit der naechsten erwarteten Id fehlt
                    if (Chunk.isChunk(messageString)) {
                        //System.out.println(messageString);
                        Chunk receiveChunk = Chunk.parse(messageString);

                        // wenn nicht erstes Chunk einer Message
                        if (!incomingChunks.isEmpty()) {
                            Chunk lastchunk = incomingChunks.get(incomingChunks.size()-1);
                            if (receiveChunk.getId() == lastchunk.getId()+1) {
                                incomingChunks.add(receiveChunk);
                                sendAck(receiveChunk);
                            } else {
                                for (Chunk c : incomingChunks) {
                                    if (c.getId() == receiveChunk.getId()) {
                                        sendAck(receiveChunk);
                                        break;
                                    }
                                }
                            }
                        // wenn erstes Chunk einer Message
                        } else if (receiveChunk.getId() == 0){
                            incomingChunks.add(receiveChunk);
                            sendAck(receiveChunk);
                            allChunksReceived = false;
                        }

                        // wenn schon alle Chunks empfangen
                        // aber Sender noch nicht alle Acks bekommen
                        // -> einfach nur Ack zurueckschicken
                        if (allChunksReceived) {
                            sendAck(receiveChunk);
                        }

                        // wenn letztes Chunk einer Message
                        if (receiveChunk.isEnd() && incomingChunks.size() == receiveChunk.getId()+1) {
                            sendAck(receiveChunk);
                            allChunksReceived = true;
                        }

                    // Wenn Ackknowledgement angekommen ist
                    } else if (Acknowledgement.isAcknowledgement(messageString)) {
                        Acknowledgement ack = Acknowledgement.parse(messageString);
                        //System.out.println(ack);
                        receivedAcks[ack.getId()] = true;
                        if (ack.getId() == lastAckId+1) {
                            lastAckId = ack.getId();
                            travellingChunks--;

                            // Naechste Chunks schicken
                            if (lastAckId != outgoingChunks.length-1) {
                                sendNextChunks();
                            }

                        }
                    }

                // SocketTimeOut abgelaufen
                } catch(SocketTimeoutException e) {
                    if (curChatAddress != null && curChatPort !=-1) {

                        // TimeOut beim SENDER
                        if (outgoingChunks != null) {
                            if (curChunkId <= outgoingChunks.length) {
                                //System.out.println("SocketTimeout");
                                travellingChunks = 0;
                                curChunkId = lastAckId+1;
                                try {
                                    // Wenn nicht alle Acks angekommen, werden weitere Chunks geschickt
                                    if (!allAcks()) {
                                        sendNextChunks();
                                    // sonst ist die Nachricht angekommen
                                    } else {
                                        System.out.println("Nachricht ist angekommen");
                                        outgoingChunks = null;
                                        curChunkId = 0;
                                        travellingChunks = 0;
                                        lastAckId = -1;
                                    }
                                } catch (IOException ioException) {
                                    ioException.printStackTrace();
                                }
                            }
                        }

                        // TimeOut beim EMPFAENGER
                        if (!incomingChunks.isEmpty()) {
                            // Nachricht ist vollstaendig angekommen und wird verarbeitet
                            if (allChunksReceived) {
                                UdpMessage udpMessage = UdpMessage.fromChunks(incomingChunks);
                                //System.out.println(udpMessage.getRaw());

                                try {
                                    handleUdpMessage(udpMessage);
                                } catch (IOException ioException) {
                                    ioException.printStackTrace();
                                }

                                // incomingChunks zuruecksetzen
                                incomingChunks.clear();
                            }

                        }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

        });
        udpThread.start();


    }

// ---- Naechste Chunks aus outgoingChunks senden ----------------------------------------------------------------------
    public static synchronized void sendNextChunks() throws IOException {
        for (int i = 0; i < Config.WINDOW_SIZE; i++) {
            if (curChunkId < outgoingChunks.length && travellingChunks < Config.WINDOW_SIZE && curChatPort != -1) {
                byte[] chunkData = outgoingChunks[curChunkId].toPacket();
                DatagramPacket sendPacket = new DatagramPacket(chunkData, chunkData.length, curChatAddress, curChatPort);
                // Packet wird nur gesendet wenn kein simulierter Netzwerkfehler aufgetreten ist
                if (!Config.isFakeNetworkError()) {
                    //System.out.println("Chunk " + curChunkId + " senden");
                    udpSocket.send(sendPacket);
                }
                travellingChunks++;
                curChunkId++;
            } else {
                break;
            }
        }
    }

// ---- Passendes Acknowledgement senden -------------------------------------------------------------------------------
    public static void sendAck(Chunk receiveChunk) throws IOException {

        Acknowledgement ack = new Acknowledgement(receiveChunk.getId());
        byte[] ackData = ack.toPacket();
        DatagramPacket sendPacket = new DatagramPacket(ackData, ackData.length, curChatAddress, curChatPort);

        // Packet wird nur gesendet wenn kein simulierter Netzwerkfehler aufgetreten ist
        if (!Config.isFakeNetworkError()) {
            udpSocket.send(sendPacket);
        }

    }

// ---- bearbeitet Nachrichten, die vom Server ueber TCP eingetroffen sind ---------------------------------------------
    public static void handleMessage(Message message) throws IOException, InterruptedException {
        switch (message.getType()) {
            case SET_USERNAME:
                usernameRegistered = true;
                break;

            case USERNAME_EXISTS:
                System.out.println("Dieser Username existiert bereits.");
                break;

            case INVALID_USERNAME:
                System.out.println("Der Username muss mindestens 3 Zeichen lang sein");
                break;

            case SET_PASSWORD:
                passwordRegistered = true;
                System.out.println("Du hast dich erfolgreich registriert.");
                break;

            case INVALID_PASSWORD:
                System.out.println("Das Passwort muss mindestens 3 Zeichen lang sein");
                break;

            case LOGIN_FAILED:
                System.out.println("Anmeldung fehlgeschlagen");
                break;

            case LOGIN_SUCCESSFUL:
                System.out.println("Herzlich Willkommen " + currentUsername + ".");
                loginSuccessful = true;
                break;

            case SHOW_ACTIVE_USERS:
                // Syntax: SHOW_ACTIVE_USERS;users=Markus|Jasmin|Yannick|
                String userString = message.getBody().get("users");
                String[] usernames = userString.split("\\|");
                System.out.println("Aktive User:");
                for (String user : usernames) {
                    if (user.equals(currentUsername)) {
                        System.out.println("   -" + user + " (Du)");
                    } else {
                        System.out.println("   -" + user);
                    }
                }
                System.out.println();
                break;

            case LOGOUT_SUCCESSFUL:
                System.out.println("Du hast dich erfolgreich abgemeldet");
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.exit(0);
                break;

            case LOGOUT_FAILED:
                System.out.println("LOGOUT failed");
                break;

            // Chatanfrage von "fromUser" bekommen
            case RECEIVE_CHAT_REQUEST:
                if (activeChat) {
                    HashMap<String, String> body = new HashMap<>();
                    body.put("fromUser", message.getBody().get("fromUser"));
                    body.put("toUser", message.getBody().get("toUser"));
                    Message sendMessage = new Message(MessageType.CURRENT_CHAT, body);
                    outToServer.writeBytes(sendMessage.serialize());
                    break;
                }
                String fromUser = message.getBody().get("fromUser");
                System.out.println("Du hast eine Chatanfrage von " + fromUser + " erhalten");
                System.out.println("Annehmen? Ja (j) oder Nein (n)?");
                outstandingRequest = true;
                break;

            case USER_NOT_EXISTS:
                System.out.println("Der eingegebene User existiert nicht oder ist nicht aktiv");
                break;

            case CHAT_NOT_POSSIBLE:
                System.out.println("Du kannst nicht mit dir selbst chatten");
                break;

            case CURRENT_CHAT:
                System.out.println(message.getBody().get("toUser") + " befindet sich in einem Chat");
                break;

            // Abgeschickte Anfrage wurde akzeptiert
            case REQUEST_CONFIRMED:
                curChatAddress = InetAddress.getByName(message.getBody().get("udpAddress"));
                curChatPort = Integer.parseInt(message.getBody().get("udpPort"));

                // Chat starten
                System.out.println("Chat mit " + message.getBody().get("toUser") + " ----------------------");
                System.out.println("  (Mit close kannst du den Chat beenden)");
                System.out.println("  (Mit loadMedia kannst du eine Datei verschicken)");
                activeChat = true;

                // udpAddress und udpPort zu Chatpartner senden
                HashMap<String, String> body = new HashMap<>();
                body.put("fromUser", currentUsername);
                body.put("udpAddress", udpAddress.getHostAddress());
                body.put("udpPort", String.valueOf(udpSocket.getLocalPort()));
                Message sendMessage = new Message(MessageType.SEND_UDP_DATA, body);
                UdpMessage sendUdpMessage = new UdpMessage(sendMessage.serialize());
                sendUdpMessage(sendUdpMessage);
                break;

            // Abgeschickte Anfrage wurde abgelehnt
            case REQUEST_DECLINED:
                String toUser = message.getBody().get("toUser");
                System.out.println(toUser + " hat deine Chatanfrage abgelehnt");
                break;

            default:
                System.out.println("Unbekannte Nachricht empfangen");
        }
    }

// ---- bearbeitet Nachrichten, die ueber UDP eingetroffen sind --------------------------------------------------------
    public static void handleUdpMessage(UdpMessage udpMessage) throws IOException {
        String messageString = udpMessage.getRaw();
        messageString = messageString.replace("\r\n", "");
        Message message = Message.parse(messageString);

        switch (message.getType()) {
            case SEND_UDP_DATA:
                curChatAddress = InetAddress.getByName(message.getBody().get("udpAddress"));
                curChatPort = Integer.parseInt(message.getBody().get("udpPort"));

                System.out.println("Chat mit " + message.getBody().get("fromUser") + " ----------------------");
                System.out.println("  (Mit close kannst du den Chat beenden)");
                System.out.println("  (Mit loadMedia kannst du eine Datei verschicken)");

                activeChat = true;
                break;

            case CHAT_MESSAGE:
                String content = message.getBody().get("content");
                System.out.println("--> " + content);
                break;

            case MEDIA_MESSAGE:
                mediaSending = false;
                String bytesString = message.getBody().get("bytes");

                String[] imageData = bytesString.split("\\|");
                byte[] imageDataBytes = new byte[imageData.length];

                for (int i = 0; i<imageData.length; i++) {
                    imageDataBytes[i] = Byte.parseByte(imageData[i]);
                }

                String filename = message.getBody().get("filename");
                System.out.println("--> " + filename);

                FileDialog dialog = new FileDialog((Frame)null, "Save");
                dialog.setMode(FileDialog.SAVE);
                dialog.setVisible(true);

                if (dialog.getFiles().length > 0) {
                    Files.write(Path.of(dialog.getFiles()[0].getPath()), imageDataBytes);
                }

                break;

            case CLOSE_CHAT:
                String username = message.getBody().get("username");
                activeChat = false;
                curChatAddress = null;
                curChatPort = -1;
                System.out.println(username + " hat den Chat beendet");
                break;

            default:
                System.out.println("Unbekannte Nachricht empfangen");
        }
    }

// ---- sendet eine UdpMessage -----------------------------------------------------------------------------------------
    // 1. Message in Chunk-Array umwandeln
    // 2. Chunks versenden
    public static void sendUdpMessage(UdpMessage sendMessage) throws IOException {
        curChunkId = 0;
        travellingChunks = 0;
        lastAckId = -1;
        outgoingChunks = sendMessage.chunk();
        receivedAcks = new boolean[outgoingChunks.length];

        sendNextChunks();

    }

// ---- gibt zurueck, ob alle Acknowledgements angekommen sind ---------------------------------------------------------
    public static boolean allAcks() {
        for (boolean b : receivedAcks) {
            if (!b) {
                return false;
            }
        }
        return true;
    }

}
