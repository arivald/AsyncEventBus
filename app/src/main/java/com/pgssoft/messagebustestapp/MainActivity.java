package com.pgssoft.messagebustestapp;

import android.app.Activity;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import com.pgssoft.async_event_bus.EventBus;
import com.pgssoft.async_event_bus.Subscribe;


public class MainActivity extends Activity implements View.OnClickListener {

    static final EventBus M_EVENT_BUS = new EventBus();

    TextView out;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        out = (TextView) findViewById(R.id.out);

        findViewById(R.id.clear).setOnClickListener(this);
        findViewById(R.id.post1).setOnClickListener(this);
        findViewById(R.id.post2).setOnClickListener(this);
        findViewById(R.id.send1).setOnClickListener(this);
        findViewById(R.id.send2).setOnClickListener(this);
        findViewById(R.id.post_delayed1).setOnClickListener(this);
        findViewById(R.id.post_delayed2).setOnClickListener(this);

        //register in bus
        M_EVENT_BUS.register(this);

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        //not required, but good practice
        M_EVENT_BUS.unregister(this);
    }

    void appendTextToOut(final String text) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            out.append(text);
            out.scrollTo(0, Integer.MAX_VALUE);
        } else
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    appendTextToOut(text);
                }
            });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.clear:
                out.setText("");
                break;

            case R.id.post1:
                appendTextToOut("Asynchronous posting: Event1\n");
                M_EVENT_BUS.post(new Event1());
                break;

            case R.id.post2:
                appendTextToOut("Asynchronous posting: Event2\n");
                M_EVENT_BUS.post(new Event2());
                break;

            case R.id.send1:
                appendTextToOut("Synchronous sending: Event1\n");
                M_EVENT_BUS.send(new Event1());
                break;

            case R.id.send2:
                appendTextToOut("Synchronous sending: Event2\n");
                M_EVENT_BUS.send(new Event2());
                break;

            case R.id.post_delayed1:
                appendTextToOut("Asynchronous delayed posting: Event1\n");
                M_EVENT_BUS.postDelayed(new Event1(), 2000);
                break;

            case R.id.post_delayed2:
                appendTextToOut("Asynchronous delayed posting: Event2\n");
                M_EVENT_BUS.postDelayed(new Event2(), 2000);
                break;
        }
    }


    /*
     Note: because Bus.register() was called in UI thread, by default events are delivered in UI thread.
     */
    @Subscribe
    void onEvent1_Standard(Event1 event) {
        appendTextToOut("onEvent1_Standard [thread: " + Thread.currentThread().getName() + "] event class: " + event.getClass().getSimpleName() + "\n");
    }

    /*
     Note: this will be called in dispatcher thread, if event was posted using Bus.post() or Bus.postdelayed(),
     or in sender thread, if event was send using Bus.send().
     */
    @Subscribe(EventBus.DeliveryThread.DISPATCHER)
    void onEvent1_Short(Event1 event) {
        appendTextToOut("onEvent1_Short [thread: " + Thread.currentThread().getName() + "] event class: " + event.getClass().getSimpleName() + "\n");
    }

    /*
     This will always be executed in background, unless Executor You passed to Bus choose differently.
     */
    @Subscribe(EventBus.DeliveryThread.BACKGROUND)
    void onEvent1_LongBackground(Event1 event) throws InterruptedException {
        appendTextToOut("onEvent1_LongBackground [thread: " + Thread.currentThread().getName() + "] event class: " + event.getClass().getSimpleName() + "\n");
        int count = 4;
        while (count-- > 0) {
            Thread.sleep(500);
            appendTextToOut("onEvent1_LongBackground [ " + count + " ] event class: " + event.getClass().getSimpleName() + "\n");
        }
    }

    ////////////////////
    // Event 2 handlers - Event 2 extends Event 1, so all Event1 handlers also will be triggered!

    @Subscribe
    void onEvent2_Short(Event2 event) {
        appendTextToOut("onEvent2_Short [thread: " + Thread.currentThread().getName() + "] event class: " + event.getClass().getSimpleName() + "\n");
    }

    @Subscribe(EventBus.DeliveryThread.BACKGROUND)
    void onEvent2_LongBackground(Event2 event) throws InterruptedException {
        appendTextToOut("onEvent2_LongBackground [thread: " + Thread.currentThread().getName() + "] event class: " + event.getClass().getSimpleName() + "\n");
        int count = 6;
        while (count-- > 0) {
            Thread.sleep(500);
            appendTextToOut("onEvent2_LongBackground [ " + count + " ] event class: " + event.getClass().getSimpleName() + "\n");
        }
    }


}
