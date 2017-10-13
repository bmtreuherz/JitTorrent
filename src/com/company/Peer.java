package com.company;

import com.company.configs.CommonConfig;
import com.company.configs.PeerInfo;

import java.util.List;

public class Peer {

    private int peerID;
    private CommonConfig commonConfig;

    Peer(int peerID, CommonConfig commonConfig){
        this.peerID = peerID;
        this.commonConfig = commonConfig;
    }

    public void start(List<PeerInfo> peerInfo){

        // TODO: Implement

        // TODO: Search through peerInfo for my peerID

        // TODO: Set bitflag

        // TODO: Start Server

        // TODO: Make connections to all peers that come before this one in peerInfo
    }
}
