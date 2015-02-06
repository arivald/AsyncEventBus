package com.pgssoft.async_event_bus.mock;

import com.pgssoft.async_event_bus.EventBus;
import com.pgssoft.async_event_bus.Subscribe;
import com.pgssoft.async_event_bus_app.Event1;

/**
 * Strange, but to make the message bus test works on my Samsung devices, the target class have to
 * be defined in the main sources, the main APK, or have to be derived from main sources/APK class.
 * If it is not, Method.getAttribute() method in the message bus will throw an error:
 * java.lang.IllegalAccessError: Class ref in pre-verified class resolved to unexpected implementation
 *
 * This is probably an error in the Samsung Android version.
 * SDK is supposed to merge the namespace of the main APK and the test APK. In all cases so far
 * it worked flawlessly.
 *
 * I choose Event1 as base, because it is no-code class.
 */
@SuppressWarnings("UnusedDeclaration")
public class TestTarget1 extends Event1 {
    public TestEvent1 lastReceivedEvent1 = null;
    public TestEvent2 lastReceivedEvent2 = null;
    public TestEvent3 lastReceivedEvent3 = null;
    public TestInterfaceEvent1 lastReceivedInterfaceEvent1 = null;

    @Subscribe(EventBus.DeliveryThread.DISPATCHER)
    public void onTestEvent1(TestEvent1 event) {
        lastReceivedEvent1 = event;
    }

    @Subscribe(EventBus.DeliveryThread.DISPATCHER)
    /* package */ void onTestEvent2(TestEvent2 event) {
        lastReceivedEvent2 = event;
    }

    @Subscribe(EventBus.DeliveryThread.DISPATCHER)
    protected void onTestEvent3(TestEvent3 event) {
        lastReceivedEvent3 = event;
    }

    @Subscribe(EventBus.DeliveryThread.DISPATCHER)
    private void onTestInterfaceEvent1(TestInterfaceEvent1 event) {
        lastReceivedInterfaceEvent1 = event;
    }
}