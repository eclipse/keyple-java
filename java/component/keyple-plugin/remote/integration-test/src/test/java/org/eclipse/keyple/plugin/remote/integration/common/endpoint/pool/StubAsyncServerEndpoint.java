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
package org.eclipse.keyple.plugin.remote.integration.common.endpoint.pool;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.eclipse.keyple.core.util.NamedThreadFactory;
import org.eclipse.keyple.plugin.remote.MessageDto;
import org.eclipse.keyple.plugin.remote.PoolRemotePluginClient;
import org.eclipse.keyple.plugin.remote.spi.AsyncEndpointServer;
import org.eclipse.keyple.plugin.remote.integration.common.util.JacksonParser;
import org.eclipse.keyple.plugin.remote.impl.PoolLocalServiceServerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simulate a async server to test {@link
 * PoolRemotePluginClient}. Send and receive asynchronously
 * serialized {@link MessageDto} with connected {@link StubAsyncClientEndpoint}
 */
public class StubAsyncServerEndpoint implements AsyncEndpointServer {

  private static final Logger logger = LoggerFactory.getLogger(StubAsyncServerEndpoint.class);
  final Map<String, StubAsyncClientEndpoint> clients; // sessionId_client
  final Map<String, Integer> messageCounts; // sessionId_counts
  final ExecutorService taskPool;

  public StubAsyncServerEndpoint() {
    clients = new HashMap<String, StubAsyncClientEndpoint>();
    messageCounts = new HashMap<String, Integer>();
    taskPool = Executors.newCachedThreadPool(new NamedThreadFactory("server-async-pool"));
  }

  /** Simulate a close socket operation */
  public void close(String sessionId) {
    messageCounts.remove(sessionId);
    clients.remove(sessionId);
    PoolLocalServiceServerUtils.getAsyncNode().onClose(sessionId);
  }

  /**
   * Simulate data received by the socket
   *
   * @param jsonData incoming json data
   */
  public void onData(final String jsonData, final StubAsyncClientEndpoint client) {
    final MessageDto message = JacksonParser.fromJson(jsonData);
    clients.put(message.getSessionId(), client);
    taskPool.submit(
        new Runnable() {
          @Override
          public void run() {
            PoolLocalServiceServerUtils.getAsyncNode().onMessage(message);
          }
        });
  }

  @Override
  public void sendMessage(final MessageDto msg) {
    final String data = JacksonParser.toJson(msg);
    logger.trace("Data sent to client {}", data);
    final StubAsyncClientEndpoint client = clients.get(msg.getSessionId());
    taskPool.submit(
        new Runnable() {
          @Override
          public void run() {
            try {
              client.onMessage(data);
            } catch (Throwable t) {
              PoolLocalServiceServerUtils.getAsyncNode().onError(msg.getSessionId(), t);
            }
          }
        });
  }
}
