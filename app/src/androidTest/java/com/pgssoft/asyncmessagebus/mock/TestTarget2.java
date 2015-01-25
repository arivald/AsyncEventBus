package com.pgssoft.asyncmessagebus.mock;

import com.pgssoft.asyncmessagebus.Bus;
import com.pgssoft.asyncmessagebus.Subscribe;
import com.pgssoft.messagebustestapp.Event1;

import java.util.ArrayList;
import java.util.List;


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
 * I choose Event1 as base, because it is no-code class.
 */
public class TestTarget2 extends Event1 {
    public List<Object> eventsDispatcher = new ArrayList<Object>();
    public List<Object> eventsUI = new ArrayList<Object>();

    @Subscribe(Bus.DeliveryThread.DISPATCHER)
    public void onTestEvent1Dis(TestEvent1 event) {
        eventsDispatcher.add(event);
    }

    @Subscribe(Bus.DeliveryThread.UI)
    public void onTestEvent1UI(TestEvent1 event) {
        eventsUI.add(event);
    }

}