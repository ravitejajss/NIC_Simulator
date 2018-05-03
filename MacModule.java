
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MacModule {
    
    private static final Logger logger = Logger.getLogger(MacModule.class.getName());
    private static Handler fileHandler;
    private static final int FRAME_SIZE = 1500;
    private SendModule sendModule;
    private double destinedProbability;
    private long recTimeInterval;
    private long sendTimeInterval;
    
    
    // Variables to store stats
    private long totalFramesProcessed;
    private long notDestinedFrames;
    private long destinedFrames;
    
    public MacModule(final SendModule sendModule,
            final double destinedProbability, final long recTimeInterval,
            final long sendTimeInterval) throws SecurityException, IOException {
        this.sendModule = sendModule;
        this.destinedProbability = destinedProbability;
        this.recTimeInterval = recTimeInterval;
        this.sendTimeInterval = sendTimeInterval;
        
        // Initialize logger
        fileHandler = new FileHandler("contoller.log");
        logger.addHandler(fileHandler);
		logger.setUseParentHandlers(false);
        
        //Initialing stats variables
        totalFramesProcessed = 0;
        notDestinedFrames = 0;
        destinedFrames = 0;
    }
    
    /**
     * Mac Module is simple. It does following:
     * 1/ If the event is to send a packet into Internet we call processFrame from the sender module
     *    which checks if there are frames in TB and process it if there are any. We check if it is last frame 
     *    for a message if it is we create sender module finish event and return which will later be used to 
     *    collect stats for the message
     * 2/ If the event is to receive a packet from Internet we check if the frame is destined to current node and 
     *    then we generate an Event for the receiver module and insert it in our event list, which will be processed 
     *    as and when we reach it.
     */
    public Event processEvent(Event event) {
        Event eventAfterMMProcessing = null;
        double uniformRandNum = 0;
 
        if (event.getEventType().equals("MM_SEND")) {
            Event e = sendModule.processFrames();
            if (e != null) {
                totalFramesProcessed++;
                logger.log(Level.INFO, "Processing Frame from Sender Module. " + e);
                if (e.isLastSendFrame()) {
                    // create a new event if the frame is the last event in the message and return 
                    eventAfterMMProcessing = new Event(e);
                    eventAfterMMProcessing.setEventType("SM_FIN");
                    eventAfterMMProcessing.setWaitPeriod(NICSimulator.getTime() - eventAfterMMProcessing.getArrivalTimeStamp() + sendTimeInterval);
                } 
            }
        } else if (event.getEventType().equals("MM_REC")) {
            totalFramesProcessed++;
            uniformRandNum = Math.random();
            
            if (uniformRandNum > destinedProbability) {
                // Frame not destined to current node
                notDestinedFrames++;
                logger.log(Level.INFO, "Frame is DROPPED as it is for wrong destination. " + event);            
            }else {
                // Frame destined to current node
                destinedFrames++;
                logger.log(Level.INFO, "Frame is PROCESSED as it for correct destination. " + event);
                // create a new event and return it.
                eventAfterMMProcessing = new Event("RM_REC", FRAME_SIZE, NICSimulator.getTime());
                eventAfterMMProcessing.setWaitPeriod(recTimeInterval);
            }
        } else {
            // Wrong event sent to RM module, discarding the event
            logger.log(Level.WARNING, "Event wrongly sent to MM module discarding. " + event);
        }

        return eventAfterMMProcessing;
    }
    
    /**
     * Returns the stats of MM so far.
     * 
     * @return
     */
    public String getMMStats() {
        return "Mac Module Stats: \n"
                + "Total number of Frames processed by MM: " + totalFramesProcessed + "\n"
                + "Total number of Frames not destined for current node: " + notDestinedFrames + "\n"
                + "Total number of Frames destined for current node: " + destinedFrames;
    }
}
