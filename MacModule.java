import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class MacModule {
    
    private Logger logger = Logger.getLogger(MacModule.class.getName());
    private Handler fileHandler;
    private int FRAME_SIZE = 1500;
    private SendModule sendModule;
    private double destinedProbability;
    private long recTimeInterval;
    private long sendTimeInterval;
    private long totalFramesProcessed;
    private long notDestinedFrames;
    private long destinedFrames;
    
    public MacModule(SendModule sendModule, double destinedProbability, long recTimeInterval, 
    		long sendTimeInterval) throws SecurityException, IOException {
        this.sendModule = sendModule;
        this.destinedProbability = destinedProbability;
        this.recTimeInterval = recTimeInterval;
        this.sendTimeInterval = sendTimeInterval;
        
        fileHandler = new FileHandler("contoller.log");
        SimpleFormatter formatter = new SimpleFormatter();            //Logging in Human readable format.
		fileHandler.setFormatter(formatter);
        logger.addHandler(fileHandler);
		logger.setUseParentHandlers(false);
        
        totalFramesProcessed = 0;
        notDestinedFrames = 0;
        destinedFrames = 0;
    }
    
    public Event processEvent(Event event) {
        Event eventAfterMMProcessing = null;
        double uniformRandNum = 0;
        if (event.getType().equals("MM_SEND")) {
            Event e = sendModule.processFrames();
            if (e != null) {
                totalFramesProcessed++;
                logger.log(Level.INFO, "Processing Frame from Sender Module. " + e);
                if (e.isLastSendFrame()) {
                    eventAfterMMProcessing = new Event(e);
                    eventAfterMMProcessing.setType("SM_SPPExit");
                    eventAfterMMProcessing.setWaitPeriod(Simulator.getTime() - eventAfterMMProcessing.getArrivalTimeStamp() + sendTimeInterval);
                } 
            }
        } else if (event.getType().equals("MM_REC")) {
            totalFramesProcessed++;
            uniformRandNum = Math.random();
            
            if (uniformRandNum > destinedProbability) {
                notDestinedFrames++;
                logger.log(Level.INFO, "Frame is DROPPED as it is for wrong destination. " + event);            
            }else {
                destinedFrames++;
                logger.log(Level.INFO, "Frame is PROCESSED as it for correct destination. " + event);
                eventAfterMMProcessing = new Event("RM_REC", FRAME_SIZE, 0);//timeSource.getTime());
                eventAfterMMProcessing.setWaitPeriod(recTimeInterval);
            }
        } else {
            logger.log(Level.WARNING, "Event wrongly sent to MM module discarding. " + event);
        }
        return eventAfterMMProcessing;
    }

    public String getMMStats() {
        return "Mac Module Stats: \n"
                + "Total number of Frames processed by MM: " + totalFramesProcessed + "\n"
                + "Total number of Frames not destined for current node: " + notDestinedFrames + "\n"
                + "Total number of Frames destined for current node: " + destinedFrames;
    }
}
