package Bonus;

import java.net.*;
import java.util.Random;
import java.util.concurrent.Semaphore;


public class UserClient {
    private static DatagramSocket client;
    private static long programTimeLimit =-1; 
    private static long startTime;
    private static boolean endMessageSent = false;
    private static InetAddress ip;
    private static packetGenerationDistribution distribution = packetGenerationDistribution.constant;

    public static Semaphore accessToEndMessageFlag= new Semaphore(1, true);
    
    // enum for all the distributions implemented
    static enum packetGenerationDistribution {
        uniform,
        gaussian,
        exponential,
        triangular,
        constant,
    }

    // generateDistributionDelay()
    // Expects: 
    // - `minDelay` - type int : Minimum delay in milliseconds.
    // - `maxDelay` - type int : Maximum delay in milliseconds.
    // - `rand` - type Random : Random object used for calculations.
    //
    // Returns: 
    // - type int: Delay in milliseconds based on the specified distribution.
    //
    // Description:
    // Generates a delay value using the specified distribution:
    //      - Gaussian: Normally distributed around the midpoint of [minDelay, maxDelay].
    //      - Exponential: Based on an exponential distribution with a rate derived from the range.
    //      - Triangular: Triangular distribution peaking at the midpoint of the range.
    //      - Uniform: Uniformly distributed within the range [minDelay, maxDelay].
    //      - Constant: Fixed delay of 500 milliseconds.
    //
    // Throws:
    // - IllegalArgumentException: For an unrecognized distribution type.
    // (nearly impossible to occur)
    public static int generateDistributionDelay(int minDelay, int maxDelay, Random rand) {
        switch (distribution) {
            // Gaussian Distribution
            case gaussian:
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

            // Constant distribution
            case constant:
                return 500;

            default:
                throw new IllegalArgumentException("Error in identifying the distribution");      
        }
    }


    // Expects:Nothing
    // Returns:Nothing, but if the time the program has
    // been running exceeded the time provided by the user
    // we terminate the Java Virtual Machine (the whole process)
    public static void checkForTimeLimitExceeded(){
        // if the user has set a time limit on the program and that 
        // limit has been exceeded, then we begin terminating this process
        if(programTimeLimit!=-1 && startTime+programTimeLimit<System.currentTimeMillis()){
            System.out.println("What took you so long :/\nTime limit of the program has exceeded");
            try{
                // we always send a END message to the server
                // to inform it of our disconnection
                if(endMessageSent==false){
                    // Send final packet with end message.
                    byte[] endMessage = "END".getBytes();
                    DatagramPacket last = new DatagramPacket(endMessage, endMessage.length, ip, 8888);
                    client.send(last);
                }
            }catch(Exception e){
                System.out.println(e);
            }
            endMessageSent=true;
            // Closing the socket and terminating this process
            client.close();
            System.exit(0);
        }        
    }

    public static void main(String[] args) throws Exception {
        // Time at which the program has started in milliseconds
        startTime = System.currentTimeMillis();
        // We make sure the operator input the source name and the destination.
        if (args.length < 2) {
            System.out.println("Please enter source, and destination(java Userclient <source> <destination>)");
            return;
        }
        // Initalize socket for client to send and recieve packets, then set timeout to
        // 100 ms
        client = new DatagramSocket();
        client.setSoTimeout(100);
        ip = InetAddress.getLocalHost();
        String identifier = args[0];
        String destination = args[1];
        // The third argument inputted by the user will be the maximum amount
        // of time the program can execute, in seconds.
        // This will be used to terminate the program in case it exceeded
        // its time limit; avoids infinite loops in some cases!
        // Enter -1 if you don't want a time limit
        programTimeLimit = Long.parseLong(args[2]);
        // If program limit -1, keep as it, else, get the time in milliseconds
        programTimeLimit = (programTimeLimit == -1) ? -1 : programTimeLimit * 1000;

        int minDelay = Integer.parseInt(args[3]);
        int maxDelay = Integer.parseInt(args[4]);

        // The fourth argument is reserved for deciding
        // which packet generation distribution the user wants to 
        // choose.
        try {
            // Parse the argument into a packetGenerationDistribution enum
            distribution = packetGenerationDistribution.valueOf(args[5].toLowerCase());
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            // Handle invalid input or no input case
            System.out.println("Invalid or missing input. Defaulting to constant distribution.");
        }
        // Random object to be used in generating different
        // delay distributions
        Random rand = new Random();

        // creating a listener thread
        listener lThread = new listener(client);
        lThread.start();

        // note we are only sending 10 packets for testing purposes MUST BE CHANGED FOR
        // FINAL VERSION!!!
        for (int i = 0; i < 10; i++) {
            // Sequence number is 0 on first packet sent 1 on second and it alternates as
            // per the equation below.
            int seq = i % 2;
            // Formulate our message to be sent.
            byte[] msg = (identifier + " " + destination + " " + seq).getBytes();
            // Initialize new packet to be sent and send it.
            DatagramPacket send = new DatagramPacket(msg, msg.length, ip, 8888);
            System.out.println("Sending: "+identifier + " " + destination + " " + seq);
            client.send(send);
            // The amount of delay generated given a distribution
            // No need to use semaphores because we have only 1 thread executing
            // this code
            int delay = generateDistributionDelay(minDelay, maxDelay, rand);
            // Sleep for the amount of delay generated
            Thread.sleep(delay);
            System.out.println("This packet got delayed "+delay+" ms.");
            
            // Check if program is overdue
            accessToEndMessageFlag.acquire();
            checkForTimeLimitExceeded();
            accessToEndMessageFlag.release();
        }
        // Acquire a permit from the semaphore to be able to change the
        // value of the EndMessageFlag in a thread-safe manner
        accessToEndMessageFlag.acquire();
        // Send final packet with end message.
        byte[] endMessage = "END".getBytes();
        DatagramPacket last = new DatagramPacket(endMessage, endMessage.length, ip, 8888);
        client.send(last);
        endMessageSent=true;
        accessToEndMessageFlag.release();
        System.out.println("Client: " + identifier + " done sending.");

        // Program stops once the two threads have finished execution
        lThread.join();
        client.close();

        // This user has kept track of all the messages received
        // from the other user!
        System.out.println("Total number of packets received from " + destination + " : "
                          + listener.nOfPacketsFromB);
    }

}

class listener extends Thread {
    private static DatagramSocket client;
    private static boolean isListening = false;
    public static int nOfPacketsFromB=0;

    // thread object that will continously read incoming packets
    listener(DatagramSocket Client) {
        client = Client;
    }

    // Keeps checking for received packets by calling 
    // receivedPackets()
    public void run() {
        // Recieves and reads the packets until the server closed communication
        while (!isListening) {
            try {
                recievePackets();
           
            //check if program is overdue
            UserClient.accessToEndMessageFlag.acquire();
            UserClient.checkForTimeLimitExceeded();
            UserClient.accessToEndMessageFlag.release();
            } catch (Exception e) {
                System.out.println(e);
                return;
            }
        }
        return;
    }

    // recievePackets:
    // Expects:Nothing
    // Returns:Nothing, but listens for a packet for a period of 0.5 seconds, if
    // a packet is recieved its contents are printed, otherwise it times out and no
    // packets are recieved
    public static void recievePackets() {
        byte[] buffer = new byte[1024];
        DatagramPacket recieved = new DatagramPacket(buffer, buffer.length);
        try {
            client.receive(recieved);
        } catch (Exception e) {
            return;
        }

        String message = new String(recieved.getData(), 0, recieved.getLength()).trim();
        System.out.println("Message from server: "+message);

        // if communication has stopped, we need to stop listening from the socket
        if (message.equals("Communication has stopped!")) {
            isListening = true;
            return;
        }
        // keeping track of all the packets sent from the
        // other user
        nOfPacketsFromB++;
    }

}

//Debugging Notes
/*
 * Decreasing the timeout to 1 ms doesn't affect (1) clients' ability to properly read from the buffer
 * (2) clients ability to receive packets. Previous problems were from the server-side i believe.
 * 
 * If client program started before server, it will never close the socket because we are continously
 * listening to the server
 *    - solve through the use of an optional timer. We will add this in the report.
 * 
 * Timeout doesn't change the speed of concurrency, very negligible improvements
 */