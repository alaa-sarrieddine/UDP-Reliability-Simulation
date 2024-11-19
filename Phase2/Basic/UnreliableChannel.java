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
            //Here we initialize a string from the data we get from the client, however we make sure to start reading from 0 only untill the length of the message.
            //This prevents the slots that arent written to affecting the output
            String message = new String(messageFromClient.getData(),0,messageFromClient.getLength());
            System.err.println(message);
            //When client sends end message break.
            if (message.equals("END")) {
                break;
            }
        }
        System.out.println("Client sent end message!");
    }
}