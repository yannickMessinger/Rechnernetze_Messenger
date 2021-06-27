import java.net.Socket;
import java.util.Objects;

// Yannick Messinger, Jasmin Steiner, Markus Wagner

public class User {
    private String username;
    private String password;
    private boolean active;
    private Socket currentSocket;

    public User() {

    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        if (active) {
            System.out.println("false -> true");
        } else {
            System.out.println("true -> false");
        }
        this.active = active;
    }

    public Socket getCurrentSocket() {
        return currentSocket;
    }

    public void setCurrentSocket(Socket currentSocket) {
        this.currentSocket = currentSocket;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return username.equals(user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", password='" + password + '\'' +
                '}';
    }
}
