package Basic;
import java.net.*;
public class UserClient {
    public static void main(String[] args)throws Exception {
        //We make sure sure inputs the source name and the destination.
        if (args.length < 2) {
            System.out.println("Please enter source, and destination(java Userclient <source> <destination>)");
            return;
        }
        DatagramSocket client = new DatagramSocket();
        InetAddress ip = InetAddress.getLocalHost();
        String identifier = args[0];
        String destination = args[1];
        for (int i = 0; i < 1000; i++) {
            //Sequence number is 0 on first packet sent 1 on second and it alternates as per the equation below.
            int seq = i % 2;
            //Formulate our message to be sent.
            byte[] msg = (identifier+" "+destination+" "+seq).getBytes();
            //Initialize new packet to be sent and send it.
            DatagramPacket send = new DatagramPacket(msg, msg.length,ip,8888);
            client.send(send);
            //Sleep for half a second before sending next packet.
            Thread.sleep(500);      
        }
        //Send final packet with end message.
        byte[] endMessage = "END".getBytes();
        DatagramPacket last = new DatagramPacket(endMessage, endMessage.length,8888);
        client.send(last);
        System.out.println("Client: "+identifier + " done sending.");
    }
}
