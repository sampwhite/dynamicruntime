package dynamicruntime.org.util;

import java.util.*;

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

}
