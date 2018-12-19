package org.dynamicruntime.hook;

import static org.dynamicruntime.util.DnCollectionUtil.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class DnHook<T> {
    /** Treat this as immutable. */
    public final List<DnHookFunctionEntry<T>> entries;

    public final List<DnHookFunctionEntry<T>> newEntries = mList();
    public volatile boolean hasNewChanges;

    public DnHook(List<DnHookFunctionEntry<T>> entries) {
        this.entries = entries;
    }

    public DnHook<T> mkUpdate() {
        Map<String,DnHookFunctionEntry<T>> collation = mMapT();
        for (var entry : entries) {
            collation.put(entry.name, entry);
        }
        for (var entry: newEntries) {
            collation.put(entry.name, entry);
        }
        var newList = cloneList(collation.values());
        newList.sort(Comparator.comparingInt(x -> x.priority));
        return new DnHook<>(newList);
    }

    public void addFunctionEntry(String name, int priority, T function) {
        var entry = new DnHookFunctionEntry<>(name, priority, function);
        addFunctionEntry(entry);
    }

    public synchronized void addFunctionEntry(DnHookFunctionEntry<T> entry) {
        newEntries.add(entry);
        hasNewChanges = true;
    }
}
