package Basic;

import java.net.*;

public class UserClient {
    private static DatagramSocket client;
  

    public static void main(String[] args) throws Exception {
        // We make sure the operator inputs the source name and the destination.
        if (args.length < 2) {
            System.out.println("Please enter source, and destination(java Userclient <source> <destination>)");
            return;
        }
        // Initalize socket for client to send and recieve packets, then set timeout to
        // 0.5 seconds.
        client = new DatagramSocket();
        client.setSoTimeout(1);
        InetAddress ip = InetAddress.getLocalHost();
        String identifier = args[0];
        String destination = args[1];

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
            client.send(send);
            // Sleep for half a second before sending next packet.
            Thread.sleep(500);
        }

        // Send final packet with end message.
        byte[] endMessage = "END".getBytes();
        DatagramPacket last = new DatagramPacket(endMessage, endMessage.length, ip, 8888);
        client.send(last);
        System.out.println("Client: " + identifier + " done sending.");

        // Program stops once the two threads have finished execution
        lThread.join();
        client.close();
    }


}

class listener extends Thread{
    private static DatagramSocket client;
    private static boolean otherStopped=false;

    // thread object that will continously read incoming packets
    listener(DatagramSocket Client){
        client = Client;
    }

    public void run(){
        // Recieves and reads the packets until the server closed communication
        while(!otherStopped){
            try{
                recievePackets();
            }catch(Exception e){
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
        } catch (Exception e) {}
        String message = new String(recieved.getData(), 0, recieved.getLength()).trim();
        
        //if communication has stopped, we need to stop listening from the socket
        if(message.equals("Communication has stopped!")){ 
            otherStopped=true;
        }
    }

}
