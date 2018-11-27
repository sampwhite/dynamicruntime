package org.dynamicruntime.util;

import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.function.DnFunction;

import java.util.*;
import java.util.function.Function;

@SuppressWarnings("WeakerAccess")
public class DnCollectionUtil {
    /** Groovy envy method for creating maps. Creates a mutable map and drops nulls. */
    public static Map<String,Object> mMap(Object... args) {
        if (args.length % 2 != 0) {
            throw new RuntimeException("Number of arguments to map creation but be even.");
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

    /** Creates a mutable map with type parameters being arbitrary. Drops null values. */
    @SuppressWarnings("unchecked")
    public static <U,V> Map<U,V> mMapT(Object... args) {
        if (args.length % 2 != 0) {
            throw new RuntimeException("Number of arguments to map creation but be even.");
        }
        var map = new HashMap<U,V>();
        for (int i = 0; i < args.length; i += 2) {
            U k = (U)args[i];
            V v = (V)args[i + 1];
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

    /** Performs 'map' style call (called *collect* in Groovy) but drops nulls. The letter
     * n at the beginning of the method name indicates nulls are dropped. */
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

}
