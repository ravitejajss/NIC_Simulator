
public class SendPacketProcessor{
    
    private boolean isBusy;
    private float processingRate;
    private Event currentEvent;

    public SendPacketProcessor(boolean isBusy, float processingRate) {
        this.isBusy = isBusy;
        this.processingRate = processingRate;
        currentEvent = null;
    }

    public boolean isBusy() {
        return isBusy;
    }

    public void setBusy(boolean isBusy) {
        this.isBusy = isBusy;
    }

    public float getProcessingRate() {
        return processingRate;
    }

    public void setProcessingRate(float processingRate) {
        this.processingRate = processingRate;
    }
    
    public Event getCurrentEvent() {
        return currentEvent;
    }

    public void setCurrentEvent(Event currentEvent) {
        this.currentEvent = currentEvent;
    }

    public long getTimeforProcessingMessage(int messageSize) {
        return (long) ((messageSize * 8 * processingRate) / 1000) ;
    }
}