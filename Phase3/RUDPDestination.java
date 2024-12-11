package Phase3;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Random;

import javax.imageio.plugins.bmp.BMPImageWriteParam;

public class RUDPDestination {
    private static DatagramSocket server;
    private static double packetLossChance = 0.5;
    private static int minDelay = 0;
    private static int maxDelay = 100;
    private static int TCPheaderSize = 16; // bytes
    private static int TCPdataSize = 64; // bytes
    private static int TCPSegmentSize = TCPheaderSize + TCPdataSize;
    private static int clientSeqNum = 0;
    private static int currentSegNum = 0;  // sendbase
    // Function to create a custom TCP header
    // Input: - (int) sequence number: the sequence number of the first byte
    // in the segment
    // - (int) acknowledgement number: the next sequence number expected
    // from the other
    // - (bool) SYN: Synchronization event
    // - (bool) ACK: Acknowledging sequence numbers event
    // - (bool) FIN: Closing connection event
    //
    // Output: a byte array that represents a TCP header and
    // contains all the information needed to correctly
    // read and assemble the data.
    //
    // Header format:
    // <------------- 32 bits ------------->
    // | source port # | dest. port # |
    // | Sequence number |
    // | Acknowledgement number |
    // | Checksum | Header len | flgs |

    public static byte[] createTCPHeader(int AckNum, boolean SYN,
            boolean ACK, boolean FIN) {
        byte[] header = new byte[16];

        // SRC PORT NUMBER: 16-bits
        header[0] = (byte) (server.getLocalPort() >> 8);
        header[1] = (byte) (server.getLocalPort());

        // DEST PORT NUMBER: 16-bits
        header[2] = (byte) (destinationPortNumber >> 8);
        header[3] = (byte) (destinationPortNumber);

        // SEQ NUMBER: 32-bits
        header[4] = (byte) (currentSegNum >> 24);
        header[5] = (byte) (currentSegNum >> 16);
        header[6] = (byte) (currentSegNum >> 8);
        header[7] = (byte) (currentSegNum);

        // ACK NUMBER: 32-bits
        header[8] = (byte) (AckNum >> 24);
        header[9] = (byte) (AckNum >> 16);
        header[10] = (byte) (AckNum >> 8);
        header[11] = (byte) (AckNum);

        // CHECKSUM: 16-bits
        // Intialized to zero, will be computed later
        header[12] = (byte) 0;
        header[13] = (byte) 0;

        // HEADER LENGTH: 8-bits
        // Amount of 4-byte words in header
        header[14] = (byte) (4);

        // FLAGS: 8-bits
        // We will only need the ACK, SYN, and FIN flags
        int sumOfFlags = 0;
        // ACK's bit will be the 3rd from the right
        if (ACK) {
            sumOfFlags += 4;
        }
        // SYN's bit will be the 2nd from the right
        if (SYN) {
            sumOfFlags += 2;
        }
        // FIN's bit will be the 1st from the right
        if (FIN) {
            sumOfFlags += 1;
        }
        header[15] = (byte) sumOfFlags;
        return header;
    }

    // serverSend()
    // Expects: A valid PacketObject containing a valid datagram packet which has a
    // valid destination.
    // Returns: nothing, but is responsible for sednding the packet to its correct
    // destination.
    public static void serverAck(PacketObject current, int destinationPort) throws Exception {
        if (current == null) {
            return;
        }
        byte[] message = current.packetData.getData();
        // random variables that will determine whether we drop the packet or not
        Random rand = new Random();
        int packetLossRandomVariable = rand.nextInt(101);
        // Certain issues with how random works and doubles were causing an issue where
        // even when packetLossChance was 0 some packets would be dropped this insures
        // that doesnt happen.
        if (packetLossChance <= 0) {
            packetLossRandomVariable = 200;
        }
        // if random variable is greater than packetloss chance, we will send the
        // packet
        if (packetLossRandomVariable > packetLossChance * 100) {
            // Intialize new packet to be sent. Note that since this is running locally we
            // do not need to get the IP address of the destination,only the port.
            DatagramPacket packetToBeSent = new DatagramPacket(message, message.length, InetAddress.getLocalHost(),
                    destinationPort);

            // Get a random delay betweem ,mindDelay, and maxDelay
            int delay = minDelay + rand.nextInt((maxDelay - minDelay) + 1);

            try {
                // Sleep this thread as necessary to simulate the delay, and then send the
                Thread.sleep(delay);
                server.send(packetToBeSent);
            } catch (Exception e) {
            }
        }
    }

    // Function that computes the checksum of the header and data of the packet
    public static int computeChecksum(byte[] packet) {
        int sum = 0, firstByte, secondByte;

        // To calculate the checksum, we need to add the
        // TCP pseudo-header, TCP header, and TCP data
        // Source IP (4 bytes)
        // Source IP (convert to bytes)
        byte[] srcIPBytes = client.getLocalAddress().getAddress();
        for (int i = 0; i < 4; i += 2) {
            int word = ((srcIPBytes[i] & 0xFF) << 8) | (srcIPBytes[i + 1] & 0xFF);
            sum += word;
        }

        // Destination IP (convert to bytes)
        byte[] destIPBytes = destinationIP.getAddress();
        for (int i = 0; i < 4; i += 2) {
            int word = ((destIPBytes[i] & 0xFF) << 8) | (destIPBytes[i + 1] & 0xFF);
            sum += word;
        }

        // Protocol (equal to six for TCP) (1 byte)
        sum += (byte) 6 & 0xFF;

        // Length (2 bytes)
        sum += (byte) packet.length & 0xFFFF;
        for (int i = 0; i < packet.length; i += 2) {
            firstByte = packet[i] & 0xFF;

            // If the packet has an odd number of bytes, pad with 0 for the last byte
            if (i == packet.length - 1) {
                secondByte = 0;
            } else {
                secondByte = packet[i + 1] & 0xFF;
            }

            // Combine the two bytes into a 16-bit word
            int word = (firstByte << 8) | secondByte;

            // Add the 16-bit word to the sum
            sum += word;

            // Handle wraparound (if the sum exceeds 16 bits)
            if ((sum & 0xFFFF0000) != 0) {
                // Carry occurred, so wrap around by adding the carry
                sum = (sum & 0xFFFF) + (sum >> 16);
            }
        }

        // One's complement of the sum
        return ~sum & 0xFFFF;
    }

    // Function that inserts the correct checksum into the header
    public static void addChecksum(byte[] packet) {
        int checksum = computeChecksum(packet);
        packet[12] = (byte) (checksum >> 8);
        packet[13] = (byte) (checksum);
        return;
    }

    public static void printDataTrans(byte[] packetBytes) {
        // Getting sequence number from packetBytes by perfoming
        // a bitwise to fit them all on to an int
        int seqNum = packetBytes[4] << 24 | packetBytes[5] << 16
                | packetBytes[6] << 8 | packetBytes[7];
        System.out.println("[DATA TRANSMISSION]: " + seqNum + " | " + packetBytes.length);
    }

    // initializeConnection()
    // Expects and returns nothing, is responsible for performing the three way
    // handshake to establish connection.
    public static void listenForConnection() {
        try {
            // Buffer to store incoming packets
            byte[] dataReceived = new byte[TCPSegmentSize];
            DatagramPacket receivedPacket = new DatagramPacket(dataReceived, dataReceived.length);

            // Listen for incoming SYN message
            while (true) {
                // Receive packet from client
                server.receive(receivedPacket);
                byte[] SYN_msg_data = receivedPacket.getData();

                // Extract source IP and port for reply
                InetAddress clientIP = receivedPacket.getAddress();
                int clientPort = receivedPacket.getPort();

                // Check if it's a SYN message (SYN flag should be set)
                if ((int) SYN_msg_data[15] == 2) { // SYN flag (binary 0010)
                    // Get the client's sequence number from the SYN message
                    clientSeqNum = (SYN_msg_data[4] << 24) | (SYN_msg_data[5] << 16) |
                            (SYN_msg_data[6] << 8) | SYN_msg_data[7];

                    // Create the SYN-ACK response
                    // Server's ISN is arbitrary, here we use 0 (it can be randomized later)
                    int serverSeqNum = 0;
                    byte[] SYN_ACK_msg_data = createTCPHeader(serverSeqNum, true, true, false); // SYN + ACK flags set
                    addChecksum(SYN_ACK_msg_data);

                    // Create the SYN-ACK packet
                    DatagramPacket SYN_ACK_msg = new DatagramPacket(SYN_ACK_msg_data, SYN_ACK_msg_data.length, clientIP,
                            clientPort);

                    // Send the SYN-ACK message to the client
                    server.send(SYN_ACK_msg);
                    printDataTrans(SYN_ACK_msg_data);

                    // Wait for the ACK message from the client
                    while (true) {
                        byte[] ackData = new byte[TCPSegmentSize];
                        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length);
                        server.receive(ackPacket);
                        byte[] ACK_msg_data = ackPacket.getData();

                        // Check if the received packet is an ACK (ACK flag should be set)
                        if ((int) ACK_msg_data[15] == 16) { // ACK flag (binary 10000)
                            // We can now safely conclude that the connection is established
                            System.out.println("Connection successfully established with the client.");
                            break;
                        }
                    }
                    break; // Exit the loop once ACK is received
                }
            }
        } catch (Exception e) {
            System.out.println("Error in establishing connection!\n" + e);
            System.exit(-1);
        }
    }

    public static void main(String[] args) throws Exception {
        // Make sure operator inputs correct amount of parameters.
        HashMap<Integer, byte[]> fragments = new HashMap<>();
        if (args.length < 1) {
            System.out.println(
                    "Please enter correct parameters.(java RUDPDestination.java <Recieve Port>");
        }
        int port = Integer.parseInt(args[0]);
        int nOfMessages = 0;
        // Create socket at port 8888 to listen for client messages, and facilitate the
        // sending of messages.
        server = new DatagramSocket(port);
        listenForConnection();
        while (true) {

        }
        server.close();
    }
}

class PacketObject {
    protected long receivedTime;
    protected DatagramPacket packetData;

    public PacketObject(long receivedTime, DatagramPacket packetData) {
        this.packetData = packetData;
        this.receivedTime = receivedTime;
    }
}
