/********************************************************************************
 * Copyright (c) 2019 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information regarding copyright
 * ownership.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.keyple.core.seproxy.plugin.state;

import org.eclipse.keyple.core.seproxy.plugin.AbstractObservableLocalReader;
import org.eclipse.keyple.core.seproxy.plugin.SmartPresenceReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class ThreadedWaitForSeProcessing extends DefaultWaitForSeProcessing {

    /** logger */
    private static final Logger logger = LoggerFactory.getLogger(ThreadedWaitForSeProcessing.class);

    private Future<Boolean> waitForCardAbsent;
    private final long timeout;
    private final ExecutorService executor;

    public ThreadedWaitForSeProcessing(AbstractObservableLocalReader reader, long timeout, ExecutorService executor) {
        super(reader);
        this.timeout = timeout;
        this.executor = executor;
    }

    @Override
    public void activate() {
        logger.debug("[{}] Activate ThreadedWaitForSeProcessing removal background", this.reader.getName());

        if (!(reader instanceof SmartPresenceReader)) {
            logger.trace("[{}] Reader is not SmartPresence enabled, can not detect removal event while in WaitForSeProcessing state",
                    this.reader.getName());
            return;
        }

        logger.trace("[{}] Reader is SmartPresence enabled ", this.reader.getName());
        waitForCardAbsent = executor.submit(waitForCardAbsent());
    }

    /**
     * Invoke waitForCardAbsent
     * 
     * @return true is the card was removed
     */
    private Callable<Boolean> waitForCardAbsent() {
        logger.debug("[{}] Using method waitForCardAbsentNative", this.reader.getName());

        return new Callable<Boolean>() {
            @Override
            public Boolean call() {
                if (((SmartPresenceReader) reader).waitForCardAbsentNative(timeout)) {// timeout is
                                                                                      // already
                                                                                      // managed
                                                                                      // within the
                                                                                      // task
                    onEvent(AbstractObservableLocalReader.InternalEvent.SE_REMOVED);
                    return true;
                } else {
                    // se was not removed within timeout
                    onEvent(AbstractObservableLocalReader.InternalEvent.TIME_OUT);
                    return false;
                }
            }
        };
    }


    @Override
    public void deActivate() {
        logger.debug("[{}] deActivate ThreadedWaitForSeRemoval", this.reader.getName());
        if (waitForCardAbsent != null && !waitForCardAbsent.isDone()) {
            waitForCardAbsent.cancel(true);// TODO not tested
        }
    }

}