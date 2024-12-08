package Basic;

import java.net.*;
import java.util.concurrent.Semaphore;


public class UserClient {
    private static DatagramSocket client;
    private static long programTimeLimit =-1; 
    private static long startTime;
    private static boolean endMessageSent = false;
    private static InetAddress ip;
    
    private static Semaphore accessToEndMessageFlag= new Semaphore(1, true);

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
                accessToEndMessageFlag.acquire();
                if(endMessageSent==false){
                    // Send final packet with end message.
                    byte[] endMessage = "END".getBytes();
                    DatagramPacket last = new DatagramPacket(endMessage, endMessage.length, ip, 8889);
                    client.send(last);
                }
            }catch(Exception e){
                System.out.println(e);
            }
            endMessageSent=true;
            accessToEndMessageFlag.release();
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
        if(args.length==3){
            // The third argument inputted by the user will be the maximum amount
            // of time the program can execute, in seconds.
            // This will be used to terminate the program in case it exceeded
            // its time limit; avoids infinite loops in some cases!
            programTimeLimit = Long.parseLong(args[2]) * 1000;
            System.out.println(programTimeLimit);
        }
        System.out.println("Please note you could have inputed the maximum time to execute" + 
                                "(java Userclient <source> <destination> <programTimeLimit>)");

        // creating a listener thread
        listener lThread = new listener(client);
        lThread.start();

        for (int i = 0; i < 1000; i++) {
            // Sequence number is 0 on first packet sent 1 on second and it alternates as
            // per the equation below.
            int seq = i % 2;
            // Formulate our message to be sent.
            byte[] msg = (identifier + " " + destination + " " + seq).getBytes();
            // Initialize new packet to be sent and send it.
            DatagramPacket send = new DatagramPacket(msg, msg.length, ip, 8888);
            System.out.println("Sending: "+identifier + " " + destination + " " + seq);
            client.send(send);
            // Sleep for half a second before sending next packet.
            Thread.sleep(500);
            //check if program is overdue
            checkForTimeLimitExceeded();
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

    public void run() {
        // Recieves and reads the packets until the server closed communication
        while (!isListening) {
            try {
                recievePackets();
            } catch (Exception e) {
                return;
            }
            //check if program is overdue
            UserClient.checkForTimeLimitExceeded();
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
