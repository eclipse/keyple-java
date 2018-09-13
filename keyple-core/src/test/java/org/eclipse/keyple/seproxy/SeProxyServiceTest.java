/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License version 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */

package org.eclipse.keyple.seproxy;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import java.util.concurrent.ConcurrentSkipListSet;

import org.eclipse.keyple.seproxy.exception.KeyplePluginNotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@SuppressWarnings("PMD.SignatureDeclareThrowsException")
@RunWith(MockitoJUnitRunner.class)
public class SeProxyServiceTest {

    // class to test
    SeProxyService proxyService;

    @Mock
    ReaderPlugin plugin1;


    static String PLUGIN_NAME = "plugin1";

    @Before
    public void setupBeforeEach() {

        // init class to test
        proxyService = SeProxyService.getInstance();
    }


    @Test
    public void testGetInstance() {
        // test
        assertNotNull(proxyService);
        // assertNull(proxyService);

    }

    @Test
    public void testGetVersion() {
        // test
        assertEquals(1, proxyService.getVersion().intValue());
    }

    @Test
    public void testGetSetPlugins() {
        // init
        ConcurrentSkipListSet<ReaderPlugin> plugins = getPluginList();

        // test
        proxyService.setPlugins(plugins);
        assertArrayEquals(plugins.toArray(), proxyService.getPlugins().toArray());
    }

    @Test
    public void testGetPlugin() throws KeyplePluginNotFoundException {
        // init

        ConcurrentSkipListSet<ReaderPlugin> plugins = getPluginList();

        proxyService.setPlugins(plugins);

        // test
        assertEquals(plugin1, proxyService.getPlugin(PLUGIN_NAME));
    }

    @Test(expected = KeyplePluginNotFoundException.class)
    public void testGetPluginFail() throws Exception {

        // init
        ConcurrentSkipListSet<ReaderPlugin> plugins = getPluginList();
        proxyService.setPlugins(plugins);

        // test
        proxyService.getPlugin("unknown");// Throw exception


    }


    /*
     * HELPERS
     */

    private ConcurrentSkipListSet<ReaderPlugin> getPluginList() {

        // ReaderPlugin plugin2 = Mockito.mock(ReaderPlugin.class);
        // when(plugin2.getName()).thenReturn(PLUGIN_NAME_2);

        when(plugin1.getName()).thenReturn(PLUGIN_NAME);
        ConcurrentSkipListSet<ReaderPlugin> plugins = new ConcurrentSkipListSet<ReaderPlugin>();


        plugins.add(plugin1);
        // plugins.add(plugin2);

        assertEquals(1, plugins.size()); // impossible to add 2 ReaderPlugin mocks

        return plugins;
    }


}
