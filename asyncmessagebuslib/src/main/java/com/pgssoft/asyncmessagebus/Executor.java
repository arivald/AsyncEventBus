package com.pgssoft.asyncmessagebus;

import android.support.annotation.NonNull;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Class that execute Subscriber method.
 * <p/>
 * Executor is a Runnable,  subscriber method will be executed by posting Executor to Handler,
 * or by executing it on App background thread pool.
 * <p/>
 * Executors are reused, You have to use Executor.obtain() to get instance, and You are not allowed
 * to do anything after You posted this instance.
 * <p/>
 * Note: package access, class is for internal bus use.
 */
/*package*/ class Executor implements Runnable {
    static final Queue<Executor> mPool = new ConcurrentLinkedQueue<Executor>();

    Subscriber mSubscriber;
    Object mEvent;

    @NonNull
    static Executor obtain(@NonNull Subscriber subscriber, @NonNull Object event) {
        Executor result;
        result = mPool.poll();
        if (result == null) {
            result = new Executor();
        }
        result.mSubscriber = subscriber;
        result.mEvent = event;
        return result;
    }

    @Override
    public void run() {
        mSubscriber.deliverEvent(mEvent);

        //reset and move myself to pool
        mSubscriber = null;
        mEvent = null;
        synchronized (mPool) {
            mPool.add(this);
        }
    }
}
