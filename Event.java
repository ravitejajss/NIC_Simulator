
import java.util.ArrayList;
import java.util.List;

public class Event {
    
    private String eventType;
    private long arrivalTimeStamp;
    private int messageLength;
    private long waitPeriod;
    private long rbTimeStamp;
    private long pqTimeStamp;
    private long tbTimeStamp;
    private long sppTimeStamp;
    private boolean isLastSendFrame;
    private int totalMessageLength;
    private List<Event> linkedEvents;
    
    public Event(String eventType, int messageLength, long arrivalTimeStamp) {
        this.eventType = eventType;
        this.messageLength = messageLength;
        this.arrivalTimeStamp = arrivalTimeStamp;
        linkedEvents = new ArrayList<Event>();
    }
    
    public Event(Event event) {
        this.eventType = event.getType();
        this.arrivalTimeStamp = event.getArrivalTimeStamp();
        this.messageLength = event.getMessageLength();
        this.waitPeriod = event.getWaitPeriod();
        this.rbTimeStamp = event.getRbTimeStamp();
        this.pqTimeStamp = event.getPqTimeStamp();
        this.tbTimeStamp = event.getTbTimeStamp();
        this.sppTimeStamp = event.getSppTimeStamp();
        this.isLastSendFrame = event.isLastSendFrame();
        this.totalMessageLength = event.getTotalMessageLength();
        this.linkedEvents = new ArrayList<Event>(event.getLinkedEvents());
    }

    public long getArrivalTimeStamp() {
        return arrivalTimeStamp;
    }

    public void setArrivalTimeStamp(long arrivalTimeStamp) {
        this.arrivalTimeStamp = arrivalTimeStamp;
    }

    public int getMessageLength() {
        return messageLength;
    }

    public void setMessageLength(int messageLength) {
        this.messageLength = messageLength;
    }

    public String getType() {
        return eventType;
    }

    public void setType(String eventType) {
        this.eventType = eventType;
    }
    
    public long getWaitPeriod() {
        return waitPeriod;
    }
    
    public void setWaitPeriod(long waitPeriod) {
        this.waitPeriod = waitPeriod;
    }

    public List<Event> getLinkedEvents() {
        return linkedEvents;
    }

    public void addLinkedEvents(Event event) {
        this.linkedEvents.add(event);
    }
    
    public long getRbTimeStamp() {
        return rbTimeStamp;
    }

    public void setRbTimeStamp(long rbTimeStamp) {
        this.rbTimeStamp = rbTimeStamp;
    }
    
    public void setIsLastSendFrame(boolean isLastSendFrame) {
        this.isLastSendFrame = isLastSendFrame;
    }
    
    public boolean isLastSendFrame() {
        return isLastSendFrame;
    }
    
    public long getPqTimeStamp() {
        return pqTimeStamp;
    }

    public void setPqTimeStamp(long pqTimeStamp) {
        this.pqTimeStamp = pqTimeStamp;
    }

    public long getTbTimeStamp() {
        return tbTimeStamp;
    }

    public void setTbTimeStamp(long tbTimeStamp) {
        this.tbTimeStamp = tbTimeStamp;
    }
    
    public long getSppTimeStamp() {
        return sppTimeStamp;
    }

    public void setSppTimeStamp(long sppTimeStamp) {
        this.sppTimeStamp = sppTimeStamp;
    }
    
    public int getTotalMessageLength() {
        return totalMessageLength;
    }

    public void setTotalMessageLength(int totalMessageLength) {
        this.totalMessageLength = totalMessageLength;
    }
    
    public String toString() {
        return "Event Details: \n"
                + "Event start time: " + arrivalTimeStamp + "\n"
                + "Event type: " + eventType + "\n"
                + "Event mesage lenght: " + messageLength;
    }
}
