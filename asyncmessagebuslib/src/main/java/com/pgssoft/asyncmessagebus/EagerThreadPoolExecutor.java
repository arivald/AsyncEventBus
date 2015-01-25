package com.pgssoft.asyncmessagebus;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Java ThreadPoolExecutor first add task to "core" threads, then it adds tasks to queue,
 * then if queue is full it add more threads. It means if we use infinite queue, it will
 * never add more threads. If we use finite, small queue, in order to spawn more threads,
 * it become very likelly to raise RejectedExecutionException.
 * <p/>
 * As solution, there is adjusted version of ThreadPoolExecutor.
 * It creates threads eagerly, and adds tasks to queue only when all threads are occupied.
 * <p/>
 * Parts of code borrowed from javax.swing.SwingWorker
 * <p/>
 * Created by lplominski on 2014-10-17.
 */
public class EagerThreadPoolExecutor extends ThreadPoolExecutor {

    /**
     * Creates a new {@code EagerThreadPoolExecutor} with the given initial
     * parameters and default thread factory and rejected execution handler.
     * It will use LinkedBlockingQueue<Runnable> as queue.
     * It may be more convenient to use one of the {@link java.util.concurrent.Executors} factory
     * methods instead of this general purpose constructor.
     *
     * @param keepAlivePoolSize the number of threads to keep in the pool, even
     *                          if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize   the maximum number of threads to allow in the
     *                          pool
     * @param keepAliveTime     when the number of threads is greater than
     *                          the core, this is the maximum time that excess idle threads
     *                          will wait for new tasks before terminating.
     * @param unit              the time unit for the {@code keepAliveTime} argument
     * @throws IllegalArgumentException if one of the following holds:<br>
     *                                  {@code keepAlivePoolSize < 0}<br>
     *                                  {@code keepAliveTime < 0}<br>
     *                                  {@code maximumPoolSize <= 0}<br>
     *                                  {@code maximumPoolSize < keepAlivePoolSize}
     * @throws NullPointerException     if {@code workQueue} is null
     */
    @SuppressWarnings("UnusedDeclaration")
    public EagerThreadPoolExecutor(int keepAlivePoolSize,
                                   int maximumPoolSize,
                                   long keepAliveTime,
                                   TimeUnit unit) {
        super(keepAlivePoolSize, maximumPoolSize, keepAliveTime, unit, new LinkedBlockingQueue<Runnable>());
        init(keepAlivePoolSize);
    }

    /**
     * Creates a new {@code EagerThreadPoolExecutor} with the given initial
     * parameters and default rejected execution handler.
     * It will use LinkedBlockingQueue<Runnable> as queue.
     *
     * @param keepAlivePoolSize the number of threads to keep in the pool, even
     *                          if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize   the maximum number of threads to allow in the
     *                          pool
     * @param keepAliveTime     when the number of threads is greater than
     *                          the core, this is the maximum time that excess idle threads
     *                          will wait for new tasks before terminating.
     * @param unit              the time unit for the {@code keepAliveTime} argument
     * @param threadFactory     the factory to use when the executor
     *                          creates a new thread
     * @throws IllegalArgumentException if one of the following holds:<br>
     *                                  {@code keepAlivePoolSize < 0}<br>
     *                                  {@code keepAliveTime < 0}<br>
     *                                  {@code maximumPoolSize <= 0}<br>
     *                                  {@code maximumPoolSize < keepAlivePoolSize}
     * @throws NullPointerException     if {@code workQueue}
     *                                  or {@code threadFactory} is null
     */
    public EagerThreadPoolExecutor(int keepAlivePoolSize,
                                   int maximumPoolSize,
                                   long keepAliveTime,
                                   TimeUnit unit,
                                   ThreadFactory threadFactory) {
        super(keepAlivePoolSize, maximumPoolSize, keepAliveTime, unit, new LinkedBlockingQueue<Runnable>(), threadFactory);
        init(keepAlivePoolSize);
    }

    /**
     * Executes the given task sometime in the future.  The task
     * may execute in a new thread or in an existing pooled thread.
     * <p/>
     * If the task cannot be submitted for execution, either because this
     * executor has been shutdown or because its capacity has been reached,
     * the task is handled by the current {@code RejectedExecutionHandler}.
     *
     * @param command the task to execute
     * @throws java.util.concurrent.RejectedExecutionException at discretion of
     *                                                         {@code RejectedExecutionHandler}, if the task
     *                                                         cannot be accepted for execution
     * @throws NullPointerException                            if {@code command} is null
     */
    @Override
    public void execute(Runnable command) {
        /*
         * We need to change the order of the execution.
         * First try corePool then try maximumPool pool and only then store to the waiting
         * queue. We can not do that because we would need access to the private methods.
         *
         * Instead we enlarge corePool to mMaximumPoolSize before the execution and
         * shrink it back to mKeepAlivePoolSize after. It does pretty much what we need.
         *
         * While we changing the corePoolSize we need to stop running worker threads from
         * accepting new tasks (see afterExecute() method).
         */

        //we need atomicity for the execute method.
        mEecuteLock.lock();
        try {

            mPauseLock.lock();
            try {
                mIsPaused = true;
            } finally {
                mPauseLock.unlock();
            }

            super.setCorePoolSize(getMaximumPoolSize());
            super.execute(command);
            super.setCorePoolSize(mKeepAlivePoolSize);

            mPauseLock.lock();
            try {
                mIsPaused = false;
                mUnpaused.signalAll();
            } finally {
                mPauseLock.unlock();
            }
        } finally {
            mEecuteLock.unlock();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //implementation

    private int mKeepAlivePoolSize;

    private final ReentrantLock mPauseLock = new ReentrantLock();
    private final Condition mUnpaused = mPauseLock.newCondition();
    private boolean mIsPaused = false;
    private final ReentrantLock mEecuteLock = new ReentrantLock();

    protected void init(int keepAlivePoolSize) {
        mKeepAlivePoolSize = keepAlivePoolSize;
    }


    @Override
    public void setCorePoolSize(int corePoolSize) {
        super.setCorePoolSize(corePoolSize);
        mKeepAlivePoolSize = corePoolSize;
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        /*
         * While we changing the corePoolSize we need to stop running worker threads from
         * accepting new tasks.
         */
        super.afterExecute(r, t);
        mPauseLock.lock();
        try {
            while (mIsPaused) {
                mUnpaused.await();
            }
        } catch (InterruptedException ignore) {

        } finally {
            mPauseLock.unlock();
        }
    }
}
