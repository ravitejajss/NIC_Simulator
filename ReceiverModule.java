
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReceiverModule {
    
    private static final Logger logger = Logger.getLogger(ReceiverModule.class.getName());
    private static final int FRAME_SIZE = 1500;
    private static Handler fileHandler;

    private TimeSource timeSource;
    private ReceivePacketProcessor rpp;
    private FinisherModule finisherModule;
    
    private Queue<Event> receiveBuffer;
    private int maxFramesInRB;
    
    // Variables to store stats
    private int totalFramesProcessed;
    private int droppedFrames;
    private int rbDelay;

    
    public ReceiverModule(final TimeSource timeSource, final ReceivePacketProcessor rpp,
             final FinisherModule finisherModule, final int receiveBufferSize) throws SecurityException, IOException {
        // Initialize RM parameters
        this.timeSource = timeSource;
        this.rpp = rpp;
        this.finisherModule = finisherModule;
        
        // set the max number of frames in ReceiveBuffer
        maxFramesInRB = receiveBufferSize / FRAME_SIZE;
        
        // Initialize Receive Buffer
        receiveBuffer = new LinkedList<Event>();
        
        // Initialize logger
        fileHandler = new FileHandler("./receivebuffer-log");
        logger.addHandler(fileHandler);
        
        //Initialing stats variables
        totalFramesProcessed = 0;
        droppedFrames = 0;
        rbDelay = 0;
    }
    
    /*
     * Receive Module events: RM_REC and RM_VACATE
     * 
     * Also we update the number of events processed by RM only if the event is of type RM_REC.
     * If event is of type RM_VACATE we would have already accounted the event previosuly when
     * it entered RM as RM_REC event. 
     */
    public Event processEvent(Event event) {
        if (event.getEventType().equals("RM_REC")) {
            return processRMRecEvent(event);
        } else if (event.getEventType().equals("RM_VACATE")) {
            return processRMVacateEvent(event);
        } else {
            // Wrong event sent to RM module, discarding the event
            logger.log(Level.WARNING, "Event wrongly sent to RM module discarding. " + event);
            return null;
        }
    }
    
    /**
     * This function processes the frames sent by MM as event. It follows steps given below:
     * 1/ Checks RPP if it is busy, if yes RM might store the frame in RB or drop it,
     *    if not goes to step 2 
     * 2/ If RPP is not busy it checks if RPP is waiting for accumulating packets in RB, if yes
     *    RPP checks if it has enough frames to process and processes them else waits further, if not 
     *    goes to step 3
     * 3/ RRP generates random number of packets it has to accumulate and does the process accordingly.
     * 
     * @param event
     */
    private Event processRMRecEvent(Event event) {
        totalFramesProcessed++;
        Event eventAfterRMProcessing = null;
        logger.log(Level.INFO, "Frame from MM is being PROCCESED in RM. " + event);
            
        // Step 1 checks status of RPP 
        if (rpp.isBusy()) {
            // check if RB can store more Frames.
            if (receiveBuffer.size() >= maxFramesInRB) {
                droppedFrames++;
                logger.log(Level.INFO, "Frame from MM is DROPPED "
                        + "as Receive Buffer is full. " + event);
            } else {
                event.setRbTimeStamp(timeSource.getTime());
                receiveBuffer.add(event);
            }                
        } else {
            // Step 2 & step 3
            if (!rpp.isFramesToAccumulateGenerated()) {
                rpp.generateFramesToAccumulate();
            }
            
            int framesToAccumulate = rpp.getFramesToAccumulate();
            event.setRbTimeStamp(timeSource.getTime());
            receiveBuffer.add(event);
            if (framesToAccumulate <= receiveBuffer.size()) {
                rpp.setBusy(true);
                eventAfterRMProcessing = new Event(receiveBuffer.remove());                    
                eventAfterRMProcessing.setEventType("RM_VACATE");
                rbDelay += (timeSource.getTime() - eventAfterRMProcessing.getRbTimeStamp());
                long firstFrameTimeStamp = eventAfterRMProcessing.getArrivalTimeStamp();
                long lastFrameTimeStamp = firstFrameTimeStamp;
                for (int i = 1; i < framesToAccumulate; i++) {
                    Event waitingEvent = receiveBuffer.remove();
                    rbDelay += (timeSource.getTime() - waitingEvent.getRbTimeStamp());
                    eventAfterRMProcessing.addLinkedEvents(waitingEvent);
                    if ((i + 1) == framesToAccumulate) {
                        lastFrameTimeStamp = waitingEvent.getArrivalTimeStamp();
                    }
                }     
                eventAfterRMProcessing.setWaitPeriod(lastFrameTimeStamp - firstFrameTimeStamp + rpp.getTimeForProcessingFrames(FRAME_SIZE));
            }                
        }
        
        return eventAfterRMProcessing;        
    }
    
    /**
     * This function handles a vacate event from RM module. It calls finishEvent to collect stats
     * about it and then later checks if there are any events that are waiting in RB, if they are 
     * we generate random Frames to accumulate and check if RB has required number of Frames if yes
     * we create a RM_VACATE event and return it. Else we just return null waiting for more frames.
     *  
     * @param event
     * @return
     */
    private Event processRMVacateEvent(Event event) {
        Event eventAfterRMRPPProcessing = null;
        
        // calculates all the Delays and other stats for the event and it's linked ones.
        logger.log(Level.INFO, "Frame is vacated from RM. " + event);
        finisherModule.finishEvent(event);
        rpp.setBusy(false);

        if (receiveBuffer.isEmpty()) {
            rpp.resetFramesToAccumulate();
        } else {
            rpp.generateFramesToAccumulate();
            int framesToAccumulate = rpp.getFramesToAccumulate();
            if (framesToAccumulate <= receiveBuffer.size()) {
                rpp.setBusy(true);
                eventAfterRMRPPProcessing = new Event(receiveBuffer.remove());
                rbDelay += (timeSource.getTime() - eventAfterRMRPPProcessing.getRbTimeStamp());
                eventAfterRMRPPProcessing.setEventType("RM_VACATE");
                eventAfterRMRPPProcessing.setWaitPeriod(rpp.getTimeForProcessingFrames(FRAME_SIZE));
                for (int i = 1; i < framesToAccumulate; i++) {
                    Event waitingEvent = receiveBuffer.remove();
                    rbDelay += (timeSource.getTime() - waitingEvent.getRbTimeStamp());
                    eventAfterRMRPPProcessing.addLinkedEvents(waitingEvent);
                }                    
            }
        }
        return eventAfterRMRPPProcessing;     
    }
    
    public int getDroppedFrames() {
        return droppedFrames;
    }
    
    /**
     * Returns the stats of RM so far.
     * 
     * @return
     */
    public String getRMStats() {
        return "Receive Module Stats: \n"
                + "Total number of Frames processed by RM: " + totalFramesProcessed + "\n"
                + "Total number of Frames dropped by RM: " + droppedFrames + "\n"
                + "Total wait time in RB: " + rbDelay + "\n"
                + "Average wait time in RB: " + (rbDelay * 1.0) / (totalFramesProcessed - droppedFrames);
    }
}
