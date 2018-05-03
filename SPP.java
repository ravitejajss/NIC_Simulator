
public class SPP {

	private Boolean sppBusy;
	private float sppRate;
	private Event currentEvent;
	
	public SPP(Boolean sppBusy, float sppRate) {
		this.sppBusy = sppBusy;
		this.sppRate = sppRate;
		currentEvent = null;
	}
	
	public boolean isBusy() {
        return sppBusy;
    }

    public void setBusy(boolean isBusy) {
        this.sppBusy = isBusy;
    }

    public float getProcessingRate() {
        return sppRate;
    }

    public void setProcessingRate(float processingRate) {
        this.sppRate = processingRate;
    }
    
    public Event getCurrentEvent() {
        return currentEvent;
    }

    public void setCurrentEvent(Event currentEvent) {
        this.currentEvent = currentEvent;
    }

    public long getTimeforProcessingMessage(int messageSize) {
        return (long) ((messageSize * 8 * sppRate) / 1000) ;
    }

}
