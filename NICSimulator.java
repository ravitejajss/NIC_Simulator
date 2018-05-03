
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

public class NICSimulator {
    
    public static final int FRAME_SIZE = 1526;
    private static long maxEventTime = 0l;

    public static void main(String args[]) throws SecurityException, IOException {
        // initialize all the needed objects.
        TimeSource ts = new TimeSource(0);
        ReceivePacketProcessor rpp = new ReceivePacketProcessor(false, 5);
        SendPacketProcessor spp = new SendPacketProcessor(false, 2);
        FinisherModule fm = new FinisherModule(ts);
        ReceiverModule rm = new ReceiverModule(ts, rpp, fm, 6000);
        
        // Set Buffer capacity values for send module.
        int smTotalBufferCapacity = 512 * 1024;
        int smPacketQueueCapacity = 64 * 1024;
        int smTransmitBufferCapacity = smTotalBufferCapacity - smPacketQueueCapacity;
        SendModule sm = new SendModule(ts, spp, fm, smPacketQueueCapacity, smTransmitBufferCapacity);
        
        double destinationProbability = 0.5; // Ps
        int meanMessageLength = 16;
        int possionMean = 100;
        double isReceiveModeProb = 0.5;
        long mmTimeInterval = 16l; // calculated using the bandwidth and the processing speed of the MAC
        
        //converging
        long convergance = 50;
        
        MacModule mm = new MacModule(ts, sm, destinationProbability, mmTimeInterval, mmTimeInterval);
        long oldAvgMetric = 0;
        boolean run = true;
        
        while (run) {
            run = false;
            List<Event> currentProcessingEvents;

            PriorityQueue<QueueEvents> eventsList = new PriorityQueue<QueueEvents>();
            // Call the method to add send events.
            getPossionEvents(eventsList, possionMean, meanMessageLength, ts);
            // Call the method to add Mac events.
            getMacReceiveEvents(eventsList, isReceiveModeProb, mmTimeInterval, ts);            
            
            while (!eventsList.isEmpty()) {
                QueueEvents queueEvents = eventsList.poll();
                currentProcessingEvents = queueEvents.getEventsAtThisTime();
                ts.setTime(queueEvents.getTime());
                System.out.println("Event processed time: " + ts.getTime());
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

            long newAvgMetric = fm.getAverageDelay();
            run = (Math.abs(newAvgMetric - oldAvgMetric) > convergance);
            oldAvgMetric = newAvgMetric;
        }
        
        System.out.println(mm.getMMStats());
        System.out.println();
        System.out.println(rm.getRMStats());
        System.out.println();
        System.out.println(sm.getSMStats());
        System.out.println();
        System.out.println(fm.getStats());
        
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
            int possionMean, int messageLengthMean, TimeSource ts){
        long nextArrival = ts.getTime();
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
            long timeSlot, TimeSource ts) {
        int numTimeSlots = (int) Math.ceil(maxEventTime * 1.0 / timeSlot);
        long mmTimeEvent = ts.getTime();
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
