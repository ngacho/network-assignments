// =============================================================================
// IMPORTS

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
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
public class FloodNetworkLayer extends NetworkLayer {
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
    public FloodNetworkLayer() {
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
        // packet structure
        // hopcount, length, length, length, length, source, source, source, source, dest, dest, dest, dest
        // packet length = 13

        // add a hopcount of 6.
        byte[] hopCount = new byte[] { (byte) 10 };
        logger.info("Creating a packet");

        // COMPLETE ME
        byte[] packetLengthAsByteArray = intToByteArray(data.length + bytesPerHeader);

        byte[] sourceAsByteArray = intToByteArray(this.address);
        byte[] destAsByteArray = intToByteArray(destination);

        // split destination into bytes
        byte[] packetData = unpackByteArrays(hopCount, packetLengthAsByteArray, sourceAsByteArray, destAsByteArray,
                data);

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

    private void sendPacket(byte[] packet) {

        // convert destination to int

        // // get all the links
        Collection<DataLinkLayer> links = this.dataLinkLayers.values();
        // for each link
        for (DataLinkLayer link : links) {
            // send the packet
            link.send(packet);
        }

    }

    // =========================================================================
    /**
     * Randomly choose the link through which to send a packet given its
     * destination.
     *
     * @param destination The address to which this packet is being sent.
     */
    protected DataLinkLayer route(int destination) {
        throw new UnsupportedOperationException("We shouldn't use route at all");

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
        if (buffer.size() < bytesPerHeader) {
            logger.info("Non-full packet: Unpacked data: " + buffer.size() + " bytes");
            return null;
        }

        Queue<Byte> packetsBuffer = new LinkedList<>(buffer);
        // remove the first byte
        int hopCount = (int) packetsBuffer.remove();
        logger.info("Hop count: " + hopCount);
        logger.info("Bytes per header: " + bytesPerHeader);

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
        if (packetLength > buffer.size()) {
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
        // extract hopcount
        int hopcount = packet[0];

        // extract packet count
        int packetCount = packet[1];

        // extract destination
        byte[] destAsByteArray = Arrays.copyOfRange(packet, destinationOffset, bytesPerHeader);

        // convert destination to int
        int destination = byteArrayToInt(destAsByteArray);

        // get hopcount,
        // reduce hopcount by 1


        if (destination == this.address) {
            logger.info("Deliver to client");
            // extract data
            byte[] data = Arrays.copyOfRange(packet, bytesPerHeader, packet.length);
            client.receive(data);
            logger.info("Packet delivered to client");
            
        } else {

            // reduce the hopcount
            packet[0] = (byte) (--hopcount);
            // if hopcount is 0, drop the packet
            if (hopcount <= 0)
                return;
            
            logger.info("Forwarding packet...");
            sendPacket(packet);
            logger.info("new hop count " + hopcount + " from " + this.address + " to " + destination);
            
        }

        // COMPLETE ME
        return;

    } // processPacket ()
      // =========================================================================

    // =========================================================================
    /**
     * Send a sequence of bytes through this layer. Expected to be called by
     * the client. Packets are constructed and then sent via whichever data
     * link the is chosen by the routing method.
     *
     * @param destination The name of the destination host.
     * @param data        The sequence of bytes to send.
     */
    public void send(String destination, byte[] data) {

        // Determine the address of the destination.
        int destinationAddress = destination.hashCode();

        // Loop through the data in packet-size chunks.
        int numPackets = ((data.length / MAX_PACKET_SIZE) +
                (data.length % MAX_PACKET_SIZE == 0 ? 0 : 1));
        for (int i = 0; i < numPackets; i += 1) {

            // Grab the next packet-worth of data a make of packet of it.
            int start = i * MAX_PACKET_SIZE;
            int end = Math.min((i + 1) * MAX_PACKET_SIZE,
                    data.length);
            byte[] packetData = Arrays.copyOfRange(data, start, end);
            byte[] packet = createPacket(destinationAddress, packetData);

            // Choose the data link layer through which to route.

            sendPacket(packet);

            if (debug) {
                System.err.printf("Address %d sent packet:\n\t%s\n",
                        address,
                        bytesToString(packet));
            }

        }

    } // send ()
      // =========================================================================

    // =========================================================================
    // INSTANCE DATA MEMBERS

    // =========================================================================

    // =========================================================================
    // CLASS DATA MEMBERS



    /** The offset into the header for the length. */
    public static final int hopCountOffset = 0; // 0
    
    public static final int packetCountOffset = hopCountOffset + 1; // 1

    /** The offset into the header for the source address. */
    public static final int sourceOffset = packetCountOffset + Integer.BYTES; // 5

    /** The offset into the header for the destination address. */
    public static final int destinationOffset = sourceOffset + Integer.BYTES; // 9

    /** How many total bytes per header. */
    public static final int bytesPerHeader = destinationOffset + Integer.BYTES; // 13

    

    /** Whether to emit debugging information. */
    public static final boolean debug = false;
    // =========================================================================

    // =============================================================================
} // class RandomNetworkLayer
  // =============================================================================
