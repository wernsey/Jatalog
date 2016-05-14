package za.co.wstoop.jdatalog;

/**
 * An interface for an object that can be indexed for use with {@link IndexedSet}
 * @param <T> The index type
 */
interface Indexable<T> {
	T index();
}
