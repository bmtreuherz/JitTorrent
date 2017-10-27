package com.company;

import com.company.configs.CommonConfig;
import com.company.configs.PeerInfo;
import com.company.delegates.ClientDelegate;
import com.company.delegates.ServerDelegate;
import com.company.messages.HandshakeMessage;
import com.company.messages.Message;
import com.company.messages.MessageType;
import com.company.services.Client;
import com.company.services.Server;
import com.company.utilities.InMemoryFile;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Consumer;

public class Peer implements ClientDelegate, ServerDelegate {

    // Private Members
    private int peerID;
    private CommonConfig commonConfig;
    private BitSet bitField;
    private int numPieces;
    private InMemoryFile inMemFile;
    private HashSet<Integer> clientConnections;
    private HashMap<Integer, PeerInfo> peerInfo;


    Peer(int peerID, CommonConfig commonConfig){
        this.peerID = peerID;
        this.commonConfig = commonConfig;

        // Initialize the bitField
        int fileSize = commonConfig.getFileSize();
        int pieceSize = commonConfig.getPieceSize();
        this.numPieces = fileSize / pieceSize + (fileSize % pieceSize == 0 ? 0 : 1);
        this.bitField = new BitSet(numPieces);

        // Initialize an empty file in memory.
        this.inMemFile = new InMemoryFile(new byte[fileSize], pieceSize);

        this.clientConnections = new HashSet<>();
        this.peerInfo = new HashMap<>();
    }

    public void start(List<PeerInfo> peerInfo) throws Exception{

        // Search through peerInfo for my peerID
        int index = 0;
        while (index < peerInfo.size() && peerInfo.get(index).getPeerID() != peerID){
            index++;
        }

        // Verify that we have found the peerID
        if (index == peerInfo.size()){
            throw new Exception("PeerID: " + peerID + " not found in PeerInfo.");
        }

        // Get the PeerInfo corresponding to this peer.
        PeerInfo myInfo = peerInfo.get(index);

        // If the peer has the file, set the bitfield and load the file into memory.
        if (myInfo.getHasFile()){
            bitField.set(0, numPieces);

            // Load the file into memory
            this.inMemFile = InMemoryFile.createFromFile(getFilePath(), commonConfig.getPieceSize());
        }

        // Start the server.
        startServer(myInfo.getListeningPort());

        // Make connections to all peers  that came before this one in the PeerInfo list.
        for (int i = 0; i < index; i++){
            startClientConnection(peerInfo.get(i));
        }

        // For later use create a map of peerInfo (will be needed later on to initiate client connections)
        for (int i = 0; i < peerInfo.size(); i++){
            this.peerInfo.put(peerInfo.get(i).getPeerID(), peerInfo.get(i));
        }
    }


    // Helper Methods
    private String getFilePath(){
        return "./peer_" + peerID + "/" + commonConfig.getFileName();
    }

    private void startServer(int port){
        Server server = new Server(this);
        new Thread(() -> {
            try {
                server.start(port);
            } catch (Exception e) {
                e.printStackTrace();
            } finally{
                try {
                    server.closeConnection();
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
        }).start();
    }

    private void startClientConnection(PeerInfo serverInfo){
        Client client = new Client(peerID, this);

        clientConnections.add(serverInfo.getPeerID());
        new Thread(() -> {
            try {
                client.startConnection(serverInfo);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    client.closeConnection();
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
        }).start();
    }

    // ClientDelegate Methods
    @Override
    public Message onServerHandshakeReceived(Message message, int serverPeerID) {
        System.out.println("SERVER " + message.getType().name() + " RECEIVED FROM: " + serverPeerID + " TO: " + peerID);

        // check whether the handshake header is right and the peer ID is the expected one
        if (HandshakeMessage.class.isInstance(message)) {
            HandshakeMessage handshakeMessage = HandshakeMessage.class.cast(message);
            if (handshakeMessage.getPeerID() != serverPeerID) {
                return null;
            }
        }

        // send bitfield message to server
        return MessageType.BITFIELD.createMessageFromPayload(bitField.toByteArray());
    }

    @Override
    public Message onServerBitfieldReceived(Message message, int serverPeerID) {
        System.out.println("SERVER " + message.getType().name() + " RECEIVED FROM: " + serverPeerID + " TO: " + peerID);

        final BitSet serverBitSet = BitSet.valueOf(message.getPayloadField());
        int index = 0;
        ArrayList<Integer> missingBitIndices = new ArrayList<Integer>();
        while (index < numPieces) {
            int nextClearBit = this.bitField.nextClearBit(index);
            if (nextClearBit >= numPieces) { break; } // because we have all the pieces

            if (serverBitSet.get(nextClearBit)) {
                missingBitIndices.add(nextClearBit);
            }
            index = nextClearBit + 1;
        }

        // Interested and Not Interested Messages have no payload
        // if has pieces I don't have send "interested" message
        if (missingBitIndices.size() > 0) {
            return MessageType.INTERESTED.createMessageFromPayload(new byte[] {});
        }
        // else sends "not interested" message
        else {
            return MessageType.NOT_INTERESTED.createMessageFromPayload(new byte[] {});
        }
    }

    @Override
    public Message onChokeReceived(Message message, int serverPeerID) {
        System.out.println(message.getType().name() + " RECEIVED FROM: " + serverPeerID + " TO: " + peerID);

        // no payload

        return null;
    }

    @Override
    public Message onUnChokeReceived(Message message, int serverPeerID) {
        System.out.println(message.getType().name() + " RECEIVED FROM: " + serverPeerID + " TO: " + peerID);

        // no payload

        return null;
    }

    @Override
    public Message onHaveReceived(Message message, int serverPeerID) {
        System.out.println(message.getType().name() + " RECEIVED FROM: " + serverPeerID + " TO: " + peerID);

        // payload contains a 4-byte piece index field

        return null;
    }

    @Override
    public Message onPieceReceived(Message message, int serverPeerID) {
        System.out.println(message.getType().name() + " RECEIVED FROM: " + serverPeerID + " TO: " + peerID);

        // payload consists of a 4-byte piece index field and the content of the piece

        return null;
    }
    
    
    // Server Delegate Methods
    @Override
    public Message onClientHandshakeReceived(Message message, Consumer<Integer> setClientPeerID) {
        System.out.println("CLIENT " + message.getType().name() + " RECEIVED FROM: " + ((HandshakeMessage)message).getPeerID() + " TO: " + peerID);

        // Set the client peerID
        setClientPeerID.accept(((HandshakeMessage)message).getPeerID());

        // Send a handshake back to the client
        byte[] handshakePayload = ByteBuffer.allocate(4).putInt(peerID).array();
        return MessageType.HANDSHAKE.createMessageFromPayload(handshakePayload);
    }

    @Override
    public Message onClientBitfieldReceived(Message message, int clientPeerID) {
        System.out.println("CLIENT " + message.getType().name() + " RECEIVED FROM: " + clientPeerID + " TO: " + peerID);

        // send bitfield message or should skip bitfield message if doesn't have anything
        if (!bitField.isEmpty()) {
            /* have a bitfield as its payload
             * each bit in the bitfield payload represents whether the peer has the corresponding piece or not
             */
            return MessageType.BITFIELD.createMessageFromPayload(bitField.toByteArray());
        }

        return null;
    }

    @Override
    public Message onInterestedReceived(Message message, int clientPeerID) {
        System.out.println(message.getType().name() + " RECEIVED FROM: " + clientPeerID + " TO: " + peerID);

        // no payload

        return null;
    }

    @Override
    public Message onNotInterestedReceived(Message message, int clientPeerID) {
        System.out.println(message.getType().name() + " RECEIVED FROM: " + clientPeerID + " TO: " + peerID);

        // no payload

        return null;
    }

    @Override
    public Message onRequestReceived(Message message, int clientPeerID) {
        System.out.println(message.getType().name() + " RECEIVED FROM: " + clientPeerID + " TO: " + peerID);

        // payload consists of a 4-byte piece index field

        return null;
    }
}
