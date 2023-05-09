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
import umontreal.ssj.stat.TallyStore;
import umontreal.ssj.stat.list.ListOfStatProbes;

/**
 * Class for simulating the threshold queue for Assignment 3, see main method
 * for examples on how to use it.
 *
 * @author mctenthij
 * Edited by qvdkaaij and jberkhout
 *
 */
public class ThresholdQueue {

    // threshold queue variables
    int k; // threshold value when server starts working again on normalServiceRate
    int K; // threshold value from which server starts working on highServiceRate
    double arrivalRate;
    double lowServiceRate; // standard service rate
    double highServiceRate;
    double stopTime;
    LinkedList<Customer> queue;
    Server server;

    // RNGs
    ArrivalProcess arrivalProcess;
    MRG32k3a serviceTimeRNG; // random numbers to use when generating service times

    // stats counters
    Tally serviceTimeTally;
    TallyStore waitTimeTally;
    Accumulate utilization;
    Accumulate runningCosts;
    ListOfStatProbes<StatProbe> listStatsAccumulate;
    ListOfStatProbes<StatProbe> listStatsTallies;

    public ThresholdQueue(double arrivalRate, double lowServiceRate, double highServiceRate, double stopTime, int k, int K, MRG32k3a arrival, MRG32k3a service) {

        // set threshold queue variables
    	this.arrivalRate = arrivalRate;
        this.lowServiceRate = lowServiceRate;
        this.highServiceRate = highServiceRate;
        this.stopTime = stopTime;
        this.k = k;
        this.K = K;
        queue = new LinkedList<>();

        // create the single server
        server = new Server();

        // set RNGs
        arrivalProcess = new ArrivalProcess(arrival, arrivalRate);
        serviceTimeRNG = service;

        // for collecting Tallies and Accumulate
        listStatsAccumulate = new ListOfStatProbes<>("Stats for Accumulate");
        listStatsTallies = new ListOfStatProbes<>("Stats for Tallies");

        // create Tallies and add Tallies to ListOfStatProbes for later reporting
        waitTimeTally = new TallyStore("Waiting times");
        serviceTimeTally = new Tally("Service times");
        listStatsTallies.add(waitTimeTally);
        listStatsTallies.add(serviceTimeTally);

        // create Accumulates and add them to listStatsAccumulate for later reporting
        utilization = new Accumulate("Server utilization");
        runningCosts = new Accumulate("Running cost");
        listStatsAccumulate.add(utilization);
        listStatsAccumulate.add(runningCosts);
    }

    public void simulateOneRun() {

		Sim.init();

		// reset stats counters
		listStatsTallies.init();
		listStatsAccumulate.init();

		// set first events
		arrivalProcess.init(); // schedules first arrival
		new StopEvent().schedule(stopTime); // schedule stopping time

		// start simulation
		Sim.start();

    }

    public StatProbe getAverageCosts(){
        simulateOneRun();
        return runningCosts;
    }

    void handleArrival() {
        Customer cust = new Customer();
        if (utilization.getLastValue() == 1.0) {
            queue.addLast(cust);
            updateRegime();
        } else {
            server.startService(cust);
        }
    }

    void updateRegime() {
        if (queue.size() + 1 <= k && server.inHighRegime == true) {
            server.setRegime(false);
        }
        if (queue.size() + 1 >= K && server.inHighRegime == false) {
            server.setRegime(true);
        }
    }

    void serviceCompleted(Server server, Customer cust) {
        cust.completed();
        waitTimeTally.add(cust.waitTime);
        serviceTimeTally.add(cust.serviceTime);
        if (!queue.isEmpty()) {
            Customer newCust = queue.removeFirst();
            server.startService(newCust);
        }
    }

    double drawExponentialValue(double x, double mu) {
        return -1/mu*Math.log(x);
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

    class Customer {

        private double arrivalTime;
        private double startTime;
        private double completionTime;
        private double waitTime;
        private double serviceTime;
        private double serviceRand;

        public Customer() {
            // record arrival time when creating a new customer
            arrivalTime = Sim.time();
            startTime = Double.NaN;
            completionTime = Double.NaN;
            waitTime = Double.NaN;
            serviceTime = Double.NaN;
            serviceRand = serviceTimeRNG.nextDouble();
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

    //This Event object represents a server
    class Server extends Event {

        static final double BUSY = 1.0;
        static final double IDLE = 0.0;
        static final double LOWCOST = 5.0;
        static final double HIGHCOST = 10.0;
        boolean inHighRegime = false;
        Customer currentCust;   //Current customer in service

        public Server() {
            currentCust = null;
        }

        // event: service completion
        @Override
        public void actions() {
            utilization.update(IDLE);
            runningCosts.update(IDLE);
            serviceCompleted(this, currentCust);
        }

        public void startService(Customer cust) {

            utilization.update(BUSY);
            currentCust = cust;
            cust.serviceStarted();

            // based on regime: generate service time & update costs
            double serviceTime;
            if (inHighRegime) {
                serviceTime = drawExponentialValue(cust.serviceRand, highServiceRate);
                runningCosts.update(HIGHCOST);
            } else {
                serviceTime = drawExponentialValue(cust.serviceRand, lowServiceRate);
                runningCosts.update(LOWCOST);
            }
            
            schedule(serviceTime); // schedule completion time
        }
        
        public void setRegime(boolean toHigh) {
            if (toHigh) {
                inHighRegime = true;
                runningCosts.update(HIGHCOST);
            } else {
                inHighRegime = false;
                runningCosts.update(LOWCOST);
            }
        }
    }

    // stop simulation by scheduling this event
    class StopEvent extends Event {
        @Override
        public void actions() {
            Sim.stop();
        }
    }

    /**
     * Main method of the class to run some tests
     *
     * @param args Unused.
     */
    public static void main(String[] args) {

        // threshold queue variables
        int k = 10; // k-threshold for queue
        int K = 20; // K-threshold for queue
        double lambda = 1.5; // arrival rate
        double mu = 2.0; // service rate
        double muHigh = 4.0; // high service rate
        double simTime = 10000; // simulation endtime (seconds)

        // test 1 (balanced system)
        ThresholdQueue thresholdQueue = new ThresholdQueue(lambda, mu, muHigh, simTime, k, K, new MRG32k3a(), new MRG32k3a());
        thresholdQueue.simulateOneRun();
        System.out.println(thresholdQueue.listStatsAccumulate.report());
        System.out.println(thresholdQueue.listStatsTallies.report());

        // test 2 (overloaded system)
        thresholdQueue = new ThresholdQueue(10, mu, muHigh, simTime, k, K, new MRG32k3a(), new MRG32k3a());
        thresholdQueue.simulateOneRun();
        System.out.println(thresholdQueue.listStatsAccumulate.report());
        System.out.println(thresholdQueue.listStatsTallies.report());

        // test 3 (k = K)
        thresholdQueue = new ThresholdQueue(lambda, mu, muHigh, simTime, k, k, new MRG32k3a(), new MRG32k3a());
        thresholdQueue.simulateOneRun();
        System.out.println(thresholdQueue.listStatsAccumulate.report());
        System.out.println(thresholdQueue.listStatsTallies.report());

        // test 4 (quiet system)
        thresholdQueue = new ThresholdQueue(0.01, mu, muHigh, simTime, k, k, new MRG32k3a(), new MRG32k3a());
        thresholdQueue.simulateOneRun();
        System.out.println(thresholdQueue.listStatsAccumulate.report());
        System.out.println(thresholdQueue.listStatsTallies.report());
    }
}