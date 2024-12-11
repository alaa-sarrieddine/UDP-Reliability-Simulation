package Phase3;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Random;

public class RUDPDestination {
    private static DatagramSocket server;
    static double packetLossChance = 0.5;
    static int minDelay = 0;
    static int maxDelay = 100;
    // serverSend()
    // Expects: A valid PacketObject containing a valid datagram packet which has a
    // valid destination.
    // Returns: nothing, but is responsible for sednding the packet to its correct
    // destination.
    public static void serverSend(PacketObject current,int destinationPort) throws Exception {
        if (current == null) {
            return;
        }
        byte[] message = current.packetData.getData();
        // random variables that will determine whether we drop the packet or not
        Random rand = new Random();
        int packetLossRandomVariable = rand.nextInt(101);
        //Certain issues with how random works and doubles were causing an issue where even when packetLossChance was 0 some packets would be dropped this insures that doesnt happen.
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
            } catch (Exception e) {}
        }
    }
    //initializeConnection()
    //Expects and returns nothing, is responsible for performing the three way handshake to establish connection.
    public static void initializeConnection(){

    }
    public static void main(String[] args) throws Exception {
        // Make sure operator inputs correct amount of parameters.
        HashMap<Integer,byte[]> fragments = new HashMap<>();
        if (args.length < 1) {
            System.out.println(
                    "Please enter correct parameters.(java RUDPDestination.java <Recieve Port>");
        }    
        int port = Integer.parseInt(args[0]);
        int nOfMessages = 0;
        // Create socket at port 8888 to listen for client messages, and facilitate the
        // sending of messages.
        server = new DatagramSocket(port);
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
