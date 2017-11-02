package com.company.services;

import com.company.configs.PeerInfo;
import com.company.delegates.ClientDelegate;
import com.company.messages.Message;
import com.company.messages.MessageType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Client {
    private int peerID;
    private int serverPeerID;
    private ClientDelegate delegate;
    Socket requestSocket;           //socket connect to the server
    DataOutputStream out;         //stream write to the socket
    DataInputStream in;          //stream read from the socket
    private ConcurrentLinkedQueue<Message> outboundMessageQueue;
    private ConcurrentLinkedQueue<Message> inboundMessageQueue;
    private Thread listenerThread;

    public Client(int peerID, ClientDelegate delegate){
        this.peerID = peerID;
        this.delegate = delegate;
        this.outboundMessageQueue = new ConcurrentLinkedQueue<>();
        this.inboundMessageQueue = new ConcurrentLinkedQueue<>();
    }

    public void startConnection(PeerInfo peerInfo) throws Exception
    {
        // Set the server peerID
        serverPeerID = peerInfo.getPeerID();

        //create a socket to connect to the server
        requestSocket = new Socket(peerInfo.getHostName(), peerInfo.getListeningPort());

        //initialize inputStream and outputStream
        out = new DataOutputStream(requestSocket.getOutputStream());
        out.flush();
        in = new DataInputStream(requestSocket.getInputStream());

        // Send Handshake message on initial connection
        byte[] handshakePayload = ByteBuffer.allocate(4).putInt(peerID).array();
        Message handshake = MessageType.HANDSHAKE.createMessageFromPayload(handshakePayload);
        sendMessage(handshake);

        while (true)
        {
            // Wait to receive a response.
            //receive the message sent from the client if we are not already waiting for the next message.
            if (listenerThread == null || !listenerThread.isAlive()){
                listenerThread = new Thread(() -> this.readMessage(in));
                listenerThread.start();
            }

            // Check the Outbound queue and send any messages that need to be sent.
            while(!outboundMessageQueue.isEmpty()){
                sendMessage(outboundMessageQueue.poll());
            }

            // Check if we have any incoming messages
            while(!inboundMessageQueue.isEmpty()){
                notifyDelegate(inboundMessageQueue.poll());
            }
        }
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

    private void notifyDelegate(Message message){

        Message m;
        switch(message.getType()){
            case HANDSHAKE:
                m = delegate.onServerHandshakeReceived(message, serverPeerID);
                break;
            case BITFIELD:
                m = delegate.onServerBitfieldReceived(message, serverPeerID);
                break;
            case CHOKE:
                m = delegate.onChokeReceived(message, serverPeerID);
                break;
            case UNCHOKE:
                m = delegate.onUnChokeReceived(message, serverPeerID);
                break;
            case HAVE:
                m = delegate.onHaveReceived(message, serverPeerID);
                break;
            case PIECE:
                m = delegate.onPieceReceived(message, serverPeerID);
                break;
            default:
                m = null;
        }

        if (m != null){
            outboundMessageQueue.add(m);
        }
    }

    public void sendMessageToServer(Message m){
        outboundMessageQueue.add(m);
    }

    public void closeConnection() throws Exception{
        in.close();
        out.close();
        requestSocket.close();
    }

    //send a message to the output stream
    void sendMessage(Message msg)
    {
        try{
            //stream write the message
            out.writeInt(msg.getBytes().length);
            out.write(msg.getBytes());
            out.flush();
        }
        catch(IOException ioException){
            ioException.printStackTrace();
        }
    }
}
