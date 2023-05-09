package assignments;

public class Accident {

    private double arrivalTime; // when accident occurred
    private double startTime;
    private double completionTime;
    private double waitTime;
    private double serviceTime;
    private double[] location;
    private int region;

    public Accident(double arrivalTime, double[] location, int region) {
        this.arrivalTime = arrivalTime;
        this.startTime = Double.NaN;
        this.completionTime = Double.NaN;
        this.waitTime = Double.NaN;
        this.serviceTime = Double.NaN;
        this.location = location;
        this.region = region;
    }

    public double[] getLocation() {
        return location;
    }

    public int getRegion() {
        return region;
    }

    public double getArrivalTime() {
    	return arrivalTime;
    }
    
    public double getServiceTime() {
        return serviceTime;
    }
    
    public double getWaitTime() {
        return waitTime;
    }

    // call this when the service starts
    public void serviceStarted(double currentTime) {
        this.startTime = currentTime;
        this.waitTime = currentTime - this.arrivalTime;
    }

    // call this when the service is completed
    public void completed(double timeOfCompletion) {
        this.completionTime = timeOfCompletion;
        this.serviceTime = timeOfCompletion - this.startTime;
    }
}
