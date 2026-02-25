/*
 * [cite_start]TODO: Implement Client-side logic and State Machine [cite: 75, 76]
 * - [cite_start]Session Establishment: Initiate session with SYN and agree on parameters [cite: 38, 39, 41]
 * - [cite_start]File Transfer: Implement Download request and Upload sending logic [cite: 48, 53]
 * - [cite_start]Reliability: Handle ACKs and reassemble packets for ordered delivery [cite: 44, 46]
 * - [cite_start]Termination: Implement clean close via FIN/FIN-ACK exchange [cite: 63, 64]
 * 
 * Assigned To: RAMOS
 */

import java.io.*;
import java.net.*;
import java.util.Arrays;   

public class ReliableClient {
    private SocketManager socketManager;
    private InetAddress serverAddress;
    private int serverPort;
    private static final int MAX_RETRIES = 5;

    public ReliableClient(String serverIp, int port) throws IOException {
        this.serverAddress = InetAddress.getByName(serverIp);
        this.serverPort = port;
        this.socketManager = new SocketManager(0, 1000); 
    }

    // --- Session Establishment ---
    public boolean establishSession(String operation, String filename, long initialSeq) throws IOException {
        // [cite: 38, 39, 41] Initiate session with SYN and agree on parameters
        String payload = operation + ":" + filename;
        Packet synPacket = new Packet(Packet.SYN, initialSeq, payload.getBytes());
        
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            System.out.println("Sending SYN for " + operation + " (Attempt " + (attempt + 1) + ")");
            socketManager.sendPacket(synPacket, serverAddress, serverPort);
            
            DatagramPacket incoming = new DatagramPacket(new byte[Packet.MTU], Packet.MTU);
            Packet response = socketManager.receivePacket(incoming);
            
            if (response != null) {
                if (response.getMessageType() == Packet.SYN_ACK && response.getSequenceNumber() == initialSeq) {
                    System.out.println("Session established via SYN_ACK.");
                    return true;
                } else if (response.getMessageType() == Packet.ERROR) {
                    System.out.println("Server Error: " + new String(response.getPayload()));
                    return false;
                }
            }
        }
        System.out.println("Timeout: Failed to establish session after " + MAX_RETRIES + " attempts.");
        return false;
    }

    // --- Upload Logic ---
    public void uploadFile(String filename) {
        // [cite: 48, 53] Implement Upload sending logic
        long currentSeq = 0; // Initial sequence number
        
        try (FileInputStream fis = new FileInputStream(filename)) {
            if (!establishSession("UPLOAD", filename, currentSeq)) {
                return;
            }

            byte[] buffer = new byte[Packet.MAX_PAYLOAD_SIZE];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                // [cite: 48, 53] Truncate buffer to actual bytes read if it's the last chunk
                byte[] payload = (bytesRead == Packet.MAX_PAYLOAD_SIZE) ? buffer : Arrays.copyOf(buffer, bytesRead);
                Packet dataPacket = new Packet(Packet.DATA, currentSeq, payload);
                
                boolean ackReceived = false;
                for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                    socketManager.sendPacket(dataPacket, serverAddress, serverPort);
                    
                    DatagramPacket incoming = new DatagramPacket(new byte[Packet.MTU], Packet.MTU);
                    Packet response = socketManager.receivePacket(incoming);
                    
                    // [cite: 44, 46] Handle ACKs for ordered delivery
                    if (response != null && response.getMessageType() == Packet.ACK) {
                        // [cite: 44, 46] Match server logic: The server ACKs with the CURRENT sequence number
                        if (response.getSequenceNumber() == currentSeq) {
                            ackReceived = true;
                            currentSeq++;
                            break;
                        }
                    } else if (response != null && response.getMessageType() == Packet.ERROR) {
                        System.out.println("Server Error during transfer: " + new String(response.getPayload()));
                        return;
                    }
                }
                
                if (!ackReceived) {
                    System.out.println("Connection lost: Failed to receive ACK for seq " + currentSeq);
                    return;
                }
            }
            
            closeSession(currentSeq);
            System.out.println("Upload complete!");

        } catch (FileNotFoundException e) {
            System.err.println("File not found locally: " + filename);
        } catch (IOException e) {
            System.err.println("File read error: " + e.getMessage());
        }
    }

    // --- Download Logic ---
    public void downloadFile(String filename) {
        // [cite: 48, 53] Implement Download request
        long expectedSeq = 0; 
        
        // Prepend "client_" to avoid overwriting files if testing on the same machine
        try (FileOutputStream fos = new FileOutputStream("client_" + filename)) {
            if (!establishSession("DOWNLOAD", filename, expectedSeq)) {
                return;
            }

            System.out.println("Session established. Waiting to receive data...");
            boolean downloading = true;

            while (downloading) {
                DatagramPacket incoming = new DatagramPacket(new byte[Packet.MTU], Packet.MTU);
                Packet received = socketManager.receivePacket(incoming);

                if (received == null) {
                    continue; 
                }

                switch (received.getMessageType()) {
                    case Packet.DATA:
                        // [cite: 44, 46] Reassemble packets for ordered delivery
                        if (received.getSequenceNumber() == expectedSeq) {
                            fos.write(received.getPayload());
                            
                            // [cite: 44, 46] Send ACK for the sequence we just correctly received
                            Packet ack = new Packet(Packet.ACK, expectedSeq, null);
                            socketManager.sendPacket(ack, serverAddress, serverPort);
                            
                            expectedSeq++;
                        } else {
                            // [cite: 44, 46] If we got a duplicate/wrong packet, just ACK the last good sequence
                            Packet ack = new Packet(Packet.ACK, received.getSequenceNumber(), null);
                            socketManager.sendPacket(ack, serverAddress, serverPort);
                        }
                        break;
                        
                    case Packet.FIN:
                        System.out.println("Received FIN from server. Closing session...");
                        Packet finAck = new Packet(Packet.ACK, received.getSequenceNumber(), null);
                        socketManager.sendPacket(finAck, serverAddress, serverPort);
                        downloading = false;
                        System.out.println("Download complete!");
                        break;
                        
                    case Packet.ERROR:
                        System.out.println("Server Error: " + new String(received.getPayload()));
                        downloading = false;
                        break;
                }
            }

        } catch (IOException e) {
            System.err.println("File write error: " + e.getMessage());
        }
    }

    // --- Session Termination ---
    private void closeSession(long currentSeq) throws IOException {
        // [cite: 63, 64] Implement clean close via FIN/FIN-ACK exchange
        Packet finPacket = new Packet(Packet.FIN, currentSeq, null);
        
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            socketManager.sendPacket(finPacket, serverAddress, serverPort);
            DatagramPacket incoming = new DatagramPacket(new byte[Packet.MTU], Packet.MTU);
            Packet response = socketManager.receivePacket(incoming);
            
            if (response != null && response.getMessageType() == Packet.ACK) {
                System.out.println("Session closed cleanly.");
                return;
            }
        }
        System.out.println("Timeout waiting for FIN_ACK. Terminating locally.");
    }

    // --- Main Driver ---
    public static void main(String[] args) {
        try {
            ReliableClient client = new ReliableClient("127.0.0.1", 12345);
            
            // Uncomment to test Upload
            client.uploadFile("test.txt"); 
            
            // Uncomment to test Download
            // client.downloadFile("test.txt");
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}