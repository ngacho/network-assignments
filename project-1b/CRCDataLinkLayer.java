
// =============================================================================
// IMPORTS
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Arrays;
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
public class CRCDataLinkLayer extends DataLinkLayer {
    private boolean isLogOn;
    private Logger logger;

    public CRCDataLinkLayer() {
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
            int sizeOfBuffer = (data.length - i) > data.length % maxDataInFrame ? maxDataInFrame : data.length % maxDataInFrame;
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
        
        // max len of data is the total num of bits + num of bits in generator - 1.
        int maxLenOfData = (data.length * bitsPerByte) + (countBits(polynomial) - 1);
        int remainder = getCRCRemainder(data, polynomial, maxLenOfData);
        byte[] new_data = addRemainderByteToData(data, remainder);
        

        // add start, parity, data, and stop tags
        Queue<Byte> framingData = new LinkedList<Byte>();
        framingData.add(startTag);

        for (byte dataByte : new_data) {
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

        if(!verifyCRC(extractedData)) return null;

        // strip the remainder array and return the data.
        int numRemainderBytes = getNumOfRemainderBytes();
        System.out.println("Remainder occupies " + numRemainderBytes);

        extractedData = removeRemainderBytes(extractedData, numRemainderBytes);

        

        return extractedData;

    } // processFrame ()
      // ===============================================================

      private byte[] removeRemainderBytes(byte[] array, int n) {
        if (n >= array.length) {
            return new byte[0]; // Return an empty array if n is greater than or equal to the array length
        }

        int newSize = array.length - n;
        byte[] result = Arrays.copyOf(array, newSize);
        return result;
    }


    private int getNumOfRemainderBytes(){
        int numGeneratorBits = countBits(polynomial);
        int quotient = numGeneratorBits / bitsPerByte;
        int remainder = numGeneratorBits % bitsPerByte;

        return remainder > 0 ? quotient + 1 : quotient; 

    }

    private boolean verifyCRC(byte[] data){
        int remainder = getCRCRemainder(data, polynomial, data.length * bitsPerByte);

        System.out.println("verify crc : Remainder " + remainder);
        return remainder == 0;
    }

    /**
     * Given a remainder int, convert it into a byte array.
     * 
     * @param remainder
     * @return
     */
    private byte[] convertRemainderIntoByteArray(int remainder){
        
        // calculate the shift size
        int shiftSize = bitsPerByte - (countBits(polynomial) - 1) % bitsPerByte;
        // shift the remainder by the remaining zeros towards the end
        remainder <<= shiftSize;
        // Calculate the number of bytes required for the new remainder
        int numBytes = (int) Math.ceil((double) countBits(remainder) / 8);


        byte[] byteArray = new byte[numBytes];

        for (int i = numBytes - 1; i >= 0; i--) {
            byteArray[i] = (byte) (remainder & 0xFF); // Extract the least significant byte
            remainder >>= 8; // Shift the integer to the right by 8 bits
        }
    
        return byteArray;
        
    }

    /*
     * Given a byte array and a remainder int, 
     * convert the int into a series of bytes such that they match the position where the extra zeros were in the data
     * then add these extra bytes to the data
     * 
     * return a byte array
     */
    private byte[] addRemainderByteToData(byte[] data, int remainder){
        byte[] remainderByteArray = convertRemainderIntoByteArray(remainder);

        byte[] new_data = new byte[remainderByteArray.length + data.length];
        for(int i = 0; i < data.length; i++){
            new_data[i] = data[i];
        }

        int size = data.length;

        for(int j = 0; j < remainderByteArray.length; j++){
            new_data[size + j] = remainderByteArray[j];
        }


        return new_data;

    }



    /**
     * Given a byte array, a generator and a maximum length of the data
     * @param data byte array
     * @param generator
     * @param maxLenOfData (varying depending on whether we are adding zeros or not)
     * @return an int that's the remainder of the division.
     */
     private int getCRCRemainder(byte[] data, int generator, int maxLenOfData) {
        System.out.println(convertByteArrayToBinaryString(data));

        // after loop, x is the remainder.
        // find the position of the most significant bit of the generator, generatorMostSigBit = 7
        int generatorMostSigBit = getMostSigBitPosition(generator);
        int generatorLength = generatorMostSigBit + 1;
        // let x = data[0]; x = 01111101
        int x = (int) (data[0] & 0xFF);
        // update counter; counter = 8
        int counter = 8;
        // while counter < (array.length * bitsPerByte) + genMostSigBit
            // find the index of first 1 in x (7 left most, 0 right most) => i = 6
            // while numbits < numBitsOfGenerator
                // shift x << (generatorMostSigBit - i), x = 11111010
                // or x with the next bit x = x | bitAt(counter)
                // update the counter; x = x + 1
            // xor with the generator and update this value to be x.; x = x ^ generator
        // repeat
        while(counter < maxLenOfData){
            int xMostSigBit = getMostSigBitPosition(x);
            int xLen = xMostSigBit + 1;
            
            while(xLen < generatorLength && counter < maxLenOfData){
                // fill up bits to get to len of the generator
                x <<= 1;
                x = (x | getBitFromByteArray(data, counter));
                // new len of x
                xLen = getMostSigBitPosition(x) + 1;
                counter += 1;
            }
            // if x len is stil less than generator len, return
            if(xLen < generatorLength) break;
            
            // else calculate the xor value
            x = x ^ generator;
        }

        return x;
    }



    /**
     * Given a number, calculate the number of bits in the number. include the unset bits.
     * @param num
     * @return
     */
    private int countBits(int num){
        int count = 0;
        while (num != 0) {
         count++;
         num >>= 1;
        }  

        return count;
    }

    /**
     * Given a byte array, find the position bit in the array
     * @param array
     * @param position
     * @return
     */
    public int getBitFromByteArray(byte[] array, int position){
        
        // assuming position is within the boundaries of the added data stuff.
        if(position >= array.length * bitsPerByte) return 0;
        // get the position of the byte in the array
        // floor division
        int bytePos = position / bitsPerByte;
        // remainder is the position in that byte.
        int remainder = position % bitsPerByte;


        // get the bit
        return getBit(array[bytePos], remainder);

    }

    
    /**
     * Given an int type, find where it's most signficant bit is
     * left most is most significant
     * @param data
     * @return
     */
    public int getMostSigBitPosition(int data){
        int mask = 1 << 31;
        for(int bitIndex = 31; bitIndex >= 0; bitIndex--){
          if((data & mask) > 0){
            return bitIndex;
          }
          mask >>>= 1;
        }
        return -1;
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
            String byteString = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(" ", "0");
            bytesSent += (byteString + " ");
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

    /**
     * Given a byte array, convert it into a single long consisting all the bytes provided
     * 
     * @param data
     * @return
     */
    public long convertByteArrayToLong(byte[] data){
        // for each byte, move 8 bits then shift
        long result = 0;
        int shift = 8;
        for(byte dataByte : data){
            result |= dataByte;
            result <<= shift;
        }

        // shift back 8 bits as it'd have moved after the loop
        result >>= shift;

        System.out.println(result);

        return result;


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
    private final int polynomial = 0x97;
    private final int bitsPerByte = 8;
    // ===============================================================

    // ===================================================================
} // class DumbDataLinkLayer
  // ===================================================================
