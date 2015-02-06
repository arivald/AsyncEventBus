package com.pgssoft.async_event_bus;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
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
public class EventBus {

    public enum DeliveryThread {
        /**
         * The subscriber will be called in UI thread
         */
        UI,
        /**
         * The subscriber prefer to be called in background thread (random thread from Executor pool)
         */
        BACKGROUND,
        /**
         * The subscriber prefer to be called in events dispatcher thread.
         * This is preferable for small and short subscribers.
         * Note: If the sender choose to dispatch in his own thread (by calling the EventBus.send()),
         * the subscriber will be called in the same thread.
         */
        DISPATCHER,
        /**
         * The subscriber must be called in the same thread that it registered itself to the bus.
         * The thread have to have an associated {@link android.os.Looper} (Looper.myLooper() should not be null)
         * at the time the register() is called.
         * If there is no {@link android.os.Looper}, this is equivalent of the DeliveryThread.BACKGROUND.
         * If object is registered in UI thread, this is effectively equivalent of the Thread.UI,
         * because UI thread always have a Looper.
         * Best to ue with the {@link android.os.HandlerThread}.
         * Note: this is the default delivery thread value for the @Subscribe annotation.
         */
        AS_REGISTERED,
    }

    /**
     * Creates a new EventBus named "default".
     */
    public EventBus() {
        this("default", null);
    }

    /**
     * Creates a new EventBus with the given {@code name}.
     *
     * @param name a brief name for this bus, for debugging purposes.
     */
    public EventBus(@NonNull String name) {
        this(name, null);
    }

    /**
     * Creates a new EventBus with the given {@code name} and Executor.
     *
     * @param name     a brief name for this bus, for debugging purposes.
     * @param executor executor to manage background threads. Pass null to use internal one.
     */
    public EventBus(@NonNull String name, @Nullable java.util.concurrent.Executor executor) {
        mName = name;
        new HandlerThread(toString() + ".dispatcher", android.os.Process.THREAD_PRIORITY_BACKGROUND) {
            @Override
            protected void onLooperPrepared() {
                mDispatcherThreadHandler = new Handler(getLooper());
            }
        }.start();

        if (executor != null) {
            mBackgroundExecutor = executor;
        } else {
            if (mDefaultExecutor == null)
                mDefaultExecutor = new EagerThreadPoolExecutor(
                        2, Math.max(2, Runtime.getRuntime().availableProcessors() * 2),
                        60, TimeUnit.SECONDS,
                        new ThreadFactory() {
                            private final AtomicInteger mCount = new AtomicInteger(1);

                            @Override
                            public Thread newThread(@NonNull Runnable r) {
                                Thread thread = new Thread(r, "EventBus.Background #" + mCount.getAndIncrement());
                                thread.setPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                                return thread;
                            }
                        });
            mBackgroundExecutor = mDefaultExecutor;
        }

        //wait for mDispatcherThreadHandler to be set
        while (mDispatcherThreadHandler == null) {
            Thread.yield();
        }
    }

    /**
     * Registers all subscriber methods on {@code target} to receive events.
     *
     * @param target @NonNull object whose subscriber methods should be registered.
     */
    public void register(@NonNull Object target) {
        assignThreadForTarget(target);

        //Key: event class
        //Value: set of Subscriber's that can handle this event class.
        for (Map.Entry<Class<?>, Set<Subscriber>> entry : findAllSubscribers(target).entrySet()) {
            getSubscribersForEventType(entry.getKey()).addAll(entry.getValue());
        }
    }

    /**
     * Assign given target object to thread calling this method.
     * Can re-assign default thread (AS_REGISTERED) for already registered object
     *
     * @param target @NonNull object to assign.
     *               todo unit test
     */
    public void assignThreadForTarget(@NonNull Object target) {
        Looper looper = Looper.myLooper();
        if (looper != null)
            synchronized (mTargetsLoopers) {
                //assign Looper for object
                mTargetsLoopers.put(target, new WeakReference<Looper>(looper));
                //create Handler for Looper, if it is not created yet.
                if (mLoopersHandlers.get(looper) == null) {
                    mLoopersHandlers.put(looper,
                            looper != Looper.getMainLooper()
                                    ? new Handler(looper) : mUiThreadHandler);
                }
            }
    }

    /**
     * Unregister all subscriber methods on a registered {@code target}.
     * While processing it will remove also all data for already garbage collected objects.
     *
     * @param target @Nullable object whose subscriber methods should be unregistered.
     *               Pass null ro remove just garbage collected objects.
     */
    public void unregister(@Nullable Object target) {
        synchronized (mCurrentlyRegisteredSubscribersByEventType) {
            for (Map.Entry<Class<?>, CopyOnWriteArraySet<Subscriber>> entry : mCurrentlyRegisteredSubscribersByEventType.entrySet()) {
                CopyOnWriteArraySet<Subscriber> subscribers = entry.getValue();
                for (Subscriber subscriber : subscribers) {
                    Object aTarget = subscriber.mTarget.get();
                    //aTarget == null > target was GCed.
                    if (aTarget == null || aTarget == target) {
                        subscribers.remove(subscriber);
                    }
                }
            }
        }

        //Todo remove looper and probably Handler
        //todo this can be delayed.
//        Looper looper =
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
     *                              todo unit test
     */
    public void postToTarget(@NonNull final Object event, @NonNull Object target) {
        mDispatcherThreadHandler.post(Dispatcher.obtain(this, event, target));
    }

    /**
     * Posts an event to all registered subscribers after given number of miliseconds. Target must be
     * registered after requested time passes, so it is possible to deliver to target that was not registered
     * while postDelayed() was called.
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
     * Posts an event to all registered subscribers in one specific target object, after given number
     * of milliseconds.Target must be registered after requested time passes, so it is possible to
     * deliver to target that was not registered while postDelayed() was called.
     * This method will initiate posting process, and return immediately.
     * <p/>
     * If no subscribers have been subscribed for {@code event}'s class, and {@code event} is not already a
     * {@link DeadEvent}, it will be wrapped in a DeadEvent and re-posted.
     *
     * @param event        @NonNull event to post.
     * @param target       @NonNull target to deliver event to. Target must be registered in bus already.
     * @param milliseconds delay in milliseconds
     * @throws NullPointerException if the event is null.
     *                              todo unit test
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
     *                              todo unit test
     */
    public void sendToTarget(@NonNull final Object event, @NonNull Object target) {
        Dispatcher.obtain(this, event, target).run();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    // for descendants

    /**
     * Handles the given exception thrown by a subscriber with the given context.
     */
    protected void onSubscriberException(@NonNull Object target, @NonNull Method method, @NonNull Throwable exception) {
        //by default just print it to log.
        exception.printStackTrace();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    // implementation

    /**
     * The UI thread Handler.
     * Some subscribers have to be called in this thread.
     * Object shared by all Bus instances, there is just one main thread anyway ;-).
     */
    static final Handler mUiThreadHandler = new Handler(Looper.getMainLooper());

    /**
     * A Thread for events processing.
     * Event passed to post() methods will be processed in this thread, then delivered in this thread,
     * or background thread, or UI thread.
     */
    //todo use one shared dispatcher thread
    Handler mDispatcherThreadHandler;

    /**
     * Executor responsible for managing background threads.
     */
    @NonNull
    final Executor mBackgroundExecutor;

    /**
     * one, shared instance of Executor, used as mBackgroundExecutor in case if application didn't provided any.
     */
    static Executor mDefaultExecutor;


    /**
     * Identifier used to differentiate the event bus instance.
     */
    final String mName;

    /**
     * All registered subscribers, indexed by event type.
     * Inner Set is a CopyOnWriteArraySet.
     */
    final Map<Class<?>, CopyOnWriteArraySet<Subscriber>> mCurrentlyRegisteredSubscribersByEventType = new HashMap<Class<?>, CopyOnWriteArraySet<Subscriber>>();

    /**
     * target-to-looper map.
     */
    final static Map<Object, WeakReference<Looper>> mTargetsLoopers = new WeakHashMap<Object, WeakReference<Looper>>();

    /**
     * looper-to-handler map.
     */
    final static Map<Looper, Handler> mLoopersHandlers = new WeakHashMap<Looper, Handler>();

    @NonNull
    CopyOnWriteArraySet<Subscriber> getSubscribersForEventType(Class<?> type) {
        synchronized (mCurrentlyRegisteredSubscribersByEventType) {
            CopyOnWriteArraySet<Subscriber> result = mCurrentlyRegisteredSubscribersByEventType.get(type);
            if (result == null) {
                result = new CopyOnWriteArraySet<Subscriber>();
                mCurrentlyRegisteredSubscribersByEventType.put(type, result);
            }
            return result;
        }
    }

    /**
     * Cache event bus subscriber methods for each registered class.
     * This will speed-up registering another objects of given class with any EventBus instance.
     * <p/>
     * First key: listener class
     * Second key: event class
     * Value: set of Method's
     */
    static final Map<Class<?>, Map<Class<?>, MethodDescLinkedListItem>> mSubscriberMethodsCache = new HashMap<Class<?>, Map<Class<?>, MethodDescLinkedListItem>>();

    @Override
    public String toString() {
        return "EventBus[" + mName + "]";
    }

    /**
     * Load all methods annotated with {@link Subscribe} into their respective caches for the
     * specified class.
     */
    static Map<Class<?>, MethodDescLinkedListItem> scanForSubscriberMethods(@NonNull final Class<?> listenerClass) {
        Map<Class<?>, MethodDescLinkedListItem> result = new HashMap<Class<?>, MethodDescLinkedListItem>();

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

                //most probably here would be 1 item only, no one register in one object many subscribers for same event...
                Class<?> eventType = parameterTypes[0];
                result.put(eventType, new MethodDescLinkedListItem(method, result.get(eventType)));
            }
        }

        return result;
    }

    /**
     * This method finds all methods marked with a {@link Subscribe} annotation in passed "target" object.
     * Returned map Key is event class, value is set of Subscriber's that can handle this event class.
     */
    @NonNull
    static Map<Class<?>, Set<Subscriber>> findAllSubscribers(@NonNull final Object target) {
        Class<?> targetClass = target.getClass();
        Map<Class<?>, MethodDescLinkedListItem> methods;
        synchronized (mSubscriberMethodsCache) {
            methods = mSubscriberMethodsCache.get(targetClass);
            if (methods == null) {
                methods = scanForSubscriberMethods(targetClass);
                mSubscriberMethodsCache.put(targetClass, methods);
            }
        }

        Map<Class<?>, Set<Subscriber>> result = new HashMap<Class<?>, Set<Subscriber>>();
        //Key: event class
        //Value: set of @Subscribe methods that can handle this event class.
        for (Map.Entry<Class<?>, MethodDescLinkedListItem> e : methods.entrySet()) {
            Set<Subscriber> subscribers = new HashSet<Subscriber>();
            for(MethodDescLinkedListItem methodDesc= e.getValue(); methodDesc != null; methodDesc = methodDesc.next) {
                subscribers.add(new Subscriber(target, methodDesc.method, methodDesc.thread));
            }
            result.put(e.getKey(), subscribers);
        }
        return result;
    }


    /**
     * Finds Handler related to given subscriber target object.
     * Used only for DeliveryThread.DISPATCHER.
     */
    @Nullable
    static Handler getHandlerForTarget(@NonNull Object target) {
        synchronized (mTargetsLoopers) {
            WeakReference<Looper> ref = mTargetsLoopers.get(target);
            if (ref == null) return null;
            return mLoopersHandlers.get(ref.get());
        }
    }

    /**
     * One Method with @Subscribe descriptor.
     * Linked list, done old way, for maximum efficiency and minimum memory footprint.
     */
    static class MethodDescLinkedListItem {
        final Method method;
        final DeliveryThread thread;
        MethodDescLinkedListItem next;

        MethodDescLinkedListItem(@NonNull Method method, @Nullable MethodDescLinkedListItem next) {
            this.method = method;
            this.thread = method.getAnnotation(Subscribe.class).value();
            this.next = next;
        }
    }

}
