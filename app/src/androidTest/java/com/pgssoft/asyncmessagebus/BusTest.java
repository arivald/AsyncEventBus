package com.pgssoft.asyncmessagebus;

import android.os.Looper;
import android.test.InstrumentationTestCase;

import com.pgssoft.asyncmessagebus.mock.Reference;
import com.pgssoft.asyncmessagebus.mock.TestEvent1;
import com.pgssoft.asyncmessagebus.mock.TestEvent2;
import com.pgssoft.asyncmessagebus.mock.TestEvent3;
import com.pgssoft.asyncmessagebus.mock.TestInterfaceEvent1;
import com.pgssoft.asyncmessagebus.mock.TestTarget1;
import com.pgssoft.asyncmessagebus.mock.TestTarget2;

import java.lang.ref.WeakReference;
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
public class BusTest extends InstrumentationTestCase {

    @Override
    public void setUp() throws Exception {
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testGetEventClasses() throws Exception {

        //first clear the cache, to ensure constant conditions
        Bus.mEventClassHierarchyCache.clear();
        Set<Class<?>> classes;

        //now generate classes set for the test event1
        TestEvent1 testEvent1 = new TestEvent1();
        classes = Bus.getEventClasses(testEvent1);
        assertTrue(classes.contains(Object.class));
        assertTrue(classes.contains(TestEvent1.class));

        //now generate classes set for the test event2
        TestEvent2 testEvent2 = new TestEvent2();
        classes = Bus.getEventClasses(testEvent2);
        assertTrue(classes.contains(Object.class));
        assertTrue(classes.contains(TestInterfaceEvent1.class));
        assertTrue(classes.contains(TestEvent2.class));

        //now generate classes set for the test event2
        TestEvent3 testEvent3 = new TestEvent3();
        classes = Bus.getEventClasses(testEvent3);
        assertTrue(classes.contains(Object.class));
        assertTrue(classes.contains(TestEvent1.class));
        assertTrue(classes.contains(TestEvent3.class));

        //test for the null
        try {
            //noinspection ConstantConditions
            Bus.getEventClasses(null);
            fail("Should have throw");
        } catch (Throwable ignored) {
        }

        //test that the cache is working:
        assertNotNull(Bus.mEventClassHierarchyCache.get(TestEvent1.class));
        assertNotNull(Bus.mEventClassHierarchyCache.get(TestEvent2.class));
        assertNotNull(Bus.mEventClassHierarchyCache.get(TestEvent3.class));

        assertSame(classes, Bus.mEventClassHierarchyCache.get(TestEvent3.class));
    }

    public void testFindAllSubscribersAndScanForSubscriberMethods() throws Exception {

        TestTarget1 testTarget1 = new TestTarget1();

        //Key: event class
        //Value: set of Subscriber's that can handle this event class.
        Map<Class<?>, Set<Subscriber>> found = Bus.findAllSubscribers(testTarget1);
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
    }

    public void testRegisterUnregisterAndGetSubscribersForEventType() throws Exception {

        Bus bus = new Bus();
        TestTarget1 testTarget1 = new TestTarget1();
        TestTarget1 testTarget2 = new TestTarget1();
        Set<Subscriber> found;


        bus.register(testTarget1);


        found = bus.getSubscribersForEventType(TestEvent1.class);
        assertEquals(1, found.size());

        found = bus.getSubscribersForEventType(TestEvent2.class);
        assertEquals(1, found.size());

        found = bus.getSubscribersForEventType(TestEvent3.class);
        assertEquals(1, found.size());

        found = bus.getSubscribersForEventType(TestInterfaceEvent1.class);
        assertEquals(1, found.size());


        bus.unregister(testTarget1);


        found = bus.getSubscribersForEventType(TestEvent1.class);
        assertEquals(0, found.size());

        found = bus.getSubscribersForEventType(TestEvent2.class);
        assertEquals(0, found.size());

        found = bus.getSubscribersForEventType(TestEvent3.class);
        assertEquals(0, found.size());

        found = bus.getSubscribersForEventType(TestInterfaceEvent1.class);
        assertEquals(0, found.size());


        bus.register(testTarget1);
        bus.register(testTarget2);


        found = bus.getSubscribersForEventType(TestEvent1.class);
        assertEquals(2, found.size());

        found = bus.getSubscribersForEventType(TestEvent2.class);
        assertEquals(2, found.size());

        found = bus.getSubscribersForEventType(TestEvent3.class);
        assertEquals(2, found.size());

        found = bus.getSubscribersForEventType(TestInterfaceEvent1.class);
        assertEquals(2, found.size());
    }

    public void testDelivery() throws Exception {
        Bus bus = new Bus();
        TestTarget1 testTarget1 = new TestTarget1();
        TestTarget1 testTarget2 = new TestTarget1();

        TestEvent1 testEvent1 = new TestEvent1();
        TestEvent2 testEvent2 = new TestEvent2();
        TestEvent3 testEvent3 = new TestEvent3();

        bus.register(testTarget1);
        bus.register(testTarget2);

        //case 1 TestEvent1
        //note: use send(), because it will dispatch in this thread, while all subscribers in TestTarget1
        //are Bus.DISPATCHER_THREAD, so all processing will be in this thread;
        bus.send(testEvent1);

        assertSame(testEvent1, testTarget1.lastReceivedEvent1);
        assertSame(testEvent1, testTarget2.lastReceivedEvent1);

        assertNull(testTarget1.lastReceivedEvent2);
        assertNull(testTarget1.lastReceivedEvent3);
        assertNull(testTarget1.lastReceivedInterfaceEvent1);

        //cleanup
        testTarget1.lastReceivedEvent1 = null;
        testTarget2.lastReceivedEvent1 = null;


        //case 2 TestEvent2
        bus.send(testEvent2);

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
        bus.send(testEvent3);

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

    DeadEvent deadEvent = null;

    @Subscribe(Bus.DeliveryThread.DISPATCHER)
    void onDeadEvent(DeadEvent event) {
        deadEvent = event;
    }

    public void testWeakReferenceToTargets() throws Exception {
        deadEvent = null;
        Bus bus = new Bus();
        TestTarget1 testTarget1 = new TestTarget1();
        WeakReference<TestTarget1> weakTarget = new WeakReference<TestTarget1>(testTarget1);
        TestEvent1 testEvent1 = new TestEvent1();
        bus.register(testTarget1);
        bus.register(this); //for DeadEvent

        //first make sure it delivers corectly
        //note: use send(), because it will dispatch in this thread, while all subscribers in TestTarget1
        //are Bus.DISPATCHER, so all processing will be in this thread;
        bus.send(testEvent1);

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
        bus.send(testEvent1);

        assertNotNull(deadEvent);
        assertSame(testEvent1, deadEvent.event);
        assertSame(bus, deadEvent.source);
    }

    Runnable testHandlerRunnableUI = null;

    @Subscribe(Bus.DeliveryThread.UI)
    void onUITestHandler(BusTest event) {
        if (testHandlerRunnableUI != null)
            testHandlerRunnableUI.run();
    }

    Runnable testHandlerRunnableBKG = null;

    @Subscribe(Bus.DeliveryThread.BACKGROUND)
    void onBackgroundTestHandler(BusTest event) {
        if (testHandlerRunnableBKG != null)
            testHandlerRunnableBKG.run();
    }

    public void testUIThreadDelivery() throws Exception {

        Bus bus = new Bus();
        TestTarget1 testTarget1 = new TestTarget1();
        final Reference<Boolean> isUIthread = new Reference<Boolean>(null);
        bus.register(testTarget1);
        bus.register(this); //for onTestHandler

        testHandlerRunnableUI = new Runnable() {
            @Override
            public void run() {
                isUIthread.ref = Looper.myLooper() == Looper.getMainLooper();
            }
        };

        bus.send(this);

        long timeLimit = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
        while (isUIthread.ref == null && timeLimit > System.currentTimeMillis()) {
            System.gc();
            Thread.sleep(100);
        }
        assertTrue(isUIthread.ref);

        testHandlerRunnableUI = null;
    }

    public void testBackgroundThreadDelivery() throws Exception {

        Bus bus = new Bus();
        TestTarget1 testTarget1 = new TestTarget1();
        final Reference<Boolean> isBkgThread = new Reference<Boolean>(null);
        bus.register(testTarget1);
        bus.register(this); //for onTestHandler

        testHandlerRunnableBKG = new Runnable() {
            @Override
            public void run() {
                isBkgThread.ref = Looper.myLooper() != Looper.getMainLooper()
                        && Thread.currentThread().getName().contains("Background ");
            }
        };

        bus.send(this);

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
        final int COUNT = 100;
        Bus bus = new Bus();
        TestTarget2 testTarget = new TestTarget2();

        List<Object> sequence = new ArrayList<Object>();
        for (int i = 0; i < COUNT; i++)
            sequence.add(new TestEvent1());


        bus.register(testTarget);

        //post sequence
        for (int i = 0; i < COUNT; i++)
            bus.post(sequence.get(i));

        //wait a bit for delivery
        long timeLimit = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
        while (testTarget.eventsDispatcher.size() < COUNT
                && testTarget.eventsUI.size() < COUNT
                && timeLimit > System.currentTimeMillis()) {
            System.gc();
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
