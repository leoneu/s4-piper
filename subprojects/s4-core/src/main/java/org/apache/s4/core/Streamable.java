package org.apache.s4.core;

import org.apache.s4.base.Event;

/**
 * We use this interface to put events into objects.
 * 
 * @param <T>
 */
public interface Streamable {

    /**
     * Put an event into the streams.
     * 
     * @param event
     */
    public void put(Event event);

    /**
     * Stop and close all the streams.
     */
    public void close();

    /**
     * @return the name of this streamable object.
     */
    public String getName();
}
