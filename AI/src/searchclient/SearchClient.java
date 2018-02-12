package searchclient;

import java.awt.Point;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import searchclient.Heuristic.AStar;
import searchclient.Heuristic.Greedy;
import searchclient.Heuristic.WeightedAStar;
import searchclient.Strategy.StrategyBFS;
import searchclient.Strategy.StrategyBestFirst;
import searchclient.Strategy.StrategyDFS;

public class SearchClient {
	public Node initialState;
	private int MAX_ROW;
	private int MAX_COL;
	private boolean[][] walls;
	private Goal[][] goals;
	private List<Goal> goalList;
	private Map<Character, ArrayList<Goal>> goalMap;
	
	public SearchClient(BufferedReader serverMessages) throws Exception {
		// Read lines specifying colors
		String line = serverMessages.readLine();
		if (line.matches("^[a-z]+:\\s*[0-9A-Z](\\s*,\\s*[0-9A-Z])*\\s*$")) {
			System.err.println("Error, client does not support colors.");
			System.exit(1);
		}
		
		//Max columns and rows
		MAX_COL = line.length();	
		LinkedList<String> lines = new LinkedList<>();
		while(!line.equals("")){
			lines.add(line);
			line = serverMessages.readLine();
		}	
		MAX_ROW = lines.size();
		
		//Initialize arrays
		walls = new boolean[MAX_ROW][MAX_COL];
		goals = new Goal[MAX_ROW][MAX_COL];
		goalList = new ArrayList<Goal>();		
		goalMap = new HashMap<Character, ArrayList<Goal>>();
		boolean agentFound = false;

		this.initialState = new Node(null, this);
		
		for (int row = 0; row < lines.size(); row++) {
			line = lines.get(row);
			
			for (int col = 0; col < line.length(); col++) {
				char chr = line.charAt(col);

				if (chr == '+') { // Wall.
					walls[row][col] = true;
				} else if ('0' <= chr && chr <= '9') { // Agent.
					if (agentFound) {
						System.err.println("Error, not a single agent level");
						System.exit(1);
					}
					agentFound = true;
					this.initialState.agentRow = row;
					this.initialState.agentCol = col;
				} else if ('A' <= chr && chr <= 'Z') { // Box.
					this.initialState.boxes[row][col] = new Box(chr);
				} else if ('a' <= chr && chr <= 'z') { // Goal.
					Goal goal = new Goal(chr, row, col);
					goalList.add(goal);
					goals[row][col] = goal;
					
					if (!goalMap.containsKey(chr))
						goalMap.put(chr, new ArrayList<Goal>());
					goalMap.get(chr).add(goal);
					
				} else if (chr == ' ') {
					// Free space.
				} else {
					System.err.println("Error, read invalid level character: " + chr);
					System.exit(1);
				}
			}
		}
	}

	public LinkedList<Node> Search(Strategy strategy) throws IOException {
		System.err.format("Search starting with strategy %s.\n", strategy.toString());
		strategy.addToFrontier(this.initialState);

		int iterations = 0;
		while (true) {
            if (iterations == 1000) {
				System.err.println(strategy.searchStatus());
				iterations = 0;
			}

			if (strategy.frontierIsEmpty()) {
				return null;
			}

			Node leafNode = strategy.getAndRemoveLeaf();
			
			if (leafNode.isGoalState()) {
				return leafNode.extractPlan();
			}
			
//			System.err.println("-------------------");
//			System.err.println("Agent: " + leafNode.agentRow + "," + leafNode.agentCol);
//			//System.err.println("NBox: " + leafNode.nearestBoxRow + "," + leafNode.nearestBoxCol);
//			System.err.println("Agent price: f: " + (leafNode.h() + leafNode.g()) + ", g: " + leafNode.g() + ", h: " + leafNode.h());
			
			strategy.addToExplored(leafNode);
//			System.err.println("Frontier: " + strategy.countFrontier());
//			System.err.println("Exploired: " + strategy.countExplored());
			for (Node n : leafNode.getExpandedNodes()) { // The list of expanded nodes is shuffled randomly; see Node.java.
				if (!strategy.isExplored(n) && !strategy.inFrontier(n)) {
					n.calculateDistanceToGoal();
					strategy.addToFrontier(n);
//					System.err.println("Add to frontier: " + n.agentRow + "," + n.agentCol);
//					System.err.println("Price for frontier: " + n.getTotalDistanceToGoals());
				}
			}
			iterations++;
		}
	}

	public static void main(String[] args) throws Exception {
		BufferedReader serverMessages = new BufferedReader(new InputStreamReader(System.in));
		
		// Use stderr to print to console
		System.err.println("SearchClient initializing. I am sending this using the error output stream.");

		// Read level and create the initial state of the problem
		SearchClient client = new SearchClient(serverMessages);

        Strategy strategy;
        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "-bfs":
                    strategy = new StrategyBFS();
                    break;
                case "-dfs":
                    strategy = new StrategyDFS();
                    break;
                case "-astar":
                    strategy = new StrategyBestFirst(new AStar(client.initialState));
                    break;
                case "-wastar":
                    // You're welcome to test WA* out with different values, but for the report you must at least indicate benchmarks for W = 5.
                    strategy = new StrategyBestFirst(new WeightedAStar(client.initialState, 5));
                    break;
                case "-greedy":
                    strategy = new StrategyBestFirst(new Greedy(client.initialState));
                    break;
                default:
                    strategy = new StrategyBFS();
                    System.err.println("Defaulting to BFS search. Use arguments -bfs, -dfs, -astar, -wastar, or -greedy to set the search strategy.");
            }
        } else {
            strategy = new StrategyBFS();
            System.err.println("Defaulting to BFS search. Use arguments -bfs, -dfs, -astar, -wastar, or -greedy to set the search strategy.");
        }

		LinkedList<Node> solution;
		try {
			solution = client.Search(strategy);
		} catch (OutOfMemoryError ex) {
			System.err.println("Maximum memory usage exceeded.");
			solution = null;
		}

		if (solution == null) {
			System.err.println(strategy.searchStatus());
			System.err.println("Unable to solve level.");
			System.exit(0);
		} else {
			System.err.println("\nSummary for " + strategy.toString());
			System.err.println("Found solution of length " + solution.size());
			System.err.println(strategy.searchStatus());

			for (Node n : solution) {
				String act = n.action.toString();
				System.out.println(act);
				String response = serverMessages.readLine();
				if (response.contains("false")) {
					System.err.format("Server responsed with %s to the inapplicable action: %s\n", response, act);
					System.err.format("%s was attempted in \n%s\n", act, n.toString());
					break;
				}
			}
		}
	}

	public int getMaxRow() {
		return MAX_ROW;
	}
	
	public int getMaxCol(){
		return MAX_COL;
	}
	
	public Goal[][] getGoals(){
		return goals;
	}
	
	public List<Goal> getGoalList(){
		return goalList;
	}
	
	public Map<Character, ArrayList<Goal>> getGoalMap(){
		return goalMap;
	}
	
	public boolean[][] getWalls(){
		return walls;
	}
}
