/*
 * [cite_start]TODO: Implement low-level UDP socket handling [cite: 28, 30]
 * - Initialize DatagramSocket and DatagramPacket
 * - Implement 'sendPacket' and 'receivePacket' methods
 * - [cite_start]Implement a Timeout mechanism for retransmissions [cite: 45, 59]
 * - [cite_start]Ensure all transmissions use the custom Packet class format [cite: 33]
 * 
 * Assigned to: ABENES
 */

import java.io.*;
import java.net.*;

public class SocketManager {
    private DatagramSocket socket;
    
    private int timeout;

    public SocketManager(int port, int timeout) throws SocketException {
        this.socket = new DatagramSocket(port);
        this.timeout = timeout;
        this.socket.setSoTimeout(timeout); 
    }

    public void sendPacket(Packet packet, InetAddress address, int port) throws IOException {
        byte[] data = packet.toByteArray(); 
        DatagramPacket datagram = new DatagramPacket(data, data.length, address, port);
        socket.send(datagram);
    }

    public Packet receivePacket(DatagramPacket incoming) throws IOException {
        try {
            socket.receive(incoming);
            return Packet.fromByteArray(incoming.getData());
        } catch (SocketTimeoutException e) {
            return null; 
        }
    }

    public void close() {
        socket.close();
    }
}