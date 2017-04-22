import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.sql.*;
import java.text.DateFormat;
import java.util.Date;
import java.util.Vector;

public class Program {
    private static Vector<Client> clients = new Vector<>();
    private static DateFormat dateFormat = DateFormat.getTimeInstance();
    private static Connection con;
    private static Statement stmt;
    private static ResultSet rs;

    public static void main(String[] args) {
        try {
            Class.forName("org.sqlite.JDBC");
            File file = new File("settings.conf");
            System.out.println("Open settings file");
            BufferedReader in = new BufferedReader(new FileReader(file));
            String optionsStr;
            String hostName = "127.0.0.1", database = "data.db";
            int port = 2170;
            int maxMessageCount = 100;
            while ((optionsStr = in.readLine()) != null) {
                switch (optionsStr.split(":")[0]) {
                    case "hostname": {
                        hostName = optionsStr.split(":")[1];
                        System.out.println("hostname=" + hostName);
                        break;
                    }
                    case "database": {
                        database = optionsStr.split(":")[1];
                        System.out.println("database=" + database);
                        break;
                    }
                    case "max message count": {
                        maxMessageCount = Integer.parseInt(optionsStr.split(":")[1]);
                        System.out.println("max message count=" + maxMessageCount);
                    }
                }
            }
            con = DriverManager.getConnection("jdbc:sqlite:" + database);
            System.out.println("Connected to database");
            stmt = con.createStatement();
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Create server socket");
            System.out.println("For connect may use this IP:");
            for (InetAddress hostAddress : InetAddress.getAllByName(hostName)) {
                System.out.println(hostAddress.toString());
            }
            System.out.print("Wait for clients...\n");
            //noinspection InfiniteLoopStatement
            while (true) {
                final Client client = new Client();
                client.setSocket(serverSocket.accept());
                System.out.println("Create new socket");
                final int finalMaxMessageCount = maxMessageCount;
                new Thread() {
                    Vector<Integer> sendTo;

                    public void run() {
                        try {
                            System.out.println("Starting listening client");
                            //noinspection InfiniteLoopStatement
                            while (true) {
                                checkMsg(client.getdIS().readUTF());
                            }
                        } catch (Exception e) {
                            System.out.print("Close connect to " + client.getName() + "\n");
                            clients.remove(client);
                        }
                    }

                    void checkMsg(String msg) {
                        try {
                            System.out.println("Get new message");
                            switch (msg.getBytes()[0]) {
                                case 'r':
                                    if (register(msg.split("\n")[1], msg.split("\n")[2])) {
                                        sendLoginAnsver("s-ok");
                                        sendClientList();
                                    }
                                    break;
                                case 'l':
                                    if (login(msg.split("\n")[1], msg.split("\n")[2])) {
                                        sendLoginAnsver("l-ok");
                                        sendClientList();
                                        load_message();
                                    } else {
                                        sendLoginAnsver("l-no");
                                    }
                                    break;
                                case 't':
                                    sendTo = new Vector<>();
                                    for (int i = 1; i < msg.split("\n").length; i++) {
                                        sendTo.add(Integer.parseInt(msg.split("\n")[i]));
                                    }
                                    break;
                                case 'm':
                                    System.out.println("Get message from " + client.getID());
                                    String sendedMessage = msg.split("\n")[1];
                                    for (Client client1 : clients) {
                                        if (sendTo.contains(client1.getID())) {
                                            try {
                                                client1.getdOS().writeUTF(generateMessage(sendedMessage));
                                                System.out.println(client1.getID() + " get mes from " + client.getID() + " :" + sendedMessage);
                                            } catch (Exception ignored) {
                                            }
                                        }
                                        for (Integer aSendTo : sendTo) {
                                            save_message(aSendTo, generateMessage(sendedMessage));
                                        }
                                    }
                                    break;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    boolean register(String username, String password) {
                        try {
                            System.out.println("Try to register:" + username);
                            stmt = con.createStatement();
                            System.out.println("Get database state");
                            rs = stmt.executeQuery("select username from users\n where username='" + username + "';");
                            rs.getType();
                            if (!rs.next()) {
                                stmt.executeUpdate("insert into users(username, password)\n" +
                                        "values ('" + username + "','" + password + "');");
                                System.out.println("Register ok - " + username);
                                return true;
                            }
                            System.out.println("This username " + username + " already use.");
                            return false;
                        } catch (Exception ignored) {
                            System.out.println("Exception when try register:" + username);
                            return false;
                        }
                    }

                    boolean login(String username, String password) {
                        try {
                            System.out.println("Try to login:" + username);
                            stmt = con.createStatement();
                            System.out.println("Get database state");
                            String query = "select * from users\n where username='" + username + "';";
                            rs = stmt.executeQuery(query);
                            if (rs.next()) {
                                if (rs.getString(3).equals(password)) {
                                    client.setID(rs.getInt(1));
                                    client.setName(rs.getString(2));
                                    clients.add(client);
                                    System.out.println("Login ok - " + username);
                                    return true;
                                }
                            }
                            System.out.println("Wrong password for " + username);
                            return false;
                        } catch (Exception ignored) {
                            return false;
                        }
                    }

                    void save_message(int id, String message) throws SQLException {
                        stmt = con.createStatement();
                        System.out.println("Get database state");
                        stmt.executeUpdate("insert into message(id, message)\n" +
                                "values ('" + id + "','" + message + "');");
                        System.out.println("Save message to " + id);
                        stmt = con.createStatement();
                        System.out.println("Get database state");
                        rs = stmt.executeQuery("select * from message where id=" + id + ";");
                        int count = 0;
                        while (rs.next()) {
                            count++;
                        }
                        rs = stmt.executeQuery("select * from message where id=" + id + ";");
                        while (count > finalMaxMessageCount && rs.next()) {
                            count--;
                            System.out.println("Delete old message for " + id);
                            stmt.executeUpdate("DELETE FROM Message\n" +
                                    "      WHERE id = " + rs.getString(1) + " AND \n" +
                                    "            message = \'" + rs.getString(2) + "\'");
                            rs = stmt.executeQuery("select * from message where id=" + id + ";");
                        }

                    }

                    void load_message() throws SQLException, IOException {
                        stmt = con.createStatement();
                        System.out.println("Get database state");
                        rs = stmt.executeQuery("select * from message where id=" + client.getID() + ";");
                        System.out.println("Load message for " + client.getID());
                        while (rs.next()) {
                            client.getdOS().writeUTF(rs.getString(2));
                        }
                        stmt = con.createStatement();
                        System.out.println("Get database state");
                        rs = stmt.executeQuery("select * from message where id=" + client.getID() + ";");
                        int count = 0;
                        while (rs.next()) {
                            count++;
                        }
                        rs = stmt.executeQuery("select * from message where id=" + client.getID() + ";");
                        while (count > finalMaxMessageCount && rs.next()) {
                            count--;
                            System.out.println("Delete old message for " + client.getID());
                            stmt.executeUpdate("DELETE FROM Message\n" +
                                    "      WHERE id = " + rs.getString(1) + " AND \n" +
                                    "            message = \'" + rs.getString(2) + "\'");
                            rs = stmt.executeQuery("select * from message where id=" + client.getID() + ";");
                        }
                    }

                    void sendLoginAnsver(String msg) {
                        try {
                            client.getdOS().writeUTF(msg);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    String generateMessage(String message) {
                        String dateTime = dateFormat.format(new Date());
                        return "m\n" + dateTime + "\n" + client.getName() + "\n" + message;
                    }

                    void sendClientList() throws SQLException {
                        stmt = con.createStatement();
                        System.out.println("Get database state");
                        rs = stmt.executeQuery("select * from users;");
                        System.out.println("Generate client list for send");
                        StringBuilder clientsNames = new StringBuilder("u\n");
                        while (rs.next()) {
                            clientsNames.append(rs.getInt(1)).append(" ").append(rs.getString(2)).append("\n");
                        }
                        System.out.println("Start sending client list for all users");
                        for (Client client1 : clients) {
                            try {
                                client1.getdOS().writeUTF(clientsNames.toString());
                            } catch (Exception ignored) {
                            }
                        }
                        System.out.println("Client list already sended");
                    }

                }.start();
            }
        } catch (Exception exception) {
            System.out.print(exception.toString());
        }
    }
}