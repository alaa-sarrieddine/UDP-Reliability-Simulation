package Phase3;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.io.FileOutputStream;
import java.io.IOException;
public class RUDPDestination {
    private static DatagramSocket server;
    private static ArrayList<byte[]> fragmentStorage; // This arraylist will hold all the fragments until after the
                                                         // whole interaction is done they are written into the
                                                           // file.
    private static double packetLossChance = 0.5;//Defined by whoever is running the code to control loss , and delay parameters
    private static int minDelay = 0;           
    private static int maxDelay = 100;         
    private static int TCPheaderSize = 16;// Sizes in Bytes as defined by protocols.
    private static int TCPdataSize = 64;  // Bytes
    private static int TCPSegmentSize = TCPheaderSize + TCPdataSize;// Logical this is to avoid having to repeat the addition
    private static int expectedClientSeqNum;// This is decided by the ISN sent by the client using handshake. And it is
                                            // the sequence number sent in the ack telling the client what it is we
                                            // expect from them next.
    private static int mySeqNum = 0; // Servers ISN
    private static InetAddress destinationIP;//These values are gotten from the client when we first recieve a message.
    private static int destinationPortNumber;
  
//isDelayed():
//Calculates how much delay, and adminsters it based on the min, and max Delay parameters.
    public static void isDelayed() throws InterruptedException {
        Random rand = new Random();
        int delay = minDelay + rand.nextInt((maxDelay - minDelay) + 1);
        Thread.sleep(delay);
    }
//isLost():
//Calculates if the packet should be treated as lost or not, based on the packetLossChance parameter.
    public static boolean isLost() {
        Random rand = new Random();
        int packetLossRandomVariable = rand.nextInt(101);
        if (packetLossChance == 0) {
            return false;
        }
        if (packetLossRandomVariable > packetLossChance * 100) {
            return false;
        }
        return true;
    }
    //Function that helps with debugging TO BE DELETED FROM FINAL VERSION!
    public static void printByteArray(byte[] byteArray) {
        System.out.println("Byte Array Contents:");
        StringBuilder hexBuilder = new StringBuilder();
        StringBuilder asciiBuilder = new StringBuilder();
    
        // Start from the 17th byte (index 16) and process the rest
        for (int i = 16; i < byteArray.length; i++) {
            // Convert byte to hex
            hexBuilder.append(String.format("%02X ", byteArray[i]));
    
            // Convert byte to ASCII if printable
            char asciiChar = (byteArray[i] >= 32 && byteArray[i] <= 126) ? (char) byteArray[i] : '.';
            asciiBuilder.append(asciiChar);
    
            // Print in chunks of 16 bytes for better readability
            if ((i - 15) % 16 == 0 || i == byteArray.length - 1) { // Adjusted for skipping 16 bytes
                System.out.printf("%-48s | %s%n", hexBuilder.toString(), asciiBuilder.toString());
                hexBuilder.setLength(0);
                asciiBuilder.setLength(0);
            }
        }
        System.out.println();
    }
    // turnIntoInteger(byteDataToReadFrom,first,second,third,fourth):
    // Rather than do the shifting manually each time have a function do it and just
    // pass the indices for either the seq or the ack number
    public static int turnIntoInteger(byte[] data, int first, int second, int third, int fourth) {
        //This is responsible for reverse engineering from bytes into normal integers.
        int valueInInteger = (data[first] & 0xFF) << 24 |
                ((data[second] & 0xFF) << 16) |
                ((data[third] & 0xFF) << 8) |
                (data[fourth] & 0xFF);
        return valueInInteger;
    }

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
    public static byte[] createTCPHeader(int AckNum, boolean SYN, boolean ACK, boolean FIN) {
        byte[] header = new byte[16];

        // SRC PORT NUMBER: 16-bits
        header[0] = (byte) (server.getLocalPort() >> 8);
        header[1] = (byte) (server.getLocalPort());

        // DEST PORT NUMBER: 16-bits
        header[2] = (byte) (destinationPortNumber >> 8);
        header[3] = (byte) (destinationPortNumber);
        //This is stored as a global variable and will be changed appropriately as per recieved ACKS
        // SEQ NUMBER: 32-bits
        header[4] = (byte) (mySeqNum >> 24);
        header[5] = (byte) (mySeqNum >> 16);
        header[6] = (byte) (mySeqNum >> 8);
        header[7] = (byte) (mySeqNum);
        //This value is mostly controlled by a global variable expectedClientSeqNum which we changed based on ACKS aswell
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
    public static void printDataTrans(byte[] packetBytes) {
        int seqNum = turnIntoInteger(packetBytes,8,9 ,10 ,11 );
        System.out.println("[DATA TRANSMISSION]: " + seqNum + " | " + packetBytes.length);
        System.out.println();
    }
    public static void printDataReception(byte[] packetBytes) {
       int seqNum =  turnIntoInteger(packetBytes,4,5 ,6 ,7 );
        System.out.println("[DATA RECEPTION]: " + seqNum + " | " + packetBytes.length + " | OK");
        System.out.println();
    }
    public static void printDataReceptionReject(byte[] packetBytes) {
        int seqNum =  turnIntoInteger(packetBytes,4,5 ,6 ,7 );
         System.out.println("[DATA RECEPTION]: " + seqNum + " | " + packetBytes.length + "| DISCARDED");
         System.out.println();
     }

    // initializeConnection():
    // Expects and returns nothing, is responsible for performing the three way
    // handshake to establish connection.
    public static void listenForConnection() {
        try {
            byte[] dataReceived = new byte[TCPSegmentSize];
            DatagramPacket receivedPacket = new DatagramPacket(dataReceived, dataReceived.length);

            // Listen for incoming SYN message
            while (true) {
                server.receive(receivedPacket);
                byte[] SYN_msg_data = receivedPacket.getData();
                // Extract source IP and port from message and set them globally.
                destinationIP = receivedPacket.getAddress();
                destinationPortNumber = receivedPacket.getPort();
                // Check if it's a SYN message
                if ((int) SYN_msg_data[15] == 2) {
                    printDataReception(SYN_msg_data);
                    // Get the client's initial sequence number from the SYN message. And then set
                    // our expected to isn + 1
                    expectedClientSeqNum = (SYN_msg_data[4] << 24) | (SYN_msg_data[5] << 16) |
                            (SYN_msg_data[6] << 8) | SYN_msg_data[7];
                    expectedClientSeqNum++;
                    // Create the SYN-ACK response
                    // Server's ISN is arbitrary, here we use 0 as defined by the mySeqNum (it can be randomized later).
                    //Set syn and ack in header to true.
                    byte[] SYN_ACK_msg_data = createTCPHeader(expectedClientSeqNum, true, true, false); 
                    // addChecksum(SYN_ACK_msg_data);
                    // Create the SYN-ACK packet
                    DatagramPacket SYN_ACK_msg = new DatagramPacket(SYN_ACK_msg_data, SYN_ACK_msg_data.length,
                            destinationIP, destinationPortNumber);
                    printDataTrans(SYN_ACK_msg_data);
                    server.send(SYN_ACK_msg);
                    //printDataTrans(SYN_ACK_msg_data);
                    // Wait for the ACK message from the client
                    while (true) {
                        byte[] ackData = new byte[TCPSegmentSize];
                        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length);
                        server.receive(ackPacket);
                        printDataReception(ackData);
                        byte[] ACK_msg_data = ackPacket.getData();
                        // Set our next sequence number to the one expected from us by the ACK of the client.
                        mySeqNum = SYN_ACK_msg_data[8] << 24 | SYN_ACK_msg_data[9] << 16
                                | SYN_ACK_msg_data[10] << 8 | SYN_ACK_msg_data[11];
                        // Check if the received packet is an ACK
                        if ((int) ACK_msg_data[15] == 4) {
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
    //writeToList():
    //Is responsible for adding each segment that needs to be written into the file into a list where they will later be written into the file
    public static void writeToList(byte[] dataToProcess) {
        fragmentStorage.add(dataToProcess);
    }

//writeToFile():
//Copies all the fragements in the fragmentStorage arrayList into an outputFile.
public static void writeToFile() {
    // Define the output file
    String outputFile = "copy.txt";

    try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
        // Iterate through the ArrayList
        for (byte[] segment : fragmentStorage) {
            //printByteArray(segment);
            byte[] tcpData = new byte[segment.length - TCPheaderSize];
            //Copy only the data, skipping over the header since its not relevant.
            System.arraycopy(segment, TCPheaderSize, tcpData, 0, tcpData.length);
            outputStream.write(tcpData);
        }
        System.out.println("Data successfully written to " + outputFile);
    } catch (IOException e) {
        System.err.println("Error while writing to file: " + e.getMessage());
    }
}
//closeConnection():
//Responsible for going through the fin ack handshake.
    public static void closeConnection(byte[] finPacketData) throws IOException {
        int packetSeqNumber = turnIntoInteger(finPacketData, 4, 5, 6, 7);
        expectedClientSeqNum = packetSeqNumber + 1;
        mySeqNum = turnIntoInteger(finPacketData, 8,9, 10, 11);
        byte[] FIN_ACKData = createTCPHeader(expectedClientSeqNum, false, true, true);
        DatagramPacket FIN_ACK_Packet = new DatagramPacket(FIN_ACKData,FIN_ACKData.length,destinationIP,destinationPortNumber);
        printDataTrans(FIN_ACKData);
        server.send(FIN_ACK_Packet);
        while (true) {
            System.out.println("Waiting for ACK");
            byte[] ACK_data = new byte[TCPheaderSize];
            DatagramPacket ACK= new DatagramPacket(ACK_data, ACK_data.length);
            server.receive(ACK);
            printDataReception(ACK_data);
            if ((int)ACK_data[15] == 4) {
                break;
            }
            printDataTrans(FIN_ACKData);
            server.send(FIN_ACK_Packet);
        }
        System.out.println();
        System.out.println("[COMPLETE]");
        server.close();
    }

    public static void main(String[] args) throws Exception {
        // Make sure operator inputs correct amount of parameters.
        fragmentStorage = new ArrayList<>();
        if (args.length < 1) {
            System.out.println(
                    "Please enter correct parameters.(java RUDPDestination.java <Recieve Port>");
        }
        int port = Integer.parseInt(args[0]);
        //Open the server at the input port.
        server = new DatagramSocket(port);
        //Then listen for connection.
        listenForConnection();
        
        while (true) {
            //Generate unique buffer that will be used later.
            byte[] recievedData = new byte[TCPheaderSize + TCPdataSize];
            DatagramPacket recievedPacket = new DatagramPacket(recievedData, recievedData.length);
            server.receive(recievedPacket);
            int actualLength = recievedPacket.getLength();
            byte[] dataToProcess = Arrays.copyOf(recievedData, actualLength);;
            // printByteArray(dataToProcess);
            //If packet is lost we resend our old ack with the old ack number.
            if (isLost()) {
                printDataReceptionReject(dataToProcess);
                byte[] ackData = createTCPHeader(expectedClientSeqNum, false, true, false);
                DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, destinationIP,
                        destinationPortNumber);
                printDataTrans(ackData);
                server.send(ackPacket);
            } else {
                //Check for delay and delay as needed.
                isDelayed();
                printDataReception(dataToProcess);
                //If the message has a FIN flag we start closing the connection.
                if ((int) dataToProcess[15] == 1) {
                    System.out.println("FIN recieved!");
                    closeConnection(dataToProcess);
                    break;
                }
                //If what weve gotten isnt a Fin we add the bytes recieved into the list.
                writeToList(dataToProcess);
                //We extract the sequence number from the packet.
                int packetSeqNumber = turnIntoInteger(dataToProcess, 4, 5, 6, 7);
                //After the packets sequence number is gotten we then ask for the nexst sequence number that being the length of the data we have recieved - the length of the header because it doesnt affect the sequence number.
                expectedClientSeqNum = packetSeqNumber + dataToProcess.length- TCPheaderSize;
                //Move sequence number forward as per the ack of the clients message.
                mySeqNum = turnIntoInteger(dataToProcess, 8,9, 10, 11);
                //Generate the header with our ack number being the next expectted client num.
                byte[] ackData = createTCPHeader(expectedClientSeqNum, false, true, false);
                DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, destinationIP,
                        destinationPortNumber);
                printDataTrans(ackData);
                server.send(ackPacket);
            }

        }
        writeToFile();
    }
}
