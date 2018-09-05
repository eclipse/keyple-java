/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclise.keyple.example.remote.server;

import java.io.IOException;
import java.util.Map;
import org.eclipse.keyple.seproxy.ProxyReader;
import org.eclipse.keyple.seproxy.SeRequestSet;
import org.eclipse.keyple.seproxy.SeResponseSet;
import org.eclipse.keyple.seproxy.event.ObservableReader;
import org.eclipse.keyple.seproxy.event.ReaderEvent;
import org.eclipse.keyple.seproxy.exception.IOReaderException;
import org.eclipse.keyple.seproxy.exception.NoStackTraceThrowable;
import org.eclipse.keyple.seproxy.protocol.SeProtocolSetting;
import org.eclipse.keyple.util.Observable;
import org.eclise.keyple.example.remote.server.transport.ServerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class RemoteSeReader extends Observable implements ObservableReader {

    ServerConnection transport;
    String remoteName;
    String name;

    private static final Logger logger = LoggerFactory.getLogger(RemoteSeReader.class);

    public RemoteSeReader(ServerConnection transport, String remoteName) {
        this.transport = transport;
        this.remoteName = remoteName;
        this.name = "remote-" + remoteName;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getRemoteName() {
        return remoteName;
    }


    @Override
    public boolean isSePresent() throws NoStackTraceThrowable {
        return transport.isSePresent();
    }


    @Override
    public SeResponseSet transmit(SeRequestSet seApplicationRequest) throws IOReaderException {
        return transport.transmit(seApplicationRequest);
    }

    @Override
    public void addSeProtocolSetting(SeProtocolSetting seProtocolSetting) {
        transport.addSeProtocolSetting(seProtocolSetting);
    }

    /**
     * When an event occurs on the Remote LocalReader, notify Observers
     * 
     * @param event
     */
    public void onRemoteReaderEvent(ReaderEvent event) {
        this.notifyObservers(event);
    }


    /**
     *
     * HELPERS
     */



    // compare by name
    @Override
    public int compareTo(ProxyReader o) {
        return o.getName().compareTo(this.getName());
    }

    @Override
    public Map<String, String> getParameters() {
        return null;
    }



    /**
     * Add an observer. This will allow to be notified about all readers or plugins events.
     *
     * @param observer Observer to notify
     */

    public void addObserver(ReaderObserver observer) {
        logger.trace("[{}][{}] addObserver => Adding an observer.", this.getClass(),
                this.getName());
        super.addObserver(observer);
    }

    /**
     * Remove an observer.
     *
     * @param observer Observer to stop notifying
     */

    public void removeObserver(ReaderObserver observer) {
        logger.trace("[{}] removeObserver => Deleting a reader observer", this.getName());
        super.removeObserver(observer);
    }



    /**
     * This method shall be called only from a SE Proxy plugin or reader implementing
     * AbstractObservableReader or AbstractObservablePlugin. Push a ReaderEvent / PluginEvent of the
     * selected AbstractObservableReader / AbstractObservablePlugin to its registered Observer.
     *
     * @param event the event
     */

    public final void notifyObservers(ReaderEvent event) {
        logger.trace("[{}] AbstractObservableReader => Notifying a reader event: ", this.getName(),
                event);
        setChanged();
        super.notifyObservers(event);

    }

    /**
     * Set a list of parameters on a reader.
     * <p>
     * See {@link #setParameter(String, String)} for more details
     *
     * @param parameters the new parameters
     * @throws IOReaderException This method can fail when disabling the exclusive mode as it's
     *         executed instantly
     */
    public final void setParameters(Map<String, String> parameters) throws IOException {
        for (Map.Entry<String, String> en : parameters.entrySet()) {
            setParameter(en.getKey(), en.getValue());
        }
    }

    @Override
    public void setParameter(String key, String value) throws IOException {}

}
