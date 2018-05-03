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
    private double destProb;
    private long recTime;
    private long sendTime;
    private long totalFramesProcessed;
    private long notDestinedFrames;
    private long destinedFrames;
    
    public MacModule(SendModule sendModule, double destProb, long recTime, 
    		long sendTime) throws SecurityException, IOException {
        this.sendModule = sendModule;
        this.destProb = destProb;
        this.recTime = recTime;
        this.sendTime = sendTime;
        
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
        Event eventOut = null;
        double uniRandNum = 0;
        if (event.getType().equals("MM_SEND")) {
            Event e = sendModule.processFrames();
            if (e != null) {
                totalFramesProcessed++;
                logger.log(Level.INFO, "MAC MODULE: Processing Frame from Transfer Buffer. " + e);
                if (e.isLastSendFrame()) {
                    eventOut = new Event(e);
                    eventOut.setType("SM_SPPExit");
                    eventOut.setWaitPeriod(Simulator.getTime() - eventOut.getArrivalTimeStamp() + sendTime);
                } 
            }
        } else if (event.getType().equals("MM_REC")) {
            totalFramesProcessed++;
            uniRandNum = Math.random();
            
            if (uniRandNum > destProb) {
                notDestinedFrames++;
                logger.log(Level.INFO, "MAC MODULE: Wrong destination Frame is received. So, the Frame is DROPPED . " + event);            
            }else {
                destinedFrames++;
                logger.log(Level.INFO, "MAC MODULE: Correct destination Frame is beeing PROCESSED. " + event);
                eventOut = new Event("RM_REC", FRAME_SIZE, Simulator.getTime());
                eventOut.setWaitPeriod(recTime);
            }
        } else {
            logger.log(Level.WARNING, "MAC MODULE: Discarding the wrongly sent Event. " + event);
        }
        return eventOut;
    }

    public String getMMStats() {
        return "Mac Module Stats: \n"
                + "Total number of Frames processed by MM: " + totalFramesProcessed + "\n"
                + "Total number of Frames not destined for current node: " + notDestinedFrames + "\n"
                + "Total number of Frames destined for current node: " + destinedFrames;
    }
}
