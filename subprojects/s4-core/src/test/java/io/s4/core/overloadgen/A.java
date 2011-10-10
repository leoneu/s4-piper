package io.s4.core.overloadgen;

import io.s4.base.Event;
import io.s4.core.ProcessingElement;

public class A extends ProcessingElement {
    
    public Class<? extends Event> processedEventClass;
    public Class<? extends Event> processedOuputEventClass;
    boolean processedOutputEventThroughGenericMethod = false;
    
    public void processInputEvent(Event event) {
        processedEventClass = Event.class;
    }
        
    public void processInputEvent(Event2 event) {
        processedEventClass = event.getClass();
    }
    
    public void processInputEvent(Event1 event) {
        processedEventClass = event.getClass();
    }

    public void processInputEvent(Event1a event) {
        processedEventClass = event.getClass();
    }
    
    public void processOutputEvent(Event event) {
        processedOuputEventClass = event.getClass();
        processedOutputEventThroughGenericMethod = true;
    }
    
    public void processOutputEvent(Event1 event ) {
        processedOuputEventClass = event.getClass();
        processedOutputEventThroughGenericMethod = false;
    }
    
    
    @Override
    protected void onCreate() {
        // TODO Auto-generated method stub
        
    }
    @Override
    protected void onRemove() {
        // TODO Auto-generated method stub
        
    }

}
