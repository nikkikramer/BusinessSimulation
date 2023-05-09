package assignments;


import java.util.HashSet;
import java.util.Random;

import umontreal.ssj.probdist.NormalDist;
import umontreal.ssj.rng.MRG32k3a;
import umontreal.ssj.simevents.Sim;
import umontreal.ssj.stat.TallyStore;
import java.util.Iterator;

public class Assignment3 {

    // optimization variables
    State[] outputs;
    int numStates;
    int budget;
    int xmin;
    int ymin;
    int xmax;
    int ymax;

    // threshold queue variables
    int k;
    int K;
    double lambda;
    double avgService;
    double avgHighService;
    double stopTime;

    Random rng = new Random();

    static final double BIGM = 9999999999999.99;

    public Assignment3(int xmin, int xmax, int ymin, int ymax, int budget, double lambda, double muLow, double muHigh, double stopTime, int k, int K) {
       
    	// check how many states are possible
    	int xrange = xmax - xmin + 1;
        int yrange = ymax - ymin + 1;
        numStates = xrange*yrange;
        outputs = new State[numStates];

        // create states and store them in outputs[]
        for (int i = 0; i < xrange; i++) {
            for (int j = 0; j < yrange; j++) {
                State state = new State(i+xmin,j+ymin);
                outputs[(yrange)*i+j] = state;
            }
        }

        // set optimization variables
        this.budget = budget;
        this.ymin = ymin;
        this.xmin = xmin;
        this.ymax = ymax;
        this.xmax = xmax;

        // set threshold queue variables
        this.k = k;
        this.K = K;
        this.lambda = lambda;
        this.avgService = muLow;
        this.avgHighService = muHigh;
        this.stopTime = stopTime;
    }

	// A state represents a solution and has an x and y value which correspond
    // with the k and K value, respectively.
    class State {

        int xval;
        int yval;
        TallyStore values;

        public State(int x, int y) {
            this.xval = x;
            this.yval = y;
            this.values = new TallyStore("("+x+","+y+")");
            this.values.init();
        }
    }

    // Calculates the index position of a state in the output array.
    public int calcPos(int x, int y) {
        return (x-xmin)*(ymax-ymin+1)+y-ymin;
    }

    // Returns the optimal state after using a ranking algorithm based on the average costs
    public State selectOptimalState() {
        double minimum = BIGM;
        State min = null;
        for (int i = 0; i < numStates; i++) {
            if (outputs[i].values.numberObs() > 0) {
                if (outputs[i].values.average() < minimum) {
                    minimum = outputs[i].values.average();
                    min = outputs[i];
                }
            }
        }
        return min;
    }

    public State getState(int[] val) {
        int pos = calcPos(val[0],val[1]);
        return outputs[pos];
    }

    // Use this method to create a new, random MRGG32k3a variable.
    public MRG32k3a getStream() {
        long[] seed = new long[6];
        //TO DO: Fill the long[] with random seeds, for example, using rng
        	//first 3 values must be less than 4294967087
        	//last 3 values must be less than 4294944443
        	//first and last 3 values must not be 0
        int i=0;
        long range1 = 4294967087L;
        long range2 = 4294944443L;
        do {
        	long randomLong = (long) (rng.nextDouble()*range1);
        	if(randomLong < range1 && randomLong > 0) {
        		seed[i] = randomLong;
        		i++;
        	}
        } while(i<3);
        
        do {
        	long randomLong2 = (long) (rng.nextDouble()*range2);
        	if(randomLong2 < range2 && randomLong2 > 0) {
        		seed[i] = randomLong2;
        		i++;
        	}
        } while(i<6);
    
        	
        MRG32k3a myrng = new MRG32k3a();
        myrng.setSeed(seed);
        return myrng;
    }

    public void runSingleRun(int k, int K) {

        // init random sources
    	MRG32k3a arrival = getStream();
        MRG32k3a service = getStream();
        
        // init simulation and create threshold queue
        Sim.init();
        ThresholdQueue model = new ThresholdQueue(lambda, avgService, avgHighService, stopTime, k, K, arrival, service);
       
        // get results and add to the right state
        double result = model.getAverageCosts().average();
        int i = calcPos(k, K);
        outputs[i].values.add(result);
    }
    
    public State runRankingSelection(int initialRuns, double alpha) { 
		for (int i = 0; i < initialRuns; i++) {
			for(int j = 0; j<outputs.length; j++) {
				runSingleRun(outputs[j].xval,outputs[j].yval);
			}	
		}
		HashSet<State> I = selectCandidateSolutions(alpha);
		Iterator<State> remainderIterator = I.iterator();
		
		while(remainderIterator.hasNext()) {
			State remainingStates = remainderIterator.next();
			for(int i =0; i<budget-(initialRuns*outputs.length); i++) {
				runSingleRun(remainingStates.xval, remainingStates.yval);
			}	
		}
		State opt = selectOptimalState();
        System.out.println("R&S k=" + opt.xval + "R&S K=" + opt.yval + "R&S average=" + opt.values.average());
        System.out.println(opt.values.report());

		return opt;
	}

	public HashSet<State> selectCandidateSolutions(double alpha) { 
		HashSet<State> I = new HashSet();
	
		NormalDist distribution = new NormalDist();
		for(int i=0; i<outputs.length; i++) {
			int random = rng.nextInt(outputs.length); 
			if(outputs[i].values.average() < (outputs[random].values.average() - distribution.inverseF(1-(1-Math.pow(1-alpha, outputs.length-1)))* Math.sqrt(outputs[i].values.variance() + outputs[random].values.variance())/Math.sqrt(outputs.length))) {
				I.add(outputs[i]);
			}
		}
		// find all candidate solutions for the ranking and selection method
		return I;
	}

    public State runLocalSearch() {

    	State currentState = selectRandomStart();
    	State neighborState;
    	double result1;
    	double result2;
    	int output1;
    	int output2;
    
    	while(budget>0) {

    		neighborState = selectRandomNeighbor(currentState);

    		MRG32k3a arrival = getStream();
            MRG32k3a service = getStream();
            
            ThresholdQueue currentModel = new ThresholdQueue(lambda, avgService, avgHighService, stopTime, currentState.xval, currentState.yval, arrival, service);
            result1 = currentModel.getAverageCosts().average();
            currentState.values.add(result1);
            output1 = calcPos(currentState.xval,currentState.yval);
            outputs[output1].values.add(result1);
            this.budget--;
            
            ThresholdQueue current2Model = new ThresholdQueue(lambda, avgService, avgHighService, stopTime, neighborState.xval, neighborState.yval, arrival, service);
            result2 = current2Model.getAverageCosts().average();
            neighborState.values.add(result2);
            output2 = calcPos(neighborState.xval,neighborState.yval);
            outputs[output2].values.add(result2);
            this.budget--;
            
            currentState = selectBestState(currentState,neighborState);  
    	}
    	
        State opt = selectOptimalState();
        System.out.println("LS k=" + opt.xval + "LS K=" + opt.yval + "LS average=" + opt.values.average());
        System.out.println(opt.values.report());
        
        return opt;
    }
    
    public State selectBestState(State current, State neighbor){
    		// return best state
    		if (current.values.average() > neighbor.values.average()) {
    			current = neighbor;
    		}
    		return current;
    	}
 
    public State selectRandomStart() {
        State state;
        int xValue = rng.ints(1, xmin, xmax).findAny().getAsInt();
		int yValue = rng.ints(1, ymin, ymax).findAny().getAsInt();
		state = new State(xValue, yValue);
        return state;
    }

    public State selectRandomNeighbor(State state) {
        State neighbor = null;
        boolean notWithinLimits = true;
        
     	State neighbor1 = new State(state.xval + 1, state.yval + 1);
     	State neighbor2 = new State(state.xval + 1, state.yval - 1);
     	State neighbor3 = new State(state.xval - 1, state.yval + 1);
     	State neighbor4 = new State(state.xval - 1, state.yval - 1);
     	State neighbor5 = new State(state.xval, state.yval + 1);
     	State neighbor6 = new State(state.xval, state.yval - 1);
     	State neighbor7 = new State(state.xval + 1, state.yval);
     	State neighbor8 = new State(state.xval - 1, state.yval);
     	
		while(notWithinLimits) {
     		int randomNumber = (int) (10.0 * Math.random());

     		if (randomNumber == 1) {
				neighbor = neighbor1;
			}
			if (randomNumber == 2) {
				neighbor = neighbor2;
			}
			if (randomNumber == 3) {
				neighbor = neighbor3;
			}
			if (randomNumber == 4) {
				neighbor = neighbor4;
			}
			if (randomNumber == 5) {
				neighbor = neighbor5;
			}
			if (randomNumber == 6) {
				neighbor = neighbor6;
			}
			if (randomNumber == 7) {
				neighbor = neighbor7;
			} else {
				neighbor = neighbor8;
			}
			if (neighbor.xval >= xmin && neighbor.yval >=ymin && neighbor.xval <= xmax && neighbor.yval<= ymax){
				notWithinLimits = false;
			}
		}
		return neighbor;
	}
	
    public double[] simulateCommonRandomNumbersRun(int k2, int K2){
        double[] results = new double[2];
        
        Sim.init();
        MRG32k3a arrival = getStream();
        Sim.init();
        MRG32k3a service = getStream();
        double firstSum = 0;
        double secondSum = 0;
        
        for(int i=0; i<stopTime; i++) {
        	Sim.init();
            ThresholdQueue firstModel = new ThresholdQueue(lambda, avgService, avgHighService, stopTime, k, K, arrival, service);
            firstSum += firstModel.getAverageCosts().average();
            Sim.init();
            ThresholdQueue secondModel = new ThresholdQueue(lambda, avgService, avgHighService, stopTime, k2, K2, arrival, service);
            secondSum += secondModel.getAverageCosts().average();
         
        }
        results[0] = firstSum/stopTime;
        results[1] = secondSum/stopTime;
        
        System.out.println("CRN k = " +k + ", CRN K = " +K + " and average: " +results[0]);
		System.out.println("CRN k = " +k2 + ", CRN K = " +K2 + " and average: " +results[1]);
       
        // perform CRN on (k,K) and (k2,K2) as parameters, average costs is result per run

        return results;
    }

    public static void main(String[] args) {
        int k = 5;             			 // k-threshold for queue
        int K = 20;             			 // K-threshold for queue
        int k2 = 10;            			 // k-threshold for alternative queue
        int K2 = 20;            			 // K-threshold for alternative queue
        double lambda = 3./2;     			 //service rate	
        double muLow = 2.0;				 // average low service time
        double muHigh = 4.0;    			 // average high service time
        double stopTime = 10000;     	 // Simulation endtime (seconds)

        int xmin = 5;					 // Lowest possible value for k
        int xmax = 10;					 // Highest possible value for k
        int ymin = 10;					 // Lowest possible value for K	
        int ymax = 20;					 // Highest possible value for K
        int budget = 5000;				 // Budget for the initial runs
        
        int initialRuns = 2500;			  // initial runs for the Ranking and selection method
        double alpha = 0.05; 			  // alpha value for the Ranking and selection method

        Assignment3 crn = new Assignment3(xmin, xmax, ymin, ymax, budget, lambda, muLow, muHigh, stopTime, k, K);
        crn.simulateCommonRandomNumbersRun(k2,K2);

        Assignment3 optimization = new Assignment3(xmin, xmax, ymin, ymax, budget, lambda, muLow, muHigh, stopTime, k, K);
        optimization.runLocalSearch();

        Assignment3 optimization2 = new Assignment3(xmin, xmax, ymin, ymax, budget, lambda, muLow, muHigh, stopTime, k, K);
        optimization2.runRankingSelection(initialRuns, alpha);
    }
}