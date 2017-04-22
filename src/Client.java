import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

class Client {
    private String name;
    private DataOutputStream dOS;
    private DataInputStream dIS;
    private int ID;

    void setSocket(Socket _socket) {
        try {
            dOS = new DataOutputStream(_socket.getOutputStream());
            dIS = new DataInputStream(_socket.getInputStream());
        } catch (Exception ignored) {
        }
    }

    Client() {
    }

    int getID() {
        return ID;
    }

    void setName(String name) {
        this.name = name;
    }

    void setID(int ID) {
        this.ID = ID;
    }

    String getName() {
        return name;
    }

    DataOutputStream getdOS() {
        return dOS;
    }

    DataInputStream getdIS() {
        return dIS;
    }
}
