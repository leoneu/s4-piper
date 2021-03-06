package org.apache.s4.core;

import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.junit.Test;

public class NoTriggerTest extends TriggerTest {

    @Test
    public void testNoTrigger() throws Exception {
        triggerType = TriggerType.NONE;
        Assert.assertFalse(createTriggerAppAndSendEvent().await(5, TimeUnit.SECONDS));
    }

}
