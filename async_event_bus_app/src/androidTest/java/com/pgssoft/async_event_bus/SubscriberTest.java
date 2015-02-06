package com.pgssoft.async_event_bus;

import android.test.InstrumentationTestCase;

import com.pgssoft.async_event_bus.mock.TestEvent1;
import com.pgssoft.async_event_bus.mock.TestEvent2;
import com.pgssoft.async_event_bus.mock.TestTarget1;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Messagebus Subscriber tests
 * <p/>
 * The Subscriber is package accessible, so it have to be tested inside package.
 * <p/>
 * Created by lplominski on 2014-10-10.
 */
public class SubscriberTest extends InstrumentationTestCase {


    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testConstructorAndHashCode() throws Exception {

        EventBus bus = new EventBus("test");
        TestTarget1 testTarget1 = new TestTarget1();
        Method method = TestTarget1.class.getDeclaredMethod("onTestEvent1", TestEvent1.class);

        //constructor does not allow null parameters, should throw
        try {
            //noinspection ConstantConditions
            new Subscriber(null, method, method.getAnnotation(Subscribe.class).value());
            fail("Should have throw");
        } catch (Throwable ignored) {
        }
        try {
            //noinspection ConstantConditions
            new Subscriber(testTarget1, null, null);
            fail("Should have throw");
        } catch (Throwable ignored) {
        }

        //but will proper parameters it should not throw
        Subscriber subject = new Subscriber(testTarget1, method, method.getAnnotation(Subscribe.class).value());

        //test for weak reference
        //set testTarget1 to null, then call GC few times
        int oldHashCode = subject.hashCode();

        //noinspection UnusedAssignment
        testTarget1 = null;
        long timeLimit = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
        while (subject.mTarget.get() != null && timeLimit > System.currentTimeMillis()) {
            System.gc();
            Thread.sleep(100);
        }
        //reference should have been freed
        assertNull(subject.mTarget.get());
        //hash code must remain unchanged
        assertEquals(oldHashCode, subject.hashCode());
        //and deliverEvent should not try to deliver, nor throw.
        subject.deliverEvent(bus, new Object());
    }

    public void testEquality() throws Exception {

        //two subscribers created for same method and same target should be equal, and have same hashcode.

        TestTarget1 testTarget1 = new TestTarget1();
        TestTarget1 testTarget2 = new TestTarget1();

        Method method1 = TestTarget1.class.getDeclaredMethod("onTestEvent1", TestEvent1.class);
        Method method2 = TestTarget1.class.getDeclaredMethod("onTestEvent2", TestEvent2.class);

        Subscriber subject_1_1 = new Subscriber(testTarget1, method1, method1.getAnnotation(Subscribe.class).value());
        Subscriber subject_1_2 = new Subscriber(testTarget1, method2, method2.getAnnotation(Subscribe.class).value());
        Subscriber subject_2_1 = new Subscriber(testTarget2, method1, method1.getAnnotation(Subscribe.class).value());
        Subscriber subject_2_2 = new Subscriber(testTarget2, method2, method2.getAnnotation(Subscribe.class).value());

        Subscriber secondSubject_1_1 = new Subscriber(testTarget1, method1, method1.getAnnotation(Subscribe.class).value());

        assertTrue(secondSubject_1_1.equals(subject_1_1));
        assertTrue(subject_1_1.equals(secondSubject_1_1));
        assertTrue(subject_1_1.hashCode() == secondSubject_1_1.hashCode());

        assertFalse(subject_1_1.equals(subject_1_2));
        assertFalse(subject_1_1.equals(subject_2_1));
        assertFalse(subject_1_1.equals(subject_2_2));
        assertFalse(subject_1_2.equals(subject_2_1));
        assertFalse(subject_1_1.equals(subject_2_2));
        assertFalse(subject_2_1.equals(subject_2_2));
    }

    public void testEventDelivery() throws Exception {

        EventBus bus = new EventBus("test");

        TestTarget1 testTarget1 = new TestTarget1();
        Method method = TestTarget1.class.getDeclaredMethod("onTestEvent1", TestEvent1.class);
        TestEvent1 testEvent1 = new TestEvent1();
        Subscriber subject = new Subscriber(testTarget1, method, method.getAnnotation(Subscribe.class).value());

        subject.deliverEvent(bus, testEvent1);

        assertSame(testEvent1, testTarget1.lastReceivedEvent1);

        //try to deliver wrong event class
        subject.deliverEvent(bus, new TestEvent2());
    }


}
