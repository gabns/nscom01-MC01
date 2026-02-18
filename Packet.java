import java.nio.ByteBuffer;
import java.util.Arrays;
/*
 * [cite_start]TODO: Define the Packet structure according to Project Specs [cite: 32, 33]
 * - [cite_start]Implement fields for Message Type (SYN, DATA, ACK, FIN, ERROR) [cite: 34]
 * - [cite_start]Implement Sequence Number field [cite: 35]
 * - [cite_start]Implement Payload Length field [cite: 36]
 * - [cite_start]Implement the Payload byte array [cite: 37]
 * - Add methods for serialization/deserialization to/from byte arrays for UDP transmission
 * - Define a constant for Maximum Transmission Unit (MTU) to prevent IP fragmentation
 * 
 * Assigned To: RAMOS
 */

public class Packet {

    // --- Message Type Constants ---
    public static final int SYN = 0;
    public static final int DATA = 1;
    public static final int ACK = 2;
    public static final int FIN = 3;
    public static final int ERROR = 4;
    public static final int SYN_ACK = 5; // for three-way handshake

    // --- MTU Configuration ---
    public static final int MTU = 1400;
    public static final int HEADER_SIZE = 16; // Header Size: Type (4 bytes) + Sequence Number (8 bytes) + Payload Length (4 bytes)
    public static final int MAX_PAYLOAD_SIZE = MTU - HEADER_SIZE;

    // --- Packet Fields ---
    private int messageType; // 4 bytes
    private long sequenceNumber; // 8 bytes
    private int payloadLength; // 4 bytes
    private byte[] payload; // Variable length

    /**
     * Constructor for creating a new Packet
     * @param messageType The type of the message (SYN, DATA, ACK, FIN, ERROR)
     * @param sequenceNumber The sequence number for ordering packets
     * @param payload The data payload to be sent
     */
    public Packet(int messageType, long sequenceNumber, byte[] payload) {
        this.messageType = messageType;
        this.sequenceNumber = sequenceNumber;
        if (payload == null) {
            this.payload = new byte[0];
        } else {
            if (payload.length > MAX_PAYLOAD_SIZE) {
                throw new IllegalArgumentException("Payload size " + payload.length + " exceeds limit of " + MAX_PAYLOAD_SIZE + " bytes.");
            }
            this.payload = Arrays.copyOf(payload, payload.length);
        }

        this.payloadLength = this.payload.length;
}

    // Serialization: Convert Packet to byte array for transmission 
    public byte[] toByteArray() {
        ByteBuffer bb = ByteBuffer.allocate(HEADER_SIZE + payloadLength);
        bb.putInt(messageType);
        bb.putLong(sequenceNumber);
        bb.putInt(payloadLength);
        if (payloadLength > 0) {
            bb.put(payload, 0, payloadLength);
        }
        return bb.array();
    }

    // Deserialization: Create Packet from byte array received 
    public static Packet fromByteArray(byte[] byteArray) {
        if (byteArray.length < HEADER_SIZE) {
            throw new IllegalArgumentException("Byte array too short to be a valid packet.");
        }
        ByteBuffer bb = ByteBuffer.wrap(byteArray);
        int messageType = bb.getInt();
        long sequenceNumber = bb.getLong();
        int payloadLength = bb.getInt();
        if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("Invalid payload length: " + payloadLength);
        }
        if (byteArray.length < HEADER_SIZE + payloadLength) {
            throw new IllegalArgumentException("Byte array too short for payload: expected " + (HEADER_SIZE + payloadLength) + ", got " + byteArray.length);
        }
        byte[] payload = new byte[payloadLength];
        if (payloadLength > 0) {
            bb.get(payload);
        }
        return new Packet(messageType, sequenceNumber, payload);
    }

    // Getters for Packet Fields
    public int getMessageType() {
        return messageType;
    }  
    public long getSequenceNumber() {
        return sequenceNumber;
    }
    public int getPayloadLength() {
        return payloadLength;
    }
    public byte[] getPayload() {
        return Arrays.copyOf(payload, payloadLength);
    }

    // Helper method to convert message type to string for debugging 
    public static String messageTypeToString(int messageType) {
        switch (messageType) {
            case SYN: return "SYN";
            case DATA: return "DATA";
            case ACK: return "ACK";
            case FIN: return "FIN";
            case ERROR: return "ERROR";
            case SYN_ACK: return "SYN_ACK";
            default: return "UNKNOWN";
        }
    }

    @Override
    public String toString() {
        return "Packet{" +
                "messageType=" + messageTypeToString(messageType) +
                ", sequenceNumber=" + sequenceNumber +
                ", payloadLength=" + payloadLength +
                '}';
    }
}