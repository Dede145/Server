package Server4;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.*;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import static java.nio.file.StandardOpenOption.*;

public class Server {
    private BufferedReader packetMain;
    private BufferedReader packetStatus;
    private int timeout = 500;
    private Logger log;
    private PrintWriter packetResponse;
    private ServerSocket serverSocket;
    private ServerSocket serverStatus;
    private Socket clientSocket;
    private Socket clientStatus;
    private String server = "Server4";

    private static int port = 30006;
    private static int status = 30007;

    public Server(int port, int status) {
        try {
            newLog();
            log.info(server + " is online.");

            serverStatus = new ServerSocket(status);
            clientStatus = serverStatus.accept(); 

            clientStatus.setSoTimeout(0);

            log.info("Connected to " + clientStatus.getLocalAddress().toString() + " on port " + clientStatus.getLocalPort() + ".");

            packetStatus = new BufferedReader(new InputStreamReader(clientStatus.getInputStream()));
            packetResponse = new PrintWriter(clientStatus.getOutputStream(), true);            
        } catch (IOException e) {
            log.info("ERROR! An unkown error occured while connecting status socket to Middleware!");
            e.printStackTrace();
            closeConnection(true);
        }
        
        getRole(port);
    }

    //Doc.
    private void newLog() {
        log = Logger.getLogger("Server4");
        FileHandler fh;

        try {
            fh = new FileHandler("C:/Users/win/Documents/Projects Java/Server/Server4/Logs/Server4.log");
            log.addHandler(fh);
            SimpleFormatter format = new SimpleFormatter();
            fh.setFormatter(format);
        } catch (SecurityException e) {
            System.err.println("ERROR! Security systems aren't allowing the log to register server events!");
            closeConnection(true);
        } catch (IOException e) {
            e.printStackTrace();
            closeConnection(true);
        }
    }

    private void mainServer(int port) {
        try {
            System.out.println("OK");
            serverSocket = new ServerSocket(port);
            
            clientSocket = serverSocket.accept();
            clientSocket.setSoTimeout(0);

            log.info("Connected to " + clientSocket.getLocalAddress().toString() + " on port " + clientSocket.getLocalPort() + ".");

            packetMain = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));         
        } catch (IOException e) {
            log.info("ERROR! An unkown error occured while connecting main socket to Middleware!");
            e.printStackTrace();
            closeConnection(true);
        }
    }

    private long totalStorage(File folder) {
        File[] files = folder.listFiles();
        long storage = 0;

        if (files.length == 0) return 0;

        for (File file : files) {
            if (file.isFile()) storage += file.length();
            else storage += totalStorage(file);
        }

        return storage;
    }

    //TODO: Secondary.
    private void getRole(int port) {
        File folder = new File("C:/Users/win/Documents/Projects Java/Server/Server4/Files");
        long storage = totalStorage(folder);
        String packet;

        try {
            packet = packetStatus.readLine();
            log.info(packet); //DEBUG
            packetResponse.println(storage);

            packet = packetStatus.readLine();
            log.info(packet); //DEBUG
            packetResponse.println("ACK.");

            if (packet.equals("Main server.")) mainServer(port);
            else if (packet.equals("Secondary server.")) {
                closeConnection(false);
            } else {
                closeConnection(false);
            }
        } catch (IOException e) {
            //TODO: Treat exception.
            e.printStackTrace();
        }
    }

    //Doc.
    private String[] receivePacket(int packetNum) {
        String[] packets = new String[2];

        try {
            packets[1] = packetStatus.readLine();
            packets[0] = packetMain.readLine();

            if (packets[1].equals("Close connection.")) {
                packetResponse.println("Closing connection.");
                log.info(clientSocket.getLocalAddress().toString() + " requested connection closure.");
            } else {
                packetResponse.println("Packet received.");
                log.info("Received packet " + packetNum + " from " + clientSocket.getLocalAddress().toString() + "."); 
            }
            
            if (packets[0].equals("Packet sent.")) System.out.println(packets[0]);
        } catch (SocketTimeoutException e) {
            log.info("ERROR! Packet " + packetNum + " was lost!");
            packetResponse.println("Packet lost.");
            packets[1] = "Packet lost.";                   
        } catch (IOException e) {
            log.info("ERROR! An unknown error occured while receiving packets!");
            e.printStackTrace();
            closeConnection(true);
        }
        
        return packets;
    }

    //Doc.
    private void receiveFile() {
        byte data[];
        int packetNum = 0;
        Path path = Paths.get(server, "Files");
        String packets[] = new String[2];     
        
        try {
            packets[1] = packetStatus.readLine();

            clientSocket.setSoTimeout(timeout);
            clientStatus.setSoTimeout(timeout);
        } catch (SocketException e) {
            log.info("ERROR! An unknown error occured while setting timeout for sockets!");
            e.printStackTrace();
        } catch (IOException e) {
            log.info("ERROR! An unknown error occured while receiving packets!");
            e.printStackTrace();
            closeConnection(true);
        }

        while (true) {
            packets = receivePacket(packetNum);

            if (packets[1].equals("Close connection.")) break;
            if (packets[1].equals("Lost packet.")) continue;
            if (packets[1].equals("File sent.")) {
                path = Paths.get(server, "Files", packets[0]);
                System.out.println("\nPATH: " + path);
                path = path.toAbsolutePath();
                System.out.println("\nABSOLUTE PATH: " + path);
            }

            try (OutputStream contents = new BufferedOutputStream(Files.newOutputStream(path, CREATE, APPEND))) {
                if (!packets[1].equals("File sent.")) {
                    data = packets[0].getBytes();
                    contents.write(data, 0, data.length);
                    contents.write("\n".getBytes(), 0, 1);
                } else if (!Files.exists(path)) {
                    log.info("ERROR! " + path.toString() + " wasn't created or it doesn't exist!");
                    closeConnection(true);
                }
                
            } catch (IOException e) {
                log.info("ERROR! An unkown error occured while writing the file " + path.toString() + "!");
                e.printStackTrace();
                closeConnection(true);
            }

            packetNum++;
        }

        closeConnection(false);
    }

    //Doc.
    private void closeConnection(boolean exceptionFired) {
        try {
            if (exceptionFired) packetResponse.println("Shutdown.");

            if (packetMain != null) packetMain.close();
            packetStatus.close();
            packetResponse.close();
            if (clientSocket != null) clientSocket.close();
            clientStatus.close();
            if (serverSocket != null) serverSocket.close();
            serverStatus.close();

            log.info("Connection with server was closed.");
        } catch (IOException e) {
            log.info("ERROR: An unkown error occured while closing sockets and readers!");
            e.printStackTrace();
            exceptionFired = true;
        }

        log.info("Server 4 is offline.");

        if (exceptionFired == true) {
            System.exit(1);
        }

        System.exit(0);
    }

    public static void main(String[] args) {
        Server server = new Server(port, status);

        server.receiveFile();
    }
}
