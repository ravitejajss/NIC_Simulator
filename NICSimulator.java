
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

public class NICSimulator {
    
    public static final int FRAME_SIZE = 1526;
    private static long maxEventTime = 0l;

    private static long time;
    
    private static long rmEvents;
    private static long smEvents;
    private static long smEventDelay;
    private static long rmEventDelay;
    private static long rmBytes;
    private static long smBytes;
    
    public static void main(String args[]) throws SecurityException, IOException {
        // initialize all the needed objects.
        ReceivePacketProcessor rpp = new ReceivePacketProcessor(false, 5);
        ReceiverModule rm = new ReceiverModule(rpp, 6000);
        
        // Set Buffer capacity values for send module.
        int smTotalBufferCapacity = 512 * 1024;
        int smPacketQueueCapacity = 256 * 1024;
        int smTransmitBufferCapacity = smTotalBufferCapacity - smPacketQueueCapacity;
        SendModule sm = new SendModule(smPacketQueueCapacity, smTransmitBufferCapacity, false, 2);
        
        double destinationProbability = 0.5; // Ps
        int meanMessageLength = 16;
        int possionMean = 100;
        double isReceiveModeProb = 0.5;
        long mmTimeInterval = 16l; // calculated using the bandwidth and the processing speed of the MAC
        
        //converging
        long convergance = 50;
        
        MacModule mm = new MacModule(sm, destinationProbability, mmTimeInterval, mmTimeInterval);
        long oldAvgMetric = 0;
        boolean run = true;
        
        while (run) {
            run = false;
            List<Event> currentProcessingEvents;

            PriorityQueue<QueueEvents> eventsList = new PriorityQueue<QueueEvents>();
            // Call the method to add send events.
            getPossionEvents(eventsList, possionMean, meanMessageLength);
            // Call the method to add Mac events.
            getMacReceiveEvents(eventsList, isReceiveModeProb, mmTimeInterval);            
            
            while (!eventsList.isEmpty()) {
                QueueEvents queueEvents = eventsList.poll();
                currentProcessingEvents = queueEvents.getEventsAtThisTime();
                setTime(queueEvents.getTime());
                System.out.println("Event processed time: " + getTime());
                for (Event event : currentProcessingEvents) {
                    Event returnedEvent;
                    if (event.getEventType().startsWith("RM_")) {
                        returnedEvent = rm.processEvent(event);
                        if (returnedEvent != null) {
                            addEvent(eventsList, returnedEvent);
                        }
                    } else if (event.getEventType().startsWith("SM_")) {
                        returnedEvent = sm.processEvent(event);
                        if (returnedEvent != null) {
                            addEvent(eventsList, returnedEvent);
                        }
                    } else if (event.getEventType().startsWith("MM_")){
                        returnedEvent = mm.processEvent(event);
                        if (returnedEvent != null) {
                            addEvent(eventsList, returnedEvent);
                        }
                        // Also call SM checkBusyWaitingSPP if it is MM_SEND event
                        if (event.getEventType().equals("MM_SEND")) {
                            returnedEvent = sm.checkBusyWaitingSPP();
                            if (returnedEvent != null) {
                                addEvent(eventsList, returnedEvent);
                            }
                        }                        
                    } else {
                        System.out.println("Wrong event: " + event);
                    }
                }
            }

            long newAvgMetric = getAverageDelay();
            run = (Math.abs(newAvgMetric - oldAvgMetric) > convergance);
            oldAvgMetric = newAvgMetric;
        }
        
        System.out.println(mm.getMMStats());
        System.out.println();
        System.out.println(rm.getRMStats());
        System.out.println();
        System.out.println(sm.getSMStats());
        System.out.println();
        System.out.println(getStats());
        
    }
    
    public static long getTime() {
        return time;
    }
    
    public static void setTime(long t) {
        time = t;
    }
    
    public static void finishEvent(Event event) {
        // Add code to calculate average delay and other stats for each event.
        if (event.getEventType().startsWith("RM_")) {
            rmEvents++;
            rmEventDelay += (getTime() - event.getArrivalTimeStamp());
            rmBytes += event.getMessageLength();
            // also process all the linked events
            for (Event e : event.getLinkedEvents()) {
                rmEvents++;
                rmEventDelay += (getTime() - e.getArrivalTimeStamp());
                rmBytes += event.getMessageLength();
            }
        } else if (event.getEventType().startsWith("SM_")) {
            smEvents++;
            smEventDelay += (getTime() - event.getArrivalTimeStamp());
            smBytes += event.getTotalMessageLength();
            // also process all the linked events
            for (Event e : event.getLinkedEvents()) {
                smEvents++;
                smEventDelay += (getTime() - e.getArrivalTimeStamp());
                smBytes += event.getTotalMessageLength();
            }
        }
    }
    
    public static long getFinishedSendEvents() {
        return smEvents;
    }
    
    public static long getFinishedReceiveEvents() {
        return rmEvents;
    }
    
    public static long getFinishedSendDelay() {
        return smEventDelay;
    }
    
    public static long getFinishedReceiveDelay() {
        return rmEventDelay;
    }
    
    public static long getAverageSendDelay() {
        if (smEvents != 0) {
            return smEventDelay / smEvents;
        } else {
            return 0;
        }               
    }
    
    public static long getAverageReceiveDelay() {
        if (rmEvents != 0) {
            return rmEventDelay / rmEvents;
        } else {
            return 0;
        }
    } 
    
    public static long getAverageDelay() {
        if (rmEvents == 0 && smEvents == 0) {
            return 0;
        } else {
            return (smEventDelay + rmEventDelay)  / (smEvents + rmEvents);
        }
    }
    
    public static double getThroughPut() {
        if (rmEvents == 0 && smEvents == 0) {
            return 0;
        } else {
            return (smBytes + rmBytes)  / (smEventDelay + rmEventDelay);
        }
    }
    
    public static String getStats() {
        return "Finisher Module stats: \n"
                + "Total finished send events: " + smEvents + "\n"
                + "Average send events Delay: " + getAverageSendDelay() + "\n"
                + "Total finished receive events: " + rmEvents + "\n"
                + "Total wait time in RB: " + rmEventDelay + "\n"
                + "Average Receive events Delay: " + getAverageReceiveDelay() + "\n"
                + "Total Average events Delay: " + getAverageDelay() + "\n"
                + "Average Throughput: " + getThroughPut();        
    }
    
    private static void addEvent(PriorityQueue<QueueEvents> eventsList, Event event) {
        QueueEvents queueEventToInsert = new QueueEvents(event.getArrivalTimeStamp() + event.getWaitPeriod());
        if (eventsList.contains(queueEventToInsert)) {
            QueueEvents qe;
            List<QueueEvents> myListQE = new ArrayList<QueueEvents>();
            qe = eventsList.remove();
            // If the there are events for this time stamp get added events
            while (qe.getTime() != queueEventToInsert.getTime()) {
                myListQE.add(qe);
                qe = eventsList.remove();                
            }
            
            qe.addEvent(event);
            eventsList.add(qe);
            
            for (QueueEvents queueEvents : myListQE) {
                eventsList.add(queueEvents);
            }
            
        } else {
            // add Events
            queueEventToInsert.addEvent(event);
            eventsList.add(queueEventToInsert);
        }
    } 
    
    private static void getPossionEvents(PriorityQueue<QueueEvents> eventsList, 
            int possionMean, int messageLengthMean){
        long nextArrival = getTime();
        for(int i = 0; i < 200 ; i++){
            long interArrival   = (long)StdRandom.poisson(possionMean);
            nextArrival = nextArrival + interArrival;
            int messageLength =  (int) StdRandom.exp(1.0 / (messageLengthMean * 1024));
            // clamp the message length to 64KB
            messageLength = Math.min(messageLength, (64 * 1024)); 
            Event e = new Event("SM_SEND", messageLength, nextArrival);
            e.setTotalMessageLength(messageLength);
            addEvent(eventsList, e);
        } 
        maxEventTime = nextArrival;
    }
    
    private static void getMacReceiveEvents(PriorityQueue<QueueEvents> eventsList, double isReceiveModeProb,
            long timeSlot) {
        int numTimeSlots = (int) Math.ceil(maxEventTime * 1.0 / timeSlot);
        long mmTimeEvent = getTime();
        for (int i = 0; i < numTimeSlots; i++) {
            mmTimeEvent += timeSlot;
            Event e;
            if (Math.random() < isReceiveModeProb) {
                e = new Event("MM_REC", FRAME_SIZE, mmTimeEvent);
            } else {
               e = new Event("MM_SEND", FRAME_SIZE, mmTimeEvent);
            }
            addEvent(eventsList, e);
        }        
    }
}
