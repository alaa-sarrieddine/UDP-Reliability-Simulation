package Bonus;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.Semaphore;
import java.util.HashSet;

public class UnreliableChannel {
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
    private static DelayDistribution distribution = DelayDistribution.uniform;
    private static pktLossPatterns pktLossPattern = pktLossPatterns.standard;
    
    // Boolean to track whether or not we are
    // in a burst state for our custom packet loss pattern
    private static boolean currentBurstState = false;
    // Counter for the amount of packets lost in
    // current burst state
    private static int pktsLostInBurst = 0;

    // The initial state in the Markov Model
    // for packet loss patterns is true. Here,
    // true indicates a "good" state while false indicates
    // a "bad" state
    private static boolean currentLossState = true;

    // This semaphore will be used to make sure the current state and 
    // information about packet loss patterns are accessed in a thread
    // safe manner.
    private static Semaphore accessToCurrentState = new Semaphore(1, true);

    // Boolean that indicates whether there are still messages being sent or not
    private static boolean serverIsSendingMessages = true;
    private static boolean moreThanTwoClients = false;
    // Add the true attribute here to make sure the mutexes are starvation free (It
    // insures a FIFO scheme). Additionally note that whenever the statistics or the
    // messageQueue are accessed for the rest of the code this must be done with the
    // mutex aquired for thread saftey.
    private static Semaphore accessToQueue = new Semaphore(1, true);
    private static Semaphore accessToStatistics = new Semaphore(1, true);

    static enum pktLossPatterns{
        custom,
        markov,
        standard,
    }

    public static void packetLossPatternChoice(){
        Scanner in = new Scanner(System.in);
        System.out.println("If you would like a specific packet loss pattern," + 
                            " choose from the below:\n1) markov\n2) custom\n3) standard");
        try {
            // Parse the argument into a pktLossPattern enum
            pktLossPattern = pktLossPatterns.valueOf(in.nextLine().toLowerCase());
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            // Handle invalid input or no input case
            System.out.println("Invalid or missing input. Defaulting to the use of the real constant as a " +
                                "probabilistic loss rate factor (standard model).");
        }
        in.close();
    }

    public static boolean loseThisPacket(Random rand){
        switch(pktLossPattern){
            // Gilbert-Elliot model for Packet Loss (Based on )
            case markov:
            try{
                // We will consider true to indicate a "good" state while
                // false to indicate a "bad" state

                // Could've probabily used accessToStatistics but thats
                // a minor issue, CHECK BEFORE FINAL SUBMISSION
                accessToCurrentState.acquire();
                // Probability of transitioning from "bad" to "good" state
                double probBadToGood = 0.4; 
                // Probability of transitioning from "good" to "bad" state
                double probGoodToBad = 0.15;
                // Probability of losing a packet while being in a "good" state
                double goodStatePktLossProb = 0.02;
                // Probability of losing a packet while being in a "bad" state
                double badStatePktLossProb = 0.75; 
                
                //random double in range [0,1]
                double randDouble = (double)(rand.nextInt(101))/100;;

                // STATE TRANSITIONS
                // Transition to "bad" state if: If we are in a "good" state
                // and the random double is greater than our probGoodToBad
                if (randDouble < probGoodToBad && currentLossState == true) {
                    currentLossState = false;
                }
                // Transition to "good" state if: we are in a "bad" state
                // and the random double is greater than our probBadToGood
                else if (randDouble < probBadToGood && currentLossState == false) {
                    currentLossState = false;
                }
                
                // Determine the probability to lose a packet 
                // based on our current state
                double currentLossChance = (currentLossState == true) ? goodStatePktLossProb : badStatePktLossProb;
                
                // Random double in range [0,1]
                // We are getting a new random variable to
                // make the output independent of the previous
                // randomization
                randDouble = (double)(rand.nextInt(101))/100;;
                // If random double is smaller than the probability for
                // a packet to be lost in our given state, return false.
                // In other words, return "Don't lose this packet"
                accessToCurrentState.release();
                if (rand.nextDouble() < currentLossChance) {return false;}
                return true;

            }catch(Exception e){
                System.out.println(e);
            }

            // Custom generator for bursty packet loss utilizing
            // the concept of states.
            case custom:
            try{
                accessToCurrentState.acquire();
                // Number of consecutive packets to drop
                // while in a burst state
                int lengthOfBurst = 6;
                
                // Probability of transitioning into a
                // burst state
                double probToGoInBurstState = 0.3;
                
                // This will store the result of our choice
                boolean packetWillBeLost= false;

                // Conditional for when we are in the burst state
                if (currentBurstState) {
                    pktsLostInBurst++;
                    // Once we reached the amount of packets that
                    // could be dropped given lengthOfBurst, we
                    // should transition back to the non-burst state.
                    if (lengthOfBurst < pktsLostInBurst) {
                        pktsLostInBurst = 0;
                        currentBurstState = false; 
                        // Here we don't lose the packet since we've
                        // already lost the length's worth of packets
                        packetWillBeLost = false;
                    }
                    // If we haven't yet lost enough packets,
                    // we will lose until we can't no more
                    // "inserts a motivational background song"
                    packetWillBeLost = true;   
                } 
                // If we are not in a burst state 
                else {
                    double randDouble = (double)(rand.nextInt(101))/100;
                    // If random variable is less than the probability
                    // to enter the the burst phase, we enter the burst phase
                    if (randDouble < probToGoInBurstState) {
                        currentBurstState = true;
                        // I don't know if this is needed
                        pktsLostInBurst = 0;
                        
                        // Start loosing packets
                        pktsLostInBurst++;
                        packetWillBeLost = true;
                    }
                    packetWillBeLost = false;
                }
                accessToCurrentState.release();
                return packetWillBeLost;
            }catch(Exception e){
                System.out.println(e);
            }

            
            // The ordinary packet loss pattern based on
            // probabilisitc loss rate factor
            case standard:
                // Getting a random integer in the range of [0,100] 
                int pktLossRandomVariable = rand.nextInt(101);
                // if random variable is greater than packetloss chance, we will send the
                // packet
                return (pktLossRandomVariable > packetLossChance * 100) ? false : true;
            default:
                throw new IllegalArgumentException("Error in identifying the distribution");
        }
    }

    // enum for all the distributions implemented
    static enum DelayDistribution {
        uniform,
        guassian,
        exponential,
        triangular,
    }

    public static int generateDelay(int minDelay, int maxDelay, Random rand) {
        switch (distribution) {
            // Guassian Distribution
            case guassian:
                // We will choose the mean to be the midpoint
                // of the given delay range.
                double mean = (minDelay + maxDelay) / 2.0;
                // We approximate the standard deviation using the 
                // 3-sigma rule which states that 99.7% of the values
                // in this distribution fall withing 3 standard deviations
                // from the mean
                double standardDev = (maxDelay - minDelay) / 6.0;
                int gaussianDelay;
                do {
                    // We first generate a value from a normal
                    // distribution over the range of [0,1]
                    double normalyDistributedValue = rand.nextGaussian(); 
                    // Then, we scale and shift this value to acheive
                    // our desired output
                    gaussianDelay = (int) (mean + normalyDistributedValue * standardDev);
                    // if the delay is outside our range, we generate a new delay.
                    // This is highly unlikely as we are covering 99.7% of the
                    // range through the 3-sigma property
                } while (gaussianDelay < minDelay || gaussianDelay > maxDelay);
                return gaussianDelay;
                
            // Exponential Distribution
            case exponential:
                // We will choose the mean of the rate parameter
                // to be our midpoint.
                double lambda = 1.0 / ((maxDelay - minDelay) / 2.0);
                int exponentialDelay;
                do {
                    // Get a unirformly distributed variable in
                    // the range of [0,1). This is perfect since if 1 was allowed, 
                    // it would correspond to an infinite value per this CDF
                    double uniformRandVar = rand.nextDouble(); 

                    // Solving for x in the exponential distribution CDF
                    // we get the formula below. Checkout inverse transform
                    // sampling method for more on this technique.
                    exponentialDelay = minDelay + (int) (-1*(Math.log(1 - uniformRandVar)/ lambda));
                    System.out.println("Delay we got is: "+ exponentialDelay);
                } while (exponentialDelay < minDelay || exponentialDelay > maxDelay);
                return exponentialDelay;
            
            // Triangular Distribution
            case triangular:
                // Here we chose the peak of our distribution
                // to be our midpoint. Again, we can ask the user
                // for this information but this gets the job done.
                double mode = (minDelay + maxDelay) / 2.0;
                //random float in range [0,1)
                double u = rand.nextDouble();
                int triangularDelay;
                // Now we will have to determine where our random value
                // falls in the distribution: left or right side. This
                // condition checks whether our random variable is increasing
                // linearly towards the mode 
                if ((mode - minDelay) / (maxDelay - minDelay) > u) {
                    // random variable on the left side, calculate accordingly
                    triangularDelay = (int) (Math.sqrt((maxDelay - minDelay) * u * (mode - minDelay)) + minDelay);
                } else {
                    // random variable on the right side, calculate accordingly
                    triangularDelay = (int) (maxDelay - Math.sqrt((maxDelay - minDelay) * (1 - u) * (maxDelay - mode)));
                }
                return triangularDelay;
            
            // Uniform Distribution
            case uniform: 
                // Concise, simple, and clean
                return minDelay + rand.nextInt((maxDelay - minDelay) + 1);

            default:
                throw new IllegalArgumentException("Error in identifying the distribution");      
        }
    }
    // serverSend()
    // Expects: Nothing
    // Returns: nothing, but is responsible for
    // sending queued packets to their respective destinations.
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
            // it to the queue for when the destination eventually becomes valid (Needed for
            // N clients, also works off the assumption that the destination inputed was
            // correct otherwise infinite loop)
            accessToQueue.acquire();
            messageQueue.add(current);
            accessToQueue.release();
            return;
        }
        // Random number generator used in getting the
        // delay and packet loss
        Random rand = new Random();

        if (!loseThisPacket(rand)) {
            // Intialize new packet to be sent. Note that since this is running locally we
            // do not need to get the IP address of the destination,only the port.
            DatagramPacket packetToBeSent = new DatagramPacket(message, message.length, InetAddress.getLocalHost(),
                    port);

            // Get a delay from the inputted distribution and,
            // minDelay and maxDelay values
            int delay = generateDelay(minDelay, maxDelay, rand);
            System.out.println("Packet Experienced "+delay+" ms delay.");
            
            try {
                // Sleep this thread as necessary to simulate the delay, and then send the
                // packet.
                Thread.sleep(delay);

                // Calculating the delay experienced by packets from A or B
                // and tracking the number of packets that got delayed, in a thread safe manner.
                accessToStatistics.acquire();
                if (destination.equals("B")) {
                    // Here, the total delay is the delay experienced by each packet
                    // since initially being put in a queue. The delayed packets are
                    // those who recieved an additional delay because of the random
                    // variables
                    totalDelayA += System.currentTimeMillis() - current.receivedTime;
                    // If the delay is equal to the minDelay then the packet wasnt delayed.
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
                String test = new String(packetToBeSent.getData(), 0, packetToBeSent.getLength()).trim();
                System.out.println("SYNCH sending following packet: " + test);
            } catch (Exception e) {
            }

        } else {
            // Tracking the number of packets that got dropped based on their destination
            // which tells us where they came from(For N clients version use the source from
            // the packet and an array representing the packets lost from each client).
            if (destination.equals("B")) {
                packetsFromALost++;
                return;
            }
            packetsFromBLost++;
        }

    }

    public static void threadCreator() throws Exception {
        // Check whether queue is empty or not in a thread safe manner.
        accessToQueue.acquire();
        boolean queueContainsMessages = !messageQueue.isEmpty();
        accessToQueue.release();
        // While the queue contains messages to be sent, or the server is actively still
        // recieving and sending messages we loop through.
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
                // This part might be not needed we could maybe send messagetobesent, but I cant
                // check it now CHECK BEFORE FINAL VERSION!

                // Arthur: I think this is uneccessary because the object we are popping of off the queue 
                // is unreachable by anyone other than the current thread
                //DatagramPacket finalPacket = new DatagramPacket(messageToBeSent.getData(), messageToBeSent.getLength(),
                //        messageToBeSent.getAddress(), messageToBeSent.getPort());
                // Look up MultiThreading Lambda function to understand the syntax for this,
                // basically as shorthand for making a thread and giving it a function to
                // execute.
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

        // The fourth argument is reserved for deciding
        // which delay distribution the user wants to 
        // choose.
        try {
            // Parse the argument into a DelayDistribution enum
            distribution = DelayDistribution.valueOf(args[3].toLowerCase());
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            // Handle invalid input or no input case
            System.out.println("Invalid or missing input. Defaulting to uniform distribution.");
        }
        // Let's the user choose one of the 
        // packet loss patterns we've implemented
        packetLossPatternChoice();



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
            //New buffer initialized for each packet to make sure their packets get allcated on the heap when they are being sent between users and functions.
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
                    // Disable thread that is sending messages in the background
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

class PacketObject{
    protected long receivedTime;
    protected DatagramPacket packetData;
    
    public PacketObject(long receivedTime, DatagramPacket packetData){
        this.packetData = packetData;
        this.receivedTime = receivedTime;
    }
}

