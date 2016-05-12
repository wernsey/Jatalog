package za.co.wstoop.jdatalog;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class TestUtils {
	public static boolean mapContains(Map<String, String> map, String key, String value) {
		if(map.containsKey(key)) {
			return map.get(key).equals(value);
		}
		return false;
	}
	
	public static boolean mapContains(Map<String, String> haystack, Map<String, String> needle) {
		for(String key : needle.keySet()) {
			if(!haystack.containsKey(key))
				return false;
			if(!haystack.get(key).equals(needle.get(key)))
				return false;
		}
		return true;
	}
	
	public static boolean answerContains(Collection<Map<String, String>> answers, String... kvPairs) throws Exception {
		Map<String, String> needle = new HashMap<String,String>();
		if(kvPairs.length % 2 != 0) 
			throw new Exception("kvPairs must be even");
		for(int i = 0; i < kvPairs.length/2; i++) {
			String k = kvPairs[i*2];
			String v = kvPairs[i*2 + 1];
			needle.put(k, v);
		}
		for(Map<String, String> answer : answers) {
			if(mapContains(answer, needle))
				return true;
		}
		return false;
	}
}
