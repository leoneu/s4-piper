/*
 * Copyright (c) 2011 Yahoo! Inc. All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *          http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License. See accompanying LICENSE file. 
 */
package io.s4.core;

import io.s4.base.Event;
import io.s4.core.gen.OverloadDispatcher;
import io.s4.core.gen.OverloadDispatcherGenerator;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import net.jcip.annotations.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.MapMaker;

/**
 * @author Leo Neumeyer
 * 
 *         Base class for implementing processing in S4. All instances are
 *         organized as follows. A PE prototype is a special type of instance
 *         that defines the topology of the graph and manages the creation and
 *         destruction of the actual instances that do the processing. PE
 *         instances are clones of the prototype. To initialize PE instance
 *         variables implement the {@link #onCreate()} method. Be aware that
 *         Class variables are simply copied to the clones, even references.
 */
public abstract class ProcessingElement implements Cloneable {

    private static final Logger logger = LoggerFactory
            .getLogger(ProcessingElement.class);

    protected App app;
    protected ConcurrentMap<String, ProcessingElement> peInstances;
    protected String id = ""; // PE instance id
    protected ProcessingElement pePrototype;
    private int outputIntervalInEvents = 0;
    private long outputIntervalInMilliseconds = 0;
    private int eventCount = 0;
    private Timer timer;
    private boolean isTimedOutput = false;
    private boolean isOutputOnEvent = true;
    private boolean isPrototype = true;
    private boolean isThreadSafe = false;
    private boolean isFirst = true;

    private transient OverloadDispatcher overloadDispatcher;
    
    protected ProcessingElement() {}

    /**
     * Create a PE prototype. The PE instance will never expire.
     * 
     * @param app
     *            the app that contains this PE
     */
    public ProcessingElement(App app) {
        OverloadDispatcherGenerator oldg = new OverloadDispatcherGenerator(this.getClass());
        Class<?> overloadDispatcherClass = oldg.generate();
        try {
            overloadDispatcher = (OverloadDispatcher) overloadDispatcherClass.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        this.app = app;
        app.addPEPrototype(this);

        peInstances = new MapMaker().makeMap();
        /*
         * Only the PE Prototype uses the constructor. The PEPrototype field
         * will be cloned by the instances and point to the prototype.
         */
        this.pePrototype = this;
    }

    /**
     * @return the app
     */
    public App getApp() {
        return app;
    }

    public int getNumPEInstances() {

        return peInstances.size();
    }

    /**
     * Set expiration. PE instances will be automatically removed once a fixed
     * duration has passed since the PE's last read or write access.
     * 
     * All existing PE instance will destroyed!
     * 
     * 
     * @param duration
     *            the fixed duration
     * @param timeUnit
     *            the time unit
     */
    public void setExpiration(long duration, TimeUnit timeUnit) {

        if (!isPrototype)
            return;

        peInstances = new MapMaker().expireAfterAccess(duration, timeUnit)
                .makeMap();
    }

    /**
     * @return the outputIntervalInEvents - the number of input events after
     *         which we call {@link #processOutputEvent(Event)}.
     */
    public int getOutputIntervalInEvents() {
        return outputIntervalInEvents;
    }

    /**
     * Set an event-based policy to call {@link #processOutputEvent(Event)}. The
     * output method will be called as follows:
     * 
     * <ul>
     * <li>An input event arrived.
     * <li>There have been <tt>N</tt> input events since the last time the
     * output method was called.
     * <li>The value of <tt>N</tt> is set by this method.
     * <li>When <tt>N=0</tt> the event-based policy is disabled.
     * <li>The event- and time-based policies are not mutually exclusive, they
     * can overlap.
     * </ul>
     * 
     * @param outputIntervalInEvents
     *            - the number of input events after which we call
     *            {@link #processOutputEvent(Event)}. Set to zero to stop
     *            calling the output method.
     */
    public void setOutputIntervalInEvents(int outputIntervalInEvents) {
        this.outputIntervalInEvents = outputIntervalInEvents;
    }

    /**
     * The method {@link #processOutputEvent(Event)} is called when an input
     * event arrives and the time since the last input event is greater than
     * this interval.
     * 
     * @param timeUnit
     * @return interval in timeUnit
     */
    public long getOutputInterval(TimeUnit timeUnit) {
        return timeUnit.convert(outputIntervalInMilliseconds,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Set a time-based policy to call {@link #processOutputEvent(Event)}. The
     * time based policy and the event-based policy are not mutually exclusive.
     * There are two modes:
     * 
     * <ol>
     * <li>When {@code onEvent==true} method {@link #processOutputEvent(Event)}
     * is only when an input event arrives *and* the time since the last input
     * event is greater than this interval.
     * 
     * <li>When {@code onEvent==false} method {@link #processOutputEvent(Event)}
     * is called periodically whether or not an input event has arrived. Because
     * the method will be called forever for every PE in the prototype you
     * should use this setting it with caution.
     * </ol>
     * 
     * If {@code interval==0} the time-based policy is disabled.
     * 
     * @param interval
     *            in timeUnit
     * @param timeUnit
     *            the timeUnit of interval
     * @param onEvent
     *            selects event-time policy mode.
     * 
     */
    public void setOutputInterval(long interval, TimeUnit timeUnit,
            boolean onEvent) {
        outputIntervalInMilliseconds = TimeUnit.MILLISECONDS.convert(interval,
                timeUnit);

        /* We only allow timers in the PE prototype, not in the instances. */
        if (!isPrototype)
            return;

        if (timer != null)
            timer.cancel();

        if (interval == 0) {
            isTimedOutput = false;
            return;
        }

        isOutputOnEvent = onEvent;
        timer = new Timer();
        timer.schedule(new PETask(), 0, outputIntervalInMilliseconds);
    }

    /**
     * Set to true if the concrete PE class has the {@link ThreadSafe}
     * annotation. The default is false (no annotation). In general, application
     * developers don't need to worry about thread safety in the concrete PEs.
     * In some cases the PE needs to be thread safe to avoid deadlocks. For
     * example , if the application graph has cycles and the queues are allowed
     * to block, then some critical PEs with multiple incoming streams need to
     * be made thread safe to avoid locking the entire PE instance.
     * 
     * @return true if the PE implementation is considered thread safe.
     */
    public boolean isThreadSafe() {
        return isThreadSafe;
    }

    protected void handleInputEvent(Event event) {

        Object object;
        if (isThreadSafe) {
            object = new Object(); // a dummy object TODO improve this.
        } else {
            object = this;
        }

        synchronized (object) {
            eventCount++;

            overloadDispatcher.dispatchInputEvent(this, event);

            if (isOutput()) {
                overloadDispatcher.dispatchOutputEvent(this, event);
            }
        }
    }

    private boolean isOutput() {

        /* Handle time-based policies. */
        if (isTimedOutput) {
            isTimedOutput = false;
            return true;
        }

        /*
         * Handle event-based policy. Output event at regular intervals based on
         * the number of input events.
         */
        if (outputIntervalInEvents > 0
                && (eventCount % outputIntervalInEvents == 0)) {
            return true;
        }

        return false;
    }

    /**
     * This method is called after a PE instance is created. Use it to
     * initialize fields that are PE instance specific. PE instances are created
     * using {#clone()}. Fields initialized in the class constructor are shared
     * by all PE instances.
     */
    abstract protected void onCreate();

    /**
     * This method is called before a PE instance is removed. Use it to close
     * resources and clean up.
     */
    abstract protected void onRemove();

    private void removeInstanceForKeyInternal(String id) {

        if (id == null)
            return;

        /* First let the PE instance clean after itself. */
        onRemove();

        /* Remove PE instance. */
        peInstances.remove(id);
    }

    protected void removeAll() {

        /* Close resources in prototype. */
        if (timer != null) {
            timer.cancel();
            logger.info("Timer stopped.");
        }

        /* Remove all the instances. */
        for (Map.Entry<String, ProcessingElement> entry : peInstances
                .entrySet()) {

            String key = entry.getKey();

            if (key != null)
                removeInstanceForKeyInternal(key);
        }

        /*
         * TODO: This object (the PE prototype) may still be referenced by other
         * objects at this point. For example a stream object may still be
         * referencing PEs.
         */
    }

    protected void close() {
        removeInstanceForKeyInternal(id);
    }

    /**
     * This method is designed to be used within the package. We make it
     * package-private. The returned instances are all in the same JVM. Do not
     * use it to access remote objects.
     */
    public ProcessingElement getInstanceForKey(String id) {

        /* Check if instance for key exists, otherwise create one. */
        ProcessingElement pe = peInstances.get(id);
        if (pe == null) {
            /* PE instance for key does not yet exist, cloning one. */
            pe = (ProcessingElement) this.clone();
            pe.isPrototype = false;
            if (isFirst)
                onCreateInternal(pe);

            /*
             * The thread safe method putIfAbsent will most likely return null.
             * However, in the rare event that a thread created the a PE with
             * the same key a microsecond before, then pe2 will return the
             * recently created object. In that case, we discard the second
             * clone. With this precaution, we avoid synchronizing the
             * {#getInstanceForKey} method and avoid deadlocks.
             */
            ProcessingElement pe2 = peInstances.putIfAbsent(id, pe);

            if (pe2 != null) {
                pe = pe2;
            }

            pe.id = id;
            pe.onCreate();

            logger.trace("Num PE instances: {}.", getNumPEInstances());
        }
        return pe;
    }

    /**
     * Get all the local instances. See notes in
     * {@link #getInstanceForKey(String) getLocalInstanceForKey}
     */
    public Collection<ProcessingElement> getInstances() {

        return peInstances.values();
    }

    /**
     * This method returns a remote PE instance for key. TODO: not implemented
     * for cluster configuration yet, use it only in single node configuration.
     * for testing apps.
     * 
     * @return pe instance for key. Null if if doesn't exist.
     */
    public ProcessingElement getRemoteInstancesForKey() {
        logger.warn("The getRemoteInstancesForKey() method is not implemented. Use "
                + "it to test your app in single node configuration only. Should work "
                + "transparently for remote objects once it is implemented.");

        ProcessingElement pe = peInstances.get(id);
        return pe;
    }

    /**
     * This method returns a map of PE instances for this prototype in the
     * network. This could be an expensive operation. TODO: not implemented for
     * cluster configuration yet, use it only in single node configuration. for
     * testing apps.
     */
    public Map<String, ProcessingElement> getRemoteInstances() {

        logger.warn("The getRemoteInstances() method is not implemented. Use "
                + "it to test your app in single node configuration only. Should work "
                + "transparently for remote objects once it is implemented.");

        /*
         * For now we just return a copy as a placeholder. We need to implement
         * a custom map capable of working on an S4 cluster as efficiently as
         * possible.
         */
        return new HashMap<String, ProcessingElement>(peInstances);
    }

    /*
     * Called when we create the first PE instance. TODO: Would be better to do
     * this as part of the PE lifecycle after PE construction.
     */
    private void onCreateInternal(ProcessingElement pe) {

        isFirst = false;

        logger.trace("OnCreateInternal");

        /*
         * If PE class has the @ThreadSafe annotation, then we set isThtreadSafe
         * to true in the prototype so all future PE instances inherit the
         * setting.
         */
        if (pe.getClass().isAnnotationPresent(ThreadSafe.class) == true) {
            pe.isThreadSafe = true;
            isThreadSafe = true;

            logger.trace("Annotated with @ThreadSafe");
        }
    }

    /**
     * Unique ID for a PE instance.
     * 
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id
     *            the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * This method exists simply to make <code>clone()</code> protected.
     */
    @Override
    protected Object clone() {
        try {
            Object clone = super.clone();
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    private class PETask extends TimerTask {

        protected class TimerEvent extends Event {
        }

        @Override
        public void run() {

            for (Map.Entry<String, ProcessingElement> entry : peInstances
                    .entrySet()) {

                ProcessingElement peInstance = entry.getValue();

                peInstance.isTimedOutput = true;

                if (!isOutputOnEvent) {
                    /* Call output method asynchronously using a fake event. */

                    Object object;
                    if (isThreadSafe) {
                        object = new Object(); // a dummy object TODO improve
                                               // this.
                    } else {
                        object = this;
                    }

                    synchronized (object) {
                        overloadDispatcher.dispatchOutputEvent(peInstance, new TimerEvent());
                    }
                }
            }
        }
    }
}
