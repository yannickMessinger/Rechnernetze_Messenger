import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

// Yannick Messinger, Jasmin Steiner, Markus Wagner

public class Server {

    private static Set<User> users;

    // Mindestlaenge von Username und Password
    private static final int LOGIN_FORMAT_SIZE = 3;


    public static void main(String[] argv) throws Exception {

        ServerSocket welcomeSocket = new ServerSocket(6789);
        System.out.println("Server gestartet");

        users = new HashSet<>();

        while (true) {
            Socket connectionSocket = welcomeSocket.accept();

            User newUser = new User();

// ---- fuer jeden Client, der sich verbunden hat, wird der Thread ausgefuehrt -----------------------------------------
            Thread t = new Thread(() -> {
                BufferedReader inFromClient;
                try {
                    String clientInput;

                    inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));

                    DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());

                    while(true) {
                        clientInput = inFromClient.readLine();

                        Message inputMessage = Message.parse(clientInput);
                        if (inputMessage != null) {
                            handle(newUser, inputMessage, outToClient, connectionSocket);
                        }

                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            t.start();
        }

    }

// ---- bearbeitet eingegangene Nachrichten ----------------------------------------------------------------------------
    public static void handle(User user, Message inputMessage, DataOutputStream outToClient, Socket connectionSocket) throws IOException {
        if (inputMessage.getType() != null) {
            System.out.println("Message From Client: " + inputMessage.getType());
            switch (inputMessage.getType()) {
                // Wenn Username gesendet wurde, wird ueberprueft, ob...
                // ...Syntax ok
                // ...Username verfuegbar
                case SEND_USERNAME:
                    String username = inputMessage.getBody().get("username");
                    if (username != null && !username.equals("") && username.length() >= LOGIN_FORMAT_SIZE) {
                        boolean usernameExists = false;
                        for (User u : users) {
                            if (u.getUsername().equals(username)) {
                                usernameExists = true;
                                break;
                            }
                        }
                        if (usernameExists) {
                            HashMap<String, String> body = new HashMap<>();
                            Message sendMessage = new Message(MessageType.USERNAME_EXISTS, body);
                            outToClient.writeBytes(sendMessage.serialize());
                        } else {
                            user.setUsername(username);
                            HashMap<String, String> body = new HashMap<>();
                            Message sendMessage = new Message(MessageType.SET_USERNAME, body);
                            outToClient.writeBytes(sendMessage.serialize());
                        }
                    } else {
                        HashMap<String, String> body = new HashMap<>();
                        Message sendMessage = new Message(MessageType.INVALID_USERNAME, body);
                        outToClient.writeBytes(sendMessage.serialize());
                    }
                    break;

                // Wenn Passwort gesendet wurde, wird die Syntax ueberprueft
                case SEND_PASSWORD:
                    String password = inputMessage.getBody().get("password");
                    if (password != null && !password.equals("") && password.length() >= LOGIN_FORMAT_SIZE) {
                        users.add(user);
                        user.setPassword(password);
                        for (User u : users) {
                            System.out.println(u.toString());
                        }
                        HashMap<String, String> body = new HashMap<>();
                        Message sendMessage = new Message(MessageType.SET_PASSWORD, body);
                        outToClient.writeBytes(sendMessage.serialize());
                    } else {
                        HashMap<String, String> body = new HashMap<>();
                        Message sendMessage = new Message(MessageType.INVALID_PASSWORD, body);
                        outToClient.writeBytes(sendMessage.serialize());
                    }

                    break;

                // Anmeldung (Ueberpruefung, ob Username und Passwort stimmen)
                case SEND_LOGIN_DATA:
                    username = inputMessage.getBody().get("username");
                    password = inputMessage.getBody().get("password");
                    if (username == null || username.equals("") || username.length() < LOGIN_FORMAT_SIZE) {
                        HashMap<String, String> body = new HashMap<>();
                        Message sendMessage = new Message(MessageType.INVALID_USERNAME, body);
                        outToClient.writeBytes(sendMessage.serialize());
                        break;
                    }
                    if (password == null || password.equals("") || password.length() < LOGIN_FORMAT_SIZE) {
                        HashMap<String, String> body = new HashMap<>();
                        Message sendMessage = new Message(MessageType.INVALID_PASSWORD, body);
                        outToClient.writeBytes(sendMessage.serialize());
                        break;
                    }
                    boolean correct = false;
                    for (User u : users) {
                        if (u.getUsername().equals(username)) {
                            if (u.getPassword().equals(password) && !u.isActive()) {
                                correct = true;
                                u.setActive(true);
                                u.setCurrentSocket(connectionSocket);
                            }
                            break;
                        }

                    }
                    for (User u : users) {
                        System.out.println(u.toString());
                    }
                    HashMap<String, String> body = new HashMap<>();
                    Message sendMessage;
                    if (correct) {
                        sendMessage = new Message(MessageType.LOGIN_SUCCESSFUL, body);
                    } else {
                        sendMessage = new Message(MessageType.LOGIN_FAILED, body);
                    }
                    outToClient.writeBytes(sendMessage.serialize());

                    break;

                // Sendet Liste von aktiven Usern zurueck
                // Syntax: SHOW_ACTIVE_USERS;users=Markus|Jasmin|Yannick|
                case GET_ACTIVE_USERS:
                    StringBuilder usersString = new StringBuilder();
                    for (User u : users) {
                        if (u.getCurrentSocket() != null) {
                            System.out.println(u + " " + u.getCurrentSocket().toString());
                        } else {
                            System.out.println(u);
                        }
                        if (u.isActive()) {
                            usersString.append(u.getUsername()).append("|");
                        }
                    }
                    body = new HashMap<>();
                    body.put("users", usersString.toString());
                    sendMessage = new Message(MessageType.SHOW_ACTIVE_USERS, body);
                    outToClient.writeBytes(sendMessage.serialize());
                    break;

                // Abmeldung
                case LOGOUT:
                    System.out.println("Logging out...");
                    username = inputMessage.getBody().get("username");
                    for (User u : users) {
                        if (u.getUsername().equals(username)) {
                            u.setActive(false);
                            u.setCurrentSocket(null);
                            body = new HashMap<>();
                            sendMessage = new Message(MessageType.LOGOUT_SUCCESSFUL, body);
                            outToClient.writeBytes(sendMessage.serialize());
                            return;
                        }
                    }
                    body = new HashMap<>();
                    sendMessage = new Message(MessageType.LOGOUT_FAILED, body);
                    outToClient.writeBytes(sendMessage.serialize());
                    break;

                // Chatanfrage von "fromUser" an "toUser"
                case CHAT_REQUEST:
                    String fromUser = inputMessage.getBody().get("fromUser");
                    String toUser = inputMessage.getBody().get("toUser");
                    if (fromUser.equals(toUser)) {
                        body = new HashMap<>();
                        sendMessage = new Message(MessageType.CHAT_NOT_POSSIBLE, body);
                        outToClient.writeBytes(sendMessage.serialize());
                        break;
                    }
                    // Sucht User, der angefragt wird und sendet bei Treffer die Anfrage weiter
                    boolean userFound = false;
                    for (User u : users) {
                        if (u.getUsername().equals(toUser) && u.isActive()) {
                            userFound = true;
                            Socket socket = u.getCurrentSocket();
                            System.out.println(socket);
                            body = new HashMap<>();
                            body.put("fromUser", fromUser);
                            body.put("toUser", toUser);
                            sendMessage = new Message(MessageType.RECEIVE_CHAT_REQUEST, body);
                            DataOutputStream stream = new DataOutputStream(socket.getOutputStream());
                            stream.writeBytes(sendMessage.serialize());
                            stream.flush();
                            break;
                        }
                    }
                    if (!userFound) {
                        body = new HashMap<>();
                        body.put("username", toUser);
                        sendMessage = new Message(MessageType.USER_NOT_EXISTS, body);
                        outToClient.writeBytes(sendMessage.serialize());
                    }
                    break;

                // "toUser" hat die Anfrage angenommen
                case CONFIRM_CHAT_REQUEST:
                    fromUser = inputMessage.getBody().get("fromUser");
                    toUser = inputMessage.getBody().get("toUser");
                    body = new HashMap<>();
                    for (User u : users) {
                        if (u.getUsername().equals(toUser)) {
                            body.put("toUser", toUser);
                            body.put("udpAddress", inputMessage.getBody().get("udpAddress"));
                            body.put("udpPort", inputMessage.getBody().get("udpPort"));
                            break;
                        }
                    }
                    for (User u : users) {
                        if (u.getUsername().equals(fromUser)) {
                            Socket socket = u.getCurrentSocket();
                            sendMessage = new Message(MessageType.REQUEST_CONFIRMED, body);
                            DataOutputStream stream = new DataOutputStream(socket.getOutputStream());
                            stream.writeBytes(sendMessage.serialize());
                            break;
                        }
                    }
                    break;

                // "toUser" hat die Anfrage abgelehnt
                case DECLINE_CHAT_REQUEST:
                    fromUser = inputMessage.getBody().get("fromUser");
                    toUser = inputMessage.getBody().get("toUser");

                    body = new HashMap<>();
                    body.put("toUser", toUser);
                    for (User u : users) {
                        if (u.getUsername().equals(fromUser)) {
                            Socket socket = u.getCurrentSocket();
                            sendMessage = new Message(MessageType.REQUEST_DECLINED, body);
                            DataOutputStream stream = new DataOutputStream(socket.getOutputStream());
                            stream.writeBytes(sendMessage.serialize());
                            break;
                        }
                    }
                    break;

                // "toUser" befindet sich gerade in einem aktiven Chat
                case CURRENT_CHAT:
                    fromUser = inputMessage.getBody().get("fromUser");
                    toUser = inputMessage.getBody().get("toUser");

                    body = new HashMap<>();
                    body.put("toUser", toUser);
                    for (User u : users) {
                        if (u.getUsername().equals(fromUser)) {
                            Socket socket = u.getCurrentSocket();
                            sendMessage = new Message(MessageType.CURRENT_CHAT, body);
                            DataOutputStream stream = new DataOutputStream(socket.getOutputStream());
                            stream.writeBytes(sendMessage.serialize());
                            break;
                        }
                    }
                    break;

                default:
                    System.out.println("Unbekannte Nachricht empfangen");

            }
        }
    }

}
