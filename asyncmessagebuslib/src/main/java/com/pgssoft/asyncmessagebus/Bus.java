package com.pgssoft.asyncmessagebus;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dispatches events to listeners, and provides ways for listeners to register themselves.
 * <p/>
 * The Bus allows publish-subscribe-style communication between components without requiring the components to
 * explicitly register with one another (and thus be aware of each other).  It is designed exclusively to replace
 * traditional Android in-process event distribution using explicit registration or listeners.
 * <p/>
 * Messages can be delivered in different process than they have been send. In fact, most processing is in special
 * thread, we saving cycles in UI thread.
 * <p/>
 * <h2>Receiving Events</h2>
 * To receive events, an object should:
 * <ol>
 * <li>Expose a method (any access specifier), known as the <i>subscriber</i>, which accepts a single argument of the type of event
 * desired;</li>
 * <li>Mark it with a {@link Subscribe} annotation;</li>
 * <li>Register itself to an Bus instance's, using {@link #register(Object)} method.
 * </li>
 * </ol>
 * <p/>
 * <h2>Posting Events</h2>
 * To post an event, simply provide the event object to the {@link #post(Object)}, or the {@link #postDelayed(Object, long)},
 * or the {@link #send(Object)} method.
 * The Bus instance will determine the type of event and route it to all registered listeners.<br/>
 * The difference between posting methods:
 * <ol>
 * <li>the {@link #post(Object)} will schedule an event for the dispatching thread, and return immediately.
 * Event will be processed in dispatching thread, then delivered to subscribers, in threads according to their needs.</li>
 * <li>the {@link #postDelayed(Object, long)} works like the {@link #post(Object)}, except that event will
 * be scheduled for delayed delivery, after specified number of milliseconds. Note that there is no guarantee to deliver
 * event at specific time, we can only guarantee to deliver event after specified amount of time passed.</li>
 * <li>the {@link #send(Object)} works like the {@link #post(Object)}, except that the whole dispatch procedure will happen in
 * the caller thread. It may bring better overall performance (no thread hoop), but will slow down caller thread.
 * As a side effect, all subscribers with the DeliveryThread.DISPATCHER will be executed in the caller thread too, which may
 * be desirable in some cases.</li>
 * </ol>
 * <p/>
 * Events are routed based on their type &mdash; an event will be delivered to any subscriber for any type to which the
 * event is <em>assignable.</em>  This includes implemented interfaces, all superclasses, and all interfaces implemented
 * by superclasses.
 * <p/>
 * There is no guarantee that subscribers registered for given event will be executed in any order. In fact, subscribers
 * that run in different threads may execute in same time. Because of this, and fact that all subscribers will got reference
 * to same event object, it is strongly recommended to make events objects thread-safe (ex. immutable). Subscribers also
 * should not modify event objects.
 * <p/>
 * But for the subsequent events, there is guarantee that events will be delivered in same order, with exception for
 * DeliveryThread.BACKGROUND subscribers, which may be executed simultaneously.
 * <p/>
 * <h2>Subscribers</h2>
 * Subscribers must accept only one argument: the event.
 * <p/>
 * Subscribers should not, in general, throw.  If they do, the Bus will log the exception, but will not re-throw.
 * <p/>
 * <h2>Dead Events</h2>
 * If an event is posted, but no registered subscribers can accept it, it is considered "dead."  To give the system a
 * second chance to handle dead events, they are wrapped in an instance of {@link DeadEvent}
 * and reposted. Note that this check is done on dispatch phase, not on delivery phase. It is possible that event was dispatched,
 * but not executed, because subscribing object have been GCed
 * <p/>
 * This class is safe for concurrent use.
 */
@SuppressWarnings("UnusedDeclaration")
public class Bus {
    static final String TAG = "messagebus.Bus";

    public enum DeliveryThread {
        /**
         * The subscriber must be called in UI thread
         */
        UI,
        /**
         * The subscriber prefer to be called in background thread (random thread from pool)
         */
        BACKGROUND,
        /**
         * The subscriber prefer to be called in events dispatcher thread.
         * This is preferable for small and short subscribers.
         */
        DISPATCHER,
        /**
         * The subscriber must be called in same thread that it registered itself to bus.
         * The thread have to have associated {@link android.os.Looper} (Looper.myLooper() should not be null)
         * at the time the register() is called.
         * If there is no {@link android.os.Looper}, this is equivalent of the DeliveryThread.BACKGROUND.
         * if object is registered in UI thread, this is effectively equivalent of the Thread.UI.
         * Best to ue with the {@link android.os.HandlerThread}.
         */
        AS_REGISTERED,
    }

    /**
     * Creates a new Bus named "default".
     */
    public Bus() {
        this("unnamed");
    }

    /**
     * Creates a new Bus with the given {@code name}.
     *
     * @param name a brief name for this bus, for debugging purposes.
     */
    public Bus(@NonNull String name) {
        this(name, null);
    }

    /**
     * Creates a new Bus with the given {@code name}.
     *
     * @param name     a brief name for this bus, for debugging purposes.
     * @param executor executor to manage background threads. Pass null to use internal one.
     */
    public Bus(@NonNull String name, @Nullable java.util.concurrent.Executor executor) {
        mName = name;
        new HandlerThread(toString() + ".dispatcher", android.os.Process.THREAD_PRIORITY_BACKGROUND) {
            @Override
            protected void onLooperPrepared() {
                mDispatcherThreadHandler = new Handler(getLooper());
            }
        }.start();

        if (executor != null)
            mBackgroundExecutor = executor;
        else
            mBackgroundExecutor = new EagerThreadPoolExecutor(
                    2, Math.max(2, Runtime.getRuntime().availableProcessors() * 2),
                    10, TimeUnit.SECONDS,
                    new ThreadFactory() {
                        private final AtomicInteger mCount = new AtomicInteger(1);

                        @Override
                        public Thread newThread(@NonNull Runnable r) {
                            Thread thread = new Thread(r, "Bus.Background #" + mCount.getAndIncrement());
                            thread.setPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                            return thread;
                        }
                    });

        //wait for mDispatcherThreadHandler to be set
        while (mDispatcherThreadHandler == null)

        {
            Thread.yield();
        }

    }

    /**
     * Registers all subscriber methods on {@code object} to receive events.
     * <p/>
     *
     * @param object @NonNull object whose subscriber methods should be registered.
     */
    public void register(@NonNull Object object) {
        Looper looper = Looper.myLooper();
        if (looper != null)
            synchronized (mTargetsLoopers) {
                //assign Looper for obiect
                mTargetsLoopers.put(object, new WeakReference<Looper>(looper));
                //create Handler for Looper, if it is not created yet.
                if (mLoopersHandlers.get(looper) == null) {
                    mLoopersHandlers.put(looper,
                            looper != Looper.getMainLooper()
                                    ? new Handler(looper) : mUiThreadHandler);
                }
            }

        //Key: event class
        //Value: set of Subscriber's that can handle this event class.
        for (Map.Entry<Class<?>, Set<Subscriber>> entry : findAllSubscribers(object).entrySet()) {
            getSubscribersForEventType(entry.getKey()).addAll(entry.getValue());
        }
    }

    /**
     * Unregisters all subscriber methods on a registered {@code object}.
     * While processing it will remove also all data for already garbage collected objects.
     *
     * @param object @Nullable object whose subscriber methods should be unregistered.
     *               Pass null ro remove just garbage collected objects.
     */
    public void unregister(@Nullable Object object) {
        synchronized (mCurrentlyRegisteredSubscribersByEventType) {
            for (Map.Entry<Class<?>, Set<Subscriber>> entry : mCurrentlyRegisteredSubscribersByEventType.entrySet()) {
                Set<Subscriber> subscribers = entry.getValue();
                for (Subscriber subscriber : subscribers) {
                    Object target = subscriber.mTarget.get();
                    if (target == null || target == object) {
                        subscribers.remove(subscriber);
                    }
                }
            }
        }
    }

    /**
     * Posts an event to all registered subscribers.
     * This method will initiate posting process, and return immediately.
     * <p/>
     * If no subscribers have been subscribed for {@code event}'s class, and {@code event} is not already a
     * {@link DeadEvent}, it will be wrapped in a DeadEvent and reposted.
     *
     * @param event @NonNull event to post.
     * @throws NullPointerException if the event is null.
     */
    public void post(@NonNull final Object event) {
        mDispatcherThreadHandler.post(Dispatcher.obtain(this, event, null));
    }

    /**
     * Posts an event to all registered subscribers in one specific target object.
     * This method will initiate posting process, and return immediately.
     * <p/>
     * If no subscribers have been subscribed for {@code event}'s class, and {@code event} is not already a
     * {@link DeadEvent}, it will be wrapped in a DeadEvent and re-posted.
     *
     * @param event  @NonNull event to post.
     * @param target @NonNull target to deliver event to. Target must be registered in bus already.
     * @throws NullPointerException if the event is null.
     * todo unit test
     */
    public void postToTarget(@NonNull final Object event, @NonNull Object target) {
        mDispatcherThreadHandler.post(Dispatcher.obtain(this, event, target));
    }

    /**
     * Posts an event to all registered subscribers after given number of miliseconds.
     * This method will initiate posting process, and return immediately.
     * <p/>
     * If no subscribers have been subscribed for {@code event}'s class, and {@code event} is not already a
     * {@link DeadEvent}, it will be wrapped in a DeadEvent and reposted.
     *
     * @param event       @NonNull event to post.
     * @param miliseconds delay in miliseconds
     * @throws NullPointerException if the event is null.
     */
    public void postDelayed(@NonNull final Object event, long miliseconds) {
        mDispatcherThreadHandler.postDelayed(Dispatcher.obtain(this, event, null), miliseconds);
    }

    /**
     * Posts an event to all registered subscribers in one specific target object, after given number of milliseconds.
     * This method will initiate posting process, and return immediately.
     * <p/>
     * If no subscribers have been subscribed for {@code event}'s class, and {@code event} is not already a
     * {@link DeadEvent}, it will be wrapped in a DeadEvent and re-posted.
     *
     * @param event        @NonNull event to post.
     * @param target       @NonNull target to deliver event to. Target must be registered in bus already.
     * @param milliseconds delay in milliseconds
     * @throws NullPointerException if the event is null.
     * todo unit test
     */
    public void postToTargetDelayed(@NonNull final Object event, @NonNull Object target, long milliseconds) {
        mDispatcherThreadHandler.postDelayed(Dispatcher.obtain(this, event, target), milliseconds);
    }

    /**
     * Send an event to all registered subscribers.
     * This method will process all dispatch code in caller thread. It means that Thread.DISPATCHER subscribers
     * will have to finish before control will be returned to caller.
     * <p/>
     * If no subscribers have been subscribed for {@code event}'s class, and {@code event} is not already a
     * {@link DeadEvent}, it will be wrapped in a DeadEvent and reposted.
     *
     * @param event @NonNull event to post.
     * @throws NullPointerException if the event is null.
     */
    public void send(@NonNull final Object event) {
        Dispatcher.obtain(this, event, null).run();
    }

    /**
     * Send an event to all registered subscribers in one specific target object.
     * This method will process all dispatch code in caller thread. It means that Thread.DISPATCHER subscribers
     * will have to finish before control will be returned to caller.
     * <p/>
     * If no subscribers have been subscribed for {@code event}'s class, and {@code event} is not already a
     * {@link DeadEvent}, it will be wrapped in a DeadEvent and re-posted.
     *
     * @param event  @NonNull event to post.
     * @param target @NonNull target to deliver event to. Target must be registered in bus already.
     * @throws NullPointerException if the event is null.
     * todo unit test
     */
    public void sendToTarget(@NonNull final Object event, @NonNull Object target) {
        Dispatcher.obtain(this, event, target).run();
    }


    //////////////////////////////////////////////////////////////////////////////////////////
    // implementation

    /**
     * Main, UI thread Handler.
     * Some subscribers have to be called in this thread.
     * Object shared by all Bus instances, there is just one main thread anyway ;-).
     */
    static final Handler mUiThreadHandler = new Handler(Looper.getMainLooper());

    /**
     * Thread for events processing.
     * Event passed to post() will be processed in dispatcher thread, then delivered in this thread,
     * or background thread, or UI thread.
     */
    Handler mDispatcherThreadHandler;

    java.util.concurrent.Executor mBackgroundExecutor;

    /**
     * Identifier used to differentiate the event bus instance.
     */
    final String mName;

    /**
     * All registered subscribers, indexed by event type.
     * Inner Set is a CopyOnWriteArraySet.
     */
    final Map<Class<?>, Set<Subscriber>> mCurrentlyRegisteredSubscribersByEventType = new HashMap<Class<?>, Set<Subscriber>>();

    /**
     * Cache for all classes/interfaces of given event class.
     * Key: event class
     * Value: set of classes/interfaces
     */
    final static Map<Class<?>, Set<Class<?>>> mEventClassHierarchyCache = new HashMap<Class<?>, Set<Class<?>>>();

    /**
     * target-to-looper map.
     */
    final static Map<Object, WeakReference<Looper>> mTargetsLoopers = new WeakHashMap<Object, WeakReference<Looper>>();

    /**
     * looper-to-handler map.
     */
    final static Map<Looper, Handler> mLoopersHandlers = new WeakHashMap<Looper, Handler>();

    @NonNull
    Set<Subscriber> getSubscribersForEventType(Class<?> type) {
        synchronized (mCurrentlyRegisteredSubscribersByEventType) {
            Set<Subscriber> result = mCurrentlyRegisteredSubscribersByEventType.get(type);
            if (result == null) {
                result = new CopyOnWriteArraySet<Subscriber>();
                mCurrentlyRegisteredSubscribersByEventType.put(type, result);
            }
            return result;
        }
    }

    /**
     * Cache event bus subscriber methods for each class.
     * This will speed-up registering another objects of given class.
     * <p/>
     * First key: listener class
     * Second key: event class
     * Value: set of Method's
     */
    static final Map<Class<?>, Map<Class<?>, Set<Method>>> mSubscriberMethodsCache = new HashMap<Class<?>, Map<Class<?>, Set<Method>>>();

    @Override
    public String toString() {
        return "messagebus[" + mName + "]";
    }

    /**
     * Load all methods annotated with {@link Subscribe} into their respective caches for the
     * specified class.
     */
    static void scanForSubscriberMethods(@NonNull final Class<?> listenerClass) {
        Map<Class<?>, Set<Method>> subscriberMethods = new HashMap<Class<?>, Set<Method>>();

        for (Method method : listenerClass.getDeclaredMethods()) {
            // The compiler sometimes creates synthetic bridge methods as part of the
            // type erasure process. As of JDK8 these methods now include the same
            // annotations as the original declarations. They should be ignored for
            // subscribe/produce.
            if (method.isBridge()) {
                continue;
            }
            if (method.isAnnotationPresent(Subscribe.class)) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 1) {
                    throw new IllegalArgumentException("Method " + method + " has @Subscribe annotation but requires "
                            + parameterTypes.length + " arguments.  Methods must require a single argument.");
                }

                Class<?> eventType = parameterTypes[0];

                Set<Method> methods = subscriberMethods.get(eventType);
                if (methods == null) {
                    methods = new HashSet<Method>();
                    subscriberMethods.put(eventType, methods);
                }
                methods.add(method);
            }
        }

        mSubscriberMethodsCache.put(listenerClass, subscriberMethods);
    }

    /**
     * This method finds all methods marked with a {@link Subscribe} annotation in passed object.
     * Returned map Key is event class, value is set of Subscriber's that can handle this event class.
     */
    @NonNull
    static Map<Class<?>, Set<Subscriber>> findAllSubscribers(@NonNull final Object listener) {
        Class<?> listenerClass = listener.getClass();
        Map<Class<?>, Set<Subscriber>> subscribersByEventType = new HashMap<Class<?>, Set<Subscriber>>();

        synchronized (mSubscriberMethodsCache) {
            if (!mSubscriberMethodsCache.containsKey(listenerClass)) {
                scanForSubscriberMethods(listenerClass);
            }

            //Key: event class
            //Value: set of Subscriber's that can handle this event class.
            Map<Class<?>, Set<Method>> methods = mSubscriberMethodsCache.get(listenerClass);
            for (Map.Entry<Class<?>, Set<Method>> e : methods.entrySet()) {
                Set<Subscriber> subscribers = new HashSet<Subscriber>();
                for (Method m : e.getValue()) {
                    subscribers.add(new Subscriber(listener, m));
                }
                subscribersByEventType.put(e.getKey(), subscribers);
            }
        }
        return subscribersByEventType;
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


    /**
     * Finds Handler related to given subscriber target object.
     * Used only for DeliveryThread.DISPATCHER.
     */
    @Nullable
    static Handler getHandlerForTarget(@NonNull Object target) {
        WeakReference<Looper> ref = mTargetsLoopers.get(target);
        if (ref == null) return null;
        //thread safety: first get solid reference, THEN check for null.
        Looper looper = ref.get();
        if (looper == null) return null;
        return mLoopersHandlers.get(looper);
    }

    /**
     * Executes Runnable on mBackgroundExecutor.
     * If mBackgroundExecutor is not set, creates instance of EagerThreadPoolExecutor(true, 2, 8, 10, TimeUnit.SECONDS, internal thread factory)
     *
     * @param runnable Runnable to execute
     */
    void executeInBackground(Runnable runnable) {
        mBackgroundExecutor.execute(runnable);
    }

}
