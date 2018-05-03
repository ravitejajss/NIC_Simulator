
public class FinisherModule {
    private TimeSource timeSource;
    private long rmEvents;
    private long smEvents;
    private long smEventDelay;
    private long rmEventDelay;
    private long rmBytes;
    private long smBytes;
    
    public FinisherModule(TimeSource timeSource) {
        this.timeSource = timeSource;
    }
    
    public void finishEvent(Event event) {
        // Add code to calculate average delay and other stats for each event.
        if (event.getEventType().startsWith("RM_")) {
            rmEvents++;
            rmEventDelay += (timeSource.getTime() - event.getArrivalTimeStamp());
            rmBytes += event.getMessageLength();
            // also process all the linked events
            for (Event e : event.getLinkedEvents()) {
                rmEvents++;
                rmEventDelay += (timeSource.getTime() - e.getArrivalTimeStamp());
                rmBytes += event.getMessageLength();
            }
        } else if (event.getEventType().startsWith("SM_")) {
            smEvents++;
            smEventDelay += (timeSource.getTime() - event.getArrivalTimeStamp());
            smBytes += event.getTotalMessageLength();
            // also process all the linked events
            for (Event e : event.getLinkedEvents()) {
                smEvents++;
                smEventDelay += (timeSource.getTime() - e.getArrivalTimeStamp());
                smBytes += event.getTotalMessageLength();
            }
        }
    }
    
    public long getFinishedSendEvents() {
        return smEvents;
    }
    
    public long getFinishedReceiveEvents() {
        return rmEvents;
    }
    
    public long getFinishedSendDelay() {
        return smEventDelay;
    }
    
    public long getFinishedReceiveDelay() {
        return rmEventDelay;
    }
    
    public long getAverageSendDelay() {
        if (smEvents != 0) {
            return smEventDelay / smEvents;
        } else {
            return 0;
        }               
    }
    
    public long getAverageReceiveDelay() {
        if (rmEvents != 0) {
            return rmEventDelay / rmEvents;
        } else {
            return 0;
        }
    } 
    
    public long getAverageDelay() {
        if (rmEvents == 0 && smEvents == 0) {
            return 0;
        } else {
            return (smEventDelay + rmEventDelay)  / (smEvents + rmEvents);
        }
    }
    
    public double getThroughPut() {
        if (rmEvents == 0 && smEvents == 0) {
            return 0;
        } else {
            return (smBytes + rmBytes)  / (smEventDelay + rmEventDelay);
        }
    }
    
    public String getStats() {
        return "Finisher Module stats: \n"
                + "Total finished send events: " + smEvents + "\n"
                + "Average send events Delay: " + getAverageSendDelay() + "\n"
                + "Total finished receive events: " + rmEvents + "\n"
                + "Total wait time in RB: " + rmEventDelay + "\n"
                + "Average Receive events Delay: " + getAverageReceiveDelay() + "\n"
                + "Total Average events Delay: " + getAverageDelay() + "\n"
                + "Average Throughput: " + getThroughPut();        
    }
}
