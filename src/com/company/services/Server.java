package com.company.services;

import com.company.delegates.ServerDelegate;
import com.company.messages.Message;
import com.company.messages.MessageType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Server {

    private ServerDelegate delegate;
    private ServerSocket listener;
    private List<HandlerThread> handlerThreads;

    public Server(ServerDelegate delegate){

        this.delegate = delegate;
        this.handlerThreads = new ArrayList<>();
    }

    public void start(int port) throws Exception{

        listener = new ServerSocket(port);

        while (true) {
            // Start a new thread to handle an incoming connection
            HandlerThread t = new HandlerThread(listener.accept(), delegate);
            handlerThreads.add(t);
            t.start();
        }
    }

    public void closeConnection() throws Exception{
        listener.close();
    }

    // Yo Nick call this method from Peer when you need to send have message to everyone.
    public void sendMessageToAllClients(Message m){
        handlerThreads.forEach((thread) -> {
            if(thread.isAlive()){ // TODO: Should probably remove threads from the list once the thread goes inactive but too lazy right now and it won't break anything.
                thread.addOutboundMessage(m); // TODO: Also we haven't really implemented closing connections as part of the protocol yet so maybe do this with that stuff.
            }
        });
    }

    // Each of these threads will be created to handle each client request.
    private static class HandlerThread extends Thread {
        private Socket connection;
        private ServerDelegate delegate;
        private DataInputStream in;	//stream read from the socket
        private DataOutputStream out;    //stream write to the socket
        private int clientPeerID;
        private ConcurrentLinkedQueue<Message> highPriorityOutboundMessageQueue; // Handshake and Bitfield should be sent as high priority messages
        private ConcurrentLinkedQueue<Message> standardOutboundMessageQueue; // All other messages will be put here.
        private ConcurrentLinkedQueue<Message> inboundMessageQueue;
        private Thread listenerThread;

        public HandlerThread(Socket connection, ServerDelegate delegate) {
            this.connection = connection;
            this.delegate = delegate;
            this.clientPeerID = -1;
            this.highPriorityOutboundMessageQueue = new ConcurrentLinkedQueue<>();
            this.standardOutboundMessageQueue = new ConcurrentLinkedQueue<>();
            this.inboundMessageQueue = new ConcurrentLinkedQueue<>();
        }

        public void run() {
            try{
                handleRequest();
            } catch (Exception e) {
                e.printStackTrace();
            } finally{
                try {
                    closeConnection();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        public void handleRequest() throws Exception{
            //initialize Input and Output streams
            out = new DataOutputStream(connection.getOutputStream());
            out.flush();
            in = new DataInputStream(connection.getInputStream());

            while (true)
            {
                // TODO: Make sure that no race conditions can result from being able to send more than one message
                // I think the biggest risk here is having multiple back and forth's happening at once. I don't think it
                // would actually break things but it could result in worse performance and its kinda weird so there might
                // be edge cases I haven't thought of yet.

                //receive the message sent from the client if we are not already waiting for the next message.
                if (listenerThread == null || !listenerThread.isAlive()){
                    listenerThread = new Thread(() -> {
                        this.readMessage(in);
                    });
                    listenerThread.start();
                }

                // Check the Outbound queues and send any messages that need to be sent.
                while(!highPriorityOutboundMessageQueue.isEmpty()){
                    sendMessage(highPriorityOutboundMessageQueue.poll());
                }
                while(!standardOutboundMessageQueue.isEmpty()){
                    sendMessage(standardOutboundMessageQueue.poll());
                }

                // Check if we have any incoming messages
                while(!inboundMessageQueue.isEmpty()){
                    notifyDelegate(inboundMessageQueue.poll());
                }
            }
        }

        // Adds a message to the standard outbound message queue
        public void addOutboundMessage(Message m){
            standardOutboundMessageQueue.add(m);
        }

        private void notifyDelegate(Message message){

            Message m;
            switch(message.getType()){
                case HANDSHAKE:
                    m =  delegate.onClientHandshakeReceived(message, this::setClientPeerID);
                    break;
                case BITFIELD:
                    m = delegate.onClientBitfieldReceived(message, clientPeerID);
                    break;
                case INTERESTED:
                    m = delegate.onInterestedReceived(message, clientPeerID);
                    break;
                case NOT_INTERESTED:
                    m = delegate.onNotInterestedReceived(message, clientPeerID);
                    break;
                case REQUEST:
                    m = delegate.onRequestReceived(message, clientPeerID);
                    break;
                default:
                    m = null;
            }

            // Add the message to the appropriate message queue.
            if (m != null && (m.getType() == MessageType.HANDSHAKE || m.getType() == MessageType.BITFIELD)){
                highPriorityOutboundMessageQueue.add(m);
            } else if (m != null){
                standardOutboundMessageQueue.add(m);
            }
        }

        public void closeConnection() throws Exception{
            in.close();
            out.close();
            connection.close();
        }

        private void readMessage(DataInputStream in){
            try{
                int length = in.readInt();
                byte[] request = new byte[length];
                in.readFully(request);
                Message newMessage = MessageType.createMessageFromByteArray(request);
                inboundMessageQueue.add(newMessage);
            } catch (Exception e){
                e.printStackTrace();
            }
        }

        //send a message to the output stream
        private void sendMessage(Message msg)
        {
            try{
                out.writeInt(msg.getBytes().length);
                out.write(msg.getBytes());
                out.flush();
            }
            catch(IOException ioException){
                ioException.printStackTrace();
            }
        }

        public void setClientPeerID(int clientPeerID){

            this.clientPeerID = clientPeerID;
        }
    }
}
