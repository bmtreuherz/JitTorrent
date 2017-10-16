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

public class Client {
    private int peerID;
    private int serverPeerID;
    private ClientDelegate delegate;
    Socket requestSocket;           //socket connect to the server
    DataOutputStream out;         //stream write to the socket
    DataInputStream in;          //stream read from the socket

    public Client(int peerID, ClientDelegate delegate){
        this.peerID = peerID;
        this.delegate = delegate;
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
            int length = in.readInt();
            byte[] response = new byte[length];
            in.readFully(response);
            Message responseMessage = MessageType.createMessageFromByteArray(response);

            Message nextMessage = notifyDelegate(responseMessage);
            if (nextMessage != null){
                sendMessage(nextMessage);
            }
        }
    }

    private Message notifyDelegate(Message message){

        switch(message.getType()){
            case HANDSHAKE:
                return delegate.onServerHandshakeReceived(message, serverPeerID);
            case BITFIELD:
                return delegate.onServerBitfieldReceived(message, serverPeerID);
            case CHOKE:
                return delegate.onChokeReceived(message, serverPeerID);
            case UNCHOKE:
                return delegate.onUnChokeReceived(message, serverPeerID);
            case HAVE:
                return delegate.onHaveReceived(message, serverPeerID);
            case PIECE:
                return delegate.onPieceReceived(message, serverPeerID);
            default:
                return null;
        }
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
