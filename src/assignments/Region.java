package assignments;
import java.util.LinkedList;
import umontreal.ssj.randvar.ExponentialGen;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.simevents.Event;
import umontreal.ssj.simevents.Sim;

public class Region {

    // region variables
	LinkedList<Accident> queue; // for the unaddressed accidents
	LinkedList<Ambulance> idleAmbulances;
	double[] baseLocation; // of the particular region
	ArrivalProcess arrivalProcess;
	int regionID;
	Region[] regions;
	int numRegions;

	RandomStream locationStream; // random number generator for locations //(accident or bases?)
    
	public Region(int id, double[] baseLocation, RandomStream arrivalRandomStream, double arrivalRate, RandomStream locationRandomStream, Region[] regionArray, int numRegions) {

		// set region variables
		queue = new LinkedList<>();
		idleAmbulances = new LinkedList<>();
		this.baseLocation = baseLocation;
		regionID = id;
		this.regions = regionArray;
		this.numRegions = numRegions;

		// set random streams
		arrivalProcess = new ArrivalProcess(arrivalRandomStream, arrivalRate);
		locationStream = locationRandomStream;
	}
    
    public void handleArrival() {
        // create and process a new accident
    	double [] custC = drawLocation();
    	Accident cust = new Accident(Sim.time(), custC, regionID);
    	Accident firstCustomer = cust;
    	
    	if(regions[regionID].idleAmbulances.size() > 0) {
    		Ambulance firstIdleAmbulance = regions[regionID].idleAmbulances.removeFirst();
    		firstIdleAmbulance.startService(firstCustomer, (Sim.time()+firstIdleAmbulance.drivingTimeToAccident(firstCustomer)));//+firstIdleAmbulance.drivingTimeToAccident(firstCustomer)
    	} else if (regions[regionID].idleAmbulances.size() == 0) {
    		for(int i=0; i<numRegions; i++) {
    				if(regions[i].idleAmbulances.size() > 0) {
    					Ambulance firstIdleAmbulance2 = regions[i].idleAmbulances.removeFirst();
    					firstIdleAmbulance2.startService(firstCustomer, (Sim.time()+firstIdleAmbulance2.drivingTimeToAccident(firstCustomer)));//+firstIdleAmbulance2.drivingTimeToAccident(firstCustomer))
    				}
    			}
    	} else {
    			queue.addLast(cust);	
    		}	
    }

    // returns a random location inside the region
    public double[] drawLocation() {
    	//https://dawnarc.com/2019/02/mathcheck-if-a-point-is-inside-regular-hexagon/ source used for defining boundaries
        double[] location = new double[2];
        double diameter = 10.0;
        boolean coordinatesInBoundaries = false;
        double alpha = 0.25*Math.sqrt(3.0);
        double xloc = 0;
        double yloc = 0;
        
        while(!coordinatesInBoundaries) {
        	double [] center = this.baseLocation;
        	double xC = -5 + 10*locationStream.nextDouble();
            double yC = -5 + 10*locationStream.nextDouble();
            
            double newXC = xC + center[0];
            double newYC = yC + center[1];
            
            double dx = Math.abs(newXC - center[0])/diameter;
            double dy = Math.abs(newYC - center[1])/diameter;
            
            if(dy < alpha && (alpha*dx + 0.25*dy <= 0.5*alpha)) {
            	coordinatesInBoundaries = true;
            	xloc = newXC;
            	yloc = newYC;
            }
        }
        location[0] = xloc; // X-Coordinate of accident location
        location[1] = yloc; // Y-Coordinate of accident location
        
        return location;
    }
    
	class ArrivalProcess extends Event {

		ExponentialGen arrivalTimeGen;
		double arrivalRate;

		public ArrivalProcess(RandomStream rng, double arrivalRate) {
			this.arrivalRate = arrivalRate;
			arrivalTimeGen = new ExponentialGen(rng, arrivalRate);
		}

		// event: new customer arrival at the store
		@Override
		public void actions() {
			handleArrival();
			double nextArrival = arrivalTimeGen.nextDouble();
			schedule(nextArrival); // schedule a new arrival event
		}

		public void init() {
			double nextArrival = arrivalTimeGen.nextDouble();
			schedule(nextArrival); // schedule a first new arrival
		}
	}
}
