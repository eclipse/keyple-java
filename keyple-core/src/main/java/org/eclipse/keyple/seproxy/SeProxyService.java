/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.seproxy;

import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;

import org.eclipse.keyple.seproxy.exception.KeyplePluginException;
import org.eclipse.keyple.seproxy.exception.KeyplePluginNotFoundException;

/**
 * The Class SeProxyService. This singleton is the entry point of the SE Proxy Service, its instance
 * has to be called by a ticketing application in order to establish a link with a SE’s application.
 *
 */
public final class SeProxyService {

    /** singleton instance of SeProxyService */
    private static SeProxyService uniqueInstance = new SeProxyService();

    /** version number of the SE Proxy Service API */
    private Integer version = 1;

    /** the list of readers’ plugins interfaced with the SE Proxy Service */
    private SortedSet<ReaderPlugin> plugins = new ConcurrentSkipListSet<ReaderPlugin>();

    /**
     * Instantiates a new SeProxyService.
     */
    private SeProxyService() {}

    /**
     * Gets the single instance of SeProxyService.
     *
     * @return single instance of SeProxyService
     */
    public static SeProxyService getInstance() {
        return uniqueInstance;
    }

    /**
     * Sets the plugins.
     *
     * @param plugins the new plugins
     */
    public void setPlugins(SortedSet<ReaderPlugin> plugins) {
        this.plugins = plugins;
    }

    /**
     * Gets the plugins.
     *
     * @return the plugins the list of interfaced reader’s plugins.
     */
    public SortedSet<ReaderPlugin> getPlugins() {
        return plugins;
    }

    /**
     * Gets the plugin whose name is provided as an argument.
     *
     * @param name the plugin name
     * @return the plugin.
     * @throws KeyplePluginException:  if the wanted plugin is not found
     */
    public ReaderPlugin getPlugin(String name) throws KeyplePluginNotFoundException {
        for (ReaderPlugin plugin : plugins) {
            if (plugin.getName().equals(name)) {
                return plugin;
            }
        }
        throw new KeyplePluginNotFoundException(name);
    }

    /**
     * Gets the version.
     *
     * @return the version
     */
    public Integer getVersion() {
        // TODO improve version management
        return version;
    }
}
