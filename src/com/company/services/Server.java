package com.company.services;

import com.company.delegates.ServerDelegate;
import com.company.messages.Message;
import com.company.messages.MessageType;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    private ServerDelegate delegate;
    private ServerSocket listener;

    public Server(ServerDelegate delegate){
        this.delegate = delegate;
    }

    public void start(int port) throws Exception{

        listener = new ServerSocket(port);

        while (true) {
            // Start a new thread to handle an incoming connection
            new HandlerThread(listener.accept(), delegate).start();
        }
    }

    public void closeConnection() throws Exception{
        listener.close();
    }

    // Each of these threads will be created to handle each client request.
    private static class HandlerThread extends Thread {
        private Socket connection;
        private ServerDelegate delegate;
        private DataInputStream in;	//stream read from the socket
        private DataOutputStream out;    //stream write to the socket
        private int clientPeerID;

        public HandlerThread(Socket connection, ServerDelegate delegate) {
            this.connection = connection;
            this.delegate = delegate;
            this.clientPeerID = -1;
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
                //receive the message sent from the client
                int length = in.readInt();
                byte[] request = new byte[length];
                in.readFully(request);
                Message requestMessage = MessageType.createMessageFromByteArray(request);

                Message responseMessage = notifyDelegate(requestMessage);
                if (responseMessage != null){
                    sendMessage(responseMessage);
                }
            }
        }

        private Message notifyDelegate(Message message){
            switch(message.getType()){
                case HANDSHAKE:
                    return delegate.onClientHandshakeReceived(message, this::setClientPeerID);
                case BITFIELD:
                    return delegate.onClientBitfieldReceived(message, clientPeerID);
                case INTERESTED:
                    return delegate.onInterestedReceived(message, clientPeerID);
                case NOT_INTERESTED:
                    return delegate.onNotInterestedReceived(message, clientPeerID);
                case REQUEST:
                    return delegate.onRequestReceived(message, clientPeerID);
                default:
                    return null;
            }
        }

        public void closeConnection() throws Exception{
            in.close();
            out.close();
            connection.close();
        }

        //send a message to the output stream
        public void sendMessage(Message msg)
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
