package com.pgssoft.asyncmessagebus;

import android.support.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/**
 * Wraps a single-argument 'subscriber' method on a specific object.
 * Immutable, except that mTarget reference can be cleared (by GC).
 * <p/>
 * Two Subscribers are equivalent when they refer to the same mMethod on the same object (not class). This
 * property is used to ensure that no handler method is registered more than once.
 * <p/>
 * Note: package access, the class is for internal bus use.
 */
/*package*/ class Subscriber {

    /**
     * Object sporting the handler mMethod.
     */
    @NonNull
    final WeakReference<Object> mTarget;
    /**
     * Handler mMethod.
     */
    @NonNull
    final Method mMethod;
    /**
     * Object hash code.
     */
    final int mHashCode;

    Subscriber(@NonNull Object target, @NonNull Method method) {
        mTarget = new WeakReference<Object>(target);
        mMethod = method;
        method.setAccessible(true);
        // Compute hash code eagerly since we know it will be used frequently,
        //and finally target may be GCed and became null
        mHashCode = (31 + method.hashCode()) * 31 + System.identityHashCode(target);
    }

    /**
     * Invokes the wrapped handler mMethod to handle {@code event}.
     *
     * @param event @NonNull event to handle
     */
    void deliverEvent(@NonNull Object event) {
        try {
            //Important: get solid reference first, then check null on this reference!
            Object target = mTarget.get();
            if (target != null) {
                mMethod.invoke(target, event);
            }
        } catch (Throwable e) {
            //todo better logging?
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

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        final Subscriber other = (Subscriber) obj;
        return mMethod.equals(other.mMethod) && mTarget.get() == other.mTarget.get();
    }


}
