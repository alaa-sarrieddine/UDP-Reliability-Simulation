package Phase3;
import java.nio.charset.StandardCharsets;
import java.io.FileInputStream;
import java.net.*;

public class RUDPSource {
    private static DatagramSocket client;
    private static InetAddress destinationIP;
    private static int destinationPortNumber;
    private static String filePath;
    private static int timeout = 100000000;
    private static int maxRetransmissions = 5;
    private static int TCPheaderSize = 16; // bytes
    private static int TCPdataSize = 64;   // bytes
    private static int TCPSegmentSize = TCPheaderSize + TCPdataSize; //bytes
    private static int expectedServerSeqNum; //This is decided by the ISN sent by the server during handshake.
    private static int mySeqNum = 0;  //Clients ISN
    private static int currentRetransmissions=0;
    private static boolean noMoreData =false;
    private static int seqNumIncrement=0;


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
    public static void addChecksum(byte[] packet){
        int checksum = computeChecksum(packet);
        packet[12] = (byte) (checksum >> 8);
        packet[13] = (byte) (checksum);
        return;
    }

    
    public static boolean correctChecksum(byte[] packet) {
        // Save the original checksum from the packet
        int originalChecksum = (packet[12] << 8) | (packet[13] & 0xFF);
    
        // Temporarily set the checksum fields to 0 for computation
        packet[12] = 0;
        packet[13] = 0;
    
        // Recompute the checksum
        int computedChecksum = computeChecksum(packet);
    
        // Restore the original checksum in the packet
        packet[12] = (byte) (originalChecksum >> 8);
        packet[13] = (byte) (originalChecksum);
    
        // Validation: Check if recomputed checksum is 0
        return computedChecksum == 0;
    }
    

    // Function to create a custom TCP header
    // Input: - (int) sequence number: the sequence number of the first byte
    //                               in the segment
    //        - (int) acknowledgement number: the next sequence number expected
    //                                      from the other
    //        - (bool) SYN: Synchronization event
    //        - (bool) ACK: Acknowledging sequence numbers event
    //        - (bool) FIN: Closing connection event
    //
    // Output: a byte array that represents a TCP header and 
    //         contains all the information needed to correctly 
    //         read and assemble the data.
    //
    // Header format:
    //     <------------- 32 bits -------------> 
    //     |   source port #  |   dest. port # |  
    //     |          Sequence number          |
    //     |       Acknowledgement number      |
    //     |   Checksum    | Header len | flgs |
    
    public static byte[] createTCPHeader(int AckNum, boolean SYN, 
                                         boolean ACK, boolean FIN){
        byte[] header = new byte[16];

        // SRC PORT NUMBER: 16-bits
        header[0] = (byte) (client.getLocalPort() >> 8);
        header[1] = (byte) (client.getLocalPort());

        // DEST PORT NUMBER: 16-bits
        header[2] = (byte) (destinationPortNumber >> 8);
        header[3] = (byte) (destinationPortNumber);

        // SEQ NUMBER: 32-bits
        header[4] = (byte) (mySeqNum >> 24);
        header[5] = (byte) (mySeqNum >> 16);
        header[6] = (byte) (mySeqNum  >> 8);
        header[7] = (byte) (mySeqNum );

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
        int sumOfFlags=0;
        // ACK's bit will be the 3rd from the right
        if(ACK){
            sumOfFlags+=4;
        }
        // SYN's bit will be the 2nd from the right
        if(SYN){
            sumOfFlags+=2;
        }
        // FIN's bit will be the 1st from the right
        if(FIN){
            sumOfFlags+=1;
        }
        header[15] = (byte) sumOfFlags;
        return header;
    }

    public static void printDataReception(byte[] packetBytes) {
        int seqNum =  turnIntoInteger(packetBytes,8,9 ,10 ,11 );
         System.out.println("[DATA RECEPTION]: " + seqNum + " | " + packetBytes.length);
     }

    public static void printDataTrans(byte[] packetBytes){
        // Getting sequence number from packetBytes by perfoming
        // a bitwise to fit them all on to an int
        int seqNum = turnIntoInteger(packetBytes,4,5,6,7);
        System.out.println("[DATA TRANSMISSION]: " + seqNum + " | " + packetBytes.length);
    }

    // Function to send connection management packets
    public static void sendAndWait(DatagramPacket packet, byte[] packetBytes, boolean retransmit){
        int currentRetransmissions = 0;
        while(currentRetransmissions < maxRetransmissions){
            try{
                //printByteArray(packet.getData());
                client.send(packet);
                
                // For debugging
                printDataTrans(packetBytes);

                // Wait for packet
                // There MIGHT be an issue here with size. Some
                // incoming packets will be less than the segment size.
                // Couldn't test this.
                
                byte[] dataReceived = new byte[TCPSegmentSize];
                DatagramPacket ackPacket = new DatagramPacket(dataReceived, dataReceived.length);
                client.receive(ackPacket);
                byte[] ack_packet_data = ackPacket.getData();

                // Check for bit errors or ACKs < Seq num (removed checksum checking for testing purposes readd.)
                if(negativeAck(ack_packet_data)){
                    throw new SocketTimeoutException("Packet not received properly");
                }else{
                    int packetSeqNumber = turnIntoInteger(ack_packet_data, 4, 5, 6, 7);
                    expectedServerSeqNum = packetSeqNumber + 1;
                    mySeqNum = turnIntoInteger(ack_packet_data, 8, 9, 10, 11);
                    return;
                }
            }
            // If the timeout expires, we will increment the retransmissions
            // count and retransmit in the next iteration
            catch(SocketTimeoutException e){
                currentRetransmissions++;
            }
            catch(Exception e){
                System.out.println("Error occured while sending a packet :/\n"+e);
                System.exit(1);
            }            
        }
    }
    //Rather than do the shifting manually each time have a function do it and just pass the indices for either the seq or the ack number
    public static int turnIntoInteger(byte[] data,int first,int second,int third, int fourth){
        int valueInInteger = (data[first] & 0xFF) << 24 | 
        ((data[second] & 0xFF) << 16) | 
        ((data[third] & 0xFF) << 8) | 
        (data[fourth] & 0xFF);
        return valueInInteger;
    }
    public static boolean negativeAck(byte[] TCPsegment){
        int ackNumber = turnIntoInteger(TCPsegment, 8, 9, 10, 11);
        //Changed <= to < here
       printDataReception(TCPsegment);
        return ackNumber <= mySeqNum+1;
    }

    // Function to establish a connection
    public static void establishConnection(){
        try{
            // Create the SYN message
            // Our ISN will be 0 but we can randomize
            // this in the future
            //Ack here doesnt matter.
            byte[] SYN_msg_data = createTCPHeader(0, true, false, false);
            addChecksum(SYN_msg_data);

            DatagramPacket SYN_msg = new DatagramPacket(SYN_msg_data, SYN_msg_data.length, 
                                                        destinationIP, destinationPortNumber);
            
            // Sending the SYN message and receiving/verifing the SYN-ACK response
            while(currentRetransmissions <= maxRetransmissions){
                try{
                    client.send(SYN_msg);
                    printDataTrans(SYN_msg_data);
                    // Wait for SYN ACK response
                    // Array size mismatch could be an issue here;
                    // some packets are smaller than segment size.
                    byte[] dataReceived = new byte[TCPheaderSize];
                    DatagramPacket ackPacket = new DatagramPacket(dataReceived, dataReceived.length);
                    client.receive(ackPacket);
                    //Cannot reuse old buffer here need to create a new one otherwise in negativeAck we get wrong value of acknumber
                    //byte[] SYN_ACK_msg = ackPacket.getData(); 
                     byte[] syn_ack = ackPacket.getData(); 
                    // We will have to retransmit if: 
                    // 1) the syntax of the SYN_ACK message was incorrect, 
                    // 2) acknowledgement has a seq num that is before our current seq num or
                    // 3) we found bit errors in our message 
                    //RENMOVED CHECKSUM CHECKING FOR TESTING !correctChecksum(SYN_msg_data) RE ADD IF WE IMPLEMENT IT
                    int ackNumber = syn_ack[8] << 24 | syn_ack[9] << 16 
                        | syn_ack[10] << 8 | syn_ack[11];
                    if((int)syn_ack[15]!= 6 || ackNumber < mySeqNum + 1){
                        throw new SocketTimeoutException("Last message was not acknowledged");
                    }
                    System.out.println("Recieved SYN-ACK");
                    // Save the server's ISN
                    expectedServerSeqNum = syn_ack[4] << 24 | syn_ack[5] << 16 
                                  | syn_ack[6] << 8 | syn_ack[7]; 
                    
                    currentRetransmissions=0;
                    break;
                }
                // If the timeout expires, we will increment the retransmissions
                // count and retransmit in the next iteration
                catch(SocketTimeoutException e){
                    currentRetransmissions++;

                    if(currentRetransmissions>maxRetransmissions){
                        System.out.println("Server is not responding :(\n");
                        System.exit(1);
                    }
                }
                catch(Exception e){
                    System.out.println("Error occured while sending a packet :/\n"+e);
                    System.exit(1);
                }            
            }
            // After successfully recieving a SYN ACK message,
            // will will establish a three-way handshake by 
            // sending an ACK.
            // Send ACK (sequence number doesnt matter because server doesnt send an ack on for our ack but rather the first segment we send.
            //Here we send ACK for the servers ISN + 1.
            //So servers next packet will be our ack + 1.
            byte[] ACK_msg_data = createTCPHeader(expectedServerSeqNum+1, false, 
                                                 true, false);
           //addChecksum(ACK_msg_data);
            DatagramPacket ACK_msg = new DatagramPacket(ACK_msg_data, ACK_msg_data.length, 
                                                        destinationIP, destinationPortNumber);

            // Sending the ACK message and checking whether the reciever
            // retransmited the SYN ACK response (in other words, receivedd this ACKed)
            //I commented out this part and it should be deleted after the ack server doesnt respond we wait for the first ack on our data 
            
            try {
                client.send(ACK_msg);
            } catch (Exception e) {
                System.out.println("Failed to send Ack message");
            }
    /* 
            while(currentRetransmissions <= maxRetransmissions){
                try{
                    client.send(ACK_msg);
                    System.out.println("IM IN HERE!");
                    printDataTrans(ACK_msg_data);

                    // Wait for SYN ACK response
                    // Array size mismatch could be an issue here;
                    // some packets are smaller than segment size.
                    byte[] dataReceived = new byte[TCPSegmentSize];
                    DatagramPacket SYN_ACK_Packet = new DatagramPacket(dataReceived, dataReceived.length);
                    client.receive(SYN_ACK_Packet);  
                    
                    // If SYN ACK was retransmitted, we
                    // will retransmit the ACK message again
                    currentRetransmissions++;

                    if(currentRetransmissions>maxRetransmissions){
                        System.out.println("The server wasn't able to successfully receive the"+
                                            "ACK msg. Thus, the three-way handshake wasn't established\n");
                        System.exit(1);
                    }

                    continue;
                }
                // If the timeout expires, that means the server received
                // the message and did not need to retransmit
                catch(SocketTimeoutException e){
                    currentRetransmissions=0;                  
                }
                catch(Exception e){
                    System.out.println("Error occured while sending a packet :/\n"+e);
                    System.exit(1);
                }            
            }
            */
        }catch(Exception e){
            System.out.println("Error in establishing connection!\n" + e);
            System.exit(-1);
        }
        
        // At this point, the three-way handshake has been established
        // and we are ready to start sending data!
        return;
    }

    // Function that reads the input file and returns packet given
    // the current segment number and the size of data 
    public static byte[] returnNextSegment(){
        try{
            // Get all the file's information and store it in 
            // a byte array.
            // Later might use buffered reader
            // This is prone to memory segmentation
            FileInputStream fileInputStream = new FileInputStream(filePath);
            byte[] fileData = new byte[fileInputStream.available()];
            fileInputStream.read(fileData);
            fileInputStream.close();

            // Size of the whole packet we will send
            int packetSize;

            // We either have a packet with the chosen data size or
            // a packet with the data that remains int the file
            // mySeqNum - 1 is because of the byte used in SYN msg
            int fileDataAvailable = Math.min(TCPdataSize, fileData.length - mySeqNum);
            // The case were these is no more data left in the file
            if(fileDataAvailable<=0){
                // send a signal to stop requesting data
                noMoreData=true;
                return null;
            }
            else if(fileDataAvailable < TCPdataSize){
                packetSize = fileDataAvailable + TCPheaderSize;
            }
            else{
                packetSize = TCPSegmentSize;
            }
            byte[] packetData = new byte[packetSize];
            byte[] header = createTCPHeader(
                                        0, false, false, false);
            
            System.arraycopy(header, 0, packetData, 0, TCPheaderSize);
            System.arraycopy(fileData, mySeqNum, packetData, TCPheaderSize, fileDataAvailable);
            
            // Calculate and create checksum
            addChecksum(packetData);
            seqNumIncrement = fileDataAvailable;
            return packetData;
        }catch(Exception e){
            System.out.println("Error reading and/or processing the data from file\n"+e);
            return null;
        }
    }

    
    // for debugging purposes
    /*public static void printAllPacketData(){
        for (int i = 0; i < senderWindow.size(); i++) {
            byte[] packet = senderWindow.get(i);
            byte[] data = new byte[packet.length - 20];
            System.arraycopy(packet, 20, data, 0, packet.length - 20);
            System.out.println("Data " + i + ": " + new String(data));
        }
    }*/

    public static void main(String[] args) throws Exception {
        // Retrieving user input and setting up
        storingUserArguments(args);
        try {
            client = new DatagramSocket();
            client.setSoTimeout(timeout);
        } catch (Exception e) {
            System.out.println("Socket error encountered!\n" + e);
            System.exit(-1);
        }

        
        try {
            establishConnection(); // Establish the connection
    
            // Send file data while there is more to send
            while (true) {
                byte[] packetData = returnNextSegment();
                System.out.println();
                if (noMoreData) {
                    break;
                }
                DatagramPacket packet = new DatagramPacket(packetData, packetData.length, destinationIP, destinationPortNumber);
                sendAndWait(packet, packetData, noMoreData);
            }
    
            System.out.println("[COMPLETE]");
        } catch (Exception e) {
            System.out.println("An error occurred during file transfer or connection handling: " + e.getMessage());
        } finally {
            closeConnection(); // Ensure connection is closed at the end
            client.close(); // Release the DatagramSocket resources
            System.out.println("Connection successfully closed.");
        }
    }

    public static void closeConnection() {
        try {
            // Step 1: Send FIN
            byte[] FIN_msg_data = createTCPHeader(expectedServerSeqNum+1, false, false, true); // FIN = 1
            addChecksum(FIN_msg_data);
            DatagramPacket FIN_msg = new DatagramPacket(FIN_msg_data, FIN_msg_data.length, 
                                                        destinationIP, destinationPortNumber);
            
            // Sending the FIN message and receiving/verifing the FIN-ACK response
            while(currentRetransmissions <= maxRetransmissions){
                try{
                    client.send(FIN_msg);
                    
                    printDataTrans(FIN_msg_data);

                    // Wait for FIN ACK response
                    // Array size mismatch could be an issue here;
                    // some packets are smaller than segment size.
                    byte[] dataReceived = new byte[TCPSegmentSize];
                    DatagramPacket ackPacket = new DatagramPacket(dataReceived, dataReceived.length);
                    client.receive(ackPacket);

                    byte[] FIN_ACK_msg = ackPacket.getData();  
                    int ackNumber = turnIntoInteger(FIN_ACK_msg,8,9,10,11);
                    // We will have to retransmit if: 
                    // 1) the syntax of the SYN_ACK message was incorrect, 
                    // 2) acknowledgement has a seq num that is before our current seq num or
                    // 3) we found bit errors in our message 
                    //I Removed the checksum check here for testing 
                    if((int) FIN_ACK_msg[15] != 5 || ackNumber < mySeqNum + 1 ){
                        throw new SocketTimeoutException("Last message was not acknowledged");
                    }
                    expectedServerSeqNum = turnIntoInteger(FIN_ACK_msg,4,5,6,7);
                    expectedServerSeqNum++;
                    mySeqNum = turnIntoInteger(FIN_ACK_msg, 8, 9, 10, 11);
                    currentRetransmissions=0;
                    
                    // FIN takes `1` byte so, we update our seq
                    // number accordingly
                    expectedServerSeqNum++;
                    break;
                }
                // If the timeout expires, we will increment the retransmissions
                // count and retransmit in the next iteration
                catch(SocketTimeoutException e){
                    currentRetransmissions++;

                    if(currentRetransmissions>maxRetransmissions){
                        System.out.println("Server is not responding :(\n");
                        System.exit(1);
                    }
                }
                catch(Exception e){
                    System.out.println("Error occured while sending a packet :/\n"+e);
                    System.exit(1);
                }            
            }
    
            // After successfully recieving a FIN ACK message,
            // we will send the final ACK message

            // Send ACK
            byte[] ACK_msg_data = createTCPHeader(expectedServerSeqNum, false, 
                                                 true, false);
            //addChecksum(ACK_msg_data);
            DatagramPacket ACK_msg = new DatagramPacket(ACK_msg_data, ACK_msg_data.length, 
                                                        destinationIP, destinationPortNumber);
            client.send(ACK_msg);
            /*
            // Sending the ACK message and checking whether the reciever
            // retransmited the FIN ACK response (in other words, receivedd this ACKed)
            This is also not needed we close after sending our ack instantly
            while(currentRetransmissions <= maxRetransmissions){
                try{
                    client.send(ACK_msg);
                    
                    printDataTrans(ACK_msg_data);

                    byte[] dataReceived = new byte[TCPSegmentSize];
                    DatagramPacket SYN_ACK_Packet = new DatagramPacket(dataReceived, dataReceived.length);
                    client.receive(SYN_ACK_Packet);  
                    
                    // If FIN ACK was retransmitted, we
                    // will retransmit the ACK message again
                    currentRetransmissions++;

                    if(currentRetransmissions>maxRetransmissions){
                        System.out.println("The server wasn't able to successfully close the connection"+
                                            "Thus, the connection was abrubtly stopped\n");
                        System.exit(1);
                    }

                    continue;
                }
                // If the timeout expires, that means the server received
                // the message and did not need to retransmit
                catch(SocketTimeoutException e){
                    currentRetransmissions=0;                  
                }
                catch(Exception e){
                    System.out.println("Error occured while sending a packet :/\n"+e);
                    System.exit(1);
                }            
            }
  */
        }catch(Exception e){
            System.out.println("Error in closing connection!\n" + e);
            System.exit(-1);
        }
        // At this point, we've closed the connection
        // Bye-Bye
        return;

    }
    
    
}