package za.co.wstoop.jatalog;

import org.junit.Test;

import za.co.wstoop.jatalog.Expr;
import za.co.wstoop.jatalog.engine.IndexedSet;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static za.co.wstoop.jatalog.Expr.expr;

public class IndexedSetTest {
	
	@Test
	public void testBase() {
		IndexedSet<Expr, String> indexedSet = new IndexedSet<>();

		assertTrue(indexedSet.isEmpty());
		indexedSet.add(expr("foo", "a"));
		indexedSet.add(expr("foo", "b"));
		indexedSet.add(expr("foo", "c"));
		indexedSet.add(expr("bar", "a"));
		indexedSet.add(expr("bar", "b"));

		assertFalse(indexedSet.isEmpty());
		
		assertTrue(indexedSet.getIndexes().size() == 2);
		assertTrue(indexedSet.getIndexes().contains("foo"));
		assertTrue(indexedSet.getIndexes().contains("bar"));
		assertFalse(indexedSet.getIndexes().contains("baz"));
		
		Set<Expr> set = indexedSet.getIndexed("foo");
		assertTrue(set.size() == 3);
		assertTrue(set.contains(expr("foo", "a")));
		assertTrue(set.contains(expr("foo", "b")));
		assertTrue(set.contains(expr("foo", "c")));
		assertFalse(set.contains(expr("foo", "d")));
		
		assertTrue(indexedSet.contains(expr("bar", "a")));
		indexedSet.remove(expr("bar", "a"));
		assertFalse(indexedSet.contains(expr("bar", "a")));
		
		Set<Expr> toRemove = new HashSet<>();
		toRemove.add(expr("foo", "a"));
		toRemove.add(expr("bar", "b"));
		
		assertTrue(indexedSet.containsAll(toRemove));
		toRemove.add(expr("bar", "c"));
		assertFalse(indexedSet.containsAll(toRemove));
		
		indexedSet.removeAll(toRemove);		
		
		assertFalse(indexedSet.getIndexes().contains("bar"));
		assertFalse(indexedSet.contains(expr("foo", "a")));
		assertFalse(indexedSet.contains(expr("bar", "b")));

		assertFalse(indexedSet.removeAll(toRemove));
				
		indexedSet.clear();
		assertTrue(indexedSet.size() == 0);
		assertTrue(indexedSet.isEmpty());
		assertTrue(indexedSet.getIndexes().size() == 0);
	}
	
}
