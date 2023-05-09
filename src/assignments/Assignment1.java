package assignments;

import java.awt.Color;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import umontreal.ssj.charts.EmpiricalChart;
import umontreal.ssj.charts.EmpiricalSeriesCollection;
import umontreal.ssj.probdist.EmpiricalDist;
import umontreal.ssj.stat.Tally;
import java.io.PrintStream;
import java.io.BufferedReader;
import java.io.File;


public class Assignment1 {
	
	// LCG parameters (notation from slides used)
	//The parameters were retrieved from Wikipedia (source can be found in the report).
	double seed = 0.0;
	double m = Math.pow(2, 32);
	double a = 1664525;
	double c = 1013904223;

	int raceTo = 5; // number of games to win the game
	double winThreshold = 0.5; // winning probability of a player

	LCG prng;
	EmpiricalDist durationDist;

	PrintStream out;

	Assignment1() {
		out = new PrintStream(System.out);
	}

	/* DO NOT CHANGE THE CODE IN QUESTION1 AND QUESTION2 BELOW */
	
	public double[] Question1(double givenSeed, int numOutputs, boolean normalize) {
		prng = new LCG(givenSeed,a,c,m);
		double[] result = new double[numOutputs];
		for (int i = 0; i < numOutputs; i++) {
			result[i] = prng.generateNext(normalize);
		}
		return result;
	} //DONT CHANGE

	public EmpiricalDist Question2(String csvFile) {
		EmpiricalDist myDist = getDurationDist(csvFile);
		return myDist;
	} //DONT CHANGE

	public void plotEmpiricalCDF() { 
		// Use EmpiricalChart to plot the CDF
		double[] data = new double[durationDist.getN()];
		for (int i = 0; i< durationDist.getN(); i++){
			data[i] = durationDist.getObs(i);
		}
		String title = "Empirical CDF of the game length";
		String XLabel = "match length (seconds)";
		String YLabel = "Empirical cumulative distribtion function (ECDF)";
		EmpiricalChart empiricalPlot = new EmpiricalChart(title, XLabel , YLabel, data);
		empiricalPlot.view(900, 500);

	}

	public Tally Question3() {
		Tally durations = new Tally();
		int nrOfSimulations = 5000;
		double oneDuration;
		for(int i=0; i< nrOfSimulations; i++) {
			oneDuration = simulateMatch(raceTo);
			durations.add(oneDuration);	
		}
		return durations;
		
		// Simulate the matches and add the duration to the Tally.
	}

	public double simulateMatch(int raceTo) {
		//simulate match and duration of match
		double matchDuration = 0;
		int p1Wins = 0;
		int p2Wins = 0;
		while(p1Wins < raceTo && p2Wins < raceTo) {
			double winningProbability = prng.generateNext(true);
			if(winningProbability <= winThreshold) {
				p1Wins +=1;
			} else {
				p2Wins +=1;
			}
			double currentGameLength = durationDist.inverseF(prng.generateNext(true));
			matchDuration += currentGameLength;
			
		}
		return matchDuration;
	}

	public EmpiricalDist getDurationDist(String csvFile){
		//import csv file, sort the values and compute empirical distribution
		//csvFile is already sorted using Excel
		String lineReader = null;
		File file = new File(csvFile);
		String fileReader = "";
		try {
			BufferedReader matchReader = new BufferedReader(new FileReader(file));
			while((lineReader = matchReader.readLine()) != null) {
				fileReader += lineReader + "\n";
				}
		} catch (FileNotFoundException e) {
			System.out.println("File not found");
		} catch (IOException e) {
			System.out.println("Could not read file");
		}
		String [] matchInSeconds = fileReader.split("\n");
		double [] matchesArray = new double[matchInSeconds.length];
		for(int i=0; i<matchInSeconds.length; i++) {
			matchesArray[i] = Double.parseDouble(matchInSeconds[i]);
		}
		durationDist = new EmpiricalDist(matchesArray);
		return durationDist;
		
	}

	/*  ONLY CHANGE generateNext in the LCG class */
	public class LCG {
		public double seed;
		public final double m;
		public final double a;
		public final double c;

		public double lastOutput;

		public LCG(double seed, double a,double c,double m){
			this.seed = seed;
			this.m = m;
			this.a = a;
			this.c = c;

			this.lastOutput = seed;
		}

		public double generateNext(boolean normalize){
			// implement the pseudo-code algorithm here. Your code should be able to return both normalized and regular numbers based on the value of normalize.
			double resultNextSeed = (a*lastOutput + c) % m;
			lastOutput = resultNextSeed; 
			if(normalize) {
				resultNextSeed = (lastOutput + 1)/(m+1);
				}
			return resultNextSeed;
		}

		public void setSeed(double newSeed) {
			this.seed = newSeed;
		}
	}

	public void start() {
		// This is your test function. During grading we will execute the function that are called here directly.
		double givenSeed = seed;
		int numOutputs = 3;
		
		// Run Question 1: once regularly and once normalized 
		double[] outputRegRNG = Question1(givenSeed, numOutputs, false);
		double[] outputNormRNG = Question1(givenSeed, numOutputs, true);
		for (int i = 0; i < numOutputs; i++) {
			out.println("Regular:" + outputRegRNG[i]);
			out.println("Normalized:" + outputNormRNG[i]);
		}
		
		// Name of CSV file is read and passed on to Question2 for loading 
		String csvFile = "game_lengths.csv";
		EmpiricalDist myDist = Question2(csvFile);
		
		// Quantiles are printed and the ECDF plotted
		out.println(myDist.inverseF(0.0));
		out.println(myDist.inverseF(0.25));
		out.println(myDist.inverseF(0.5));
		out.println(myDist.inverseF(0.75));
		out.println(myDist.inverseF(1.0));
		plotEmpiricalCDF();
		
		// Run Question 3
		Tally durations = Question3();
		out.println(durations.report());
	}

	public static void main(String[] args){
		new Assignment1().start();
	}
}