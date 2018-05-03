
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class ReceiverModule {
    
    private final Logger logger;
    private final int FRAME_SIZE = 1500;
    private Handler fileHandler;

    private RPP rpp;
    
    private Queue<Event> receiveBuffer;
    private int maxFramesInRB;
    
    private int totalFramesProcessed;
    private int droppedFrames;
    private int rbDelay;

    public ReceiverModule(RPP rpp, int recBuffSize) throws SecurityException, IOException {
        this.rpp = rpp;
        
        maxFramesInRB = recBuffSize / FRAME_SIZE;
        
        receiveBuffer = new LinkedList<Event>();
        fileHandler = new FileHandler("RB.log");
        this.logger = Logger.getLogger(ReceiverModule.class.getName());
        SimpleFormatter formatter = new SimpleFormatter();            //Logging in Human readable format.
		fileHandler.setFormatter(formatter);
        logger.addHandler(fileHandler);
		logger.setUseParentHandlers(false);
        
        totalFramesProcessed = 0;
        droppedFrames = 0;
        rbDelay = 0;
    }
    
    public Event processEvent(Event event) {
        if (event.getType().equals("RM_REC")) {
            return processRMRecEvent(event);
        } else if (event.getType().equals("RM_VACATE")) {
            return processRMVacateEvent(event);
        } else {
            logger.log(Level.WARNING, "Event wrongly sent to RM module discarding. " + event);
            return null;
        }
    }
    
    private Event processRMRecEvent(Event event) {
        totalFramesProcessed++;
        Event eventAfterRMProcessing = null;
        logger.log(Level.INFO, "Frame from MM is being PROCCESED in RM. " + event); 
        if (rpp.isBusy()) {
            if (receiveBuffer.size() >= maxFramesInRB) {
                droppedFrames++;
                logger.log(Level.INFO, "Frame from MM is DROPPED as Receive Buffer is full. " + event);
            } else {
                event.setRbTimeStamp(Simulator.getTime());
                receiveBuffer.add(event);
            }                
        } else {
            if (!rpp.isFramesToAccumulateGenerated()) {
                rpp.generateFramesToAccumulate();
            }
            
            int framesToAccumulate = rpp.getFramesToAccumulate();
            event.setRbTimeStamp(Simulator.getTime());
            receiveBuffer.add(event);
            if (framesToAccumulate <= receiveBuffer.size()) {
                rpp.setBusy(true);
                eventAfterRMProcessing = new Event(receiveBuffer.remove());                    
                eventAfterRMProcessing.setType("RM_VACATE");
                rbDelay += (Simulator.getTime() - eventAfterRMProcessing.getRbTimeStamp());
                long firstFrameTimeStamp = eventAfterRMProcessing.getArrivalTimeStamp();
                long lastFrameTimeStamp = firstFrameTimeStamp;
                for (int i = 1; i < framesToAccumulate; i++) {
                    Event waitingEvent = receiveBuffer.remove();
                    rbDelay += (Simulator.getTime() - waitingEvent.getRbTimeStamp());
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
    
    private Event processRMVacateEvent(Event event) {
        Event eventAfterRMRPPProcessing = null;
        logger.log(Level.INFO, "Frame is vacated from RM. " + event);
        Simulator.finishEvent(event);
        rpp.setBusy(false);
        if (receiveBuffer.isEmpty()) {
            rpp.resetFramesToAccumulate();
        } else {
            rpp.generateFramesToAccumulate();
            int framesToAccumulate = rpp.getFramesToAccumulate();
            if (framesToAccumulate <= receiveBuffer.size()) {
                rpp.setBusy(true);
                eventAfterRMRPPProcessing = new Event(receiveBuffer.remove());
                rbDelay += (Simulator.getTime() - eventAfterRMRPPProcessing.getRbTimeStamp());
                eventAfterRMRPPProcessing.setType("RM_VACATE");
                eventAfterRMRPPProcessing.setWaitPeriod(rpp.getTimeForProcessingFrames(FRAME_SIZE));
                for (int i = 1; i < framesToAccumulate; i++) {
                    Event waitingEvent = receiveBuffer.remove();
                    rbDelay += (Simulator.getTime() - waitingEvent.getRbTimeStamp());
                    System.out.println("------------------"+rbDelay);
                    eventAfterRMRPPProcessing.addLinkedEvents(waitingEvent);
                }                    
            }
        }
        return eventAfterRMRPPProcessing;     
    }

    public int getDroppedFrames() {
        return droppedFrames;
    }

    public String getRMStats() {
        return "Receive Module Stats: \n"
                + "Total number of Frames processed by RM: " + totalFramesProcessed + "\n"
                + "Total number of Frames dropped by RM: " + droppedFrames + "\n"
                + "Total wait time in RB: " + rbDelay + "\n"
                + "Average wait time in RB: " + (rbDelay * 1.0) / (totalFramesProcessed - droppedFrames);
    }
}
