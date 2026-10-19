/* Source port of Mystcraft-Legacy, LGPL-3.0, commit 9bc8ddc061845df0cd5ea47f4fdef773cff786ae.
 * Original author: xcompwiz. Modern adaptation uses String grammar tokens and per-instance registries.
 */
package net.mystcraft.minecraft.age.grammar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;


import net.mystcraft.minecraft.age.grammar.WeightedItemSelector.IWeightedItem;




public class GrammarGenerator {
	public static final class RankData {
		public ArrayList<Integer> ranksizes = new ArrayList<Integer>();
		public HashMap<Integer, Integer> rankweights = null;
	}

	public final Map<String, RankData> ranks = new HashMap<>();

	public static final class Rule implements IWeightedItem {
		private final String parent;
		private final List<String> values;
		private final Integer rank;
        private float weight;

		//todo RL
		public Rule(String parent, List<String> values, Integer rank) {
			this.parent = parent;
			this.values = Collections.unmodifiableList(values);
			this.rank = rank;
		}

		@Override
		public float getWeight() {
			if (this.rank == null)
				return 0;
			return weight;
		}

		public String getParent() {
			return parent;
		}

		public List<String> getValues() {
			if (values == null)
				return null;
			return values;
		}

		public int size() {
			if (values == null)
				return 0;
			return values.size();
		}
	}

	// Rules the string expands to
	private HashMap<String, List<Rule>> mappings = new HashMap<>();
	// Rules that expand to include the string
	private HashMap<String, List<Rule>> reverseLookup = new HashMap<>();
	private HashMap<String, HashMap<String, List<List<Rule>>>> shortestpaths;

	private boolean profile_pathbuilder = false;

	public void registerRule(Rule rule) {
		if (rule.parent == null) {
			throw new RuntimeException("Invalid CFG Rule.  Requires parent to expand.");
		}
		if (shortestpaths != null) {
			throw new RuntimeException("You must register your rules before the grammar is finalized! (before Mystcraft's post-init)");
		}
		CollectionUtils.getOrCreateElement(rule.parent, mappings).add(rule);
		for (String value : rule.values) {
			CollectionUtils.getOrCreateElement(value, reverseLookup).add(rule);
		}
		if (rule.rank != null) {
			RankData rankdata = ranks.get(rule.getParent());
			if (rankdata == null) {
				rankdata = new RankData();
				ranks.put(rule.getParent(), rankdata);
			}
			while (rankdata.ranksizes.size() <= rule.rank) {
				rankdata.ranksizes.add(0);
			}
			rankdata.ranksizes.set(rule.rank, rankdata.ranksizes.get(rule.rank) + 1);
		}
	}

	public List<Rule> getParentRules(String str) {
		List<Rule> rules = CollectionUtils.getOrCreateElement(str, reverseLookup);
		return Collections.unmodifiableList(rules);
	}

	public List<Rule> getAllRules(String str) {
		List<Rule> mapping = mappings.get(str);
		if (mapping == null)
			return null;
		return Collections.unmodifiableList(mapping);
	}

	public Rule getRandomRule(String str, Random rand) {
		List<Rule> rules = mappings.get(str);
		if (rules == null || rules.size() == 0)
			return null;
		return WeightedItemSelector.getRandomItem(rand, rules);
	}

	public List<String> explore(String str, Random rand) {
        return explore(str, rand, 0, new int[]{0});
    }
    private List<String> explore(String str, Random rand, int depth, int[] work) {
        if (depth > 128 || ++work[0] > 8192) throw new IllegalArgumentException("Grammar expansion budget exceeded");
        List<String> list = new ArrayList<>();
        Rule rule = getRandomRule(str, rand);
		if (rule == null) {
			list.add(str);
			return list;
		}
		if (rule.size() == 0) {
			return list;
		}
		List<String> result = rule.getValues();
		for (String t : result) {
			list.addAll(explore(t, rand, depth + 1, work));
		}
		return list;
	}

	/**
	 * Returns the shortest connecting paths of rules from the subroot to the given node. If there are no paths then this returns null or an empty list. If
	 * multiple paths have the same length, it returns all of them.
	 * @param subtree_token The subtree to connect to node
	 * @param node_token The node to connect to via rules
	 * @return The list of rules that make up the path in order of subroot to node
	 */
	public List<List<Rule>> getShortestPaths(String subtree_token, String node_token) {
		if (shortestpaths == null)
			throw new RuntimeException("Somebody's trying to use the grammar before we're done building it!");
		List<List<Rule>> paths = null;
		HashMap<String, List<List<Rule>>> allpaths = shortestpaths.get(subtree_token);
		if (allpaths != null) {
			paths = allpaths.get(node_token);
		}
		if (paths == null)
			return null;
		return Collections.unmodifiableList(paths);
	}

	public static class VisitPair {
		public String target;
		public List<Rule> path;

		public VisitPair(String target, List<Rule> path) {
			this.target = target;
			this.path = path;
		}

	}

	private HashMap<String, List<List<Rule>>> getOrCalculatePaths(HashMap<String, HashMap<String, List<List<Rule>>>> shortestpaths, String token) {
		HashMap<String, List<List<Rule>>> allpaths = shortestpaths.get(token);
		if (allpaths != null)
			return allpaths;
		allpaths = new HashMap<>();

		List<Rule> producers = reverseLookup.get(token);
		List<VisitPair> tovisit = new LinkedList<>();

		if (producers != null)
			for (Rule rule : producers) {
				tovisit.add(new VisitPair(rule.parent, Collections.unmodifiableList(CollectionUtils.buildList(rule))));
			}
		while (tovisit.size() > 0) {
			VisitPair elem = tovisit.remove(0);
			String target = elem.target;
			if (target.equals(token))
				continue;
			List<Rule> path = elem.path;
			List<List<Rule>> paths_to_target = CollectionUtils.getOrCreateElement(target, allpaths);
			if (paths_to_target.size() > 0) {
				if (paths_to_target.get(0).size() > path.size()) { // All paths stored are the same length
					paths_to_target.clear();
				}
			}
			if (paths_to_target.size() == 0 || paths_to_target.get(0).size() == path.size()) {
				paths_to_target.add(path);
				List<Rule> target_producers = reverseLookup.get(target);
				if (target_producers != null)
					for (Rule producer : target_producers) {
						tovisit.add(new VisitPair(producer.parent, Collections.unmodifiableList(CollectionUtils.buildList(path, null, producer))));
					}
			}
		}
		shortestpaths.put(token, allpaths); // Put the paths in the lookup now that they are fully built

		return allpaths;
	}

	public void buildGrammar() {
		buildShortestPaths();
		buildRankWeights();
        for (List<Rule> rules : mappings.values()) for (Rule rule : rules)
            rule.weight = rule.rank == null ? 0 : ranks.get(rule.parent).rankweights.get(rule.rank);
	}

	private void buildShortestPaths() {
		long timestart = System.currentTimeMillis();
		if (profile_pathbuilder)
			System.out.println("Starting buildShortestPaths");
		shortestpaths = new HashMap<>();

		for (Entry<String, List<Rule>> lookup : reverseLookup.entrySet()) {
			String token = lookup.getKey();
			if (shortestpaths.containsKey(token))
				continue;
			getOrCalculatePaths(shortestpaths, token);
		}
		long timeend = System.currentTimeMillis();
		if (profile_pathbuilder)
			System.out.println("buildShortestPaths Exectution Time: " + (timeend - timestart));
	}

	private void buildRankWeights() {
		final int step = 1; //Increment between ranks
		for (RankData rankdata : ranks.values()) {
			rankdata.rankweights = new HashMap<>();
			int weight = 1;
			int lasttotal = 0;
			for (int i = rankdata.ranksizes.size() - 1; i >= 0; --i) {
				int count = rankdata.ranksizes.get(i);
				if (weight != 1 && count > 0) {
					weight = Math.max(weight, lasttotal / count + step);
				}
				rankdata.rankweights.put(i, weight);
				lasttotal = count * weight;
				weight += step;
			}
		}
	}

	public String pathToString(List<Rule> path) {
		StringBuilder out = new StringBuilder();
		for (Rule rule : path) {
			if (!out.toString().equals(""))
				out.append(" - ");
			out.append(rule.parent);
		}
		return out.toString();
	}

	public void testShortestPaths(String token1, String token2) {
		System.out.println(token1 + " - " + token2);
		List<List<Rule>> paths = getShortestPaths(token1, token2);
		if (paths == null) {
			System.out.println("no path");
			return;
		}
		for (List<Rule> path : paths) {
			System.out.println("path: " + pathToString(path));
		}
	}
}
