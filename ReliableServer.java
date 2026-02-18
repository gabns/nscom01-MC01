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

public class ReliableServer {
    private SocketManager socketManager;
    private boolean running = true;
    private long expectedSequenceNumber = 0;

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
        
        Packet synAck = new Packet(Packet.SYN_ACK, expectedSequenceNumber, null);
        socketManager.sendPacket(synAck, addr, port);
    }

    private void handleDataTransfer(Packet packet, InetAddress addr, int port) throws IOException {
        if (packet.getSequenceNumber() == expectedSequenceNumber) {
            System.out.println("Received expected DATA: " + packet.getSequenceNumber());
            
            Packet ack = new Packet(Packet.ACK, expectedSequenceNumber, null);
            socketManager.sendPacket(ack, addr, port);
            expectedSequenceNumber++;
        } else {
            System.out.println("Sequence mismatch. Expected: " + expectedSequenceNumber);
        }
    }

    private void handleTermination(Packet packet, InetAddress addr, int port) throws IOException {
        System.out.println("Received FIN. Closing session...");
        Packet finAck = new Packet(Packet.ACK, packet.getSequenceNumber(), null);
        socketManager.sendPacket(finAck, addr, port);
    }

    public static void main(String[] args) throws SocketException {
        ReliableServer server = new ReliableServer(12345);
        server.start();
    }
}