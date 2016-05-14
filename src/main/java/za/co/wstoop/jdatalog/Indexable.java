package za.co.wstoop.jdatalog;

/**
 * An interface for an object that can be indexed for use with {@link IndexedSet}
 * @param <T> The index type
 */
interface Indexable<T> {
	/**
	 * Retrieves the element according to which this instance is indexed.
	 * @return The index of this instance
	 */
	T index();
}
