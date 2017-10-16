package com.company.utilities;


import com.oracle.tools.packager.IOUtils;

import java.io.File;
import java.io.FileOutputStream;

public class InMemoryFile {

    // Attributes
    private byte[] data;
    private int pieceSize;

    public InMemoryFile(byte[] data, int pieceSize){
        this.data = data;
        this.pieceSize = pieceSize;
    }

    public byte[] getPiece(int pieceNum){

        // Determine the bounds for this piece
        int startIndex = pieceNum * pieceSize; // inclusive start
        int endIndex = startIndex + pieceSize < data.length ? startIndex + pieceSize : data.length; // not inclusive end

        // If this is not a valid piece return null.
        if (endIndex - startIndex <= 0){
            return null;
        }

        // Copy over this portion of the data
        byte[] piece = new byte[endIndex - startIndex];
        for (int i = startIndex; i < endIndex; i++){
            piece[i - startIndex] = data[i];
        }

        return piece;
    }

    // Set a piece of the file to the given data. NOTE: If the data is longer than the pieceSize (or it is the last piece and the
    // data is too long) the remainder of the data will be ignored.
    public void setPiece(int pieceNum, byte[] data){

        // Determine the bounds for this piece
        int startIndex = pieceNum * pieceSize; // inclusive start
        int endIndex = startIndex + pieceSize < this.data.length ? startIndex + pieceSize : this.data.length; // not inclusive end

        // Copy over this data the file data
        for (int i = startIndex; i < endIndex; i++){
            this.data[i] = data[i-startIndex];
        }
    }

    public static InMemoryFile createFromFile(String fileName, int pieceSize) throws Exception{
        byte[] data = IOUtils.readFully(new File(fileName));
        return new InMemoryFile(data, pieceSize);
    }

    public void writeToFile(String fileName) throws Exception{
        FileOutputStream stream = new FileOutputStream(fileName);
        stream.write(this.data);
        stream.close();
    }

}
