package com.pgssoft.async_event_bus;

import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Dispatcher is an class used to process posted event.
 * It is responsible for finding all subscribers, and delivering event to subscribers
 * according to their needs.
 * <p/>
 * Dispatcher is Runnable, it will be executed by posting to some Handler, usually to dispatcher thread Handler
 * <p/>
 * Dispatchers are reused, You have to use Dispatcher.obtain() to get instance, and You are not allowed
 * to do anything after You posted this instance.
 * <p/>
 * Note: package access, class is for internal bus use.
 */
/*package*/ class Dispatcher implements Runnable {
    static final String TAG = "EventBus.Dispatcher";

    static final Queue<Dispatcher> mPool = new ConcurrentLinkedQueue<Dispatcher>();

    /**
     * Cache for all classes/interfaces of given event class.
     * Key: event class
     * Value: set of classes/interfaces
     */
    final static Map<Class<?>, Set<Class<?>>> mEventClassHierarchyCache = new HashMap<Class<?>, Set<Class<?>>>();

    EventBus mEventBus;
    Object mEvent;
    Object mSingleTarget;

    @NonNull
    static Dispatcher obtain(@NonNull final EventBus eventBus, @NonNull final Object event, @Nullable final Object singleTarget) {
        Dispatcher result = mPool.poll();
        if (result == null) {
            result = new Dispatcher();
        }
        result.mEventBus = eventBus;
        result.mEvent = event;
        result.mSingleTarget = singleTarget;
        return result;
    }

    @Override
    public void run() {
        dispatch();

        //reset and move myself to pool
        mEventBus = null;
        mEvent = null;
        mSingleTarget = null;
        mPool.add(this);
    }

    void dispatch() {
        boolean dispatched = false;

        for (Class<?> eventClass : getEventClasses(mEvent)) {
            for (Subscriber subscriber : mEventBus.getSubscribersForEventType(eventClass)) {
                Object target = subscriber.mTarget.get();
                //skip GCed targets
                if (target == null) continue;
                //if set to deliver to single target, dispatch to this target only.
                if (mSingleTarget != null && target != mSingleTarget) continue;

                dispatched = true;
                switch (subscriber.mThread) {
                    case DISPATCHER:
                        subscriber.deliverEvent(mEventBus, mEvent);
                        break;

                    case UI:
                        EventBus.mUiThreadHandler.post(ExecutorRunnable.obtain(mEventBus, subscriber, mEvent));
                        break;

                    case BACKGROUND:
                        mEventBus.mBackgroundExecutor.execute(ExecutorRunnable.obtain(mEventBus, subscriber, mEvent));
                        break;

                    case AS_REGISTERED:
                        Handler handler = EventBus.getHandlerForTarget(target);
                        if (handler == null) {
                            Log.e(TAG, "The subscriber requested AS_REGISTERED thread, but thread which registered this subscriber had not associated Looper at the time when register() was called.");
                            //fallback to BACKGROUND
                            mEventBus.mBackgroundExecutor.execute(ExecutorRunnable.obtain(mEventBus, subscriber, mEvent));
                        } else {
                            handler.post(ExecutorRunnable.obtain(mEventBus, subscriber, mEvent));
                        }
                        break;
                }
            }
        }
        //if not dispatched, send DeadEvent
        if (!dispatched && !(mEvent instanceof DeadEvent)) {
            mEvent = new DeadEvent(mEventBus, mEvent);
            dispatch();
        }
    }


    /**
     * Get set of classes implemented by event object.
     * This includes all super classes, all implemented interfaces, and all interfaces of superclasses.
     */
    @NonNull
    static Set<Class<?>> getEventClasses(@NonNull final Object event) {
        Class<?> eventClass = event.getClass();
        Set<Class<?>> classes;
        synchronized (mEventClassHierarchyCache) {
            classes = mEventClassHierarchyCache.get(eventClass);
        }
        if (classes == null) {
            classes = new HashSet<Class<?>>();
            List<Class<?>> parents = new LinkedList<Class<?>>();
            parents.add(eventClass);

            while (!parents.isEmpty()) {
                Class<?> clazz = parents.remove(0);
                classes.add(clazz);

                Class<?> parent = clazz.getSuperclass();
                if (parent != null) {
                    parents.add(parent);
                }
                Collections.addAll(classes, clazz.getInterfaces());
            }
            synchronized (mEventClassHierarchyCache) {
                mEventClassHierarchyCache.put(eventClass, classes);
            }
        }

        return classes;
    }

}
