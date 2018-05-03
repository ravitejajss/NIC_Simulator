
import java.util.ArrayList;
import java.util.List;

public class QueueEvents implements Comparable<QueueEvents>{

    private long time;
    private List<Event> eventsAtThisTime;
    
    public QueueEvents(long time) {
        this.time = time;
        eventsAtThisTime = new ArrayList<Event>();
    }
    
    public long getTime() {
        return time;
    }

    public List<Event> getEventsAtThisTime() {
        return eventsAtThisTime;
    }
    
    public void addEvent(Event e) {
        eventsAtThisTime.add(e);
    }
    
    public boolean equals(Object qe) {
        if (qe == this) {
            return true;
        }
        
        if (!(qe instanceof QueueEvents)) {
            return false;
        }
        QueueEvents events = (QueueEvents) qe;
        return this.time == events.getTime();
    }

    @Override
    public int compareTo(QueueEvents qe) {
        return Long.valueOf(this.time).compareTo(Long.valueOf(qe.time));
    }
}
