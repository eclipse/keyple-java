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
package org.eclipse.keyple.plugin.remote;

import org.eclipse.keyple.core.service.PluginFactory;
import org.eclipse.keyple.core.service.SmartCardService;
import org.eclipse.keyple.core.service.event.ObservablePlugin;

/**
 * <b>Observable Remote Plugin Client</b> API.
 *
 * <p>This plugin must be used in the use case of the <b>Remote Plugin Client</b> configured <b>with
 * plugin observation</b>.
 *
 * <p>It must be register by a <b>client</b> application installed on the terminal not having local
 * access to the card reader and that wishes to control the remotely :
 *
 * <ul>
 *   <li>To <b>register</b> the plugin, use the Keyple service method {@link
 *       SmartCardService#registerPlugin(PluginFactory)} using the factory {link
 *       RemoteClientPluginFactory} and <b>activate the plugin observation</b>.
 *   <li>To access the plugin, use one of the following utility methods :
 *       <ul>
 *         <li>For <b>Async</b> node configuration : {link
 *             RemoteClientUtils#getAsyncObservablePlugin()}
 *         <li>For <b>Sync</b> node configuration : {link
 *             RemoteClientUtils#getSyncObservablePlugin()}
 *       </ul>
 *   <li>To <b>unregister</b> the plugin, use the Keyple service method {@link
 *       SmartCardService#unregisterPlugin(String)} using the plugin name.
 * </ul>
 *
 * <p>This plugin behaves like an {@link ObservablePlugin}.
 *
 * @since 1.0
 */
public interface ObservableRemotePluginClient extends RemotePluginClient, ObservablePlugin {}