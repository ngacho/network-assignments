// =============================================================================
// IMPORTS

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
// =============================================================================
import java.util.logging.Logger;

// =============================================================================
/**
 * @file RandomNetworkLayer.java
 * @author Scott F. Kaplan (sfkaplan@cs.amherst.edu)
 * @date April 2022
 *
 *       A network layer that perform routing via random link selection.
 */
public class RandomNetworkLayer extends NetworkLayer {
    // =============================================================================

    private Logger logger;
    private boolean isLogOn;

    // =========================================================================
    // PUBLIC METHODS
    // =========================================================================

    // =========================================================================
    /**
     * Default constructor. Set up the random number generator.
     */
    public RandomNetworkLayer() {
        this.isLogOn = debug;
        logger = Logger.getLogger(this.getClass().getName());
        logger.setUseParentHandlers(false);
        // CustomLogFormatter formatter = new CustomLogFormatter();
        ConsoleHandler handler = new ConsoleHandler();
        // handler.setFormatter(formatter);
        logger.addHandler(handler);

        if (!this.isLogOn) {
            logger.setLevel(Level.OFF);
        } else {
            logger.setLevel(Level.ALL);
        }

        random = new Random();

    } // RandomNetworkLayer ()
      // =========================================================================

    // =========================================================================
    /**
     * Create a single packet containing the given data, with header that marks
     * the source and destination hosts.
     *
     * @param destination The address to which this packet is sent.
     * @param data        The data to send.
     * @return the sequence of bytes that comprises the packet.
     */
    protected byte[] createPacket(int destination, byte[] data) {
        logger.info("Creating a packet");

        // COMPLETE ME
        byte[] packetLengthAsByteArray = intToByteArray(data.length + bytesPerHeader);

        byte[] sourceAsByteArray = intToByteArray(this.address);
        byte[] destAsByteArray = intToByteArray(destination);


        // split destination into bytes
        byte[] packetData = unpackByteArrays(packetLengthAsByteArray, sourceAsByteArray, destAsByteArray, data);

        logger.info("Packet of size " + packetData.length + " bytes created");

        return packetData;

    } // createPacket ()
      // =========================================================================

    /**
     * Given an int, convert it into a byte array
     * 
     * @param value
     * @return byte array representing the int
     */
    public byte[] intToByteArray(int value) {
        byte[] bytes = new byte[Integer.BYTES];

        for (int i = 0; i < Integer.BYTES; i++) {
            bytes[i] = (byte) ((value >>> (i * 8)) & 0xFF);
        }

        return bytes;
    }

    /**
     * Given a byte array, convert it into an integer
     * 
     * @param value
     * @return int represented by byte array
     */
    public int byteArrayToInt(byte[] array) {
        int result = 0;
        for (int i = 0; i < Integer.BYTES; i++) {
            result |= (array[i] & 0xFF) << (i * 8);
        }

        return result;
    }

    /**
     * Given an array of arrays, flatten it.
     * 
     * @param args
     * @return
     */
    byte[] unpackByteArrays(byte[]... args) {

        Queue<Byte> unpackedData = new LinkedList<Byte>();
        for (byte[] arg : args) {
            for (Byte b : arg) {
                unpackedData.add(b);
            }
        }

        byte[] byteArr = new byte[unpackedData.size()];
        int counter = 0;
        while (!unpackedData.isEmpty()) {
            byteArr[counter] = unpackedData.remove();
            counter++;
        }

        return byteArr;
    }

    // =========================================================================
    /**
     * Randomly choose the link through which to send a packet given its
     * destination.
     *
     * @param destination The address to which this packet is being sent.
     */
    protected DataLinkLayer route(int destination) {
        // COMPLETE ME
        Set<Integer> layersAddresses = this.dataLinkLayers.keySet();
        int randomIndex = random.nextInt(layersAddresses.size());
        Iterator<Integer> iterator = layersAddresses.iterator();
        int counter = 0;
        while (iterator.hasNext()) {
            int address = iterator.next();
            if (counter == randomIndex) {
                logger.info("Chosen data link layer: " + address);
                return this.dataLinkLayers.get(address);
            }
            counter++;
        }

        return null;

    } // route ()
      // =========================================================================

    // =========================================================================
    /**
     * Examine a buffer to see if it's data can be extracted as a packet; if so,
     * do it, and return the packet whole.
     *
     * @param buffer The receive-buffer to be examined.
     * @return the packet extracted packet if a whole one is present in the
     *         buffer; <code>null</code> otherwise.
     */
    protected byte[] extractPacket(Queue<Byte> buffer) {
        
        logger.info("Extracting packet");
        
        // if unpacked is lower than the size of the bytes per header
        // then we don't have a full packet
        if (buffer.size() < bytesPerHeader){
            logger.info("Non-full packet: Unpacked data: " + buffer.size() + " bytes");
            return null;
        }

        Queue<Byte> packetsBuffer = new LinkedList<>(buffer);
    
        // COMPLETE ME
        // extract length array from unpacked data
        byte[] packetLengthAsByteArray = new byte[Integer.BYTES];
        for (int i = 0; i < Integer.BYTES; i++) {
            packetLengthAsByteArray[i] = packetsBuffer.remove();
        }
        // 
        int packetLength = byteArrayToInt(packetLengthAsByteArray); 

        logger.info("Packet length: " + packetLength);
        
        // System.out.println("Data length " + packetLength);
        // if unpacked is lower than the size of the packet
        if (packetLength > buffer.size()){
            // buffer is too small to contain the packet
            return null;
        }
        // found a whole packet
        byte[] packet = new byte[packetLength];
        for (int i = 0; i < packetLength; i++) {
            // fill the packets array with the whole packet
            packet[i] = buffer.remove();
        }
       return packet;
    } // extractPacket ()
      // =========================================================================

    // =========================================================================
    /**
     * Given a received packet, process it. If the destination for the packet
     * is this host, then deliver its data to the client layer. If the
     * destination is another host, route and send the packet.
     *
     * @param packet The received packet to process.
     * @see createPacket
     */
    protected void processPacket(byte[] packet) {
        logger.info("Processing packet");
        // extract destination
        byte[] destAsByteArray = Arrays.copyOfRange(packet, destinationOffset, bytesPerHeader);

        // convert destination to int
        int destination = byteArrayToInt(destAsByteArray);

        if (destination == this.address) {
            logger.info("Deliver to client");
            // extract data
            byte[] data = Arrays.copyOfRange(packet, bytesPerHeader, packet.length);
            System.out.println("Data: " + new String(data));
            client.receive(data);
            
        }else{
            //else reroute
            DataLinkLayer randomLink = route(destination);
            randomLink.send(packet);
        }

        // COMPLETE ME
        return;

    } // processPacket ()
      // =========================================================================

    // =========================================================================
    // INSTANCE DATA MEMBERS

    /** The random source for selecting routes. */
    private Random random;
    // =========================================================================

    // =========================================================================
    // CLASS DATA MEMBERS

    /** The offset into the header for the length. */
    public static final int lengthOffset = 0;

    /** The offset into the header for the source address. */
    public static final int sourceOffset = lengthOffset + Integer.BYTES;

    /** The offset into the header for the destination address. */
    public static final int destinationOffset = sourceOffset + Integer.BYTES;

    /** How many total bytes per header. */
    public static final int bytesPerHeader = destinationOffset + Integer.BYTES;

    /** Whether to emit debugging information. */
    public static final boolean debug = false;
    // =========================================================================

    // =============================================================================
} // class RandomNetworkLayer
  // =============================================================================
