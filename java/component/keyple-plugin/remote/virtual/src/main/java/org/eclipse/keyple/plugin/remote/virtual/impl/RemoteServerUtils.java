/* **************************************************************************************
 * Copyright (c) 2020 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.eclipse.keyple.plugin.remote.virtual.impl;

import org.eclipse.keyple.core.seproxy.SeProxyService;
import org.eclipse.keyple.core.seproxy.exception.KeyplePluginNotFoundException;
import org.eclipse.keyple.plugin.remote.core.KeypleServerAsyncNode;
import org.eclipse.keyple.plugin.remote.core.KeypleServerSyncNode;
import org.eclipse.keyple.plugin.remote.virtual.RemoteServerPlugin;

/** Use this class to access the registered {@link RemoteServerPlugin} */
public class RemoteServerUtils {

  /**
   * Access the registered RemoteServerPlugin with an async Node
   *
   * @return a registered instance of the RemoteServerPlugin
   * @throws KeyplePluginNotFoundException if no RemoteServerPlugin is registered
   * @since 1.0
   */
  public static RemoteServerPlugin getAsyncPlugin() {
    return (RemoteServerPlugin)
        SeProxyService.getInstance().getPlugin(RemoteServerPluginFactory.PLUGIN_NAME_ASYNC);
  }

  /**
   * Retrieve the async node used in the RemoteServerPlugin
   *
   * @return non nullable instance of KeypleServerSyncNode
   * @since 1.0
   */
  public static KeypleServerAsyncNode getAsyncNode() {
    return (KeypleServerAsyncNode) ((RemoteServerPluginImpl) getAsyncPlugin()).getNode();
  }

  /**
   * Access the registered RemoteServerPlugin with a sync Node
   *
   * @return a registered instance of the RemoteServerPlugin
   * @throws KeyplePluginNotFoundException if no RemoteServerPlugin is registered
   * @since 1.0
   */
  public static RemoteServerPlugin getSyncPlugin() {
    return (RemoteServerPlugin)
        SeProxyService.getInstance().getPlugin(RemoteServerPluginFactory.PLUGIN_NAME_SYNC);
  }

  /**
   * Retrieve the sync node used in the RemoteServerPlugin
   *
   * @return non nullable instance of KeypleServerSyncNode
   * @since 1.0
   */
  public static KeypleServerSyncNode getSyncNode() {
    return (KeypleServerSyncNode) ((RemoteServerPluginImpl) getSyncPlugin()).getNode();
  }
}