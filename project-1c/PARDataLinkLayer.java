// =============================================================================
// IMPORTS

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
// =============================================================================

// =============================================================================
/**
 * @file ParityDataLinkLayer.java
 * @author Scott F. Kaplan (sfkaplan@cs.amherst.edu)
 * @date February 2020
 *
 *       A data link layer that uses start/stop tags and byte packing to frame
 *       the
 *       data, and that performs error management with a parity bit. It employs
 *       no
 *       flow control; damaged frames are dropped.
 */
public class PARDataLinkLayer extends DataLinkLayer {
    // =============================================================================

    // =========================================================================
    /**
     * Embed a raw sequence of bytes into a framed sequence.
     *
     * @param data The raw sequence of bytes to be framed.
     * @return A complete frame.
     */
    protected Queue<Byte> createFrame(Queue<Byte> data) {
        /**
         * switch role to sender
         */
        this.dataLinkLayerRole = DataLinkLayerRole.SENDER;

        LinkedList<Byte> frame = new LinkedList<Byte>();
        // add the ack byte and the frame number at the top of the linked list
        frame.add((byte) 0);
        frame.add((byte) this.frameCount);
        // add the rest of the data
        frame.addAll(data);

        byte parity = calculateParity(frame);

        // Begin with the start tag.
        Queue<Byte> framingData = new LinkedList<Byte>();
        framingData.add(startTag);

        // Add each byte of original data.
        for (byte currentByte : frame) {

            // If the current data byte is itself a metadata tag, then precede
            // it with an escape tag.
            if ((currentByte == startTag) ||
                    (currentByte == stopTag) ||
                    (currentByte == escapeTag)) {

                framingData.add(escapeTag);

            }

            // Add the data byte itself.
            framingData.add(currentByte);

        }

        // Add the parity byte.
        framingData.add(parity);

        // End with a stop tag.
        framingData.add(stopTag);

        return framingData;

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
    protected Queue<Byte> processFrame() {

        // Search for a start tag. Discard anything prior to it.
        boolean startTagFound = false;
        Iterator<Byte> i = receiveBuffer.iterator();
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
        int index = 1;
        LinkedList<Byte> extractedBytes = new LinkedList<Byte>();
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
            index += 1;
            if (current == escapeTag) {
                if (i.hasNext()) {
                    current = i.next();
                    index += 1;
                    extractedBytes.add(current);
                } else {
                    // An escape was the last byte available, so this is not a
                    // complete frame.
                    return null;
                }
            } else if (current == stopTag) {
                cleanBufferUpTo(index);
                stopTagFound = true;
            } else if (current == startTag) {
                cleanBufferUpTo(index - 1);
                index = 1;
                extractedBytes = new LinkedList<Byte>();
            } else {
                extractedBytes.add(current);
            }

        }

        // If there is no stop tag, then the frame is incomplete.
        if (!stopTagFound) {
            return null;
        }

        if (debug) {
            System.out.println("ParityDataLinkLayer.processFrame(): Got whole frame!");
        }

        // The final byte inside the frame is the parity. Compare it to a
        // recalculation.
        byte receivedParity = extractedBytes.remove(extractedBytes.size() - 1);
        byte calculatedParity = calculateParity(extractedBytes);
        if (receivedParity != calculatedParity) {
            switch(this.dataLinkLayerRole){
            case SENDER:
                System.out.printf("\n<%s> ParityDataLinkLayer.processFrame():\t Ack %d damaged", this.dataLinkLayerRole, this.frameCount);
                break;
            case RECEIVER:
                System.out.printf("\n<%s> ParityDataLinkLayer.processFrame():\t Frame %d damaged", this.dataLinkLayerRole, this.frameCount);
                break;
                
        }
            
            return null;
        }

        switch(this.dataLinkLayerRole){
            case SENDER:
                System.out.printf("\n<%s> Processed Ack %d", this.dataLinkLayerRole, this.frameCount);
                break;
            case RECEIVER:
                System.out.printf("\n<%s> Processed Frame %d", this.dataLinkLayerRole, this.frameCount);
                break;
                
        }
        

        return extractedBytes;

    } // processFrame ()
      // =========================================================================

    // =========================================================================
    /**
     * After sending a frame, do any bookkeeping (e.g., buffer the frame in case
     * a resend is required).
     *
     * @param frame The framed data that was transmitted.
     */
    protected void finishFrameSend(Queue<Byte> frame) {

        // COMPLETE ME WITH FLOW CONTROL

        System.out.printf("\n<%s> Finish sending frame %d", this.dataLinkLayerRole, this.frameCount);

        // increment frame count (move to be in accordance with ack)
        this.frameCount += 1;

        // update to awaiting ack
        this.awaitingAck = true;


    } // finishFrameSend ()
      // =========================================================================

    // =========================================================================
    /**
     * After receiving a frame, do any bookkeeping (e.g., deliver the frame to
     * the client, if appropriate) and responding (e.g., send an
     * acknowledgment).
     *
     * @param frame The frame of bytes received.
     */
    protected void finishFrameReceive(Queue<Byte> frame) {
        if(this.dataLinkLayerRole == DataLinkLayerRole.SENDER && this.awaitingAck){
            // extract ack and data
            // extract ack
            Iterator<Byte> i = frame.iterator();
            if(i.hasNext()){
                byte ack = i.next();
                int frameNum = -1;
                if(i.hasNext()){
                    frameNum = (int) i.next();
                }
                if(ack == 1){
                    System.out.printf("\n<%s> Received Ack %d while awaiting %d", this.dataLinkLayerRole, frameNum, this.frameCount);
                }
            }

            return;
        }

        // COMPLETE ME WITH FLOW CONTROL

        // Deliver frame to the client.
        byte[] deliverable = new byte[frame.size()];
        for (int i = 0; i < deliverable.length; i += 1) {
            deliverable[i] = frame.remove();
        }

        client.receive(deliverable);
        System.out.printf("\n<%s> Received frame %d", this.dataLinkLayerRole, this.frameCount);
        // send an ack.
        
        transmit(createAck());

        // increment frame count
        this.frameCount += 1;

    } // finishFrameReceive ()
      // =========================================================================

      private Queue<Byte> createAck(){
        System.out.printf("\n<%s> Sending Ack %d", this.dataLinkLayerRole, this.frameCount);
        // set is ack to true
        byte ackHeader = 1;

        Queue<Byte> ackHeaders = new LinkedList<>();
        ackHeaders.add(ackHeader);
        ackHeaders.add((byte) this.frameCount);

        // Calculate the parity.
        byte parity = calculateParity(ackHeaders);

        // Begin with the start tag.
        Queue<Byte> ackFrame = new LinkedList<Byte>();
        ackFrame.add(startTag);

        // Add each byte of original data.
        for (byte currentByte : ackHeaders) {

            // If the current data byte is itself a metadata tag, then precede
            // it with an escape tag.
            if ((currentByte == startTag) ||
                    (currentByte == stopTag) ||
                    (currentByte == escapeTag)) {

                ackFrame.add(escapeTag);

            }

            // Add the data byte itself.
            ackFrame.add(currentByte);

        }

        // Add the parity byte.
        ackFrame.add(parity);

        // End with a stop tag.
        ackFrame.add(stopTag);

        return ackFrame;
    }

    // =========================================================================
    /**
     * Determine whether a timeout should occur and be processed. This method
     * is called regularly in the event loop, and should check whether too much
     * time has passed since some kind of response is expected.
     */
    protected void checkTimeout() {

        // COMPLETE ME WITH FLOW CONTROL
        System.out.printf("\nchecking timeout %d", this.frameCount );

    } // checkTimeout ()
      // =========================================================================

    // =========================================================================
    /**
     * For a sequence of bytes, determine its parity.
     *
     * @param data The sequence of bytes over which to calculate.
     * @return <code>1</code> if the parity is odd; <code>0</code> if the parity
     *         is even.
     */
    private byte calculateParity(Queue<Byte> data) {

        int parity = 0;
        for (byte b : data) {
            for (int j = 0; j < Byte.SIZE; j += 1) {
                if (((1 << j) & b) != 0) {
                    parity ^= 1;
                }
            }
        }

        return (byte) parity;

    } // calculateParity ()
      // =========================================================================

    // =========================================================================
    /**
     * Remove a leading number of elements from the receive buffer.
     *
     * @param index The index of the position up to which the bytes are to be
     *              removed.
     */
    private void cleanBufferUpTo(int index) {

        for (int i = 0; i < index; i += 1) {
            receiveBuffer.remove();
        }

    } // cleanBufferUpTo ()
      // =========================================================================

    private String convertByteArrayToBinaryString(byte[] data) {
        String bytes = "";
        for (byte b : data) {
            String byteString = String.format("%8s", Integer.toBinaryString(b & 0xFF));
            bytes += byteString;
        }

        return bytes;

    }

    private String convertByteQueueToBinaryString(Queue<Byte> data) {
        Iterator<Byte> it = data.iterator();
        String bytes = "";

        while (it.hasNext()) {
            String byteString = String.format("%8s", Integer.toBinaryString(data.remove() & 0xFF));
            bytes += byteString;

        }

        return bytes;

    }

    // =========================================================================
    // DATA MEMBERS

    /** The start tag. */
    private final byte startTag = (byte) '{';

    /** The stop tag. */
    private final byte stopTag = (byte) '}';

    /** The escape tag. */
    private final byte escapeTag = (byte) '\\';
    // =========================================================================

    /** Role of the instance of this class */
    private DataLinkLayerRole dataLinkLayerRole = DataLinkLayerRole.RECEIVER;

    /**
     * Keep count of the number of frame
     */
    private int frameCount = 0;

    /**
     * 
     */
    private boolean awaitingAck = false;

    public enum DataLinkLayerRole {
        SENDER,
        RECEIVER
    }

    // =============================================================================
} // class ParityDataLinkLayer
  // =============================================================================
