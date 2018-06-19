package za.co.wstoop.jatalog.engine;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import za.co.wstoop.jatalog.Expr;
import za.co.wstoop.jatalog.Term;

/**
 * Subclass of {@link Set} that can quickly access a subset of its elements through an index.
 * Jatalog uses it to quickly retrieve the facts with a specific predicate.   
 * @param <E> Type of elements that will be stored in the set; must implement {@link Indexable}
 */
public class IndexedSet<E extends Expr, I extends Object> implements Set<E> {

  private final Set<E> contents;

	private Map<String, Set<E>> index;
	private Map<String, Set<E>> index2;

	/**
	 * Default constructor.
	 */
	public IndexedSet() {
		index = new HashMap<>();
		index2 = new HashMap<>();
		contents = new HashSet<>();
	}
	
	/**
	 * Creates the set from a different collection.
	 * @param elements The collection from which to construct
	 */
	public IndexedSet(Collection<E> elements) {		
		contents = new HashSet<>(elements);
		reindex();
	}
	
	/**
	 * Retrieves the subset of the elements in the set with the 
	 * specified index.
	 * @param key The indexed element
	 * @return The specified subset
	 */
	public Set<E> getIndexed(String key) {
		Set<E> elements = index.get(key);
		if(elements == null) {
			return Collections.emptySet();
		}
		return elements;
	}

	public Set<E> getIndexed(String key, List<Term> terms) {
		Set<E> set = new HashSet<>();
		boolean hasFixedPoint = false;
		for (int i = 0; i < terms.size(); i++) {
			Term term = terms.get(i);
			if (!term.isVariable()) {
				String k = key + "_" + Integer.toString(i, 10) + "_" + term.value();
				if (index2.containsKey(k)) {
					set.addAll(index2.get(k));
				}
				hasFixedPoint = true;
			}
		}
		return hasFixedPoint ? set : getIndexed(key);
	}

	public Collection<String> getIndexes() {
		return index.keySet();
	}
	
	private void reindex() {
		index = new HashMap<>();
		for (E element : contents) {
			Set<E> elements = index.get(element.index());
			if (elements == null) {
				elements = new HashSet<>();
				index.put(element.index(), elements);
			}
			elements.add(element);
			for (int i = 0; i < element.getTerms().size(); i++) {
				Set<E> es;
				Term term = element.getTerms().get(i);
				if (!term.isVariable()) {
					String key = element.index() + "_" + Integer.toString(i, 10) + "_" + term.value();
					if (index2.containsKey(key)) {
						es = index2.get(key);
					} else {
						es = new HashSet<>();
						index2.put(key, es);
					}
					es.add(element);
				}
			}
		}
	}

	@Override
	public boolean add(E element) {
		if (contents.add(element)) {
			Set<E> elements = index.get(element.index());
			if (elements == null) {
				elements = new HashSet<>();
				index.put(element.index(), elements);
			}
			elements.add(element);
			for (int i = 0; i < element.getTerms().size(); i++) {
				Set<E> es;
				Term term = element.getTerms().get(i);
				if (!term.isVariable()) {
					String key = element.index() + "_" + Integer.toString(i, 10) + "_" + term.value();
					if (index2.containsKey(key)) {
						es = index2.get(key);
					} else {
						es = new HashSet<>();
						index2.put(key, es);
					}
					es.add(element);
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean addAll(Collection<? extends E> elements) {
		boolean result = false;
		for(E element : elements) {
			if(add(element)) {
				result = true;
			}
		}
		return result;
	}

	@Override
	public void clear() {
		contents.clear();
		index.clear();
		index2.clear();
	}

	@Override
	public boolean contains(Object o) {
		return contents.contains(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return contents.containsAll(c);
	}

	@Override
	public boolean isEmpty() {
		return contents.isEmpty();
	}

	@Override
	public Iterator<E> iterator() {
		return contents.iterator();
	}

	@Override
	public boolean remove(Object o) {
		if(contents.remove(o)) {
			// This makes the remove O(n), but you need it like this if remove()
			// is to work through an iterator.
			// It doesn't really matter, since Jatalog doesn't use this method
			reindex();
		}
		return false;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		boolean changed = contents.removeAll(c);
		if (changed) {
			reindex();
		}
		return changed;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		boolean changed = contents.retainAll(c);
		if (changed) {
			reindex();
		}
		return changed;
	}

	@Override
	public int size() {
		return contents.size();
	}

	@Override
	public Object[] toArray() {
		return contents.toArray();
	}

	@Override
	public <A> A[] toArray(A[] a) {
		return contents.toArray(a);
	}	
}
