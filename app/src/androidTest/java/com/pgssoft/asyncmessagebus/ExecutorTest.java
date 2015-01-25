package com.pgssoft.asyncmessagebus;

import android.test.InstrumentationTestCase;

import com.pgssoft.asyncmessagebus.mock.TestEvent1;
import com.pgssoft.asyncmessagebus.mock.TestTarget1;

import java.lang.reflect.Method;

/**
 * Messagebus Executor tests
 *
 * Created by lplominski on 2014-10-10.
 */
public class ExecutorTest extends InstrumentationTestCase {

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
        Method target1SubscriberMethod = TestTarget1.class.getMethod("onTestEvent1", TestEvent1.class);

        Subscriber test1Subscriber = new Subscriber(target1, target1SubscriberMethod);
        TestEvent1 testEvent1 = new TestEvent1();

        //test 1: obtaining executor in case when pool is empty, should create new one.
        Executor.mPool.clear();
        Executor obtained1 = Executor.obtain(test1Subscriber, testEvent1);
        assertNotNull(obtained1);
        assertEquals(0, Executor.mPool.size());
        assertSame(test1Subscriber, obtained1.mSubscriber);
        assertSame(testEvent1, obtained1.mEvent);

        //test 2 try to execute executor
        //it should deliver event, then clear executor fields, and return it to pool
        obtained1.run();
        //it should deliver event...
        assertSame(testEvent1, target1.lastReceivedEvent1);
        target1.lastReceivedEvent1 = null;

        //...then clear executor fields...
        assertNull(obtained1.mSubscriber);
        assertNull(obtained1.mEvent);

        //...and return it to pool
        assertEquals(1, Executor.mPool.size());
        assertTrue(Executor.mPool.contains(obtained1));


        //Next try to get Executor should return same object.
        Executor obtained2 = Executor.obtain(test1Subscriber, testEvent1);
        assertNotNull(obtained2);
        assertSame(obtained1, obtained2);
        assertEquals(0, Executor.mPool.size());
        assertSame(test1Subscriber, obtained2.mSubscriber);
        assertSame(testEvent1, obtained2.mEvent);
    }


}
