package za.co.wstoop.jatalog.engine;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Subclass of {@link Set} that can quickly access a subset of its elements through an index.
 * Jatalog uses it to quickly retrieve the facts with a specific predicate.   
 * @param <E> Type of elements that will be stored in the set; must implement {@link Indexable}
 * @param <I> Type of the index
 */
public class IndexedSet<E extends Indexable<I>, I> implements Set<E> {
	
	private Set<E> contents;	

	private Map<I, Set<E>> index;
	
	/**
	 * Default constructor.
	 */
	public IndexedSet() {
		index = new HashMap<I, Set<E>>();
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
	public Set<E> getIndexed(I key) {		
		Set<E> elements = index.get(key);
		if(elements == null) return Collections.emptySet();
		return elements;
	}

	public Collection<I> getIndexes() {
		return index.keySet();
	}
	
	private void reindex() {
		index = new HashMap<I, Set<E>>();
		for (E element : contents) {
			Set<E> elements = index.get(element.index());
			if (elements == null) {
				elements = new HashSet<E>();
				index.put(element.index(), elements);
			}
			elements.add(element);
		}
	}

	@Override
	public boolean add(E element) {
		if (contents.add(element)) {
			Set<E> elements = index.get(element.index());
			if (elements == null) {
				elements = new HashSet<E>();
				index.put(element.index(), elements);
			}
			elements.add(element);
			return true;
		}
		return false;
	}

	@Override
	public boolean addAll(Collection<? extends E> elements) {
		boolean result = false;
		for(E element : elements) {
			if(add(element)) 
				result = true;
		}
		return result;
	}

	@Override
	public void clear() {
		contents.clear();
		index.clear();
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
