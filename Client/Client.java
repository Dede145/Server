package Client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.util.Scanner;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

//import Exceptions.FileNotReadableException;
import Exceptions.LostPacketException;

public class Client {
    private static BufferedReader packetResponse;
    private static int timeout = 500;
    private static Logger         log;
    private static PrintWriter    packetMain;
    private static PrintWriter    packetStatus;
    private static Socket         mainSocket;
    private static Socket         statusSocket;

    //Doc.
    public Client(InetAddress serverIP, int port, int status) {
        try {
            newLog();

            Client.mainSocket = new Socket(serverIP, port);
            Client.statusSocket = new Socket(serverIP, status);
            Client.packetMain = new PrintWriter(mainSocket.getOutputStream(), true);
            Client.packetStatus = new PrintWriter(statusSocket.getOutputStream(), true);
            Client.packetResponse = new BufferedReader(new InputStreamReader(statusSocket.getInputStream()));

            log.info("Connected to " + serverIP.toString() + " on port " + port + ".");
        } catch (ConnectException e) {
            log.info("ERROR! MyFiles is offline for maintainence!");
            closeConnection(true);
        } catch (IllegalArgumentException e) {
            log.info("ERROR: Invalid Port Number! Port: " + port + ", Status: " + status + ".");
            closeConnection(true);
        } catch (NullPointerException e) {
            log.info("ERROR: Null IP Address!");
            closeConnection(true);
        } catch (IOException e) {
            log.info("ERROR: An unkown error occured while connecting to MyFiles!");
            e.printStackTrace();
            closeConnection(true);
        }   
    }

    //Doc.
    private void newLog() {
        log = Logger.getLogger("Client");
        FileHandler fh;

        try {
            fh = new FileHandler("C:/Users/win/Documents/Projects Java/Server/Client/Logs/Client.log");
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

    //Doc.
    //TODO: Check if file is readable.
    //TODO: Delete comment.
    private static File getFileName() {
        File file;
        Scanner scan = new Scanner(System.in);
        String filename;

        System.out.println("Please enter the file you wish to send.");
        System.out.println("IMPORTANT: Add the file extension in the name! (ie. .mp3, .txt, etc)");

        file = new File(scan.nextLine());
        //file = new File(classLoader.getResource(scan.nextLine()).getFile());

        //if (!file.canRead()) throw new FileNotReadableException("");
        //catch (FileNotReadableException e) {}

        try {
            if (!file.exists()) {
                scan.close();
                throw new FileNotFoundException("");
            }

            filename = file.getName().replace("Client\\", "");
            packetStatus.println("Start transfer.");
            statusSocket.setSoTimeout(timeout);

            if (sendPacket(filename, "File sent.", 0) == "Packet lost.") {
                scan.close();
                throw new LostPacketException("");
            }
        } catch (SocketException e) {
            log.info("ERROR! An unknown error occured while setting timeout for sockets!");
            e.printStackTrace();
        } catch (LostPacketException e) {
            log.info("ERROR: The packet containing the filename was lost!");
            scan.close();

            if (sendPacket("", "Close connection.", 0).equals("Packet lost.")) {
                log.info("ERROR: The confirmation to close the connection was lost!");
            } 

            closeConnection(true);
        } catch (FileNotFoundException e) {
            log.info("ERROR: " + file.getName() + " cannot be found!");
            System.err.println("Check if the exists and that the file extension is added into the name!");

            if (sendPacket("", "Close connection.", 0).equals("Packet lost.")) {
                log.info("ERROR: The confirmation to close the connection was lost!");
            } 

            scan.close();
            closeConnection(true);
        }

        log.info("File to be sent: " + file.getName() + ".");
        scan.close();

        return file;
    }

    //Doc.
    //TODO: try-catch with SocketTimeoutException.
    private static String sendPacket(String packet, String message, int packetNum) {
        String response = "";

        try {
            packetStatus.println(message);
            packetMain.println(packet);
            response = packetResponse.readLine();
        } catch (SocketTimeoutException e) {
            log.info("WARNING: MyFiles confirmation of packet " + packetNum + " was lost or took longer than " + timeout + " ms.");
        } catch (IOException e) {
            log.info("ERROR: An unkown error occured while getting the server's response!");
            e.printStackTrace();
            closeConnection(true);
        }

        log.info("Packet " + packetNum + " sent to " + mainSocket.getLocalAddress().toString() + ".");

        return response;
    }

    //Doc.
    //TODO: delete comments.
    private void sendFile(File file) {
        int packetNum = 1;
        String packet = null;
        String response = "";

        try (InputStream in = Files.newInputStream(file.toPath());
            BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            packet = reader.readLine();

            while (packet != null) {
                response = sendPacket(packet, "Packet sent.", packetNum);

                if (response.equals("Packet lost.")) {
                    log.info("ERROR: A packet was lost while sending the file!");
                    throw new LostPacketException("");
                }

                if (response.equals("Shutdown.")) {
                    log.info("ERROR: Server has requested connection closure!");
                    closeConnection(false);
                }

                packet = reader.readLine();
                packetNum++;
            }

            if (sendPacket("", "Close connection.", packetNum).equals("Packet lost.")) {
                log.info("ERROR: The confirmation to close the connection was lost!");
            }
        } catch (LostPacketException e) {
            System.err.println("ERROR: A packet was lost while sending the file!");

            if (sendPacket("", "Close connection.", 0).equals("Packet lost.")) {
                log.info("ERROR: The confirmation to close the connection was lost!");
            }

            closeConnection(true);
        } catch (IOException e) {
            System.err.println("ERROR: An unkown error occured while sending packets!");
            log.info("ERROR: An unkown error occured while sending packets!");
            
            if (sendPacket("", "Close connection.", 0).equals("Packet lost.")) {
                log.info("ERROR: The confirmation to close the connection was lost!");
            }

            e.printStackTrace();
            closeConnection(true);
        }
        
        log.info("All packets were sent to " + mainSocket.getLocalAddress().toString() + ".");
        closeConnection(false);
    }

    //Doc.
    private static void closeConnection(boolean exceptionFired) {
        try {
            String serverInfo = mainSocket.getLocalAddress().toString();

            packetMain.close();
            packetStatus.close();
            packetResponse.close();
            mainSocket.close();
            statusSocket.close();

            log.info("Closed connection to " + serverInfo + ".");
        } catch (IOException e) {
            exceptionFired = true;
            
            log.info("ERROR: An unkown error occured while closing sockets and readers!");
            e.printStackTrace();
        }

        if (exceptionFired) {
            log.info("Exiting MyFiles...");
            System.exit(1);
        }
    } 

    public static void main(String[] args) {
        Client client = new Client(InetAddress.getLoopbackAddress(), 20000, 20001);
        File file = getFileName();

        client.sendFile(file);

        log.info("Client exited MyFiles.");
    }
}
