
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SendModule {

    private static final Logger logger = Logger.getLogger(SendModule.class.getName());
    private static final int FRAME_SIZE = 1526;
    private static final int FRAME_DATA_SIZE = 1500;
    private static Handler fileHandler;

    private int packetQueueCapacity;
    private int transmitBufferCapacity;
    
    private Queue<Event> packetQueue;
    private Queue<Event> transmitBuffer;
    
    private boolean isBusy;
    private float processingRate;
    private Event currentEvent;
    
    // Variables to store stats
    private int totalMessages;
    private int totalFrames;
    private int droppedMessages;
    private long tbDelay;
    private long pqDelay;
    private long sppDelay;

    public SendModule(final int packetQueueCapacity, final int transmitBufferCapacity, boolean isBusy, float processingRate) 
            throws SecurityException, IOException {
        
        //Initialize buffer capacities
        this.packetQueueCapacity = packetQueueCapacity;
        this.transmitBufferCapacity = transmitBufferCapacity;

        // Initialize Packet Queue
        packetQueue = new LinkedList<Event>();
        
        // Initialize Transmit Buffer
        transmitBuffer = new LinkedList<Event>();
        
        // Initialize SPP
        this.isBusy = isBusy;
        this.processingRate = processingRate;
        currentEvent = null;
        
        // Initialize logger
        fileHandler = new FileHandler("SM.log");
        logger.addHandler(fileHandler);
		logger.setUseParentHandlers(false);
        
        //Initialing stats variables
        totalMessages = 0;
        totalFrames = 0;
        droppedMessages = 0;
        tbDelay = 0;
        pqDelay = 0;
        sppDelay = 0;
    }

    /*
     * Sender Module events types
     * 
     */
    public Event processEvent(Event event) {
        if (event.getEventType().equals("SM_PQ")) {
            return processSMSendEvent(event);
        } else if (event.getEventType().equals("SM_SPPEnter")) {
            return processSMVacateSPPEvent(event);
        } else if (event.getEventType().equals("SM_SPPExit")) {
            // collect metrics for the finished send event
            logger.log(Level.INFO, "Message is being FINISHED in SM. " + event);
            Simulator.finishEvent(event);
            return null;
        } else {
            // Wrong event sent to SM module, discarding the event
            logger.log(Level.WARNING, "Event wrongly sent to Send module discarding: " + event);
            return null;
        }
    }

    /**
     * This function processes the Messages generated by Poisson Distribution as event. 
     * It follows steps given below:
     * 1/ checks if the Packet Processor is busy, if yes, goes to step 2, else it creates a 
     *    SM_VACATE event and returns it to be put into event list for later processing.
     * 2/ checks if the Packet Queue has enough space to store the message, if it has then message
     *    is stored in the queue else dropped.
     * @param event
     */
    private Event processSMSendEvent(Event event) {
        
        logger.log(Level.INFO, "Message is being PROCESSED in SM. " + event);
        Event eventAfterSMProcessing = null;
        int messageSize = event.getMessageLength();
        totalMessages++;

        // Step1 testing if Packet Processor is Busy    
        if (isBusy()) {
            // check if Queue has enough space to hold the incoming message.
            if (messageSize <= packetQueueCapacity) {
                // add it to queue and update the space in the queue.
                event.setPqTimeStamp(Simulator.getTime());
                packetQueue.add(event);
                packetQueueCapacity = packetQueueCapacity - event.getMessageLength();
            } else {
                droppedMessages++;
                Simulator.dm++;
                logger.log(Level.INFO, "Message is DROPPED "
                        + "as Packet Queue does not have enough space. " + event);
            }
        } else {
            // Add this message to the Queue and process the first message in the queue.
            eventAfterSMProcessing = packetQueue.poll(); // gets the first message from the queue.
            if (eventAfterSMProcessing != null) {
                // Add the delay in PQ
                pqDelay += Simulator.getTime() - eventAfterSMProcessing.getPqTimeStamp();
                packetQueueCapacity = packetQueueCapacity + eventAfterSMProcessing.getMessageLength();
                // check the size of the PQ for safety, usually PQ should have enough space
                if (messageSize <= packetQueueCapacity) {
                    // add it to queue and update the space in the queue.
                    event.setPqTimeStamp(Simulator.getTime());
                    packetQueue.add(event);
                    packetQueueCapacity = packetQueueCapacity - messageSize;
                    
                } else {
                    droppedMessages++;
                    Simulator.dm++;
                    logger.log(Level.INFO, "Message is DROPPED "
                            + "as Packet Queue does not have enough space. " + event);
                }
            } else {
                eventAfterSMProcessing = new Event(event);
            }
            setBusy(true);
            eventAfterSMProcessing.setEventType("SM_SPPEnter");
            eventAfterSMProcessing.setWaitPeriod(getTimeforProcessingMessage(eventAfterSMProcessing.getMessageLength()));
        }

        return eventAfterSMProcessing;
    }

    /** This function handles SM_SPPEnter event. It attaches the input event to SPP and then calls 
     *  checkBusyWaitingSPP(), which does further processing of the event.
     */
    private Event processSMVacateSPPEvent(Event event) {
        logger.log(Level.INFO, "Message is being VACATED from PP in SM. " + event);
        event.setSppTimeStamp(Simulator.getTime());
        setCurrentEvent(event);
        Event eventAfterSPPProcessing = checkBusyWaitingSPP();
        return  eventAfterSPPProcessing;
    }

    /**
     * This method is called from MAC module when it is in send mode. Here we check if there are frames in TB
     * if there is one we remove it from TB and send to MAC module and Busy waiting PP is checked by calling 
     * checkBusyWaitingSPP() from main method.
     * 
     */
    public Event processFrames() {
        
        Event eventToMM = null;
        if (!transmitBuffer.isEmpty()) {
            // update the number of Frames processed
            totalFrames++;
            eventToMM = transmitBuffer.remove();
            transmitBufferCapacity = transmitBufferCapacity + FRAME_SIZE;
            // Add delay in TB
            tbDelay += Simulator.getTime() - eventToMM.getTbTimeStamp();
        }
        return eventToMM;
    }
    
    public boolean isBusy() {
        return isBusy;
    }

    public void setBusy(boolean isBusy) {
        this.isBusy = isBusy;
    }

    public float getProcessingRate() {
        return processingRate;
    }

    public void setProcessingRate(float processingRate) {
        this.processingRate = processingRate;
    }
    
    public Event getCurrentEvent() {
        return currentEvent;
    }

    public void setCurrentEvent(Event currentEvent) {
        this.currentEvent = currentEvent;
    }

    public long getTimeforProcessingMessage(int messageSize) {
        return (long) ((messageSize * 8 * processingRate) / 1000) ;
    }
    
    /**
     * This function does the processing of moving a packet from PP to TB. It uses following steps to do so.
     * 1/ Checks if the TB can store the Frames obtained from the in coming message, if it does go to step 3,
     *    else step 2
     * 2/ Puts as many frames as it can into TB and SPP keeps busy waiting for the TB to become free.
     * 3/ Puts the frames into TB and marks SPP as free and which later checks if there are messages in PQ if there are
     *    it processes them.
     */
    public Event checkBusyWaitingSPP() {
        Event eventAfterBusyWaitingProcessing = null;
        
        if (isBusy() && getCurrentEvent() != null) {

            Event event = getCurrentEvent();
            int messageSize = event.getMessageLength();
            int totalFramesInMessage = (int) Math.ceil(messageSize * 1.0 / FRAME_DATA_SIZE);
            // Try adding as many Frames as you can to Transmit Buffer
            for (int i = 1; i <= totalFramesInMessage && FRAME_SIZE <= transmitBufferCapacity; i++) {
                
                Event tb = new Event("SM_TB", FRAME_SIZE, event.getArrivalTimeStamp());
                tb.setTotalMessageLength(event.getTotalMessageLength());
                // if this is the last event set isLastFrame flag
                if (i == totalFramesInMessage) {
                    tb.setIsLastSendFrame(true);
                    sppDelay += Simulator.getTime() - event.getSppTimeStamp();
                }
                tb.setTbTimeStamp(Simulator.getTime());
                transmitBuffer.add(tb);
                //Update event size and transmitBufferCapacity
                transmitBufferCapacity = transmitBufferCapacity - FRAME_SIZE;
                messageSize = Math.max((messageSize - FRAME_DATA_SIZE), 0);
                event.setMessageLength(messageSize);
            }
            
            // checking to see if all the message is converted to frames, if the message is converted to frame
            // message length in then event should be zero. Set the SPP current event to null.
            if (event.getMessageLength() <= 0) {
                setCurrentEvent(null);
                setBusy(false);
                // if the message is converted into Frames, check PQ is it have any more events to handle
                if (!packetQueue.isEmpty()) {
                   setBusy(true);
                   eventAfterBusyWaitingProcessing = new Event(packetQueue.remove());
                   eventAfterBusyWaitingProcessing.setEventType("SM_SPPEnter");
                   long waitTime = getTimeforProcessingMessage(eventAfterBusyWaitingProcessing.getMessageLength());
                   eventAfterBusyWaitingProcessing.setWaitPeriod(Simulator.getTime() - eventAfterBusyWaitingProcessing.getArrivalTimeStamp() + waitTime);
                   pqDelay += Simulator.getTime() - eventAfterBusyWaitingProcessing.getPqTimeStamp();
                }                
            } else {
                // case when a message is not completely converted into frames.
                // set the spp to busy and update the current event in 
                setBusy(true);
                setCurrentEvent(event);
            }
        }
        
        return eventAfterBusyWaitingProcessing;
    }
    
    public int getDroppedMessages() {
        return droppedMessages;
    }
    
    public String getSMStats() {
        return "Send Module Stats: \n"
                + "Total number of Messages processed by SM: " + totalMessages + "\n"
                + "Total number of Messages dropped by SM: " + droppedMessages + "\n"
                + "Total percentage of Messages dropped by SM (%): " + (1.0*droppedMessages/totalMessages)*100 + "\n"
                + "Total number of Frames in SM: " + totalFrames + "\n"
                + "Average delay in PQ: " + (pqDelay * 1.0) / totalMessages + "\n"
                + "Average delay in SPP: " + (sppDelay * 1.0) / totalMessages + "\n"
                + "Average delay in TB: " + (tbDelay * 1.0) / totalFrames;
    }
}

