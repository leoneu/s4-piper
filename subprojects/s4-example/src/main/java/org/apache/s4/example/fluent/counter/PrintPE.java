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
package org.apache.s4.example.fluent.counter;

import org.apache.s4.base.Event;
import org.apache.s4.core.App;
import org.apache.s4.core.ProcessingElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrintPE extends ProcessingElement {

    private static final Logger logger = LoggerFactory.getLogger(PrintPE.class);

    public PrintPE(App app) {
        super(app);
    }

    public void onEvent(Event event) {

        logger.info(">>> [{}].", event.toString());
    }

    @Override
    protected void onCreate() {
    }

    @Override
    protected void onRemove() {
    }
}
