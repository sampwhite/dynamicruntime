package org.dynamicruntime.function;

/** Used to retrieve values from closure blocks. */
@SuppressWarnings("WeakerAccess")
public class DnPointer<T> {
    public T value;
    public DnPointer() {}
    public DnPointer(T value) {
        this.value = value;
    }

    public static <U> DnPointer<U> of(U value) {
        return new DnPointer<>(value);
    }
}
