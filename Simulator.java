
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

public class Simulator {
    
    public static int FRAME_SIZE = 1526;
    private static long maxEventTime = 0l;

    private static long rmEvents;
    private static long smEvents;
    private static long smEventDelay;
    private static long rmEventDelay;
    private static long rmBytes;
    private static long smBytes;
    private static long time = 0;
    
    public static void main(String args[]) throws SecurityException, IOException {
        RPP rpp = new RPP(false, 5);
        SPP spp = new SPP(false, 2);
        ReceiveModule receiverModule = new ReceiveModule(rpp, 6000);
        
        int smTotalCapacity = 512 * 1024;
        int smPQCapacity = 256 * 1024;
        int smTBCapacity = smTotalCapacity - smPQCapacity;
        SendModule sendModule = new SendModule(spp, smPQCapacity, smTBCapacity);
        
        double destProbability = 0.5;
        int meanMsgLen = 32;
        int poissonMean = 100;
        double recModeProb = 0.5;
        long macTime = 16l;
        
        MacModule macModule = new MacModule(sendModule, destProbability, macTime, macTime);
        boolean run = true;
        
        while (run) {
            run = false;
            List<Event> currentProcessingEvents;

            PriorityQueue<QueueEvents> eventsList = new PriorityQueue<QueueEvents>();
            getPoissonEvents(eventsList, poissonMean, meanMsgLen);
            getMacReceiveEvents(eventsList, recModeProb, macTime);            
            
            while (!eventsList.isEmpty()) {
                QueueEvents queueEvents = eventsList.poll();
                currentProcessingEvents = queueEvents.getEventsAtThisTime();
                setTime(queueEvents.getTime());
                System.out.println("Event processed time: " + getTime());
                for (Event event : currentProcessingEvents) {
                    Event returnedEvent;
                    if (event.getType().startsWith("RM_")) {
                        returnedEvent = receiverModule.processEvent(event);
                        if (returnedEvent != null) {
                            addEvent(eventsList, returnedEvent);
                        }
                    } else if (event.getType().startsWith("SM_")) {
                        returnedEvent = sendModule.processPacket(event);
                        if (returnedEvent != null) {
                            addEvent(eventsList, returnedEvent);
                        }
                    } else if (event.getType().startsWith("MM_")){
                        returnedEvent = macModule.processEvent(event);
                        if (returnedEvent != null) {
                            addEvent(eventsList, returnedEvent);
                        }
                        if (event.getType().equals("MM_SEND")) {
                            returnedEvent = sendModule.ProtocolProcessing();
                            if (returnedEvent != null) {
                                addEvent(eventsList, returnedEvent);
                            }
                        }                        
                    } else {
                        System.out.println("Wrong event: " + event);
                    }
                }
            }
        }
        
        System.out.println(macModule.getMMStats() +"\n\n"+ receiverModule.getRMStats() +"\n\n"+ sendModule.getSMStats() +"\n\n"+ getStats());
        
    }
    
    private static void addEvent(PriorityQueue<QueueEvents> eventsList, Event event) {
        QueueEvents queueEventToInsert = new QueueEvents(event.getArrivalTimeStamp() + event.getWaitPeriod());
        if (eventsList.contains(queueEventToInsert)) {
            QueueEvents qe;
            List<QueueEvents> myListQE = new ArrayList<QueueEvents>();
            qe = eventsList.remove();
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
            queueEventToInsert.addEvent(event);
            eventsList.add(queueEventToInsert);
        }
    } 
    
    private static void getPoissonEvents(PriorityQueue<QueueEvents> eventsList, 
        int poissonMean, int msgLenMean){
        long nextArrival = getTime();
        for(int i = 0; i < 200 ; i++){
            long interArrival   = (long)StdRandom.poisson(poissonMean);
            nextArrival = nextArrival + interArrival;
            int messageLength =  (int) StdRandom.exp(1.0 / (msgLenMean * 1024));
            messageLength = Math.min(messageLength, (64 * 1024)); 
            Event e = new Event("SM_PQ", messageLength, nextArrival);
            e.setTotalMessageLength(messageLength);
            addEvent(eventsList, e);
        } 
        maxEventTime = nextArrival;
    }
    
    public static void finishEvent(Event event) {
        if (event.getType().startsWith("RM_")) {
            rmEvents++;
            rmEventDelay += (getTime() - event.getArrivalTimeStamp());
            rmBytes += event.getMessageLength();
            for (Event e : event.getLinkedEvents()) {
                rmEvents++;
                rmEventDelay += (getTime() - e.getArrivalTimeStamp());
                rmBytes += event.getMessageLength();
            }
        } else if (event.getType().startsWith("SM_")) {
            smEvents++;
            smEventDelay += (getTime() - event.getArrivalTimeStamp());
            smBytes += event.getTotalMessageLength();
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
    
    public static long getTime() {
        return time;
    }
    
    public static void setTime(long t) {
        time = t;
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
    
    public static void getMacReceiveEvents(PriorityQueue<QueueEvents> eventsList, double isReceiveModeProb, long timeSlot) {
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
