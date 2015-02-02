package com.pgssoft.async_event_bus;

import android.support.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Wraps a single-argument 'subscriber' method on a specific object, a target.
 * Immutable, except that mTarget reference can be cleared (by GC).
 * <p/>
 * Two Subscribers are equivalent when they refer to the same method on the same object instance( not class).
 * This equality property is used to ensure that no handler method is registered more than once.
 * <p/>
 * Note: package access, the class is for internal bus use.
 */
/*package*/ class Subscriber {

    /**
     * The target, and instance to object to deliver the event to.
     */
    @NonNull
    final WeakReference<Object> mTarget;

    /**
     * The method in target, handler for this certain event class.
     */
    @NonNull
    final Method mMethod;

    /**
     * Object hash code, cached because it will not change.
     */
    final int mHashCode;

    Subscriber(@NonNull Object target, @NonNull Method method) {
        mTarget = new WeakReference<Object>(target);
        mMethod = method;
        method.setAccessible(true);
        //Compute hash code eagerly since we know it will be used frequently,
        //and finally the target may be GCed and became null
        mHashCode = (31 + method.hashCode()) * 31 + System.identityHashCode(target);
    }

    /**
     * Invokes the wrapped handler mMethod to handle {@code event}.
     *
     * @param event @NonNull event to handle
     */
    void deliverEvent(@NonNull EventBus bus, @NonNull Object event) {
        try {
            //Important: get solid reference first, then check null on this reference!
            Object target = mTarget.get();
            if (target != null) {
                mMethod.invoke(target, event);
            }
        } catch (InvocationTargetException e) {
            //here exception would be InvocationTargetException. We need to unpack original exception
            bus.onSubscriberException(mTarget.get(), mMethod, e.getCause());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public int hashCode() {
        return mHashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass() || mHashCode != ((Subscriber) obj).mHashCode) {
            return false;
        }

        final Subscriber other = (Subscriber) obj;
        return mTarget.get() == other.mTarget.get() && mMethod.equals(other.mMethod);
    }

}
