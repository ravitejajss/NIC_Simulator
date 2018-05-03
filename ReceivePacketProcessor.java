
import java.util.Random;

public class ReceivePacketProcessor {
    // tells if the ReceivePacketProcessor is busy or not.
    private boolean isBusy;
    private float processingRate;
    private int framesToAccumulate;
    private Random rand = new Random();
    
    public ReceivePacketProcessor(boolean isBusy, float processingRate) {
        this.isBusy = isBusy;
        this.processingRate = processingRate;
        framesToAccumulate = 0;
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
    
}
