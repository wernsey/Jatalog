package za.co.wstoop.jdatalog;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Subclass of {@link Set} that can 
 * @param <E> Type of elements that will be stored in the set; must implement {@link Indexable}
 * @param <I> Type of the index
 */
class IndexedSet<E extends Indexable<I>, I> implements Set<E> {

	private Map<I, Set<E>> index;
	
	private Set<E> contents;
	
	/**
	 * Default constructor
	 */
	IndexedSet() {
		index = new HashMap<I, Set<E>>();
		contents = new HashSet<>();
	}
	
	/**
	 * Creates the set from a different collection
	 * @param elements
	 */
	IndexedSet(Collection<E> elements) {
		index = new HashMap<I, Set<E>>();
		contents = new HashSet<>();
		for(E element : elements) {
			add(element);
		}
	}
	
	/**
	 * Retrieves the subset of the elements in the set with the 
	 * specified index
	 * @param key The indexed element
	 * @return The specified subset
	 */
	Set<E> getIndexed(I key) {		
		Set<E> elements = index.get(key);
		if(elements == null) return Collections.emptySet();
		return elements;
	}
	
	@Override
	public boolean add(E element) {
		contents.add(element);
		Set<E> elements = index.get(element.index());
		if(elements == null) {
			elements = new HashSet<E>();
			index.put(element.index(), elements);
		}
		return elements.add(element);
	}	

	@Override
	public boolean addAll(Collection<? extends E> elements) {
		for(E e : elements) {
			if(!add(e)) 
				return false;
		}
		return true;
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
		// FIXME: I don't have StackOverflow at the moment to answer this question
		if(o instanceof Indexable<?>) {
			Indexable<I> e = (Indexable<I>)o;
			Collection<E> es = getIndexed(e.index());
			if(es == null || !es.remove(o)) 
				return false;
			return contents.remove(o);
		}
		return false;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		for(Object o : c) {
			if(!remove(o)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		// TODO What is this method supposed to do again?
		return false;
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
