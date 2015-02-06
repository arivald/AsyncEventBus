package com.pgssoft.async_event_bus;

import android.os.Looper;
import android.support.annotation.NonNull;
import android.test.InstrumentationTestCase;

import com.pgssoft.async_event_bus.mock.Reference;
import com.pgssoft.async_event_bus.mock.TestEvent1;
import com.pgssoft.async_event_bus.mock.TestEvent2;
import com.pgssoft.async_event_bus.mock.TestEvent3;
import com.pgssoft.async_event_bus.mock.TestInterfaceEvent1;
import com.pgssoft.async_event_bus.mock.TestTarget1;
import com.pgssoft.async_event_bus.mock.TestTarget2;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Messagebus Subscriber tests
 * <p/>
 * The Subscriber is package accessible, so it have to be tested inside package.
 * <p/>
 * Created by lplominski on 2014-10-10.
 */
public class EventBusTest extends InstrumentationTestCase {

    @Override
    public void setUp() throws Exception {
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testFindAllSubscribersAndScanForSubscriberMethods() throws Exception {

        TestTarget1 testTarget1 = new TestTarget1();

        //Key: event class
        //Value: set of Subscriber's that can handle this event class.
        Map<Class<?>, Set<Subscriber>> found = EventBus.findAllSubscribers(testTarget1);
        Set<Subscriber> subscribers;

        //TestEvent1
        assertTrue(found.containsKey(TestEvent1.class));
        subscribers = found.get(TestEvent1.class);
        assertEquals(1, subscribers.size());
        assertSame(testTarget1, subscribers.iterator().next().mTarget.get());
        assertEquals("onTestEvent1", subscribers.iterator().next().mMethod.getName());

        //TestEvent2
        assertTrue(found.containsKey(TestEvent2.class));
        subscribers = found.get(TestEvent2.class);
        assertEquals(1, subscribers.size());
        assertSame(testTarget1, subscribers.iterator().next().mTarget.get());
        assertEquals("onTestEvent2", subscribers.iterator().next().mMethod.getName());

        //TestEvent3
        assertTrue(found.containsKey(TestEvent3.class));
        subscribers = found.get(TestEvent3.class);
        assertEquals(1, subscribers.size());
        assertSame(testTarget1, subscribers.iterator().next().mTarget.get());
        assertEquals("onTestEvent3", subscribers.iterator().next().mMethod.getName());

        //TestInterfaceEvent1
        assertTrue(found.containsKey(TestInterfaceEvent1.class));
        subscribers = found.get(TestInterfaceEvent1.class);
        assertEquals(1, subscribers.size());
        assertSame(testTarget1, subscribers.iterator().next().mTarget.get());
        assertEquals("onTestInterfaceEvent1", subscribers.iterator().next().mMethod.getName());

        //List returned from findAllSubscribers() MUST not be affected by sub-sequent changes to Bus.
    }

    public void testRegisterUnregisterAndGetSubscribersForEventType() throws Exception {

        EventBus eventBus = new EventBus();
        TestTarget1 testTarget1 = new TestTarget1();
        TestTarget1 testTarget2 = new TestTarget1();
        Set<Subscriber> found;


        eventBus.register(testTarget1);


        found = eventBus.getSubscribersForEventType(TestEvent1.class);
        assertEquals(1, found.size());

        found = eventBus.getSubscribersForEventType(TestEvent2.class);
        assertEquals(1, found.size());

        found = eventBus.getSubscribersForEventType(TestEvent3.class);
        assertEquals(1, found.size());

        found = eventBus.getSubscribersForEventType(TestInterfaceEvent1.class);
        assertEquals(1, found.size());


        eventBus.unregister(testTarget1);


        found = eventBus.getSubscribersForEventType(TestEvent1.class);
        assertEquals(0, found.size());

        found = eventBus.getSubscribersForEventType(TestEvent2.class);
        assertEquals(0, found.size());

        found = eventBus.getSubscribersForEventType(TestEvent3.class);
        assertEquals(0, found.size());

        found = eventBus.getSubscribersForEventType(TestInterfaceEvent1.class);
        assertEquals(0, found.size());


        eventBus.register(testTarget1);
        eventBus.register(testTarget2);


        found = eventBus.getSubscribersForEventType(TestEvent1.class);
        assertEquals(2, found.size());

        found = eventBus.getSubscribersForEventType(TestEvent2.class);
        assertEquals(2, found.size());

        found = eventBus.getSubscribersForEventType(TestEvent3.class);
        assertEquals(2, found.size());

        found = eventBus.getSubscribersForEventType(TestInterfaceEvent1.class);
        assertEquals(2, found.size());
    }

    public void testDelivery() throws Exception {
        EventBus eventBus = new EventBus();
        TestTarget1 testTarget1 = new TestTarget1();
        TestTarget1 testTarget2 = new TestTarget1();

        TestEvent1 testEvent1 = new TestEvent1();
        TestEvent2 testEvent2 = new TestEvent2();
        TestEvent3 testEvent3 = new TestEvent3();

        eventBus.register(testTarget1);
        eventBus.register(testTarget2);

        //case 1 TestEvent1
        //note: use send(), because it will dispatch in this thread, while all subscribers in TestTarget1
        //are Bus.DISPATCHER_THREAD, so all processing will be in this thread;
        eventBus.send(testEvent1);

        assertSame(testEvent1, testTarget1.lastReceivedEvent1);
        assertSame(testEvent1, testTarget2.lastReceivedEvent1);

        assertNull(testTarget1.lastReceivedEvent2);
        assertNull(testTarget1.lastReceivedEvent3);
        assertNull(testTarget1.lastReceivedInterfaceEvent1);

        //cleanup
        testTarget1.lastReceivedEvent1 = null;
        testTarget2.lastReceivedEvent1 = null;


        //case 2 TestEvent2
        eventBus.send(testEvent2);

        assertSame(testEvent2, testTarget1.lastReceivedEvent2);
        assertSame(testEvent2, testTarget1.lastReceivedInterfaceEvent1);
        assertSame(testEvent2, testTarget2.lastReceivedEvent2);
        assertSame(testEvent2, testTarget2.lastReceivedInterfaceEvent1);

        assertNull(testTarget1.lastReceivedEvent1);
        assertNull(testTarget1.lastReceivedEvent3);

        //cleanup
        testTarget1.lastReceivedEvent2 = null;
        testTarget1.lastReceivedInterfaceEvent1 = null;
        testTarget2.lastReceivedEvent2 = null;
        testTarget2.lastReceivedInterfaceEvent1 = null;

        //case 3 TestEvent3
        eventBus.send(testEvent3);

        assertSame(testEvent3, testTarget1.lastReceivedEvent1);
        assertSame(testEvent3, testTarget1.lastReceivedEvent3);
        assertSame(testEvent3, testTarget2.lastReceivedEvent1);
        assertSame(testEvent3, testTarget2.lastReceivedEvent3);

        assertNull(testTarget1.lastReceivedEvent2);
        assertNull(testTarget1.lastReceivedInterfaceEvent1);

        //cleanup
        testTarget1.lastReceivedEvent1 = null;
        testTarget1.lastReceivedEvent3 = null;
        testTarget2.lastReceivedEvent1 = null;
        testTarget2.lastReceivedEvent3 = null;
    }

    volatile Throwable theException;
    public void testExceptionHandling() throws Exception {

        EventBus eventBus = new EventBus() {
            protected void onSubscriberException(@NonNull Object target, @NonNull Method method, @NonNull Throwable exception) {
                theException = exception;
            }
        };
        TestTarget2 testTarget2 = new TestTarget2();

        TestEvent2 testEvent2 = new TestEvent2();
        TestEvent3 testEvent3 = new TestEvent3();

        eventBus.register(testTarget2);

        //case 1 : one of delivery methods that involve ExecutorRunnable - shared code path
        theException = null;
        //post TestEvent2, should trigger testTarget2.onTestEvent2Exception()
        eventBus.send(testEvent2);

        //wait a bit
        long timeLimit = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
        while (theException == null
                && timeLimit > System.currentTimeMillis()) {
            Thread.sleep(100);
        }

        assertNotNull(theException);
        assertSame(theException.getClass(), RuntimeException.class);
        assertEquals(theException.getMessage(), "onTestEvent2Exception");

        //case 2 : in DISPATCHER thread
        theException = null;
        //post TestEvent2, should trigger testTarget2.onTestEvent3Exception()
        eventBus.send(testEvent3);

        //no waiting, we used send() and onTestEvent3Exception is DISPATCHER, so should be executed
        //before send() returned

        assertNotNull(theException);
        assertSame(theException.getClass(), RuntimeException.class);
        assertEquals(theException.getMessage(), "onTestEvent3Exception");

    }

    DeadEvent deadEvent = null;

    @SuppressWarnings("UnusedDeclaration")
    @Subscribe(EventBus.DeliveryThread.DISPATCHER)
    void onDeadEvent(DeadEvent event) {
        deadEvent = event;
    }

    public void testWeakReferenceToTargets() throws Exception {
        deadEvent = null;
        EventBus eventBus = new EventBus();
        TestTarget1 testTarget1 = new TestTarget1();
        WeakReference<TestTarget1> weakTarget = new WeakReference<TestTarget1>(testTarget1);
        TestEvent1 testEvent1 = new TestEvent1();
        eventBus.register(testTarget1);
        eventBus.register(this); //for DeadEvent

        //first make sure it delivers corectly
        //note: use send(), because it will dispatch in this thread, while all subscribers in TestTarget1
        //are Bus.DISPATCHER, so all processing will be in this thread;
        eventBus.send(testEvent1);

        assertNull(deadEvent);
        assertSame(testEvent1, testTarget1.lastReceivedEvent1);

        assertNull(testTarget1.lastReceivedEvent2);
        assertNull(testTarget1.lastReceivedEvent3);
        assertNull(testTarget1.lastReceivedInterfaceEvent1);

        //now force GC
        //noinspection UnusedAssignment
        testTarget1 = null;
        long timeLimit = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
        while (weakTarget.get() != null && timeLimit > System.currentTimeMillis()) {
            System.gc();
            Thread.sleep(100);
        }
        assertNull(weakTarget.get());

        //check delivery again, should not work, and should not raise exceptions
        //because no other target is registered for this event, DeadEvent should be send
        eventBus.send(testEvent1);

        assertNotNull(deadEvent);
        assertSame(testEvent1, deadEvent.event);
        assertSame(eventBus, deadEvent.source);
    }

    Runnable testHandlerRunnableUI = null;

    @SuppressWarnings("UnusedDeclaration")
    @Subscribe(EventBus.DeliveryThread.UI)
    void onUITestHandler(EventBusTest event) {
        if (testHandlerRunnableUI != null)
            testHandlerRunnableUI.run();
    }

    Runnable testHandlerRunnableBKG = null;

    @SuppressWarnings("UnusedDeclaration")
    @Subscribe(EventBus.DeliveryThread.BACKGROUND)
    void onBackgroundTestHandler(EventBusTest event) {
        if (testHandlerRunnableBKG != null)
            testHandlerRunnableBKG.run();
    }

    public void testUIThreadDelivery() throws Exception {

        EventBus eventBus = new EventBus();
        TestTarget1 testTarget1 = new TestTarget1();
        final Reference<Boolean> isUIthread = new Reference<Boolean>(null);
        eventBus.register(testTarget1);
        eventBus.register(this); //for onTestHandler

        testHandlerRunnableUI = new Runnable() {
            @Override
            public void run() {
                isUIthread.ref = Looper.myLooper() == Looper.getMainLooper();
            }
        };

        eventBus.send(this);

        long timeLimit = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
        while (isUIthread.ref == null && timeLimit > System.currentTimeMillis()) {
            System.gc();
            Thread.sleep(100);
        }
        assertTrue(isUIthread.ref);

        testHandlerRunnableUI = null;
    }

    public void testBackgroundThreadDelivery() throws Exception {

        EventBus eventBus = new EventBus();
        TestTarget1 testTarget1 = new TestTarget1();
        final Reference<Boolean> isBkgThread = new Reference<Boolean>(null);
        eventBus.register(testTarget1);
        eventBus.register(this); //for onTestHandler

        testHandlerRunnableBKG = new Runnable() {
            @Override
            public void run() {
                isBkgThread.ref = Looper.myLooper() != Looper.getMainLooper()
                        && Thread.currentThread().getName().contains("Background ");
            }
        };

        eventBus.send(this);

        long timeLimit = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
        while (isBkgThread.ref == null && timeLimit > System.currentTimeMillis()) {
            System.gc();
            Thread.sleep(100);
        }
        assertTrue(isBkgThread.ref);

        testHandlerRunnableBKG = null;
    }

    public void testDeliveryInSequence() throws Exception {
        //bus have to deliver events in same order as posted, with exception foe completely async DeliveryThread.BACKGROUND
        final int COUNT = 100 * 100;
        EventBus eventBus = new EventBus();
        TestTarget2 testTarget = new TestTarget2();

        List<Object> sequence = new ArrayList<Object>();
        for (int i = 0; i < COUNT; i++)
            sequence.add(new TestEvent1());


        eventBus.register(testTarget);

        //post sequence
        for (int i = 0; i < COUNT; i++)
            eventBus.post(sequence.get(i));

        //wait a bit for delivery
        long timeLimit = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
        while (testTarget.eventsDispatcher.size() < COUNT
                && testTarget.eventsUI.size() < COUNT
                && timeLimit > System.currentTimeMillis()) {
            Thread.sleep(100);
        }

        //check
        assertEquals(COUNT, testTarget.eventsDispatcher.size());
        for (int i = 0; i < COUNT; i++)
            assertSame(sequence.get(i), testTarget.eventsDispatcher.get(i));

        assertEquals(COUNT, testTarget.eventsUI.size());
        for (int i = 0; i < COUNT; i++)
            assertSame(sequence.get(i), testTarget.eventsUI.get(i));
    }


}
