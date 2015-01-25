package com.pgssoft.asyncmessagebus;

import android.test.InstrumentationTestCase;

import com.pgssoft.asyncmessagebus.mock.TestEvent1;
import com.pgssoft.asyncmessagebus.mock.TestTarget1;

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
        Bus bus1 = new Bus("test");
        bus1.register(target1);

        //test 1: obtaining dispatcher in case when pool is empty, should create new one.
        Dispatcher.mPool.clear();
        Dispatcher obtained1 = Dispatcher.obtain(bus1, testEvent1, target1);
        assertNotNull(obtained1);
        assertEquals(0, Dispatcher.mPool.size());
        assertSame(bus1, obtained1.mBus);
        assertSame(testEvent1, obtained1.mEvent);
        assertSame(target1, obtained1.mSingleTarget);

        //test 2 try to execute dispatcher
        //it should deliver event, then clear dispatcher fields, and return it to pool
        obtained1.run();
        //it should deliver event...
        assertSame(testEvent1, target1.lastReceivedEvent1);
        target1.lastReceivedEvent1 = null;

        //...then clear dispatcher fields...
        assertNull(obtained1.mBus);
        assertNull(obtained1.mEvent);
        assertNull(obtained1.mSingleTarget);

        //...and return it to pool
        assertEquals(1, Dispatcher.mPool.size());
        assertTrue(Dispatcher.mPool.contains(obtained1));


        //Next try to get Dispatcher should return same object.
        Dispatcher obtained2 = Dispatcher.obtain(bus1, testEvent1, null);
        assertNotNull(obtained2);
        assertSame(obtained1, obtained2);
        assertEquals(0, Dispatcher.mPool.size());
        assertSame(bus1, obtained2.mBus);
        assertSame(testEvent1, obtained2.mEvent);

        //cleanup
        bus1.unregister(target1);
    }


}
