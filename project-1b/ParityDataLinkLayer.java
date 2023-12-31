
// =============================================================================
// IMPORTS
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
// ===========================================================================

// =============================================================================
/**
 * @file ParityDataLinkLayer.java
 * @author Brandon Ngacho (bngacho24@amherst.edu)
 * @date Sep 20, 2023.
 *
 *       A data link layer that uses start/stop tags and byte packing to frame
 *       the
 *       data, and that performs no error management.
 */
public class ParityDataLinkLayer extends DataLinkLayer {
    private boolean isLogOn;
    private Logger logger;

    public ParityDataLinkLayer() {
        this.isLogOn = debug;
        logger = Logger.getLogger(this.getClass().getName());
        logger.setUseParentHandlers(false);
        CustomLogFormatter formatter = new CustomLogFormatter();
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(formatter);
        logger.addHandler(handler);

        if (!this.isLogOn) {
            logger.setLevel(Level.OFF);
        }

        
    }
    // =============================================================================

    @Override
    public void send(byte[] data) {
        int i = 0;
        while (i < data.length) {
            int j = 0;
            // send buffer
            // calculate the size of the buffer, 8 if it's full, the remainer if not.
            int sizeOfBuffer = (data.length - i) > data.length % 8 ? 8 : data.length % 8;
            logger.info("Print size of buffer:  " + sizeOfBuffer);
            byte[] sendBuffer = new byte[sizeOfBuffer];
            while (j < sizeOfBuffer) {
                byte curr_data = data[i];
                sendBuffer[j] = curr_data;
                j++; // increment j
                i++; // increment i

            }

            // send the buffer one frame at a time.
            byte[] framedData = createFrame(sendBuffer);
            for (byte dataByte : framedData) {
                transmit(dataByte);
            }
            

            logger.info("Send String: => " + new String(sendBuffer));
            logger.info("Send Bytes: => " + convertByteArrayToBinaryString(sendBuffer));

        }

    }

    // =========================================================================
    /**
     * Embed a raw sequence of bytes into a framed sequence.
     *
     * @param data The raw sequence of bytes to be framed.
     * @return A complete frame.
     */
    protected byte[] createFrame(byte[] data) {
        byte parityByte = 0;
        // calculate the parity of the data.
        for (int i = 0; i < data.length; i++) {
            int parityBit = checkParity(data[i]) ? 1 : 0;
            byte parityBitPositioned = (byte) (parityBit << ((maxDataInFrame - (i + 1))));
            parityByte = (byte) (parityByte | parityBitPositioned);
        }

        logger.info("Frame received for data: " + new String(data));
        logger.info("Frame received for bytes: " + convertByteArrayToBinaryString(data));
        logger.info("Parity Byte is " + Integer.toBinaryString(parityByte & 0xff));

        // add start, parity, data, and stop tags
        Queue<Byte> framingData = new LinkedList<Byte>();
        framingData.add(startTag);
        framingData.add(parityByte);

        for (byte dataByte : data) {
            if (dataByte == startTag || dataByte == stopTag || dataByte == escapeTag) {
                // add escape tag first
                framingData.add(escapeTag);
            }
            framingData.add(dataByte);
        }

        framingData.add(stopTag);

        // Convert to the desired byte array.
        byte[] framedData = new byte[framingData.size()];
        Iterator<Byte> byteIter = framingData.iterator();
        int j = 0;
        while (byteIter.hasNext()) {
            framedData[j++] = byteIter.next();
        }

        // logger.info("Processed " + new String(framedData));
        // logger.info("Processed Bytes: " + convertByteArrayToBinaryString(framedData));
        
        return framedData;

    } // createFrame ()
      // =========================================================================

      /**
       * 
       * @param data
       * @return true if parity is odd, false otherwise
       */
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

        // Search for a start tag. Discard anything prior to it.
        boolean startTagFound = false;
        byte parityByte = 0;
        Iterator<Byte> i = byteBuffer.iterator();
        while (!startTagFound && i.hasNext()) {
            byte current = i.next();
            if (current != startTag) {
                i.remove();
            } else {
                startTagFound = true;
                // grab the byte next to the start tag.
                if(i.hasNext()){
                    parityByte = i.next();
                    // remove the parity byte.
                }
                else{
                    logger.severe("No parity byte found");
                }
                
            }
        }

        logger.info("Parity byte: " + Integer.toBinaryString(parityByte & 0xff));

        
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
            byte curr_data = i.next();
            extractedData[j] = curr_data;
            if (debug) {
                System.out.printf("DumbDataLinkLayer.processFrame():\tbyte[%d] = %c\n",
                        j,
                        extractedData[j]);
            }
            j += 1;
        }

        if(!verifyParity(extractedData, parityByte)) return null;

        return extractedData;

    } // processFrame ()
      // ===============================================================

    /**
     * This method verifies the parity of a byte
     * Sample ([1001000 1100101 1101100 1101100 1101111 1101110 1101111 1110010]),
     * 100 => true
     * because only arr[2] has an odd parity translating to 100.
     * 
     * @param data       [1001000 1100101 1101100 1101100 1101111 1110111 1101111
     *                   1110010]
     * @param parityByte 0
     * @return true or false
     * 
     */
    private boolean verifyParity(byte[] data, byte parityByte) {

        // should we discard the whole frame or
        for (int k = 0; k < data.length; k++) {
            byte curr_data = data[k];
            int expectedParity = getBit(parityByte, k) > 0 ? 1 : 0;
            int actualParity = checkParity(curr_data) ? 1 : 0;

            logger.info("position : " + k + " : " + String.valueOf((char) curr_data) + " => "
                    + Integer.toBinaryString(curr_data & 0xFF) + " expected " + expectedParity + " vs actual : "
                    + actualParity);

            if (actualParity != expectedParity) {
                String loggingMessage = "Frame " + new String(data) + " corrupted";
                logger.severe(loggingMessage);
                return false;
            }
        }

        return true;
    }

    /**
     * Given a byte, return the bit in the ith position
     * 
     * @param data     00000100
     * @param position 5
     * 
     *                 return 1
     * 
     * @return bit in the ith position (left to right, position 0 is leftmost and position 7 is
     *         rightmost)
     */
    public int getBit(byte data, int position) {

        logger.info("Parity Byte " + Integer.toBinaryString(data & 0xff) + " Position " + position);

        byte returnByte = (byte) (((data & 0xff) >> (7 - position)) & 1);

        logger.info("Return Byte " + Integer.toBinaryString(returnByte));

        return returnByte;
    }

    /**
     * 
     * @param data : bytes[]
     * @return the string of 1s and 0s eight bytes at a time for debugging purposes.
     */
    private String convertByteArrayToBinaryString(byte[] data) {
        String bytesSent = "";
        for (byte b : data) {
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
