package Middleware;

//import javafx.util.Pair;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.ArrayList;
//import java.util.Collections;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
//import org.apache.commons.lang3.tuple;

import Exceptions.LostPacketException;

public class ServerMiddleware {
    private BufferedReader packetResponse;
    private Logger log;
    private int timeout = 500;
    private PrintWriter packetMain;
    private PrintWriter packetStatus;
    private Socket mainSocket;
    private Socket statusSocket;

    public ServerMiddleware() {
        newLog();
    }

    //Doc.
    private void newLog() {
        log = Logger.getLogger("Middleware");
        FileHandler fh;

        try {
            fh = new FileHandler("C:/Users/win/Documents/Projects Java/Server/Middleware/Logs/MiddleServer.log");
            log.addHandler(fh);
            SimpleFormatter format = new SimpleFormatter();
            fh.setFormatter(format);
        } catch (SecurityException e) {
            System.err.println("ERROR! Security systems aren't allowing the log to register server events!");
        } catch (IOException e) {
            System.err.println("ERROR! An unkown error occured while setting the log!");
            e.printStackTrace();
        }
    }

    private void connectServers(InetAddress serverIP, int port, int status, int serverQuant) {
        ArrayList<Socket> serverSocket = new ArrayList<Socket>();
        ArrayList<PrintWriter> serverPacket = new ArrayList<PrintWriter>();
        ArrayList<BufferedReader> serverResponse = new ArrayList<BufferedReader>();

        for (int i = 0; i < serverQuant; i++) {
            try {
                Socket socket = new Socket(serverIP, status);
                socket.setSoTimeout(timeout);

                serverSocket.add(socket);
                serverPacket.add(new PrintWriter(socket.getOutputStream(), true));
                serverResponse.add(new BufferedReader(new InputStreamReader(socket.getInputStream())));
    
                log.info("Connected to " + serverIP.toString() + " on port " + status + ".");
            } catch (ConnectException e) {
                log.info("WARNING: Server " + serverIP + " on port " + status + " is offline!");
            } catch (IllegalArgumentException e) {
                log.info("ERROR: Invalid Port Number!");
                closeAllConnections(serverSocket, serverPacket, serverResponse, serverSocket.size());
            } catch (NullPointerException e) {
                log.info("ERROR: Null IP Address!");
                closeAllConnections(serverSocket, serverPacket, serverResponse, serverSocket.size());
            } catch (IOException e) {
                log.info("ERROR: An unkown error occured while connecting to servers!");
                e.printStackTrace();
                closeAllConnections(serverSocket, serverPacket, serverResponse, serverSocket.size());
            } finally {
                status += 2;
            }
        }

        if (serverSocket.size() < 2) {
            if (serverSocket.size() == 0) {
                log.info("WARNING: No servers online! Cannot transfer file!");
                System.exit(1);
            }

            log.info("WARNING: Not enough servers online to transfer file!");
            closeAllConnections(serverSocket, serverPacket, serverResponse, 1);
            System.exit(1);
        }

        roleAssignment(serverSocket, serverPacket, serverResponse, serverSocket.size(), port);
    }

    private int[] getCurrentStorage(ArrayList<PrintWriter> serverPacket, ArrayList<BufferedReader> serverResponse, int numServers) {
        //ArrayList<Pair<Integer,Long>> storage = new ArrayList<Pair<Integer,Long>>();
        //Object[] storage = new Object[2];
        int[] serverToUse = {0, 1};
        long[] storage = {-1, -1};
        long temp;

        for (int i = 0; i < numServers; i++) {
            try {
                serverPacket.get(i).println("Current storage request.");
                temp = Long.parseLong(serverResponse.get(i).readLine());

                if (i == 0) storage[0] = temp;
                if (i == 1) storage[1] = temp;
                
                if (storage[0] > temp) {
                    storage[0] = temp;
                    serverToUse[0] = i;
                } else if (storage[1] > temp) {
                    storage[1] = temp;
                    serverToUse[1] = i;
                }

                log.info("Server " + (i + 1) + " current storage: " + (temp / 1048576) + " MB.");
                //storage.add(new Pair<Integer,Long>(i, temp));
            } catch (IOException e) {
                e.printStackTrace();
                //TODO: Figure out what message to add and how to treat exception.
            }
        }

        //order
        //storage.sort(null);

        //get two server numbers.
        //serverToUse[0] = storage.get(0).getKey();
        //serverToUse[1] = storage.get(1).getKey();
        
        return serverToUse;
    }

    //TODO: Check if all related single-server variables aren't null.
    private void roleAssignment(ArrayList<Socket> serverSocket, ArrayList<PrintWriter> serverPacket, ArrayList<BufferedReader> serverResponse, int numServers, int port) {
        int[] serverToUse = getCurrentStorage(serverPacket, serverResponse, numServers);

        for (int i = 0; i < numServers; i++) {
            String response = "";

            try {
                if (i == serverToUse[0]) {
                    Socket socket = serverSocket.get(i);
                    boolean connected = false;

                    serverPacket.get(i).println("Main server.");

                    if (response.equals("Lost packet.")) {
                        throw new LostPacketException("");
                    }                   

                    while (!connected) {
                        try {
                            mainSocket = new Socket(InetAddress.getLoopbackAddress(), port + 2 * i);
                            connected = true;
                        } catch (IOException e) {
                            log.info("Reattempting to connect to port " + (port + 2 * i) + "!");
                            //e.printStackTrace();
                        }

                        //if (mainSocket.isConnected()) connected = true;
                    }

                    statusSocket = socket;
                    packetMain = new PrintWriter(mainSocket.getOutputStream(), true);
                    packetStatus = serverPacket.get(i);
                    packetResponse = serverResponse.get(i);

                    log.info("Server " + (i + 1) + " set as primary server.");
                } else {
                    if (i == serverToUse[1]) {
                        serverPacket.get(i).println("Secondary server.");
                        log.info("Server " + (i + 1) + " set as secondary server.");
                    } else serverPacket.get(i).println("Close connection.");

                    response = serverResponse.get(i).readLine();

                    if (response.equals("Lost packet.")) {
                        throw new LostPacketException("");
                    } 
    
                    closeExtraConnection(serverSocket.get(i), serverPacket.get(i), serverResponse.get(i));
                    log.info("Connection to server " + (i + 1) + " was closed.");
                }
            } catch (ConnectException e) {
            } catch (LostPacketException e) {
                log.info("ERROR: The confirmation to close the connection to server " + (i + 1) + " was lost!");
            } catch (IOException e) {
                log.info("ERROR: An unkown error occured while getting assigning roles to servers!");
                e.printStackTrace();
            }
        }
    }

    //Doc.
    private void sendFilename(File file) {
        String filename;

        try {
            if (!file.exists()) throw new FileNotFoundException("");

            filename = file.getName();

            packetStatus.println("Start transfer.");
            statusSocket.setSoTimeout(timeout);

            if (sendPacket(filename, "File sent.", 0) == "Lost packet.") {
                throw new LostPacketException("");
            }
        } catch (SocketException e) {
            log.info("ERROR! An unknown error occured while setting timeout for sockets!");
            e.printStackTrace();
        } catch (LostPacketException e) {
            log.info("ERROR: The packet containing the filename was lost!");
            
            if (sendPacket("", "Close connection.", 0).equals("Packet lost.")) {
                log.info("ERROR: The confirmation to close the connection was lost!");
            }

            closeMainConnection(true);
        } catch (FileNotFoundException e) {
            log.info("ERROR: " + file.getName() + " cannot be found!");
            System.err.println("Check if the exists and that the file extension is added into the name!");

            if (sendPacket("", "Close connection.", 0).equals("Packet lost.")) {
                log.info("ERROR: The confirmation to close the connection was lost!");
            } 

            closeMainConnection(true);
        }

        log.info("File to be sent: " + file.getName() + ".");
    }

    //Doc.
    private String sendPacket(String packet, String message, int packetNum) {
        String response = "";

        try {
            packetStatus.println(message);
            packetMain.println(packet);
            response = packetResponse.readLine();
        } catch (SocketTimeoutException e) {
            log.info("WARNING: Server confirmation of packet " + packetNum + " was lost or took longer than " + timeout + " ms.");
        } catch (IOException e) {
            log.info("ERROR: An unkown error occured while getting the server's response!");
            e.printStackTrace();
            closeMainConnection(true);
        }

        log.info("Packet " + packetNum + " sent to " + mainSocket.getLocalAddress().toString() + ".");

        return response;
    }

    //Doc and delete comments.
    private void sendFile(File file) {
        //Path path = file.toPath();
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
                    throw new LostPacketException("");
                }

                packet = reader.readLine();
                packetNum++;
            }

            if (sendPacket("", "Close connection.", packetNum).equals("Packet lost.")) {
                log.info("ERROR: The confirmation to close the connection was lost!");
                throw new LostPacketException("");
            }
        } catch (LostPacketException e) {
            System.err.println("ERROR: A packet was lost while sending the file!");

            if (sendPacket("", "Close connection.", 0).equals("Packet lost.")) {
                log.info("ERROR: The confirmation to close the connection was lost!");
            }

            closeMainConnection(true);
        } catch (IOException e) {
            System.err.println("ERROR: An unkown error occured while sending packets!");
            log.info("ERROR: An unkown error occured while sending packets!");
            e.printStackTrace();

            if (sendPacket("", "Close connection.", 0).equals("Packet lost.")) {
                log.info("ERROR: The confirmation to close the connection was lost!");
            }

            closeMainConnection(true);
        }
        
        log.info("All packets were sent to " + mainSocket.getLocalAddress().toString() + ".");
    }

    private void closeAllConnections(ArrayList<Socket> serverSocket, ArrayList<PrintWriter> serverPacket, ArrayList<BufferedReader> serverResponse, int numServers) {
        for (int i = 0; i < numServers; i++) {
            closeExtraConnection(serverSocket.get(i), serverPacket.get(i), serverResponse.get(i));
        }

        log.info("Application is offline.");
        System.exit(1);
    }

    private void closeExtraConnection(Socket serverSocket, PrintWriter serverPacket, BufferedReader serverResponse) {
        int portInfo = serverSocket.getLocalPort();
        String serverInfo = serverSocket.getLocalAddress().toString();

        try {
            serverSocket.close();
            serverPacket.close();
            serverResponse.close();

            log.info("Closed connection to IP: " + serverInfo + ", on Port: " + portInfo + ".");
        } catch (IOException e) {
            log.info("ERROR: An unkown error occured while closing sockets and readers!");
            e.printStackTrace();
        }
    }

    //Doc.
    private void closeMainConnection(boolean exceptionFired) {
        int portInfo = mainSocket.getLocalPort();
        String serverInfo = mainSocket.getLocalAddress().toString();

        try {
            packetMain.close();
            packetStatus.close();
            packetResponse.close();
            mainSocket.close();
            statusSocket.close();

            //System.out.println("Connection closed.");
            log.info("Closed connection to IP: " + serverInfo + ", on Port: " + portInfo + ".");
        } catch (IOException e) {
            exceptionFired = true;

            //System.err.println("ERROR: An unkown error occured while closing sockets and readers!");
            log.info("ERROR: An unkown error occured while closing connection to IP: " + serverInfo + ", on Port: " + portInfo + ".");
            e.printStackTrace();
        }

        log.info("Application is offline.");

        if (exceptionFired) {
            System.exit(1);
        }
    }

    //TODO: Doc.
    protected void sendToServers(String filepath) {
        File file = new File(filepath);

        connectServers(InetAddress.getLoopbackAddress(), 30000, 30001, 4);
        sendFilename(file);
        sendFile(file);
        closeMainConnection(false);
    }

    //TODO: DELETE MAIN.
    /*/public static void main(String[] args) {
        ServerMiddleware s = new ServerMiddleware();
        s.sendToServers("C:/Users/win/Documents/Projects Java/Server/Middleware/Temp/random.txt");
    }*/
}
