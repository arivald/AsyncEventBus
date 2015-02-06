package com.pgssoft.async_event_bus.mock;

/**
 * Object that can be final (ex accessible in inner ad-hoc classes), and can hold reference to other object
 */
@SuppressWarnings("UnusedDeclaration")
public class Reference<T> {
    public T ref;

    public Reference() {
        this.ref = null;
    }

    public Reference(T ref) {
        this.ref = ref;
    }
}
