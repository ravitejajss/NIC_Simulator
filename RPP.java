
import java.util.Random;

public class RPP {
    private boolean isBusy;
    private float rppRate;
    private int framesToAccumulate;
    private Random rand = new Random();
    
    public RPP(boolean isBusy, float processingRate) {
        this.isBusy = isBusy;
        this.rppRate = processingRate;
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
        return rppRate;
    }

    public void setProcessingRate(float processingRate) {
        this.rppRate = processingRate;
    }

    public long getTimeForProcessingFrames(int frameSize) {
        return (long)((rppRate * framesToAccumulate * frameSize * 8) / 1000);
    }
    
}
