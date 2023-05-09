package assignments;

import umontreal.ssj.randvar.ExponentialGen;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.simevents.Event;
import umontreal.ssj.simevents.Sim;
import umontreal.ssj.stat.TallyStore;

public class Ambulance extends Event {

	// ambulance variables
	int id;
	Region baseRegion;
	Accident currentAccident;
	boolean servesOutsideRegion;
	double drivingTimeHospitalToBase;

	// RNG
	ExponentialGen serviceTimeGen;

	// stats counters
	TallyStore waitTimeTally = new TallyStore("Waiting times");
	TallyStore serviceTimeTally = new TallyStore("Service times");
	TallyStore withinTargetTally = new TallyStore("Arrival within target");

	public Ambulance(int id, Region baseRegion, RandomStream serviceRandomStream, double serviceRate, boolean servesOutsideRegion) {
		this.id = id;
		currentAccident = null;
		this.baseRegion = baseRegion;
		serviceTimeGen = new ExponentialGen(serviceRandomStream, serviceRate);
		this.servesOutsideRegion = servesOutsideRegion;
		this.drivingTimeHospitalToBase = drivingTimeHospitalToBase();
	}

    public void startService(Accident accident, double arrivalTimeAtAccident) {
    	currentAccident = accident;
    	accident.serviceStarted(arrivalTimeAtAccident);
    	
        double serviceTimeAtScene = serviceTimeGen.nextDouble();
        double drivingTimeToHostital = drivingTimeToHostital(accident);
        double drivingTimeHospitalToBase = drivingTimeHospitalToBase();
        
        double busyServing = drivingTimeToHostital + drivingTimeHospitalToBase + serviceTimeAtScene;
        // calculate the time needed to process the accident and drive back to the base
        schedule(busyServing);// after busyServing it becomes idle again
    }

    public void serviceCompleted() {
        // process the completed current accident: the ambulance brought the
        // patient to the hospital and is back at its base, what next?
    	currentAccident.completed(Sim.time()+drivingTimeToAccident(currentAccident)+drivingTimeToHostital(currentAccident));
    	waitTimeTally.add(currentAccident.getWaitTime());
    	serviceTimeTally.add(currentAccident.getServiceTime());
    	
    	if(currentAccident.getWaitTime()<=15.0) {
    		withinTargetTally.add(1.0);
    	} else {
    		withinTargetTally.add(0.0);
    	}
    	if(!baseRegion.queue.isEmpty()) {
    		if(baseRegion.idleAmbulances.size() != 0) {
    	//checks if ambulance can be put to use.
    			Accident newAccident = baseRegion.queue.removeFirst();
    			startService(newAccident,(Sim.time()+drivingTimeToAccident(newAccident)));
    		} else if (servesOutsideRegion) {
    			//check if ambulance can be put to work in (neighbour) region
    				for(int i=0; i<baseRegion.numRegions; i++) {
    					if(baseRegion.regions[i].idleAmbulances.size() != 0) {
    						Accident newAccident2 = baseRegion.regions[i].queue.removeFirst();
    						startService(newAccident2,(Sim.time()+drivingTimeToAccident(newAccident2))); 
    					}
    				}
    			}	
    	} else {
    		baseRegion.idleAmbulances.add(this);	
    	}    	
    }

    // return Euclidean distance between accident and hospital
    public double drivingTimeToAccident(Accident cust) {
        // calculate the driving time from the baselocation of the ambulance to the accident location
    	double [] accidentLocation = cust.getLocation();
    	double [] baseLocationXY = this.baseRegion.baseLocation;
    	double x1y1 = accidentLocation[0] - baseLocationXY[0];
    	double x2y2 = accidentLocation[1] - baseLocationXY[1];
    	double euclideanDriveTimeBA = Math.sqrt(Math.pow(x1y1, 2)+Math.pow(x2y2, 2));
    	
        return euclideanDriveTimeBA;
    }

    // return Euclidean distance between accident and hospital
    public double drivingTimeToHostital(Accident cust) {
        // calculate the driving time from accident location to the hospital
    	double [] accidentLocation = cust.getLocation();
    	double [] hospitalLocation = {0.0,0.0}; //hospital is centre 
    	double x1y1 = accidentLocation[0] - hospitalLocation[0];
    	double x2y2 = accidentLocation[1] - hospitalLocation[1];
    	double euclideanDistanceAH = Math.sqrt(Math.pow(x1y1, 2)+Math.pow(x2y2, 2));
        return euclideanDistanceAH;
    }

	// return Euclidean distance from the hospital to the base
	public double drivingTimeHospitalToBase() {
        // calculate the driving time from the hospital to the base
		double [] baseLocationXY = this.baseRegion.baseLocation;
		double [] hospitalLocation = {0.0,0.0};
		double x1y1 = baseLocationXY[0] - hospitalLocation[0];
    	double x2y2 = baseLocationXY[1] - hospitalLocation[1];
    	double euclideanDriveTimeHB = Math.sqrt(Math.pow(x1y1, 2)+Math.pow(x2y2, 2));
        return euclideanDriveTimeHB;
	}

    // event: the ambulance is back at its base after completing service
    @Override
    public void actions() {
        serviceCompleted();
    }
}