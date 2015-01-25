package com.pgssoft.asyncmessagebus.mock;

import com.pgssoft.asyncmessagebus.Bus;
import com.pgssoft.asyncmessagebus.Subscribe;

/**
 * Strange, but to make the message bus works, the target class have to be defined in the main sources,
 * the main APK, or have to be derived from main sources/APK class. If it is not, Method.getAttribute()
 * method in the message bus will throw an error:
 * java.lang.IllegalAccessError: Class ref in pre-verified class resolved to unexpected implementation
 *
 * This is probably an error in the SDK or the Android.
 * SDK is supposed to merge the namespace of the main APK and the test APK. In all cases so far
 * it worked flawlessly.
 *
 * I choosed RequestCodes as base, because it is no-code class.
 */
public class TestTarget1 /*extends RequestCodes*/ {
    public TestEvent1 lastReceivedEvent1 = null;
    public TestEvent2 lastReceivedEvent2 = null;
    public TestEvent3 lastReceivedEvent3 = null;
    public TestInterfaceEvent1 lastReceivedInterfaceEvent1 = null;

    @Subscribe(Bus.DeliveryThread.DISPATCHER)
    public void onTestEvent1(TestEvent1 event) {
        lastReceivedEvent1 = event;
    }

    @Subscribe(Bus.DeliveryThread.DISPATCHER)
    /* package */ void onTestEvent2(TestEvent2 event) {
        lastReceivedEvent2 = event;
    }

    @Subscribe(Bus.DeliveryThread.DISPATCHER)
    protected void onTestEvent3(TestEvent3 event) {
        lastReceivedEvent3 = event;
    }

    @Subscribe(Bus.DeliveryThread.DISPATCHER)
    private void onTestInterfaceEvent1(TestInterfaceEvent1 event) {
        lastReceivedInterfaceEvent1 = event;
    }
}