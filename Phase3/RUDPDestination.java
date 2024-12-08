package Phase3;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.HashSet;

public class RUDPDestination {
    private static DatagramSocket server;
    private static HashMap<String, Integer> clients;
    private static Queue<PacketObject> messageQueue;
    private static double packetLossChance;
    private static int minDelay;
    private static int maxDelay;
    private static long totalDelayA = 0;
    private static long totalDelayB = 0;
    private static int packetsFromADelayed = 0;
    private static int packetsFromBDelayed = 0;
    private static int packetsFromALost = 0;
    private static int packetsFromBLost = 0;
    // Boolean that indicates whether there are still messages being sent or not
    private static boolean serverIsSendingMessages = true;
    private static boolean moreThanTwoClients = false;
    // Add the true attribute to the semaphores here to make sure the mutexes are
    // starvation free (It
    // insures a FIFO scheme). Additionally note that whenever the statistics or the
    // messageQueue are accessed for the rest of the code this must be done with the
    // mutex acquired for thread saftey.
    private static Semaphore accessToQueue = new Semaphore(1, true);
    private static Semaphore accessToStatistics = new Semaphore(1, true);

    // serverSend()
    // Expects: A valid PacketObject containing a valid datagram packet which has a
    // valid destination.
    // Returns: nothing, but is responsible for sednding the packet to its correct
    // destination.
    public static void serverSend(PacketObject current) throws Exception {
        if (current == null) {
            return;
        }
        // Store the message to be sent in a byte array.
        byte[] message = current.packetData.getData();
        // Get destination which is at index 2 our string and of length 1 ("A B 0")
        String destination = new String(current.packetData.getData(), 2, 1);
        // Get the port of the destination from the hashmap.
        int port;
        if (clients.containsKey(destination)) {
            port = clients.get(destination);
        } else {
            System.out.println("Invalid client packet not sent");
            // In the case we are sending a packet and its destination is invalid we re add
            // it to the queue for when the destination eventually becomes valid (works off
            // the assumption that the destination inputed was
            // correct otherwise infinite loop)
            accessToQueue.acquire();
            messageQueue.add(current);
            accessToQueue.release();
            return;
        }
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
                    port);

            // Get a random delay betweem ,mindDelay, and maxDelay
            int delay = minDelay + rand.nextInt((maxDelay - minDelay) + 1);

            try {
                // Sleep this thread as necessary to simulate the delay, and then send the
                // packet.
                Thread.sleep(delay);

                // Calculating the delay experienced by packets from A or B
                // and tracking the number of packets that got delayed, in a thread safe manner.
                accessToStatistics.acquire();
                  //Here the delay is counted from the time of reception, untill they are sent (This helps account for the delay when one client starts sending before another.).
                   //The packets considered "Delayed" are ones that face additional delay as a result of the random variables.
                   // If the delay is equal to the minDelay then the packet was not delayed.
                if (destination.equals("B")) {
                    totalDelayA += System.currentTimeMillis() - current.receivedTime;
                    if (delay != minDelay) {
                        packetsFromADelayed++;
                    }
                } else {
                    totalDelayB += System.currentTimeMillis() - current.receivedTime;
                    if (delay != minDelay) {
                        packetsFromBDelayed++;
                    }
                }
                accessToStatistics.release();
                server.send(packetToBeSent);
            } catch (Exception e) {
            }

        } else {
            // Tracking the number of packets that got dropped based on their destination
            // which tells us where they came from.
            if (destination.equals("B")) {
                packetsFromALost++;
                return;
            }
            packetsFromBLost++;
        }

    }

    public static void threadCreator() throws Exception {
        // Check whether queue is empty or not, in a thread safe manner.
        accessToQueue.acquire();
        boolean queueContainsMessages = !messageQueue.isEmpty();
        accessToQueue.release();
        // While the queue contains messages to be sent, or the server is actively still
        // recieving and sending messages we loop.
        while (serverIsSendingMessages || queueContainsMessages) {
            // If the queue is empty we loop untill it isnt aslong as
            // serverIsSendingMessages is true
            accessToQueue.acquire();
            if (messageQueue.isEmpty()) {
                queueContainsMessages = false;
                accessToQueue.release();
                continue;
                // If queue contains messages but there isnt atleast two clients we refrain from
                // sending since the port of the destination would not be known.
            } else if (moreThanTwoClients) {
                // If there are enough clients we pop a packet from the queue under mutex, and
                // then we initialize a new thread which is tasked with sending the packet.
                PacketObject messageToBeSent = messageQueue.poll();
                accessToQueue.release();
                //Make a new thread tasked with executing the serverSend() function for the message.
                Thread messageSender = new Thread(() -> {
                    try {
                        serverSend(messageToBeSent);
                    } catch (Exception e) {
                        System.out.println("error");
                    }
                });
                messageSender.start();
                continue;
            }
            // Release the mutex for the case where there are messages but not more than two
            // clients.
            accessToQueue.release();
            // Small sleep in the case the queue there isnt more than two clients to reduce
            // the cpu overhead of the loop.
            Thread.sleep(10);
        }
        // send a message to both to stop listening
        stopClientListening();
    }

    // addclient(DatagramPacket cleintMessage):
    // Expects: A DatagramPacket
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
    public static void stopClientListening() throws Exception {
        HashSet<Integer> clientPorts = new HashSet<Integer>(clients.values());
        for (Integer port : clientPorts) {
            byte[] message = "Communication has stopped!".getBytes();
            server.send(new DatagramPacket(message, message.length, InetAddress.getLocalHost(), port));
        }
    }

    public static void main(String[] args) throws Exception {
        // Make sure operator inputs correct amount of parameters.
        if (args.length < 3) {
            System.out.println(
                    "Please enter correct parameters.(java UnreliableChannel <packetLossChance> <mindDelay> <maxDelay>)");
        }
        packetLossChance = Double.parseDouble(args[0]);
        minDelay = Integer.parseInt(args[1]);
        maxDelay = Integer.parseInt(args[2]);
        clients = new HashMap<>();
        messageQueue = new LinkedList<>();
        int nOfMessages = 0;
        boolean both = false;

        Runnable sendingThread = () -> {
            try {
                threadCreator();
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        Thread sender = new Thread(sendingThread);
        sender.start();

        // Create socket at port 8888 to listen for client messages, and facilitate the
        // sending of messages.
        server = new DatagramSocket(8888);
        while (true) {
            // New buffer initialized for each packet to make sure their packets get
            // allcated on the heap when they are being sent between users and functions.
            byte[] buffer = new byte[1024];
            nOfMessages++;
            // Initialize packet to be recieved and store data in the buffer.
            DatagramPacket messageFromClient = new DatagramPacket(buffer, buffer.length);
            server.receive(messageFromClient);
            // Here we initialize a string from the data we get from the client, however we
            // make sure to start reading from 0 only untill the length of the message.
            // This prevents the slots in the buffer that arent written too affecting the
            // output.
            String message = new String(messageFromClient.getData(), 0, messageFromClient.getLength());
            System.out.println("Server recieving: " + message);
            // When client sends end message, check if both clients have ended then stop.
            // Note that in the cases where we recieve END signals we do not add them to the
            // message Queue as they are not valid messages to be sent.
            if (message.equals("END")) {
                // We decrement number of messages to not consider the end signals.
                nOfMessages--;
                if (both) {
                    // Disable thread that is sending messages in the background.
                    serverIsSendingMessages = false;
                    break;
                }
                both = true;
                continue;
            }
            // If we have less than two clients we map their names to the ports,otherwise we
            // set the flag to true.
            if (clients.size() < 2) {
                addClient(messageFromClient);
            } else {
                moreThanTwoClients = true;
            }
            // Add packet object to queue in a thread safe manner.
            accessToQueue.acquire();
            // Recording the time at which it has been added to the queue
            // to later use in delay calculation
            messageQueue.add(new PacketObject(System.currentTimeMillis(), messageFromClient));
            accessToQueue.release();
        }

        System.out.println("Packets received from user A: " + (nOfMessages / 2) + " | Lost: " + packetsFromBLost
                + " | Delayed: " + packetsFromBDelayed);
        System.out.println("Packets received from user B: " + (nOfMessages / 2) + "| Lost: " + packetsFromALost
                + " | Delayed: " + packetsFromADelayed);

        // This avoids the error encountered when dividing by 0
        // in the case where packetsFromADelayed or packetsFromBDelayed = 0.
        // Alternatively, this could be done by setting the answer to "undefined"
        // when divisor = 0
        float avgPacketDelayAToB = (packetsFromADelayed == 0) ? 0.0f : (float) totalDelayA / packetsFromADelayed;
        float avgPacketDelayBToA = (packetsFromBDelayed == 0) ? 0.0f : (float) (totalDelayB) / packetsFromBDelayed;

        System.out.println("Average delay from A to B: " + avgPacketDelayAToB + " ms.");
        System.out.println("Average delay from B to A: " + avgPacketDelayBToA + " ms.");
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
