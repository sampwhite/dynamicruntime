package org.dynamicruntime.util;

import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.function.DnFunction;

import java.util.*;
import java.util.function.Function;

/** A lot of this class is driven by Groovy envy (as in we envy Groovy for having certain convenience
 * constructions). */
@SuppressWarnings("WeakerAccess")
public class DnCollectionUtil {
    /** Groovy envy method for creating maps. Creates a mutable map and drops nulls. We have a version
     * specific to storing *Object* types because it is the main data class of this application and
     * it means you can declare the variable that receives the results of the call with *var*.
     * Every two arguments becomes a key-value pair. */
    public static Map<String,Object> mMap(Object... args) {
        if (args.length % 2 != 0) {
            throw new RuntimeException("Number of arguments to map creation must be even.");
        }
        var map = new HashMap<String,Object>();
        for (int i = 0; i < args.length; i += 2) {
            Object k = args[i];
            Object v = args[i + 1];
            if (k != null && v != null) {
                map.put(k.toString(), v);
            }
        }
        return map;
    }

    /** Creates a mutable map with type parameters being arbitrary. Drops null values. This is a dangerous
     * method in that the caller needs to make sure the argument lines up with the expected types. */
    @SuppressWarnings("unchecked")
    public static <U,V> Map<U,V> mMapT(Object... args) {
        if (args.length % 2 != 0) {
            throw new RuntimeException("Number of arguments to map creation must be even.");
        }
        var map = new HashMap<U,V>();
        for (int i = 0; i < args.length; i += 2) {
            U k = (U)args[i]; // Dependent on the carefulness of the caller, much like it would be in Groovy.
            V v = (V)args[i + 1]; // Again, dependent on the carefulness of the caller.
            if (k != null && v != null) {
                map.put(k, v);
            }
        }
        return map;
    }

    /** Creates a mutable list from arguments. Drops null values. */
    @SafeVarargs
    public static <T> List<T> mList(T... args) {
        return mListA(args);
    }

    public static <T> List<T> mListA(T[] args) {
        var list = new ArrayList<T>();
        for (T arg : args) {
            if (arg != null) {
                list.add(arg);
            }
        }
        return list;
    }

    public static <T> List<T> cloneList(Collection<T> inList) {
        return new ArrayList<>(inList);
    }

    public static <U,V> Map<U,V> cloneMap(Map<U,V> inMap) {
        return new HashMap<>(inMap);
    }

    /** Performs 'map' style call (called *collect* in Groovy) but drops nulls. The letter
     * 'n' at the beginning of the method name indicates nulls are dropped. */
    @SuppressWarnings("Duplicates") // Removing duplicates creates awkward code for just a few lines saved.
    public static <U,V> List<V> nMap(Collection<U> inList, DnFunction<U,V> function) throws DnException {
        var outList = new ArrayList<V>();
        for (U inItem : inList) {
            if (inItem != null) {
                var outItem = function.apply(inItem);
                if (outItem != null) {
                    outList.add(outItem);
                }
            }
        }
        return outList;
    }

    /** Does the same as {@link #nMap} but does not throw DnException. */
    @SuppressWarnings("Duplicates")
    public static <U,V> List<V> nMapSimple(Collection<U> inList, Function<U,V> function) {
        var outList = new ArrayList<V>();
        for (U inItem : inList) {
            if (inItem != null) {
                var outItem = function.apply(inItem);
                if (outItem != null) {
                    outList.add(outItem);
                }
            }
        }
        return outList;
    }

    /** Creates a map from a list by using a function that extracts the key for each item. */
    public static <U,V> Map<U,V> nMkMap(Collection<V> items, Function<V,U> getKeyFunction) {
        Map<U,V> map = mMapT();
        if (items != null) {
            for (var item : items) {
                if (item != null) {
                    var key = getKeyFunction.apply(item);
                    if (key != null) {
                        map.put(key, item);
                    }
                }
            }
        }
        return map;
    }

    /** Creates map for doing last access order caching. */
    public static <U,V> CacheMap<U,V> mCacheMap(int maxItems) {
        return new CacheMap<>(maxItems, true /* Sorted by least recently accessed to most recently. */);
    }

    /** Creates map for doing simple bounded size caching. */
    public static <U,V> CacheMap<U,V> mBoundedMap(int maxItems) {
        return new CacheMap<>(maxItems, false /* Standard linked list order. */);
    }


    public static <T> T findItem(Collection<T> list, Function<T,Boolean> testFunction) {
        for (T item : list) {
            if (item != null && testFunction.apply(item)) {
                return item;
            }
        }
        return null;
    }

    public static <T> int countNotNull(Collection<T> list) {
        if (list == null) {
            return 0;
        }
        int count = 0;
        for (T item : list) {
            if (item != null) {
                count++;
            }
        }
        return count;
    }

    public static void mergeMapRecursively(Map<String,Object> curMap, Map<String,Object> newData) {
       mergeMapRecursively(curMap, newData, 0);
    }

    @SuppressWarnings("unchecked")
    private static void mergeMapRecursively(Map<String,Object> curMap, Map<String,Object> newData, int nestLevel) {
        if (nestLevel > 10) {
            return;
        }

        for (var key : newData.keySet()) {
            Object newObj = newData.get(key);
            Object curObj = curMap.get(key);
            if (curObj instanceof Map && newObj instanceof Map) {
                // We clone as needed, so we do not corrupt internals of original holder of *curMap*.
                var newMap = (Map<String,Object>)newObj;
                newObj = cloneMap((Map<String,Object>)curObj);
                mergeMapRecursively((Map<String,Object>)curObj, newMap, nestLevel + 1);
            }
            curMap.put(key, newObj);
        }
    }

    public static Map<String,Object> collapseMaps(Map<String,Object> map) {
        var newMap = mMap();
        collapseMaps(newMap, map, null, 0);
        return newMap;
    }

    @SuppressWarnings("unchecked")
    private static void collapseMaps(Map<String,Object> newMap, Map<String,Object> subMap,
            String prefix, int nestLevel) {
        if (nestLevel > 10) {
            return;
        }
        for (var key : subMap.keySet()) {
            String newKey = (prefix != null) ? prefix + "." + key : key;
            Object obj = subMap.get(key);
            if (obj instanceof Map) {
                collapseMaps(newMap, (Map<String,Object>)obj, newKey, nestLevel + 1);
            } else {
                newMap.put(newKey, obj);
            }
        }
    }

    public static <U,V> void addToListMap(Map<U,List<V>> listMap, U key, V val) {
        if (key == null || val == null) {
            return;
        }
        List<V> curList = listMap.computeIfAbsent(key, k -> mList());
        curList.add(val);
    }
}
