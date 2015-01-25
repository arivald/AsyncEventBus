package com.pgssoft.asyncmessagebus;

import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.Queue;
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
    static final String TAG = "messagebus.Dispatcher";

    static final Queue<Dispatcher> mPool = new ConcurrentLinkedQueue<Dispatcher>();

    Bus mBus = null;
    Object mEvent = null;
    Object mSingleTarget = null;

    @NonNull
    static Dispatcher obtain(@NonNull final Bus bus, @NonNull final Object event, @Nullable final Object singleTarget) {
        Dispatcher result;
        result = mPool.poll();
        if (result == null) {
            result = new Dispatcher();
        }
        result.mBus = bus;
        result.mEvent = event;
        result.mSingleTarget = singleTarget;
        return result;
    }

    @Override
    public void run() {
        dispatch();

        //reset and move myself to pool
        mBus = null;
        mEvent = null;
        mSingleTarget = null;
        mPool.add(this);
    }

    void dispatch() {
        boolean dispatched = false;

        for (Class<?> eventClass : Bus.getEventClasses(mEvent)) {
            for (Subscriber subscriber : mBus.getSubscribersForEventType(eventClass)) {
                Object target = subscriber.mTarget.get();
                if (target == null) continue;
                //if set to deliver to single target, dispatch to this target only.
                if (mSingleTarget != null && target != mSingleTarget) continue;

                dispatched = true;
                switch (subscriber.mMethod.getAnnotation(Subscribe.class).value()) {
                    case DISPATCHER:
                        subscriber.deliverEvent(mEvent);
                        break;

                    case UI:
                        Bus.mUiThreadHandler.post(Executor.obtain(subscriber, mEvent));
                        break;

                    case BACKGROUND:
                        mBus.executeInBackground(Executor.obtain(subscriber, mEvent));
                        break;

                    case AS_REGISTERED:
                        Handler handler = Bus.getHandlerForTarget(target);
                        if (handler == null) {
                            Log.e(TAG, "Subscriber requested AS_REGISTERED thread, but thread which registered this subscriber had not associated Looper at the time register was called.");
                            //fallback to BACKGROUND
                            mBus.executeInBackground(Executor.obtain(subscriber, mEvent));
                        } else {
                            handler.post(Executor.obtain(subscriber, mEvent));
                        }
                        break;
                }
            }
        }
        //if not dispatched, send DeadEvent
        if (!dispatched && !(mEvent instanceof DeadEvent)) {
            mEvent = new DeadEvent(mBus, mEvent);
            dispatch();
        }
    }
}
