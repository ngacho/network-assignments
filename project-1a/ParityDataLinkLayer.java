// =============================================================================
// IMPORTS

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
// =============================================================================

// =============================================================================
/**
 * @file DumbDataLinkLayer.java
 * @author Scott F. Kaplan (sfkaplan@cs.amherst.edu)
 * @date August 2018, original September 2004
 *
 *       A data link layer that uses start/stop tags and byte packing to frame
 *       the
 *       data, and that performs no error management.
 */
public class ParityDataLinkLayer extends DataLinkLayer {
    // =============================================================================

    // =========================================================================
    /**
     * Embed a raw sequence of bytes into a framed sequence.
     *
     * @param data The raw sequence of bytes to be framed.
     * @return A complete frame.
     */
    protected byte[] createFrame(byte[] data) {
        Queue<Byte> framingData = new LinkedList<Byte>();

        // get parity of each data byte.
        int countBytes = 0;
        int i = 0;
        // sliding window.
        while (i < data.length) {
            byte parityByte = (byte) 0;
            int j = i;
            // add start tag
            framingData.add(startTag);
            while (j < data.length && countBytes < maxDataInFrame) {
                byte curr_data = data[j];
                if(curr_data == startTag || curr_data == stopTag || curr_data == escapeTag){
                    // add escape tag first
                    framingData.add(escapeTag);
                }
                // add to frame
                framingData.add(curr_data);

                // calculate the parity of this data
                boolean parityFlag = checkParity(curr_data);
                int parityBit = parityFlag ? 1 : 0;

                // move the parity bit to it's necessary position
                byte parityBitPositioned = (byte) (parityBit << ((maxDataInFrame - (countBytes + 1))));
                parityByte = (byte) (parityByte | parityBitPositioned);
                countBytes += 1;

                // increment number of bytes
                j += 1;
            }

            // reset count bytes
            countBytes = 0;
            // reassign i to j
            i = j;
            // add stop tag
            framingData.add(stopTag);
            // add parity byte
            framingData.add((byte) (parityByte & 0xFF));    
        }

        // Convert to the desired byte array.
        byte[] framedData = new byte[framingData.size()];
        Iterator<Byte> byteIter = framingData.iterator();
        int j = 0;
        while (byteIter.hasNext()) {
            framedData[j++] = byteIter.next();
        }

        return framedData;

    } // createFrame ()
      // =========================================================================

    private boolean checkParity(byte data) {
        byte tempData = data;

        boolean parityFlag = false;
        while (tempData != 0) {
            tempData &= (tempData - 1);
            parityFlag = !parityFlag;
        }
        return parityFlag;
    }

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
        // get the parity byte
        boolean foundParityByte = false;
        byte frameParityByte =  0;
        Iterator<Byte> byteIter = byteBuffer.iterator();
        while(!foundParityByte && byteIter.hasNext()){
            byte curr = byteIter.next();
            if(curr == stopTag){
                if(byteIter.hasNext()){
                    frameParityByte = (byte) (byteIter.next() & 0xFF);
                    foundParityByte = true;
                }else{
                    // the tag after the frame should be present (byte Parity)
                    return null;
                }
            }
        }


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

        for(int k = 0; k < extractedData.length; k++){
            int expectedParity = getBit(frameParityByte, k) > 0 ? 1 : 0;
            int actualParity = checkParity(extractedData[k]) ? 1 : 0;
            
            if(actualParity != expectedParity){
                System.out.println("Error in frame : " + new String(extractedData));
                return null;
            }

        }

        return extractedData;

    } // processFrame ()
      // ===============================================================

      /** Given a parity byte and an array of bytes, determine if they match 
       * Sample ([1001000 1100101 1101100 1101100 1101111 1110111 1101111 1110010]), 0 => true
       * Sample ([1001000 1100101 1101100 1101100 1101111 1101110 1101111 1110010]), 100 => true because only arr[2] has an odd parity translating to 100.
      */
      
      

      /**
       * Given a byte, return the bit in the ith position
       * @param data
       * @param position
       * 
       * @return bit in the ith position (left to right, 0 is left and 7 is right most bit)
       */
      public int getBit(byte data, int position){
        return (byte) ((data >> (7 - position)) & 1);
      }

      
    private String convertByteArrayToBinaryString(byte[] data){
		String bytesSent = "";
		for(byte b : data){ 
            String byteString = String.format("%8s", Integer.toBinaryString(b & 0xFF));
			bytesSent += byteString;
		}

		return bytesSent;

	}

    // ===============================================================
    private void cleanBufferUpTo(Iterator<Byte> end) {

        Iterator<Byte> i = byteBuffer.iterator();
        while (i.hasNext() && i != end) {
            i.next();
            i.remove();
        }

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
    private final byte maxDataInFrame = 8;
    // ===============================================================

    // ===================================================================
} // class DumbDataLinkLayer
  // ===================================================================
