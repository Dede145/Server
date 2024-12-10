package Middleware;

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

public class ClientMiddleware {
    private BufferedReader packetMain;
    private BufferedReader packetStatus;
    private FileHandler fh;
    private int timeout = 500;
    private PrintWriter packetResponse;
    private ServerSocket serverSocket;
    private ServerSocket serverStatus;
    private Socket clientSocket;
    private Socket clientStatus;
    private String middleware = "Middleware";

    private static int in_port = 20000;
    private static int in_status = 20001;

    protected Logger log;

    protected static String tempFilePath;

    public ClientMiddleware(int in_port, int in_status) {
        try {
            newLog();
            log.info(middleware + " is online.");

            serverSocket = new ServerSocket(in_port);
            serverStatus = new ServerSocket(in_status);
            clientSocket = serverSocket.accept();
            clientStatus = serverStatus.accept();
            
            clientSocket.setSoTimeout(0);
            clientStatus.setSoTimeout(0);

            log.info("Connected to " + clientSocket.getLocalAddress().toString() + " on port " + clientSocket.getLocalPort() + ".");

            packetMain = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            packetStatus = new BufferedReader(new InputStreamReader(clientStatus.getInputStream()));
            packetResponse = new PrintWriter(clientStatus.getOutputStream(), true);            
        } catch (IOException e) {
            log.info("ERROR! An unkown error occured while connecting to Middleware!");
            e.printStackTrace();
            closeConnection(true);
        }
        
    }

    //Doc.
    private void newLog() {
        log = Logger.getLogger(middleware);

        try {
            fh = new FileHandler("C:/Users/win/Documents/Projects Java/Server/Middleware/Logs/MiddleClient.log");
            log.addHandler(fh);
            SimpleFormatter format = new SimpleFormatter();
            fh.setFormatter(format);
        } catch (SecurityException e) {
            System.err.println("ERROR! Security systems aren't allowing the log to register server events!");
            closeConnection(true);
        } catch (IOException e) {
            System.err.println("ERROR! An unkown error occured while setting the log!");
            e.printStackTrace();
            closeConnection(true);
        }
    }

    //Doc
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
        Path path = Paths.get(middleware, "Temp");
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
                path = Paths.get(middleware, "Temp", packets[0]);
                path = path.toAbsolutePath();
                tempFilePath = path.toString();
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
            String clientInfo = clientSocket.getLocalAddress().toString();

            if (exceptionFired) packetResponse.println("Shutdown.");

            packetMain.close();
            packetStatus.close();
            packetResponse.close();
            clientSocket.close();
            clientStatus.close();
            serverSocket.close();
            serverStatus.close();

            log.info("Connection with " + clientInfo + " was closed.");
        } catch (IOException e) {
            exceptionFired = true;

            System.err.println("ERROR: An unkown error occured while closing sockets and readers!");
            log.info("ERROR: An unkown error occured while closing sockets and readers!");
            e.printStackTrace();
        }

        log.removeHandler(fh);

        if (exceptionFired == true) {
            log.info(middleware + " is offline.");
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        ClientMiddleware middleware = new ClientMiddleware(in_port, in_status);
        ServerMiddleware server;

        middleware.receiveFile();

        server = new ServerMiddleware();
        server.sendToServers(tempFilePath);
    }
}
