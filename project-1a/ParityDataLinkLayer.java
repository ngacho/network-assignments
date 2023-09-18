// =============================================================================
// IMPORTS

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
// =============================================================================

// =============================================================================
/**
 * @file DumbDataLinkLayer.java
 * @author Peter B. Opondo (bngacho24@amherst.edu)
 * @date August 2018, original September 2004
 *
 * 
 *       Use a single, simple parity bit to detect one-bit errors on each frame
 *       Upon error-detection,
 *          appropriate method of this class should print an error message.
 *          show the (incorrect) data.
 *          don't deliver data to the receiving host.
 * 
 *      Set up the message into 5-bit chunks with 3 hamming codes 
 *      0 + powers of 2 - correction duty
 *      
 *      Each frame should not contain more than 8 bytes of data.
 *      Parity bits: 2^0, 2^1, 2^2, 2^3, 2^4, 2^5
 *          Each frame should have 42 data bits  + 8 start tag bits + 8 stop tag bits + 6 parity bits OR
*           Each frame should have 34 data bits + x(8 escape tag bits) + 8 start tag bits + 8 stop tag bits + 6 parity bits
 */
public class ParityDataLinkLayer extends DataLinkLayer {

    // =============================================================================

    // =========================================================================
    /**
     * Embed a raw sequence of bytes into a framed sequence.
     *
     * @param data The raw sequence of bytes to be framed.
     * @return A complete frame. [0, 1, 2, 3, 4, 5, 6, 7, 8] bytes of data.
     */
    protected byte[] createFrame(byte[] data) {

    

        Queue<Byte> framingData = new LinkedList<Byte>();

        framingData.add(parityByte);        
        // Begin with the start tag.
        framingData.add(startTag);

        int counter = 0;

        // Add each byte of original data.
        for (int i = 0; i < data.length; i += 1) {
            // If the current data byte is itself synonymous with a metadata tag, then precede
            // it with an escape tag.
            byte currentByte = data[i];
            if ((currentByte == startTag) ||
                    (currentByte == stopTag) ||
                    (currentByte == escapeTag) ||
                    (currentByte == parityByte)) {

                        if(counter >= 4){
                            // end prev frame
                            framingData.add(stopTag);
                            // start new frame
                            framingData.add(parityByte);
                            framingData.add(startTag);
                            counter = 0;
                        }

                framingData.add(escapeTag);
                counter += 1;

            }

            if(counter >= 5){
                // end prev frame
                framingData.add(stopTag);
                // start new frame
                framingData.add(parityByte);
                framingData.add(startTag);
                counter = 0;

            }

            // Add the data byte itself.
            framingData.add(currentByte);
            counter += 1;

        }

        // End with a stop tag.
        framingData.add(stopTag);

        // Convert to the desired byte array.
        byte[] framedData = new byte[framingData.size()];
        Iterator<Byte> i = framingData.iterator();
        int j = 0;
        while (i.hasNext()) {
            framedData[j++] = i.next();
        }

        return framedData;

    } // createFrame ()
      // =========================================================================

    // =========================================================================
    /**
     * Determine whether the received, buffered data constitutes a complete
     * frame. If so, then remove the framing metadata and return the original
     * data. Note that any data preceding an escaped start tag is assumed to be
     * part of a damaged frame, and is thus discarded.
     *
     * @return If the buffer contains a complete frame, the extracted, original
     *         data; <code>null</code> otherwise.
     */
    protected byte[] processFrame() {

        // Search for a start tag. Discard anything prior to it.
        boolean startTagFound = false;
        Iterator<Byte> i = byteBuffer.iterator();
        while (!startTagFound && i.hasNext()) {
            byte current = i.next();
            if (current != startTag) {
                i.remove();
            } else {
                startTagFound = true;
            }
        }

        // If there is no start tag, then there is no frame.
        if (!startTagFound) {
            return null;
        }

        // Try to extract data while waiting for an unescaped stop tag.
        Queue<Byte> extractedBytes = new LinkedList<Byte>();
        boolean stopTagFound = false;
        while (!stopTagFound && i.hasNext()) {

            // Grab the next byte. If it is...
            // (a) An escape tag: Skip over it and grab what follows as
            // literal data.
            // (b) A stop tag: Remove all processed bytes from the buffer and
            // end extraction.
            // (c) A start tag: All that precedes is damaged, so remove it
            // from the buffer and restart extraction.
            // (d) Otherwise: Take it as literal data.
            byte current = i.next();
            if (current == escapeTag) {
                if (i.hasNext()) {
                    current = i.next();
                    extractedBytes.add(current);
                } else {
                    // An escape was the last byte available, so this is not a
                    // complete frame.
                    return null;
                }
            } else if (current == stopTag) {
                cleanBufferUpTo(i);
                stopTagFound = true;
            } else if (current == startTag) {
                cleanBufferUpTo(i);
                extractedBytes = new LinkedList<Byte>();
            } else {
                extractedBytes.add(current);
            }

        }

        // If there is no stop tag, then the frame is incomplete.
        if (!stopTagFound) {
            return null;
        }

        // Convert to the desired byte array.
        if (debug) {
            System.out.println("DumbDataLinkLayer.processFrame(): Got whole frame!");
        }
        byte[] extractedData = new byte[extractedBytes.size()];
        int j = 0;
        i = extractedBytes.iterator();
        while (i.hasNext()) {
            extractedData[j] = i.next();
            if (debug) {
                System.out.printf("DumbDataLinkLayer.processFrame():\tbyte[%d] = %c\n",
                        j,
                        extractedData[j]);
            }
            j += 1;
        }

        System.out.println("<=== Printing received data ===>");
        for(byte dataByte : extractedData){
            System.out.println(dataByte + " to string " + Character.toString((char) dataByte) );
        }

        return extractedData;

    } // processFrame ()
      // ===============================================================

    // ===============================================================
    private void cleanBufferUpTo(Iterator<Byte> end) {

        Iterator<Byte> i = byteBuffer.iterator();
        while (i.hasNext() && i != end) {
            i.next();
            i.remove();
        }

    }

    protected byte[][] splitDataIntoChunks(Queue<Byte> framingData){


        return null;
    }

    protected byte[] addParity(byte[] data){

        // TODO : byte with 8 or more bits

        // Convert to the desired byte array.
        byte[] framedData = new byte[data.length];
        // add a parity check to each data byte.

        for(int i = 0; i < data.length; i++){
            byte dataByte = data[i];
            int mask = 0xFF;
            byte newData = addParityBit(dataByte);
            framedData[i] = newData;
            System.out.println("New data after parity: " + Integer.toBinaryString(newData & mask));
        }


        return framedData;
    }

    protected byte addParityBit(byte data){
        byte tempData = data;
        int mask = 0xFF;
        boolean parityFlag = checkParity(tempData);
        int parityBit = parityFlag ? 1 : 0;
        // add parity bit to the data.
        byte newData = (byte) ((parityBit << 7 | data) & mask);

        return newData;
    }

    protected boolean checkParity(byte data){
        byte tempData = data;

        boolean parityFlag = false;
        while(tempData != 0){
            tempData &= (tempData - 1);
            parityFlag = !parityFlag;
        }


        return parityFlag;
    }
    // ===============================================================

    // ===============================================================
    // DATA MEMBERS
    // ===============================================================

    // ===============================================================
    // The start tag, stop tag, and the escape tag.
    private final byte startTag = (byte) '{';
    private final byte stopTag = (byte) '}';
    private final byte escapeTag = (byte) '\\';
    private final byte parityByte = (byte) 0xFF;
    private final int maxFrameSize = 8;
    // ===============================================================

    // ===================================================================
} // class DumbDataLinkLayer
  // ===================================================================
