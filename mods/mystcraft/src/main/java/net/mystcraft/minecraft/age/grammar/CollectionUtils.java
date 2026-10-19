/* Source port of Mystcraft-Legacy, LGPL-3.0, commit 9bc8ddc061845df0cd5ea47f4fdef773cff786ae.
 * Original author: xcompwiz. Modern adaptation uses String grammar tokens and per-instance registries.
 */
package net.mystcraft.minecraft.age.grammar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public final class CollectionUtils {

	@SafeVarargs
	public static <T> HashSet<T> buildSet(T... args) {
		HashSet<T> set = new HashSet<T>();
		for (T arg : args) {
			set.add(arg);
		}
		return set;
	}

	@SafeVarargs
	public static <T> ArrayList<T> buildList(T... args) {
		ArrayList<T> list = new ArrayList<T>();
		for (T arg : args) {
			list.add(arg);
		}
		return list;
	}

	@SafeVarargs
	public static <T> ArrayList<T> buildList(List<T> prefix, List<T> suffix, T... args) {
		ArrayList<T> list = new ArrayList<T>();
		if (prefix != null)
			list.addAll(prefix);
		for (T arg : args) {
			list.add(arg);
		}
		if (suffix != null)
			list.addAll(suffix);
		return list;
	}

	public static <R, T> List<T> getOrCreateElement(R key, HashMap<R, List<T>> map) {
		List<T> rules = map.get(key);
		if (rules == null) {
			rules = new ArrayList<T>();
			map.put(key, rules);
		}
		return rules;
	}

}
