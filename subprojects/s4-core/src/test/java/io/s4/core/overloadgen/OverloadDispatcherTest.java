package io.s4.core.overloadgen;

import io.s4.core.gen.OverloadDispatcher;
import io.s4.core.gen.OverloadDispatcherGenerator;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.regex.Pattern;

import junit.framework.Assert;

import org.junit.Test;

public class OverloadDispatcherTest {

    @Test
    public void testDispatchWithEventHierarchies() throws Exception {
        OverloadDispatcherGenerator gen = new OverloadDispatcherGenerator(A.class);
        OverloadDispatcher dispatcher = (OverloadDispatcher) gen.generate().newInstance();
        A a = new A();
        // input events
        dispatcher.dispatchInputEvent(a, new Event1());
        Assert.assertEquals(Event1.class, a.processedEventClass);
        dispatcher.dispatchInputEvent(a, new Event1a());
        Assert.assertEquals(Event1a.class, a.processedEventClass);
        dispatcher.dispatchInputEvent(a, new Event2());
        Assert.assertEquals(Event2.class, a.processedEventClass);
        
        // output events
        dispatcher.dispatchOutputEvent(a, new Event2());
        Assert.assertEquals(Event2.class, a.processedOuputEventClass);
        Assert.assertTrue(a.processedOutputEventThroughGenericMethod);
        dispatcher.dispatchOutputEvent(a, new Event1());
        Assert.assertEquals(Event1.class, a.processedOuputEventClass);
        Assert.assertFalse(a.processedOutputEventThroughGenericMethod);
    }
    
    @Test
    public void testDispatchWithSingleMethod() throws Exception {
        OverloadDispatcherGenerator gen = new OverloadDispatcherGenerator(C.class);
        OverloadDispatcher dispatcher = (OverloadDispatcher) gen.generate().newInstance();
        C c = new C();
        dispatcher.dispatchInputEvent(c, new Event2());
        Assert.assertFalse(c.processedEvent1Class);
        dispatcher.dispatchInputEvent(c, new Event1());
        Assert.assertTrue(c.processedEvent1Class);
    }

    @Test
    public void testNoMatchingMethod() throws Exception {
        PrintStream stdout = System.out;
        try {
            ByteArrayOutputStream tmpOut = new ByteArrayOutputStream();
            System.setOut(new PrintStream(tmpOut));

            OverloadDispatcherGenerator gen = new OverloadDispatcherGenerator(B.class);
            OverloadDispatcher dispatcher = (OverloadDispatcher) gen.generate().newInstance();
            B b = new B();
            dispatcher.dispatchInputEvent(b, new Event1());
            String output = tmpOut.toString().trim();
            // use DOTALL to ignore previous lines in output debug mode
            Assert.assertTrue(Pattern.compile("^.+OverloadDispatcher\\d+ - Cannot dispatch event "
                    + "of type \\[" + Event1.class.getName() + "\\] to PE of type \\[" + B.class.getName()
                    + "\\] : no matching processInputEvent method found$", Pattern.DOTALL).matcher(output).matches());
        } finally {
            System.setOut(stdout);
        }

    }
}
