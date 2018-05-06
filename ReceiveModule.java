
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReceiveModule {
    
    private static final Logger logger = Logger.getLogger(ReceiveModule.class.getName());
    private static final int FRAME_SIZE = 1500;
    private static Handler fileHandler;

    private Queue<Event> receiveBuffer;
    private int maxFramesInRB;
    
    private boolean isBusy;
    private float processingRate;
    private int framesToAccumulate;
    private Random rand = new Random();
    
    // Variables to store stats
    private int totalFramesProcessed;
    private int droppedFrames;
    private int rbDelay;

    
    public ReceiveModule(final int receiveBufferSize, boolean isBusy, float processingRate) throws SecurityException, IOException {
        // set the max number of frames in ReceiveBuffer
        maxFramesInRB = receiveBufferSize / FRAME_SIZE;
        
        // Initialize Receive Buffer
        receiveBuffer = new LinkedList<Event>();
        
        // Initialize logger
        fileHandler = new FileHandler("receivebuffer.log");
        logger.addHandler(fileHandler);
		logger.setUseParentHandlers(false);
        
		this.isBusy = isBusy;
        this.processingRate = processingRate;
        framesToAccumulate = 0;
		
        //Initialing stats variables
        totalFramesProcessed = 0;
        droppedFrames = 0;
        rbDelay = 0;
    }

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
    
    private Event processRMRecEvent(Event event) {
        totalFramesProcessed++;
        Event eventAfterRMProcessing = null;
        logger.log(Level.INFO, "Frame from MM is being PROCCESED in RM. " + event);
            
        // Step 1 checks status of RPP 
        if (isBusy()) {
            // check if RB can store more Frames.
            if (receiveBuffer.size() >= maxFramesInRB) {
                droppedFrames++;
                Simulator.dm++;
                logger.log(Level.INFO, "Frame from MM is DROPPED "
                        + "as Receive Buffer is full. " + event);
            } else {
                event.setRbTimeStamp(Simulator.getTime());
                receiveBuffer.add(event);
            }                
        } else {
            // Step 2 & step 3
            if (!isFramesToAccumulateGenerated()) {
                generateFramesToAccumulate();
            }
            
            int framesToAccumulate = getFramesToAccumulate();
            event.setRbTimeStamp(Simulator.getTime());
            receiveBuffer.add(event);
            if (framesToAccumulate <= receiveBuffer.size()) {
                setBusy(true);
                eventAfterRMProcessing = new Event(receiveBuffer.remove());                    
                eventAfterRMProcessing.setEventType("RM_VACATE");
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
                eventAfterRMProcessing.setWaitPeriod(lastFrameTimeStamp - firstFrameTimeStamp + getTimeForProcessingFrames(FRAME_SIZE));
            }                
        }
        
        return eventAfterRMProcessing;        
    }

    private Event processRMVacateEvent(Event event) {
        Event eventAfterProcessing = null;
        
        // calculates all the Delays and other stats for the event and it's linked ones.
        logger.log(Level.INFO, "Frame is vacated from RM. " + event);
        Simulator.finishEvent(event);
        setBusy(false);

        if (receiveBuffer.isEmpty()) {
            resetFramesToAccumulate();
        } else {
            generateFramesToAccumulate();
            int framesToAccumulate = getFramesToAccumulate();
            if (framesToAccumulate <= receiveBuffer.size()) {
                setBusy(true);
                eventAfterProcessing = new Event(receiveBuffer.remove());
                rbDelay += (Simulator.getTime() - eventAfterProcessing.getRbTimeStamp());
                eventAfterProcessing.setEventType("RM_VACATE");
                eventAfterProcessing.setWaitPeriod(getTimeForProcessingFrames(FRAME_SIZE));
                for (int i = 1; i < framesToAccumulate; i++) {
                    Event waitingEvent = receiveBuffer.remove();
                    rbDelay += (Simulator.getTime() - waitingEvent.getRbTimeStamp());
                    eventAfterProcessing.addLinkedEvents(waitingEvent);
                }                    
            }
        }
        return eventAfterProcessing;     
    }
    
    public int getDroppedFrames() {
        return droppedFrames;
    }
    
    public boolean isBusy() {
        return isBusy;
    }

    public void setBusy(boolean isBusy) {
        this.isBusy = isBusy;
    }
    
    public boolean isFramesToAccumulateGenerated() {
        return framesToAccumulate != 0;
    }
    
    public int getFramesToAccumulate() {
        return framesToAccumulate;
         
    }
    
    public void generateFramesToAccumulate(){
        framesToAccumulate = rand.nextInt(5) + 1;
    }
    
    public void resetFramesToAccumulate() {
        framesToAccumulate = 0;
    }

    public float getProcessingRate() {
        return processingRate;
    }

    public void setProcessingRate(float processingRate) {
        this.processingRate = processingRate;
    }

    public long getTimeForProcessingFrames(int frameSize) {
        return (long)((processingRate * framesToAccumulate * frameSize * 8) / 1000);
    }
    
    public String getRMStats() {
        return "Receive Module Stats: \n"
                + "Total number of Frames processed by RM: " + totalFramesProcessed + "\n"
                + "Total number of Frames dropped by RM: " + droppedFrames + "\n"
                        + "Total percentage of Messages dropped by SM (%): " + (1.0*droppedFrames/totalFramesProcessed)*100 + "\n"
                + "Total wait time in RB: " + rbDelay + "\n"
                + "Average wait time in RB: " + (rbDelay * 1.0) / (totalFramesProcessed - droppedFrames);
    }
}
