import umontreal.iro.lecuyer.simevents.*;
import umontreal.iro.lecuyer.rng.*;
import umontreal.iro.lecuyer.randvar.*;
import umontreal.iro.lecuyer.stat.*;

import java.util.Collections;
import java.util.LinkedList;
import java.util.Random;



public class BicycleSim {
	
	//************************************************DATA STRUCTURE CLASSES************************************************
	
	
	class Bike
	{
		int bikeID; //unique identifier, set to position in bikelist
		boolean needRepair; //true if the bike needs repair
		double totalDist; //total distance the bicycle has traveled in miles
	}
	
	class Station
	{
		int coord[]; //2 item array that stores the x,y coordinates of the station (intersection)
		Post[] postlist; //array of the posts at a station
		int bikesAvailable;
		int postsAvailable;
		
	}
	
	class Post
	{
		boolean inUse; //true if a bike is parked at the post
		int bike; //the ID of the bicycle parked there, -1 if no bike
	}
	
	class Customer implements Comparable<Customer>
	{
		double arriveTime; 
		double travelTime; //travel time (founds by calculating how much time to travel distance)
		double servTime; //time the departure event should happen **THIS IS HOW CUSTOMER IS SORTED IN LIST**
		double travelDist; //travel distance in BLOCKS
		double travelDistMiles; //travel distance in MILES
		int bike; //the bicycle the customer takes
		
		int stationStart; //the originating location for the customer, refers to station
		int destStation; //destination station
		
		@Override
		public int compareTo(Customer cust)
		{
			//sort Customer based on servTime in ascending order
			return (int) (this.servTime - cust.servTime);
		}
	}
	
	//************************************************DATA MEMBERS************************************************
	
	//simulating in a 100 street, "40" avenue setting
	//only avenues that are multiples of 3 are used as "true" avenues (1 ave = 3 city blocks in Manhattan)
	int maxX = 40;
	int maxY = 100;
	int numStations;
	int postsPerStation;
	int bikesPerStation;
	double totalCustomersGen = 0;
	
	double repairThreshold = 200.0; //number of miles a bike can travel before needing repair
	double timePerBlock = 0.5; //one minute per block
	double blockLength = .05; //a block is 1/20 of a mile
	double rideCharge = 9.95; //cost of 24hr pass
	double timeLimit = 30; //30 minutes before extra fees apply
	double overageCharge[] = {4.0,9.0,12.0}; //overage charges for going over thirty minutes 
	                                   //$4 first half hour over, $9 for third, $12 for each after
	
	double grossProfit = 0.0; //total accumulated profit
	double repairCosts = 0.0; //total costs associated with repair
	double netProfit = 0.0; //grossProfit - repairCosts
	double avgRepairCost = 50.0;
	int numBikesNeedRepair = 0; //the number of bikes that need repair
	int numRedirects = 0; //number of times a customer's destination station must be changed
	int lostCustomers = 0; //number of customer's lost due to lack of bikes
	int overageCounter = 0; //number of customers charged overages
	
	RandomVariateGen genArr;
	Random rng = new Random();
	
	Tally custDistTraveled = new Tally("Distances traveled");
	Tally custTime = new Tally("Service times");
	
	LinkedList<Customer> customerList = new LinkedList<Customer>(); //list of active customers
	Station stationlist[]; //array of all stations
	Bike bikelist[]; //array of all bikes
	

	
	//********************************************SIM FUNCTIONS and CONSTRUCTOR*********************************************
	
	//constructor
	public BicycleSim(double first, int posts, int bikes)
	{
		this.numStations = 10;
		this.postsPerStation = posts;
		this.bikesPerStation = bikes;
		genArr = new ExponentialGen(new MRG32k3a(), first);
		this.stationlist = new Station[10];
		for(int i=0; i<10; i++)
		{
			this.stationlist[i] = new Station();
			this.stationlist[i].postlist = new Post[posts];
		}
		
		int totalBikes = bikes*numStations; //total bikes is bikes per station multiplied by number of stations
		this.bikelist = new Bike[totalBikes];
		//populate bikelist
		for(int i=0; i< totalBikes; i++)
		{
			Bike b = new Bike();
			b.bikeID = i;
			b.needRepair = false;
			b.totalDist = 0.0;
			this.bikelist[i] = b;
		}
		
		int counter = 0;
		
		//populate station's postlist w/ correct number of posts and bikes
		for(int i=0; i< 10; i++)
		{
			for(int j=0; j<posts; j++)
			{
				this.stationlist[i].postlist[j] = new Post();
				if(j<bikes) 
				{	
					this.stationlist[i].postlist[j].inUse = true;
					this.stationlist[i].postlist[j].bike = counter;
					counter++;
				}
				else
				{
					this.stationlist[i].postlist[j].inUse = false;
					this.stationlist[i].postlist[j].bike = -1;
				}
			}
			this.stationlist[i].bikesAvailable = bikes; //set number of available bikes
			this.stationlist[i].postsAvailable = posts-bikes; //set number of available posts
		}
		
		//SET STATION COORDINATES
		this.stationlist[0].coord = new int[2]; //station 0 at [9,0]
		this.stationlist[0].coord[0] = 9;
		this.stationlist[0].coord[1] = 0;
		this.stationlist[1].coord = new int[2]; //station 1 at [39,0]
		this.stationlist[1].coord[0] = 39;
		this.stationlist[1].coord[1] = 0;
		this.stationlist[2].coord = new int[2]; //station 2 at [0, 30]
		this.stationlist[2].coord[0] = 0;
		this.stationlist[2].coord[1] = 30;
		this.stationlist[3].coord = new int[2]; //station 3 at [21,30]
		this.stationlist[3].coord[0] = 21;
		this.stationlist[3].coord[1] = 30;
		this.stationlist[4].coord = new int[2]; //station 4 at [9,50]
		this.stationlist[4].coord[0] = 9;
		this.stationlist[4].coord[1] = 50;
		this.stationlist[5].coord = new int[2]; //station 5 at [39,50]
		this.stationlist[5].coord[0] = 39;
		this.stationlist[5].coord[1] = 50;
		this.stationlist[6].coord = new int[2]; //station 6 at [0,70]
		this.stationlist[6].coord[0] = 0;
		this.stationlist[6].coord[1] = 70;
		this.stationlist[7].coord = new int[2]; //station 7 at [30,70]
		this.stationlist[7].coord[0] = 30;
		this.stationlist[7].coord[1] = 70;
		this.stationlist[8].coord = new int[2]; //station 8 at [9,90]
		this.stationlist[8].coord[0] = 9;
		this.stationlist[8].coord[1] = 90;
		this.stationlist[9].coord = new int[2]; //station 9 at [39,90]
		this.stationlist[9].coord[0] = 39;
		this.stationlist[9].coord[1] = 90;
		
	}
	
	//simulates a run that ends at a set time
	public void simulateOneRun (double timeHorizon) 
	{
		Sim.init();
		new EndOfSim().schedule (timeHorizon);
		new Arrival().schedule (genArr.nextDouble());
		Sim.start();
	}
	
	class EndOfSim extends Event 
	{
	      public void actions() 
	      {
	         Sim.stop();
	      }
	}


	
	
	
	
	//****************************************************EVENT DEFINITIONS****************************************************
	
	class Arrival extends Event
	{
		public void actions()
		{
			new Arrival().schedule(genArr.nextDouble()); //schedules next arrival in dominoe effect
			
			//create new customer
			Customer cust = new Customer();
			totalCustomersGen++;
			cust.arriveTime = Sim.time();
			cust.stationStart = rng.nextInt(numStations);
			cust.destStation = rng.nextInt(numStations);
			//check & fix if start and endpoint are the same
			if(cust.destStation == cust.stationStart)
			{
				while(cust.destStation == cust.stationStart)
				{ cust.destStation = rng.nextInt(numStations); }
			}
			
			//check bike availability before continuing
			if(stationlist[cust.stationStart].bikesAvailable > 0)
			{
				//assign a bike
				boolean flag = false;
				for(int i=0; (i<postsPerStation)&&(flag==false); i++)
				{
					//find post w/ bike that does not need repair
					if((stationlist[cust.stationStart].postlist[i].inUse==true)&&(bikelist[stationlist[cust.stationStart].postlist[i].bike].needRepair==false))
					{ 
						cust.bike = stationlist[cust.stationStart].postlist[i].bike; 
						flag = true;
						stationlist[cust.stationStart].postlist[i].inUse = false; //FREE POST
						stationlist[cust.stationStart].postsAvailable++; //increment posts available for parking
						stationlist[cust.stationStart].bikesAvailable = stationlist[cust.stationStart].bikesAvailable-1; //decrement bikes available for that station
					}
				}
				
				//calculate travel time and distance
				int x = stationlist[cust.stationStart].coord[0]; 
				int y = stationlist[cust.stationStart].coord[1];
				int p = stationlist[cust.destStation].coord[0];
				int q = stationlist[cust.destStation].coord[1];
				int xtravel, ytravel;
				if(x>p)
				{ xtravel = x-p; }
				else
				{ xtravel = p-x; }
				if(y>q)
				{ ytravel = y-q; }
				else
				{ ytravel = q-y; }
				cust.travelDist = xtravel+ytravel; 
				cust.travelDistMiles = cust.travelDist*blockLength;
				cust.travelTime = cust.travelDist*timePerBlock;
				cust.servTime = cust.arriveTime + cust.travelTime;
				
				//schedule the expected departure event
				new Departure().schedule (cust.servTime);
				
				//add the customer to the customerList and resort it
				customerList.addLast(cust);
				Collections.sort(customerList); //sorted in ascending order of servTime
				
			}
			else
			{ lostCustomers++; } //if no bikes, lose customer
			
		}
	}
	
	class Departure extends Event
	{
		public void actions()
		{
			Customer cust = customerList.removeFirst();
			//check for available post to park bike
			if(stationlist[cust.destStation].postsAvailable > 0)
			{
				//park the bike
				boolean parked = false;
				for(int i=0; (i<postsPerStation)&&(parked==false); i++)
				{
					if(stationlist[cust.destStation].postlist[i].inUse == false)
					{
						stationlist[cust.destStation].postlist[i].inUse = true;
						stationlist[cust.destStation].postlist[i].bike = cust.bike;
						bikelist[cust.bike].totalDist += cust.travelDistMiles; //update the bike's distance traveled
						if(bikelist[cust.bike].totalDist > repairThreshold) 
						{
							bikelist[cust.bike].needRepair = true; //if bike has reached repair threshold, flag it
							numBikesNeedRepair++;
							repairCosts += avgRepairCost;
						}
						else
						{
							stationlist[cust.destStation].bikesAvailable++;
						}
					}
				}
				
				//update the tallies
				custTime.add (cust.travelTime);
				custDistTraveled.add(cust.travelDistMiles);
				
				//update profits
				grossProfit+=rideCharge;
				//if gone over the 30 minute mark, apply overage charges
				double halfHours = (cust.travelTime - cust.arriveTime)/30;
				if(halfHours > 1.0)
				{	halfHours = halfHours - 1.0;
					if(halfHours <= 1.0)
					{
						grossProfit+=overageCharge[0];
						overageCounter++;
					}
					else if((halfHours > 1.0)&&(halfHours <= 2.0))
					{
						grossProfit = grossProfit+overageCharge[0]+overageCharge[1];
						overageCounter++;
					}
					else
					{
						double x = Math.ceil(halfHours);
						grossProfit = grossProfit+overageCharge[0]+overageCharge[1]+(overageCharge[2]*x);
						overageCounter++;
					}
				}
				netProfit = grossProfit - repairCosts;
				
			}
			else
			{
				//redirect and reschedule
				//find nearest station and reset destination
				numRedirects++;
				cust.stationStart = cust.destStation;
				cust.destStation = findNearestStation(cust.destStation);
				
				//calculate travel time and distance and add to existing
				int x = stationlist[cust.stationStart].coord[0]; 
				int y = stationlist[cust.stationStart].coord[1];
				int p = stationlist[cust.destStation].coord[0];
				int q = stationlist[cust.destStation].coord[1];
				int xtravel, ytravel;
				if(x>p)
				{ xtravel = x-p; }
				else
				{ xtravel = p-x; }
				if(y>q)
				{ ytravel = y-q; }
				else
				{ ytravel = q-y; }
				cust.travelDist = cust.travelDist+xtravel+ytravel; 
				cust.travelDistMiles = cust.travelDist*blockLength;
				cust.travelTime = cust.travelDist*timePerBlock;
				cust.servTime = cust.arriveTime + cust.travelTime; //update servTime
				
				//schedule the expected new departure event
				new Departure().schedule (cust.servTime);
				
				//add the customer back to the customerList and resort it
				customerList.addLast(cust);
				Collections.sort(customerList); //sorted in ascending order of servTime
				
				
			}
		}
	}
	
	//*****************************************************MISC FUNCTIONS******************************************************
	
	//function for Euclidean distance, returns distance from [x1,y1] to [x2,y2]
	public double getDistance(int x1, int y1, int x2, int y2)
	{
		return Math.sqrt((Math.pow(x2-x1,2)+Math.pow(y2-y1, 2)));
	}
	
	//function that finds the nearest station to a station
	public int findNearestStation(int station)
	{
		int stationID = -1;
		double bestDistance = 9999;
		for(int i=0; i<numStations; i++)
		{
			if(i!=station)
			{
				double temp = getDistance(stationlist[station].coord[0], stationlist[station].coord[1], stationlist[i].coord[0], stationlist[i].coord[1]);
				if(temp < bestDistance)
				{
					bestDistance = temp;
					stationID = i;
				}
			}
		}
		
		return stationID;
	}
	
	//*****************************************************MAIN FUNCTION*******************************************************
	
	public static void main(String [] args)
	{
		BicycleSim bikesim = new BicycleSim(1.0, 50, 40); //generates with 50 posts and 40 bikes per station
		bikesim.simulateOneRun(1000);
		System.out.println("SIMULATION RESULTS");
		System.out.println(bikesim.custDistTraveled.report());
		System.out.println(bikesim.custTime.report());
		System.out.println("Gross Profit: " + bikesim.grossProfit);
		System.out.println("Repair Costs: " + bikesim.repairCosts);
		System.out.println("Net Profit: " + bikesim.netProfit);
		System.out.println("Number of bikes that need maintenance: " + bikesim.numBikesNeedRepair);
		System.out.println("Number of customers redirected: " + bikesim.numRedirects);
		System.out.println("Number of customers lost: " + bikesim.lostCustomers);
		System.out.println("Number of time overages: " + bikesim.overageCounter);
	//	System.out.println("Number of customers generated: " + bikesim.totalCustomersGen);
	}
}
