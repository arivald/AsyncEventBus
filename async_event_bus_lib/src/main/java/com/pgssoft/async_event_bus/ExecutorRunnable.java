package com.pgssoft.async_event_bus;

import android.support.annotation.NonNull;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A Runnable that execute a Subscriber method.
 * <p/>
 * ExecutorRunnable is a Runnable, subscriber method will be executed by posting Executor to Handler,
 * or by executing it on Bus Executor background thread pool.
 * <p/>
 * ExecutorRunnables are reused, You have to use ExecutorRunnable.obtain() to get instance, and You are not allowed
 * to do anything after You scheduled this instance for execution.
 * <p/>
 * Note: package access, class is for internal use only.
 */
/*package*/ class ExecutorRunnable implements Runnable {
    static final Queue<ExecutorRunnable> mPool = new ConcurrentLinkedQueue<ExecutorRunnable>();

    EventBus mEventBus;
    Subscriber mSubscriber;
    Object mEvent;

    @NonNull
    static ExecutorRunnable obtain(@NonNull EventBus bus, @NonNull Subscriber subscriber, @NonNull Object event) {
        ExecutorRunnable result = mPool.poll();
        if (result == null) {
            result = new ExecutorRunnable();
        }
        result.mEventBus = bus;
        result.mSubscriber = subscriber;
        result.mEvent = event;
        return result;
    }

    @Override
    public void run() {
        mSubscriber.deliverEvent(mEventBus, mEvent);

        //reset and move myself to pool
        mEventBus = null;
        mSubscriber = null;
        mEvent = null;
        mPool.add(this);
    }
}
