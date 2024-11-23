package Basic;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.HashSet;

//Each instance of the channel has its own socket,a map for client names and ports,a messagequeue,and the parameters.
public class UnreliableChannel {
    private static DatagramSocket server;
    private static HashMap<String, Integer> clients;
    private static Queue<DatagramPacket> messageQueue;
    private static double prob;
    private static int minD;
    private static int maxD;
    private static int totalDelayAtoB=0;
    private static int totalDelayBtoA=0;
    private static int packetsFromADelayed=0;
    private static int packetsFromBDelayed=0;
    private static int packetsFromALost=0;
    private static int packetsFromBLost=0;

    // serverSend()
    // Expects: Nothing
    // Returns: nothing, but is responsible for going through the messagequeue and
    // sending queued packets to their respective destinations.
    public static void serverSend() throws Exception {
        while (!messageQueue.isEmpty()) {
            DatagramPacket current = messageQueue.poll();
            // Store the message to be sent in a byte array.
            byte[] message = current.getData();
            // Get destination which is at index 2 our string and of length 1 ("A B 0")
            String destination = new String(current.getData(), 2, 1);
            // Get the port of the destination from the hashmap.
            int port;
            if (clients.containsKey(destination)) {
                port = clients.get(destination);
            } else {
                System.out.println("Invalid client packet not sent");
                return;
            }
            //random variables that will determine whether we drop the packet or not
            Random rand = new Random();
            int pktLossChance = rand.nextInt(101);

            //if random variable is less than probability, we will send the packet
            if((float)pktLossChance >= prob*100){
                // Intialize new packet to be sent. Note that since this is running locally we
                // do not need to get the IP address of the destination,only the port.
                DatagramPacket packetToBeSent = new DatagramPacket(message, message.length, InetAddress.getLocalHost(), port);
                
                    // Get a random delay between the specified range
                    int delay = minD+rand.nextInt((maxD - minD) + 1);
                    // Calculating the delay experienced by packets from A or B
                    // and tracking the number of packets that got delayed.
                    if(destination.equals("B")){
                        totalDelayAtoB+=delay;
                        if(delay != minD){
                            packetsFromADelayed++;
                        }
                    }
                    else{
                        totalDelayBtoA+=delay;
                        if(delay!= minD){
                            packetsFromBDelayed++;
                        }
                    }

                //Initialize a thread that handles the sleeping so that packets are not send sequentially but can be sent in parallel.
                   
             //   new Thread(() -> {
                    try {
                        // Sleep this thread as necessary to simulate the delay, and then send the packet.
                        Thread.sleep(delay);
                        server.send(packetToBeSent);
                    } catch (Exception e) {
                        System.out.println("Failed to send");
                    }
               // }).start();
            }else{
                // Tracking the number of packets that got dropped.
                if(destination.equals("B")){
                    packetsFromALost++;
                    continue;
                }
                packetsFromBLost++;
            }
        }

    }

    // addclient(DatagramPacket cleintMessage):
    // Expects: A DatagraPacket
    // Returns: nothing, but maps the sourcename to the local port.
    public static void addClient(DatagramPacket clientMessage) {
        // Get sourceName which is at index 0 our string and of length 1 ("A B 0")
        String sourceName = new String(clientMessage.getData(), 0, 1);
        if (!clients.containsKey(sourceName)) {
            clients.put(sourceName, clientMessage.getPort());
        }
    }

    // Expects: nothing
    // Returns: nothing, but informs clients to stop listening by
    // sending a special message
    public static void stopClientListening() throws Exception{
        HashSet<Integer> ports = new HashSet<Integer>(clients.values());
        byte[] message = "Communication has stopped!".getBytes();
        for (Integer port : ports) {
            server.send(new DatagramPacket(message, message.length, InetAddress.getLocalHost(), port));
        }
    }
    public static void main(String[] args) throws Exception {
        // Make sure operator inputs correct amount of parameters.
        if (args.length < 3) {
            System.out.println("Please enter correct parameters.(java UnreliableChannel <prob> <minD> <maxD>)");
        }
        prob = Double.parseDouble(args[0]);
        minD = Integer.parseInt(args[1]);
        maxD = Integer.parseInt(args[2]);
        clients = new HashMap<>();
        messageQueue = new LinkedList<>();
        int nOfMessages = 0;
        boolean both = false;

        // Create socket at port 8888 to listen for client messages, and facilitate the
        // sending of messages.
        server = new DatagramSocket(8888);
        byte[] buffer = new byte[1024];
        while (true) {
            nOfMessages++;
            // Initialize packet to be recieved and store data in the buffer.
            DatagramPacket messageFromClient = new DatagramPacket(buffer, buffer.length);
            server.receive(messageFromClient);
            // Store our client in a hashmap to easily forward messages.
            if (clients.size() < 2) {
                addClient(messageFromClient);
            }
            // Here we initialize a string from the data we get from the client, however we
            // make sure to start reading from 0 only untill the length of the message.
            // This prevents the slots in the buffer that arent written too affecting the output.
            String message = new String(messageFromClient.getData(), 0, messageFromClient.getLength());
            System.out.println(message);
            // When client sends end message, check if both clients have ended then stop.
            //Note that in the cases where we recieve END signals we do not add them to the message Queue as they are not valid messages to be sent.
            if (message.equals("END")) {
                //We decrement number of messages to not consider the end signals.
                nOfMessages--;
                if (both) {
                    //send a message to both to stop listening
                    stopClientListening();
                    break;
                }
                both = true;
                continue;
            }
            // Add message to queue, and if we have the needed number of clients then we
            // initiate sending from the server.
            messageQueue.add(messageFromClient);
            if (clients.size() >= 2) {
                serverSend();
            }
        }
        
        System.out.println("Packets received from user A: "+ (nOfMessages/2) +" | Lost: "+packetsFromBLost+" | Delayed: "+packetsFromBDelayed);
        System.out.println("Packets received from user B: "+ (nOfMessages/2) +"| Lost: "+packetsFromALost+"| Delayed: "+packetsFromADelayed);
        System.out.println("Average delay from A to B: "+ (float)(totalDelayAtoB)/nOfMessages+" ms.");
        System.out.println("Average delay from B to A: "+(float)(totalDelayBtoA)/nOfMessages+" ms.");
        server.close();
    }
}