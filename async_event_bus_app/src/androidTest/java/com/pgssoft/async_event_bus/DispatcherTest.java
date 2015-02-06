package com.pgssoft.async_event_bus;

import android.test.InstrumentationTestCase;

import com.pgssoft.async_event_bus.mock.TestEvent1;
import com.pgssoft.async_event_bus.mock.TestEvent2;
import com.pgssoft.async_event_bus.mock.TestEvent3;
import com.pgssoft.async_event_bus.mock.TestInterfaceEvent1;
import com.pgssoft.async_event_bus.mock.TestTarget1;

import java.util.Set;

/**
 * Messagebus Executor tests
 * <p/>
 * Created by lplominski on 2014-10-10.
 */
public class DispatcherTest extends InstrumentationTestCase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testExecutionAndLifecycle() throws Exception {
        //prepare
        TestTarget1 target1 = new TestTarget1();
        TestEvent1 testEvent1 = new TestEvent1();
        EventBus eventBus1 = new EventBus("test");
        eventBus1.register(target1);

        //test 1: obtaining dispatcher in case when pool is empty, should create new one.
        Dispatcher.mPool.clear();
        Dispatcher obtained1 = Dispatcher.obtain(eventBus1, testEvent1, target1);
        assertNotNull(obtained1);
        assertEquals(0, Dispatcher.mPool.size());
        assertSame(eventBus1, obtained1.mEventBus);
        assertSame(testEvent1, obtained1.mEvent);
        assertSame(target1, obtained1.mSingleTarget);

        //test 2 try to execute dispatcher
        //it should deliver event, then clear dispatcher fields, and return it to pool
        obtained1.run();
        //it should deliver event...
        assertSame(testEvent1, target1.lastReceivedEvent1);
        target1.lastReceivedEvent1 = null;

        //...then clear dispatcher fields...
        assertNull(obtained1.mEventBus);
        assertNull(obtained1.mEvent);
        assertNull(obtained1.mSingleTarget);

        //...and return it to pool
        assertEquals(1, Dispatcher.mPool.size());
        assertTrue(Dispatcher.mPool.contains(obtained1));


        //Next try to get Dispatcher should return same object.
        Dispatcher obtained2 = Dispatcher.obtain(eventBus1, testEvent1, null);
        assertNotNull(obtained2);
        assertSame(obtained1, obtained2);
        assertEquals(0, Dispatcher.mPool.size());
        assertSame(eventBus1, obtained2.mEventBus);
        assertSame(testEvent1, obtained2.mEvent);

        //cleanup
        eventBus1.unregister(target1);
    }

    public void testGetEventClasses() throws Exception {

        //first clear the cache, to ensure constant conditions
        Dispatcher.mEventClassHierarchyCache.clear();
        Set<Class<?>> classes;

        //now generate classes set for the test event1
        TestEvent1 testEvent1 = new TestEvent1();
        classes = Dispatcher.getEventClasses(testEvent1);
        assertTrue(classes.contains(Object.class));
        assertTrue(classes.contains(TestEvent1.class));

        //now generate classes set for the test event2
        TestEvent2 testEvent2 = new TestEvent2();
        classes = Dispatcher.getEventClasses(testEvent2);
        assertTrue(classes.contains(Object.class));
        assertTrue(classes.contains(TestInterfaceEvent1.class));
        assertTrue(classes.contains(TestEvent2.class));

        //now generate classes set for the test event2
        TestEvent3 testEvent3 = new TestEvent3();
        classes = Dispatcher.getEventClasses(testEvent3);
        assertTrue(classes.contains(Object.class));
        assertTrue(classes.contains(TestEvent1.class));
        assertTrue(classes.contains(TestEvent3.class));

        //test for the null
        try {
            //noinspection ConstantConditions
            Dispatcher.getEventClasses(null);
            fail("Should have throw");
        } catch (Throwable ignored) {
        }

        //test that the cache is working:
        assertNotNull(Dispatcher.mEventClassHierarchyCache.get(TestEvent1.class));
        assertNotNull(Dispatcher.mEventClassHierarchyCache.get(TestEvent2.class));
        assertNotNull(Dispatcher.mEventClassHierarchyCache.get(TestEvent3.class));

        assertSame(classes, Dispatcher.mEventClassHierarchyCache.get(TestEvent3.class));
    }


}
