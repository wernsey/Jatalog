package za.co.wstoop.jatalog.engine;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Map&lt;&gt; implementation that has a parent Map&lt;&gt; where it looks up a value if the value is not in {@code this}. 
 * <p>
 * Its behaviour is equivalent to a HashMap that is passed a parent map in its constructor, except that it keeps a reference 
 * to the parent map rather than copying it. It never modifies the parent map. (If the behaviour deviates from this, it is a bug 
 * and must be fixed).
 * </p><p>
 * Internally, it has two maps: {@code self} and {@code parent}. The {@link #get(Object)} method will look up a key in self and if it doesn't find it, looks
 * for it in {@code parent}. The {@link #put(Object, Object)} method always adds values to {@code self}. The parent in turn may also be a StackMap, so some method
 * calls may be recursive. The {@link #flatten()} method combines the map with its parents into a single one.
 * </p><p>
 * It is used by Jatalog for the scoped contexts where the variable bindings enter and leave scope frequently during the
 * recursive building of the database, but where the parent Map&lt;&gt; needs to be kept for subsequent recursions.
 * </p><p>
 * Methods like {@link #entrySet()}, {@link #keySet()} and {@link #values()} are required by the Map&lt;&gt; interface that their returned collections be backed
 * by the Map&lt;&gt;. Therefore, their implementations here will flatten the map first. Once these methods are called StackMap just
 * becomes a wrapper around the internal HashMap, hence Jatalog avoids these methods internally.
 * </p><p>
 * The {@link #remove(Object)} method also flattens {@code this} to avoid modifying the parent while and the {@link #clear()} method just sets parent to null
 * and clears {@code self}.
 * </p><p>
 * I initially just assumed that using the StackMap would be faster, so I tried an implementation with a {@link HashMap} where I just did a
 * {@code newMap.putAll(parent)} and removed the StackMap entirely. My rough benchmarks showed the StackMap-based implementation to be about 30%
 * faster than the alternative.
 * I've also tried a version that extends {@link java.util.AbstractMap}, but it proved to be significantly slower.
 * </p>
 */
public class StackMap<K,V> implements Map<K,V> {
    private Map<K,V> self;
    private Map<K,V> parent;

    public StackMap() {
        self = new HashMap<K, V>();
        this.parent = null;
    }

    public StackMap(Map<K,V> parent) {
        self = new HashMap<K, V>();
        this.parent = parent;
    }

    /**
     * Returns a new Map&lt;K,V&gt; that contains all the elements of this map,
     * but does not have a parent anymore.
     * The returned map is actually a {@code java.util.HashMap}.
     * @return a new flattened Map.
     */
    public Map<K,V> flatten() {
        Map<K,V> map = new HashMap<K,V>();
        // I don't use map.putAll(this) to avoid relying on entrySet()
        if(parent != null) {
            map.putAll(parent);
        }
        map.putAll(self);
        return map;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        Set<K> keys = new HashSet<>(self.keySet());
        keys.addAll(parent.keySet());
        int s = keys.size(), i = 0;
        for(K k : keys) {
            sb.append(k).append(": ");
            sb.append(get(k));
            if(++i < s) sb.append(", ");
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public V put(K key, V value) {
        return self.put(key, value);
    }

    @Override
    public boolean containsKey(Object key) {
        if(self.containsKey(key))
            return true;
        if(parent != null)
            return parent.containsKey(key);
        return false;
    }

    @Override
    public V get(Object key) {
        V value = self.get(key);
        if(value != null)
            return value;
        if(parent != null)
            return parent.get(key);
        return null;
    }

    @Override
    public Set<Map.Entry<K,V>> entrySet() {
        if(parent != null) {
            self = flatten(); // caveat emptor
            parent = null;
        }
        return self.entrySet();
    }

    @Override
    public int size() {
        int s = self.size();
        if(parent != null) {
            // Work around situations where self contains a `key` that's already in `parent`.
            // These situations shouldn't occur in Jatalog, though
            for(K k : parent.keySet()) {
                if(!self.containsKey(k))
                    s++;
            }
        }
        return s;
    }

    @Override
    public void clear() {
        // We don't want to modify the parent, so we just orphan this
        parent = null;
        self.clear();
    }

    @Override
    public boolean equals(Object o) {
        if(o == null || !(o instanceof Map))
            return false;
        Map<?, ?> that = (Map<?, ?>)o;
        return entrySet().equals(that.entrySet());
    }
    @Override
    public int hashCode() {
        int h = 0;
        for(Map.Entry<K,V> entry : entrySet()) {
            h += entry.hashCode();
        }
        return h;
    }
    @Override
    public Set<K> keySet() {
        if(parent != null) {
            self = flatten(); // caveat emptor
            parent = null;
        }
        return self.keySet();
    }
    @Override
    public Collection<V> values() {
        if(parent != null) {
            self = flatten(); // caveat emptor
            parent = null;
        }
        return self.values();
    }
    @Override
    public void putAll(Map<? extends K,? extends V> m) {
        self.putAll(m);
    }
    @Override
    public V remove(Object key) {
        if(parent != null) {
            self = flatten(); // caveat emptor
            parent = null;
        }
        return self.remove(key);
    }
    @Override
    public boolean containsValue(Object value) {
        return self.containsValue(value) || (parent != null && parent.containsValue(value));
    }
    @Override
    public boolean isEmpty() {
        if(self.isEmpty()) {
            if(parent != null)
                return parent.isEmpty();
            else
                return true;
        }
        return false;
    }
}