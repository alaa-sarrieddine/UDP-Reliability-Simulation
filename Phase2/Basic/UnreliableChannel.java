package Basic;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class UnreliableChannel {

    public static void main(String[] args)throws Exception {
        //Make sure operator inputs correct amount of parameters.
        if (args.length < 3) {
            System.out.println("Please enter correct parameters.(java UnreliableChannel <p> <minD> <maxD>)");
        }
        double p = Double.parseDouble(args[0]);
        int minD = Integer.parseInt(args[1]);
        int maxD = Integer.parseInt(args[2]);
        int testing = 0;
        //Create socket at port 8888 to listen for client messages.
        DatagramSocket server = new DatagramSocket(8888);
        byte[] buffer = new byte[1024];
        while (true) {
            //Initialize packet to be recieved and store data in the buffer. Then print it out.
            DatagramPacket messageFromClient = new DatagramPacket(buffer, buffer.length);
            server.receive(messageFromClient);
            String message = new String(messageFromClient.getData());
            System.err.println(message);
            //When client sends end message break.
            if (message.equals("END")) {
                break;
            }
        }
        System.out.println("Client sent end message!");
    }
}