package Phase3;

import java.io.FileInputStream;
import java.net.*;
import java.util.concurrent.Semaphore;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ArrayList;
import java.lang.Object;

public class RUDPSource {
    private static DatagramSocket client;
    private static InetAddress destinationIP;
    private static int destinationPortNumber;
    private static String filePath;
    private static ArrayList<byte[]> senderWindow = new ArrayList<>();
    private static int sendBase = 0;
    private static int nextIndex = 0;
    private static int windowSize = 7;
    private static int timeout = 1000;
    private static int maxRetransmissions = 5;
    private static int TCPheaderSize = 20; // bytes
    private static int TCPdataSize = 50;   // bytes
    private static int TCPSegmentSize = TCPheaderSize + TCPdataSize; //bytes
    private static int serverSeqNum = 0;

    private static Semaphore accessToEndMessageFlag= new Semaphore(1, true);

    // Function to store user arguments from command line
    private static void storingUserArguments(String[] args){
        if(args.length<4){
            System.out.println("Please use the following format for the inputs:\n"+
                               "java RUDPSource -r <recvHost>:<recvPort> -f <fileName>");
            System.exit(-1);
        }

        try{
            for(int i =0; i<args.length; i++){
                if(args[i].equals("-r")){
                    String[] destination = args[i+1].split(":");
                    destinationIP = InetAddress.getByName(destination[0]);
                    destinationPortNumber = Integer.parseInt(destination[1]);
                    i++;
                }
                else if(args[i].equals("-f")){
                    filePath = args[i+1];
                    i++;
                }
            }
        }catch(Exception e){
            System.out.println("Error reading/storing user arguments!\n" + e);
            System.exit(-1);
        };
    };
    
    // Function that computes the checksum of the header and data of the packet
    public static int computeChecksum(byte[] packet){
        int sum = 0;
        for (int i = 0; i < packet.length; i += 2) {
            // Convert the bytes into integers
            int firstByte = packet[i] & 0xFF;
            int secondByte = packet[i + 1] & 0xFF;

            // Add the bytes into the sum
            sum += (firstByte << 8) + secondByte;
        }
        while ((sum >> 16) > 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return ~sum & 0xFFFF;
    }

    // Function to check for any bit errors using checksum
    public static boolean checkChecksum(byte[] packet){
        int checksum = packet[16] << 8 | packet[17];
        packet[16] = 0;
        packet[17] = 0;
        boolean res =  (checksum == computeChecksum(packet));
        packet[16] = (byte) (checksum >> 8);
        packet[17] = (byte) (checksum);
        return res;
    }

    // Function to create a TCP header
    public static byte[] createTCPHeader(int SeqNum, int ackNum, boolean syn, boolean ack, boolean fin){
        byte[] header = new byte[20];
        // Convert source port number to bytes and add to header
        header[0] = (byte) (client.getLocalPort() >> 8);
        header[1] = (byte) (client.getLocalPort());

        // Convert destination port number to bytes and add to header
        header[2] = (byte) (destinationPortNumber >> 8);
        header[3] = (byte) (destinationPortNumber);

        // Convert sequence number to bytes and add to header
        header[4] = (byte) (SeqNum >> 24);
        header[5] = (byte) (SeqNum >> 16);
        header[6] = (byte) (SeqNum >> 8);
        header[7] = (byte) (SeqNum);

        // Convert acknowledgement number to bytes and add to header
        header[8] = (byte) (ackNum >> 24);
        header[9] = (byte) (ackNum >> 16);
        header[10] = (byte) (ackNum >> 8);
        header[11] = (byte) (ackNum);

        // Header length (storing them as a byte)
        // This removes our ability to use the RSV field and the URG Flag
        header[12] = (byte) (5);

        // Flags
        int sumOfFlags=0;
        if(ack){
            sumOfFlags+=128;
        }
        if(syn){
            sumOfFlags+=8;
        }
        if(fin){
            sumOfFlags+=1;
        }
        header[13] = (byte) sumOfFlags;
        
        // Window size for receiver congestion (for now, not used)
        header[14] = 0;
        header[15] = 0;

        // Checksum (initially set to 0)
        header[16] = 0;
        header[17] = 0;

        // Urgent pointer (not used)
        header[18] = 0;
        header[19] = 0;

        // Compute checksum and add to header
        int checksum = computeChecksum(header);
        header[16] = (byte) (checksum >> 8);
        header[17] = (byte) (checksum);

        return header;
    }

    // Function to send connection management packets
    public static void sendConnectionMngmtPacket(DatagramPacket packet, byte[] packetBytes, boolean retransmit){
        int currentRetransmits = 0;
        while(currentRetransmits<maxRetransmissions){
            try{
                client.setSoTimeout(timeout);
                client.send(packet);
                // Used for ACK messages
                if(!retransmit){
                    return;
                }

                // Wait for packet
                byte[] dataReceived = new byte[TCPheaderSize];
                DatagramPacket ackPacket = new DatagramPacket(dataReceived, dataReceived.length);
                client.receive(ackPacket);

                // Check for bit errors
                if (checkChecksum(dataReceived)) {
                    return;
                } else {
                    throw new Exception("Checksum error");
                }
            }catch(Exception e){
                currentRetransmits++;
                // Getting sequence number from packetBytes
                int seqNum = packetBytes[4] << 24 | packetBytes[5] << 16 | packetBytes[6] << 8 | packetBytes[7];
                System.out.println("[DATA TRANSMISSION]: "+seqNum+" | "+packetBytes.length);
            }
        }
        System.out.println("Connection failed to establish\n");
        System.exit(1);
    }

    // Function to establish a connection
    public static void establishConnection(){
        try{
            // Send SYN
            byte[] SYN_msgData = createTCPHeader(0,0, true, false, false);

            DatagramPacket SYN_msg = new DatagramPacket(SYN_msgData, SYN_msgData.length, 
                                                        destinationIP, destinationPortNumber);
            
            sendConnectionMngmtPacket(SYN_msg, SYN_msgData, true);
            
            // Receive SYN-ACK
            byte[] SYN_ACK_msgData = new byte[TCPheaderSize];
            DatagramPacket SYN_ACK_msg = new DatagramPacket(SYN_ACK_msgData, SYN_ACK_msgData.length);
            // SYN takes `1` byte
            serverSeqNum++;

            // Send ACK (sequence number doesnt matter)
            byte[] ACK_msgData = createTCPHeader(1, 1, 
                                             false, true, false);
            
            DatagramPacket ACK_msg = new DatagramPacket(ACK_msgData, ACK_msgData.length, 
                                                        destinationIP, destinationPortNumber);

            sendConnectionMngmtPacket(ACK_msg, ACK_msgData, false);

        }catch(Exception e){
            System.out.println("Error in establishing connection!\n" + e);
            System.exit(-1);
        }
    }

    // Function that reads the input file and adds the packets 
    // to the sender window. These packets will have the default headers 
    // (all fields equal to zero) zero and the data will be the contents of the file
    // NOTE FOR FUTURE: ALL THE DATA WILL BE READ INTO MEMORY, THIS MAY NOT BE THE BEST!!!
    public static void addingPacketsToWindow(){
        try{
            FileInputStream fileInputStream = new FileInputStream(filePath);
            byte[] fileData = new byte[fileInputStream.available()];
            fileInputStream.read(fileData);
            fileInputStream.close();
            int packetSize;

            // Because SYN will take 1 byte
            int currentByteSeq = 1;

            for (int i = 0; i < fileData.length; i += TCPdataSize) {
                int remainingFileData = Math.min(TCPdataSize, fileData.length - i);
                
                if(remainingFileData==0){
                    break;
                }
                else if(remainingFileData<TCPdataSize){
                    packetSize = remainingFileData+TCPheaderSize;
                }
                else{
                    packetSize = TCPSegmentSize;
                }
                byte[] packetData = new byte[packetSize];
                byte[] header = createTCPHeader(currentByteSeq,
                                            0, false, false, false);
                
                currentByteSeq += remainingFileData;
                System.arraycopy(header, 0, packetData, 0, TCPheaderSize);
                System.arraycopy(fileData, i, packetData, TCPheaderSize, remainingFileData);

                senderWindow.add(packetData);
            }
        }catch(Exception e){
            System.out.println("Error adding packet to window\n"+e);
        }
    }

    // Closing the Connection
    public static void closeConnection() {
        try {
            // Step 1: Send FIN
            byte[] FIN_msgData = createTCPHeader(1, 1, false, false, true); // FIN = 1
            DatagramPacket FIN_msg = new DatagramPacket(FIN_msgData, FIN_msgData.length, 
                                                        destinationIP, destinationPortNumber);
            sendConnectionMngmtPacket(FIN_msg, FIN_msgData, true);
    
            // Step 2: Receive ACK
            byte[] ACK_msgData = new byte[TCPheaderSize];
            DatagramPacket ACK_msg = new DatagramPacket(ACK_msgData, ACK_msgData.length);;
            client.receive(ACK_msg);
            if (!checkChecksum(ACK_msgData)) {
                throw new Exception("Checksum error in received ACK");
            }
            System.out.println("Received ACK for FIN.");
    
            // Step 3: Receive FIN from server
            byte[] FIN_fromServerData = new byte[TCPheaderSize];
            DatagramPacket FIN_fromServer = new DatagramPacket(FIN_fromServerData, FIN_fromServerData.length);
            // Here well need to wait for longer incase some packets took time to arrive
            client.setSoTimeout(100000);
            client.receive(FIN_fromServer);
            if (!checkChecksum(FIN_fromServerData)) {
                throw new Exception("Checksum error in received FIN from server");
            }
            System.out.println("Received FIN from server.");
    
            // Step 4: Send final ACK
            byte[] finalACKData = createTCPHeader(2, serverSeqNum + 1, false, true, false); // ACK = 1
            DatagramPacket finalACK = new DatagramPacket(finalACKData, finalACKData.length, 
                                                         destinationIP, destinationPortNumber);
            sendConnectionMngmtPacket(finalACK, finalACKData, false);
            
            System.out.println("Connection closed successfully.");
        } catch (Exception e) {
            System.out.println("Error during connection teardown: " + e.getMessage());
        } finally {
            client.close(); // Ensure the socket is closed
        }
    }
    
    // for debugging purposes
    public static void printAllPacketData(){
        for (int i = 0; i < senderWindow.size(); i++) {
            byte[] packet = senderWindow.get(i);
            byte[] data = new byte[packet.length - 20];
            System.arraycopy(packet, 20, data, 0, packet.length - 20);
            System.out.println("Data " + i + ": " + new String(data));
        }
    }

    // A class representing a worker thread for sending a packet
    static class PacketSender extends Thread {
        private final byte[] packetData;
        private final int packetIndex;
        private boolean isAcknowledged = false;

        public PacketSender(byte[] packetData, int packetIndex) {
            this.packetData = packetData;
            this.packetIndex = packetIndex;
        }

        @Override
        public void run() {
            int retransmissionCount = 0;
            while (!isAcknowledged && retransmissionCount < maxRetransmissions) {
                try {
                    // Send the packet
                    DatagramPacket packet = new DatagramPacket(packetData, packetData.length, 
                                                                destinationIP, destinationPortNumber);
                    client.send(packet);
                    System.out.println("Sent packet " + packetIndex);

                    // Wait for acknowledgment
                    byte[] ackData = new byte[TCPheaderSize];
                    DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length);
                    client.setSoTimeout(timeout); // Timer starts
                    client.receive(ackPacket);

                    // Check for bit errors in the ACK
                    if (checkChecksum(ackData)) {
                        // Extract ACK number
                        int ackNum = (ackData[8] << 24) | (ackData[9] << 16) | (ackData[10] << 8) | (ackData[11]);
                        if (ackNum == packetIndex + 1) {
                            isAcknowledged = true;
                            System.out.println("ACK received for packet " + packetIndex);
                        } else {
                            throw new Exception("Incorrect ACK number");
                        }
                    } else {
                        throw new Exception("Checksum error in ACK");
                    }
                
                // Here we can concat both cases BUT, incase we need this difference
                // for debugging later on
                } catch (SocketTimeoutException e) {
                    int seqNum = packetData[4] << 24 | packetData[5] << 16 | packetData[6] << 8 | packetData[7];
                    System.out.println("[DATA TRANSMISSION]: "+seqNum+" | "+packetData.length);
                    retransmissionCount++;
                } catch (Exception e) {
                    // Getting sequence number from packetBytes
                    int seqNum = packetData[4] << 24 | packetData[5] << 16 | packetData[6] << 8 | packetData[7];
                    System.out.println("[DATA TRANSMISSION]: "+seqNum+" | "+packetData.length);
                    retransmissionCount++;
                }
            }

            if (!isAcknowledged) {
                System.out.println("Max retransmissions reached for packet " + packetIndex);
            }
        }
    }

    public static void manageTransmission() {
        LinkedList<Thread> threadQueue = new LinkedList<>();

        // Start sending packets in the sender window
        for (int i = 0; i < senderWindow.size(); i++) {
            byte[] packet = senderWindow.get(i);
            PacketSender sender = new PacketSender(packet, i);

            // Add thread to the queue
            threadQueue.add(sender);

            // Ensure no more than `windowSize` threads are running
            while (threadQueue.size() >= windowSize) {
                try {
                    threadQueue.peek().join(); // Wait for the oldest thread to finish
                    threadQueue.poll();       // Remove the completed thread
                } catch (InterruptedException e) {
                    System.out.println("Error in thread management: " + e.getMessage());
                }
            }

            // Start the thread
            sender.start();
        }

        // Wait for remaining threads to finish
        while (!threadQueue.isEmpty()) {
            try {
                threadQueue.poll().join();
            } catch (InterruptedException e) {
                System.out.println("Error in thread management: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // Retrieving user input and setting up
        storingUserArguments(args);
        try {
            client = new DatagramSocket();
            client.setSoTimeout(300);
        } catch (Exception e) {
            System.out.println("Error in creating a socket!\n" + e);
            System.exit(-1);
        }

        addingPacketsToWindow();  // Populate the sender window
        establishConnection();    // Establish the connection
        manageTransmission();     // Start sending data
        System.out.println("All packets sent successfully.");

        // Close the connection
        closeConnection(); 
    }


}