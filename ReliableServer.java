/*
 * [cite_start]TODO: Implement Server-side logic and State Machine [cite: 75, 77]
 * - [cite_start]Session Management: Acknowledge session requests and sync sequence numbers [cite: 40, 41]
 * - [cite_start]Reliable Transfer: Implement retransmission logic for lost packets [cite: 45]
 * - [cite_start]Error Handling: Handle 'File Not Found' and 'Session Mismatch' [cite: 60, 61]
 * - [cite_start]Storage: Ensure binary-safe file storage for uploads [cite: 55, 56]
 * 
 * Assigned to: ABENES
 */

import java.io.*;
import java.net.*;
import java.util.Arrays;

public class ReliableServer {
    private SocketManager socketManager;
    private boolean running = true;
    private long expectedSequenceNumber = 0;
    private FileOutputStream fileOutputStream = null;

    public ReliableServer(int port) throws SocketException {
        this.socketManager = new SocketManager(port, 2000); 
    }

    public void start() {
        System.out.println("Reliable UDP Server started on port...");
        
        while (running) {
            try {
                byte[] buffer = new byte[Packet.MTU];
                DatagramPacket incoming = new DatagramPacket(buffer, buffer.length);
                Packet received = socketManager.receivePacket(incoming);
                
                if (received != null) {
                    processPacket(received, incoming.getAddress(), incoming.getPort());
                }
            } catch (IOException e) {
                System.err.println("Error receiving packet: " + e.getMessage());
            }
        }
    }

    private void processPacket(Packet packet, InetAddress clientAddr, int clientPort) throws IOException {
        switch (packet.getMessageType()) {
            case Packet.SYN:
                handleHandshake(packet, clientAddr, clientPort);
                break;
            case Packet.DATA:
                handleDataTransfer(packet, clientAddr, clientPort);
                break;
            case Packet.FIN:
                handleTermination(packet, clientAddr, clientPort);
                break;
            default:
                System.out.println("Received unexpected packet type: " + packet.getMessageType());
        }
    }

    private void handleHandshake(Packet packet, InetAddress addr, int port) throws IOException {
        // [cite: 40, 41] Server acknowledges session and agrees on sequence number
        System.out.println("Received SYN. Initializing session...");
        this.expectedSequenceNumber = packet.getSequenceNumber(); 
        
        String payload = new String(packet.getPayload()).trim();
        String[] parts = payload.split(":", 2);
        String command = parts.length > 0 ? parts[0] : "";
        String filename = parts.length > 1 ? parts[1] : "default.bin";

        if (command.equals("UPLOAD")) {
            this.fileOutputStream = new FileOutputStream("server_" + filename);
            Packet synAck = new Packet(Packet.SYN_ACK, expectedSequenceNumber, null);
            socketManager.sendPacket(synAck, addr, port);
        } else if (command.equals("DOWNLOAD")) {
            File file = new File(filename);
            if (!file.exists()) {
                Packet error = new Packet(Packet.ERROR, 0, "File Not Found".getBytes());
                socketManager.sendPacket(error, addr, port);
                return;
            }
            Packet synAck = new Packet(Packet.SYN_ACK, expectedSequenceNumber, null);
            socketManager.sendPacket(synAck, addr, port);
            
            // Start sending the file data for download requests
            sendFile(file, addr, port);
        } else {
            Packet synAck = new Packet(Packet.SYN_ACK, expectedSequenceNumber, null);
            socketManager.sendPacket(synAck, addr, port);
        }
    }

    private void handleDataTransfer(Packet packet, InetAddress addr, int port) throws IOException {
        if (packet.getSequenceNumber() == expectedSequenceNumber) {
            System.out.println("Received expected DATA: " + packet.getSequenceNumber());

            // Write the payload to our stored file correctly
            if (fileOutputStream != null) {
                fileOutputStream.write(packet.getPayload());
            }
            
            Packet ack = new Packet(Packet.ACK, expectedSequenceNumber, null);
            socketManager.sendPacket(ack, addr, port);
            expectedSequenceNumber++;
        } else {
            System.out.println("Sequence mismatch. Expected: " + expectedSequenceNumber);
            Packet ack = new Packet(Packet.ACK, expectedSequenceNumber, null);
            socketManager.sendPacket(ack, addr, port);
        }
    }

    private void handleTermination(Packet packet, InetAddress addr, int port) throws IOException {
        System.out.println("Received FIN. Closing session...");
        if (fileOutputStream != null) {
            fileOutputStream.close();
            fileOutputStream = null;
        }
        Packet finAck = new Packet(Packet.ACK, packet.getSequenceNumber(), null);
        socketManager.sendPacket(finAck, addr, port);

    }

    private void sendFile(File file, InetAddress addr, int port) {
        long currentSeq = expectedSequenceNumber; 
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[Packet.MAX_PAYLOAD_SIZE];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] payload = Arrays.copyOf(buffer, bytesRead);
                Packet dataPacket = new Packet(Packet.DATA, currentSeq, payload);
                
                boolean ackReceived = false;
                for (int attempt = 0; attempt < 5; attempt++) {
                    socketManager.sendPacket(dataPacket, addr, port);
                    
                    DatagramPacket incoming = new DatagramPacket(new byte[Packet.MTU], Packet.MTU);
                    Packet response = socketManager.receivePacket(incoming);
                    
                    if (response != null && response.getMessageType() == Packet.ACK) {
                        if (response.getSequenceNumber() == currentSeq) {
                            ackReceived = true;
                            currentSeq++;
                            break;
                        }
                    }
                }
                if (!ackReceived) {
                    System.out.println("Client disconnected during download.");
                    return;
                }
            }
            
            Packet finPacket = new Packet(Packet.FIN, currentSeq, null);
            socketManager.sendPacket(finPacket, addr, port);
            
        } catch (IOException e) {
            System.err.println("Error sending file: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws SocketException {
        ReliableServer server = new ReliableServer(12345);
        server.start();
    }
}