package assignments;

import java.util.LinkedList;

import umontreal.ssj.randvar.ExponentialGen;
import umontreal.ssj.rng.MRG32k3a;
import umontreal.ssj.rng.RandomStream;
import umontreal.ssj.simevents.Accumulate;
import umontreal.ssj.simevents.Event;
import umontreal.ssj.simevents.Sim;
import umontreal.ssj.stat.StatProbe;
import umontreal.ssj.stat.Tally;
import umontreal.ssj.stat.list.ListOfStatProbes;


public class Assignment2 {
    // first, pre-written classes are defined: Arivalprocess, Customer, Server, StopEvent

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

	class Customer {

		private double arrivalTime;
		private double startTime;
		private double completionTime;
		private double waitTime;
		private double serviceTime;
		private int chosenServerIndex;

		public Customer() {
			// record arrival time when creating a new customer
			arrivalTime = Sim.time();
			startTime = Double.NaN;
			completionTime = Double.NaN;
			waitTime = Double.NaN;
			serviceTime = serviceTimeGen.nextDouble();
		}

		// call this when a customer has chosen a server
		public void chooseServer(int serverIndex) {
			chosenServerIndex = serverIndex;
		}

		// call this when the customer starts its service
		public void serviceStarted() {
			startTime = Sim.time();
			waitTime = startTime - arrivalTime;
		}

		// call this when the service is completed
		public void completed() {
			completionTime = Sim.time();
			serviceTime = completionTime - startTime;
		}
	}

	class Server extends Event {

		static final double BUSY = 1.0;
		static final double IDLE = 0.0;
		Customer currentCust; // current customer in service
		LinkedList<Customer> queue;
		boolean openServer;
		boolean busyServer;
		Accumulate utilization; // records the server utilization

		public Server(Accumulate utilization) {
			currentCust = null;
			queue = new LinkedList<>();
			openServer = true;
			busyServer = false;
			this.utilization = utilization;
			utilization.init(IDLE);
		}

		// event: service completion
		@Override
		public void actions() {
			utilization.update(IDLE);
			busyServer = false;
			serviceCompleted(this, currentCust);
		}

		public void startService(Customer cust) {
			utilization.update(BUSY);
			busyServer = true;
			currentCust = cust;
			cust.serviceStarted();
			schedule(cust.serviceTime); // schedule completion time
		}

		public void closeServer() {
			this.openServer = false;
		}

		public void openServer() {
			this.openServer = true;
		}
	}

	// stop simulation by scheduling this event
	class StopEvent extends Event {
		@Override
		public void actions() {
			Sim.stop();
		}
	}
    
	// grocery store variables
	Server[] serverList;
	int numServers;
	double arrivalRate;
	double serviceRate;
	double stopTime;
	int openLimit;

	// RNGs
	ArrivalProcess arrivalProcess;
	ExponentialGen serviceTimeGen;

	// stats counters
	Tally serviceTimeTally;
	Tally waitTimeTally;
	ListOfStatProbes<StatProbe> listStatsAccumulate;
	ListOfStatProbes<StatProbe> listStatsTallies;
    
    public Assignment2(int numServers, double arrivalRate, double serviceRate, double stopTime, int openLimit) {

        this.arrivalRate = arrivalRate;
		this.serviceRate = serviceRate;
		this.numServers = numServers;
		serverList = new Server[numServers];
		this.openLimit = openLimit;
		this.stopTime = stopTime;

		listStatsAccumulate = new ListOfStatProbes<>("Stats for Accumulate");
		listStatsTallies = new ListOfStatProbes<>("Stats for Tallies");

		// create servers and add them to the serverList
		for (int i = 0; i < numServers; i++) {
			String id = "Server " + i;
			Accumulate utilization = new Accumulate(id);
			listStatsAccumulate.add(utilization);
			Server server = new Server(utilization);
			serverList[i] = server;
			if (i > 0) {
				server.closeServer();
			}
		}

		// create inter arrival time, and service time generators
		arrivalProcess = new ArrivalProcess(new MRG32k3a(), arrivalRate);
		serviceTimeGen = new ExponentialGen(new MRG32k3a(), serviceRate);

		// create Tallies and store them in ListOfStatProbes for later reporting
		waitTimeTally = new Tally("Waittime");
		serviceTimeTally = new Tally("Servicetime");
		listStatsTallies.add(waitTimeTally);
		listStatsTallies.add(serviceTimeTally);
    }

    public ListOfStatProbes[] simulateOneRun() {
        
		Sim.init();

		// reset stats counters
		listStatsTallies.init();
		listStatsAccumulate.init();

		// set first events
		arrivalProcess.init(); // schedules first arrival
		new StopEvent().schedule(stopTime); // schedule stopping time

		// start simulation
		Sim.start();

		// return stats counters
		ListOfStatProbes[] output = new ListOfStatProbes[2];
		output[0] = listStatsAccumulate;
		output[1] = listStatsTallies;
		return output;
    }
    
    int Question1() { 
    	// write a method that returns the index of an open server with the shortest queue
    	int indexShortestQueue = 0;
    	int initialShortestQueue = 0;
    	for(int i=0; i<numServers; i++) {
    		if(serverList[i].openServer) { 
    			initialShortestQueue = serverList[i].queue.size();
    			indexShortestQueue = i;
    		}
    	}
    	for(int i=0; i<numServers; i++) { 
    		if(serverList[i].openServer && !serverList[i].busyServer) {
    			return i;
    		}
    		if(serverList[i].openServer && initialShortestQueue > serverList[i].queue.size()) {
    			initialShortestQueue = serverList[i].queue.size();
    			indexShortestQueue = i;
    		}
    	}
    	return indexShortestQueue;
    }
    
    boolean Question2() {
    	// write a method that returns true if a new server should be opened
    	for(int i=0; i<numServers; i++) {
    		if(serverList[i].openServer) {
    			if(serverList[i].queue.size() < openLimit) {
    				return false;
    			}
    		}
    	} return true;
    }
   
    void Question3() {
    	int numServersOpened2 = 0; //open servers
    	int numNotBusyServers = 0; //idle servers
    	for(int i=0; i<numServers; i++) {
    		if(serverList[i].openServer) { //number of open servers
    			numServersOpened2 += 1;
    		}	
    		if(!serverList[i].busyServer) {//number of idle server
    			numNotBusyServers += 1;
    		}
    	}
    	if(numServersOpened2 == numNotBusyServers + 1) {
    		for(int i=0; i<numServers; i++) {
    			if(serverList[i].openServer) {
    				if(!serverList[i].busyServer) {
    					serverList[i].closeServer();
    				}
    			}
    		}
    	}
    }

    void handleArrival() { 
       // write a method that handles the arrival of a customer to the store
    	Customer cust = new Customer();
    	int indexShortestQueue2 = Question1();
    	if(serverList[indexShortestQueue2].busyServer) {
    		serverList[indexShortestQueue2].queue.addLast(cust);
    	} else {
    		serverList[indexShortestQueue2].startService(cust);
    	}

    	if(Question2()) {
    		boolean actionServerOpened = false;
    		int i = 0;
    		while(i<numServers && actionServerOpened == false) {
    			if(!serverList[i].openServer) {
    				serverList[i].openServer();
    				actionServerOpened = true;
    			}
    			i++;
    		}
    	}
    }

    void serviceCompleted(Server server, Customer cust) { 
        // write a method that completes the service of the customer, update the Tallies and starts the service of a new customer if needed
    	cust.completed();
    	waitTimeTally.add(cust.waitTime);
    	serviceTimeTally.add(cust.serviceTime);
    	if(server.queue.isEmpty()) {
    		Question3();
    	} else {
    		Customer newCust = server.queue.removeFirst();
    		server.startService(newCust);
    	}
    }
    
    public static void main(String[] args) {

    	// grocery store variables
        int numServers = 5; // number of available servers
        double lambda = 3; // arrival rate
        double mu = 3./2; // service rate
        double stopTime = 10000; // simulation endtime (minutes)
		int openLimit = 3; // number of customers at the server (excluding the one in service) before a new server can be opened

		// test 1 (balanced system)
        Assignment2 grocery = new Assignment2(numServers, lambda, mu, stopTime, openLimit);
        ListOfStatProbes[] output = grocery.simulateOneRun();
		System.out.println(output[0].report());
		System.out.println(output[1].report());
		
		// test 2 (unbalanced system)
        lambda = 3; // arrival rate
        mu = 0.01; // service rate
        Assignment2 grocery1 = new Assignment2(numServers, lambda, mu, stopTime, openLimit);
        ListOfStatProbes[] output1 = grocery1.simulateOneRun();
		System.out.println(output1[0].report());
		System.out.println(output1[1].report());
		
    }
}