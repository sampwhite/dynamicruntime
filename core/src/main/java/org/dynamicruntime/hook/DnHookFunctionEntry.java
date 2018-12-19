package org.dynamicruntime.hook;

@SuppressWarnings("WeakerAccess")
public class DnHookFunctionEntry<T> {
    public final String name;
    public final int priority;
    public final T function;

    public DnHookFunctionEntry(String name, int priority, T function) {
        this.name = name;
        this.priority = priority;
        this.function = function;
    }
}
