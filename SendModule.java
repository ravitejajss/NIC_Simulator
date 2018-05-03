import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class SendModule {
	
	private final int FRAME_SIZE = 1526;
	private final int FRAME_DATA_SIZE = 1500;
	private Queue<Event> pktQueue;
	private Queue<Event> transmitBuffer;
	private SPP spp;
	private int transBuffSize;
	private int pktQSize;
	private final Logger logger;
	private FileHandler fileHandler;
    private int totalMessages;
    private int totalFrames;
    private int droppedMessages;
    private long delayInTB;
    private long delayInPQ;
    private long delayInSPP;
	
	public SendModule(SPP spp, int transBuffSize, int pktQSize) throws SecurityException, IOException{
		this.pktQueue = new LinkedList<Event>();
		this.transmitBuffer = new LinkedList<Event>();
		
		this.spp = spp;
		this.transBuffSize = transBuffSize;
		this.pktQSize = pktQSize;
		
		this.fileHandler = new FileHandler("SM.log");
		this.logger = Logger.getLogger(SendModule.class.getName());
		SimpleFormatter formatter = new SimpleFormatter();            //Logging in Human readable format.
		fileHandler.setFormatter(formatter);
        logger.addHandler(fileHandler);
		logger.setUseParentHandlers(false);
        
        totalMessages = 0;
        totalFrames = 0;
        droppedMessages = 0;
        delayInTB = 0;
        delayInPQ = 0;
        delayInSPP = 0;
	}
	
	public Event processPacket(Event event) {
		if(event.getType().equals("SM_PQ")) {
			return processSM_PQPacket(event);
		}
		else if (event.getType().equals("SM_SPPEnter")) {
			return processSM_SPPEnterPacket(event);
		}
		else if (event.getType().equals("SM_SPPExit")){
			logger.log(Level.INFO, "Message is being FINISHED in SM. " + event);
			Simulator.finishEvent(event);
			return null;
		}
		else {
			logger.log(Level.WARNING, "Event wrongly sent to Send module discarding: " + event);
			return null;
		}
	}

	public Event processSM_PQPacket(Event event) {
		logger.log(Level.INFO, "Message is being PROCESSED in SM. " + event);
	    Event eventSMProcessed = null;
	    int messageSize = event.getMessageLength();
	    totalMessages++; 
	    if (spp.isBusy()) {
	        if (messageSize <= pktQSize) {
	            event.setPqTimeStamp(Simulator.getTime());
	            pktQueue.add(event);
	            pktQSize = pktQSize - event.getMessageLength();
	        } else {
	            droppedMessages++;
	            logger.log(Level.INFO, "Message is DROPPED "
	                    + "as Packet Queue does not have enough space. " + event);
	        }
	    } else {
	        eventSMProcessed = pktQueue.poll();
	        if (eventSMProcessed != null) {
	            delayInPQ += Simulator.getTime() - eventSMProcessed.getPqTimeStamp();
	            pktQSize = pktQSize + eventSMProcessed.getMessageLength();
	            if (messageSize <= pktQSize) {
	                event.setPqTimeStamp(Simulator.getTime());
	                pktQueue.add(event);
	                pktQSize = pktQSize - messageSize;
	                
	            } else {
	                droppedMessages++;
	                logger.log(Level.INFO, "Message is DROPPED "
	                        + "as Packet Queue does not have enough space. " + event);
	            }
	        } else {
	            eventSMProcessed = new Event(event);
	        }
	        spp.setBusy(true);
	        eventSMProcessed.setType("SM_SPPEnter");
	        eventSMProcessed.setWaitPeriod(spp.getTimeforProcessingMessage(eventSMProcessed.getMessageLength()));
	    }
	    return eventSMProcessed;
	}

	public Event processSM_SPPEnterPacket(Event event) {
		logger.log(Level.INFO, "Message is being VACATED from PP in SM. " + event);
        event.setSppTimeStamp(Simulator.getTime());
        spp.setCurrentEvent(event);
        Event eventAfterSPPProcessing = ProtocolProcessing();
        return  eventAfterSPPProcessing;
	}
	
    public Event processFrames() {
    	Event eventToMac = null;
        if (!transmitBuffer.isEmpty()) {
            totalFrames++;
            eventToMac = transmitBuffer.remove();
            transBuffSize = transBuffSize + FRAME_SIZE;
            delayInTB += Simulator.getTime() - eventToMac.getTbTimeStamp();
        }
        return eventToMac;
    }
    
    public Event ProtocolProcessing() {
    	Event eventAfterProcessing = null;
        if (spp.isBusy() && spp.getCurrentEvent() != null) {
        	Event event = spp.getCurrentEvent();
            int messageSize = event.getMessageLength();
            int totalFramesInMessage = (int) Math.ceil(messageSize * 1.0 / FRAME_DATA_SIZE);
            for (int i = 1; i <= totalFramesInMessage && FRAME_SIZE <= transBuffSize; i++) {
                Event tb = new Event("SM_TB", FRAME_SIZE, event.getArrivalTimeStamp());
                tb.setTotalMessageLength(event.getTotalMessageLength());
                if (i == totalFramesInMessage) {
                    tb.setIsLastSendFrame(true);
                    delayInSPP += Simulator.getTime() - event.getSppTimeStamp();
                }
                tb.setTbTimeStamp(Simulator.getTime());
                transmitBuffer.add(tb);
                transBuffSize = transBuffSize - FRAME_SIZE;
                messageSize = Math.max((messageSize - FRAME_DATA_SIZE), 0);
                event.setMessageLength(messageSize);
            }
            
            if (event.getMessageLength() <= 0) {
                spp.setCurrentEvent(null);
                spp.setBusy(false);
                if (!pktQueue.isEmpty()) {
                   spp.setBusy(true);
                   eventAfterProcessing = new Event(pktQueue.remove());
                   eventAfterProcessing.setType("SM_SPPEnter");
                   long waitTime = spp.getTimeforProcessingMessage(eventAfterProcessing.getMessageLength());
                   eventAfterProcessing.setWaitPeriod(Simulator.getTime() - eventAfterProcessing.getArrivalTimeStamp() + waitTime);
                   delayInPQ += Simulator.getTime() - eventAfterProcessing.getPqTimeStamp();
                }                
            } else {
                spp.setBusy(true);
                spp.setCurrentEvent(event);
            }
        }
		return eventAfterProcessing;
    }
    
    public int getDroppedMessages() {
        return droppedMessages;
    }
    
    public String getSMStats() {
        return "Send Module Stats: \n"
                + "Total number of Messages processed by SM: " + totalMessages + "\n"
                + "Total number of Messages dropped by SM: " + droppedMessages + "\n"
                + "Total number of Frames in SM: " + totalFrames + "\n"
                + "Average delay in PQ: " + (delayInPQ * 1.0) / totalMessages + "\n"
                + "Average delay in SPP: " + (delayInSPP * 1.0) / totalMessages + "\n"
                + "Average delay in TB: " + (delayInTB * 1.0) / totalFrames;
    }
}
