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
package org.eclipse.keyple.plugin.remotese.integration.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.eclipse.keyple.calypso.transaction.*;
import org.eclipse.keyple.core.selection.SeSelection;
import org.eclipse.keyple.core.selection.SelectionsResult;
import org.eclipse.keyple.core.seproxy.SeProxyService;
import org.eclipse.keyple.core.seproxy.SeReader;
import org.eclipse.keyple.core.seproxy.exception.KeyplePluginNotFoundException;
import org.eclipse.keyple.core.seproxy.exception.KeypleReaderNotFoundException;
import org.eclipse.keyple.core.seproxy.protocol.SeCommonProtocols;
import org.eclipse.keyple.plugin.remotese.core.KeypleServerAsync;
import org.eclipse.keyple.plugin.remotese.integration.common.app.ReaderEventFilter;
import org.eclipse.keyple.plugin.remotese.integration.common.app.RemoteSePluginObserver;
import org.eclipse.keyple.plugin.remotese.integration.common.endpoint.StubAsyncServerEndpoint;
import org.eclipse.keyple.plugin.remotese.integration.common.model.ConfigurationResult;
import org.eclipse.keyple.plugin.remotese.integration.common.model.DeviceInput;
import org.eclipse.keyple.plugin.remotese.integration.common.model.TransactionResult;
import org.eclipse.keyple.plugin.remotese.integration.common.model.UserInput;
import org.eclipse.keyple.plugin.remotese.integration.common.se.StubCalypsoClassic;
import org.eclipse.keyple.plugin.remotese.integration.common.util.CalypsoUtilities;
import org.eclipse.keyple.plugin.remotese.nativese.NativeSeClientService;
import org.eclipse.keyple.plugin.remotese.nativese.RemoteServiceParameters;
import org.eclipse.keyple.plugin.remotese.nativese.impl.NativeSeClientServiceTest;
import org.eclipse.keyple.plugin.remotese.virtualse.RemoteSeServerPlugin;
import org.eclipse.keyple.plugin.remotese.virtualse.impl.RemoteSeServerPluginFactory;
import org.eclipse.keyple.plugin.remotese.virtualse.impl.RemoteSeServerUtils;
import org.eclipse.keyple.plugin.stub.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseScenario {

  private static final Logger logger = LoggerFactory.getLogger(BaseScenario.class);

  public static String NATIVE_PLUGIN_NAME = "stubPlugin";
  public static String NATIVE_READER_NAME = "stubReader";

  public static String SERVICE_ID_1 = "EXECUTE_CALYPSO_SESSION_FROM_LOCAL_SELECTION";
  public static String SERVICE_ID_3 = "EXECUTE_CALYPSO_SESSION_FROM_REMOTE_SELECTION";
  public static String SERVICE_ID_2 = "CREATE_CONFIGURE_OBS_VIRTUAL_READER";

  public static String USER_ID = "Alexandre3";
  public static String DEVICE_ID = "Xo99";

  StubPlugin nativePlugin;
  StubReader nativeReader;

  RemoteSeServerPlugin remoteSePlugin;
  UserInput user1;


  /** Init native stub plugin that can work with {@link StubSecureElement} */
  void initNativeStubPlugin() {
    // reuse stub plugin
    try {
      nativePlugin = (StubPlugin) SeProxyService.getInstance().getPlugin(NATIVE_PLUGIN_NAME);
    } catch (KeyplePluginNotFoundException e) {
      nativePlugin =
          (StubPlugin)
              SeProxyService.getInstance()
                  .registerPlugin(new StubPluginFactory(NATIVE_PLUGIN_NAME));
    }
    // nativeReader should be reset
    try {
      nativeReader = (StubReader) nativePlugin.getReader(NATIVE_READER_NAME);
      assertThat(nativeReader).isNull();
    } catch (KeypleReaderNotFoundException e) {
      nativePlugin.plugStubReader(NATIVE_READER_NAME, true);
      nativeReader = (StubReader) nativePlugin.getReader(NATIVE_READER_NAME);
      // configure the procotol settings
      nativeReader.addSeProtocolSetting(
          SeCommonProtocols.PROTOCOL_ISO14443_4,
          StubProtocolSetting.STUB_PROTOCOL_SETTING.get(SeCommonProtocols.PROTOCOL_ISO14443_4));
    }
  }

  void clearNativeReader() {
    nativePlugin.unplugStubReader(NATIVE_READER_NAME, true);
  }

  /** Init a Sync Remote Se Server Plugin (ie. http server) */
  void initRemoteSePluginWithSyncNode() {
    try {
      remoteSePlugin = RemoteSeServerUtils.getSyncPlugin();
    } catch (KeyplePluginNotFoundException e) {
      remoteSePlugin =
          (RemoteSeServerPlugin)
              SeProxyService.getInstance()
                  .registerPlugin(
                      RemoteSeServerPluginFactory.builder()
                          .withSyncNode()
                          .withPluginObserver(new RemoteSePluginObserver())
                          .usingDefaultEventNotificationPool()
                          .build());
    }
  }

  /**
   * Init a Async Remote Se Server Plugin with an async server endpoint
   */
  void initRemoteSePluginWithAsyncNode(KeypleServerAsync serverEndpoint) {
    try {
      remoteSePlugin = RemoteSeServerUtils.getAsyncPlugin();
      logger.info("RemoteSePluginServer already registered, reusing it");
    } catch (KeyplePluginNotFoundException e) {
      remoteSePlugin =
          (RemoteSeServerPlugin)
              SeProxyService.getInstance()
                  .registerPlugin(
                      RemoteSeServerPluginFactory.builder()
                          .withAsyncNode(serverEndpoint)
                          .withPluginObserver(new RemoteSePluginObserver())
                          .usingDefaultEventNotificationPool()
                          .build());
    }
  }

  /**
   * Perform a calypso PO selection
   *
   * @return matching PO
   */
  CalypsoPo explicitPoSelection() {
    SeSelection seSelection = CalypsoUtilities.getSeSelection();
    SelectionsResult selectionsResult = seSelection.processExplicitSelection(nativeReader);
    return (CalypsoPo) selectionsResult.getActiveMatchingSe();
  }

  Callable<Boolean> verifyUserTransaction(
      final ReaderEventFilter eventFilter, final UserInput userInput) {
    return new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        return eventFilter.transactionResult != null
            && eventFilter.transactionResult.isSuccessful()
            && eventFilter.transactionResult.getUserId().equals(userInput.getUserId());
      }
    };
  }

  Callable<Boolean> seRemoved(final SeReader seReader) {
    return new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        return !seReader.isSePresent();
      }
    };
  }

  void execute1_localselection_remoteTransaction_successful(NativeSeClientService nativeService) {
    // insert stub SE into stub
    nativeReader.insertSe(new StubCalypsoClassic());

    CalypsoPo calypsoPo = explicitPoSelection();

    // execute remote service
    TransactionResult output =
        nativeService.executeRemoteService(
            RemoteServiceParameters.builder(SERVICE_ID_1, nativeReader)
                .withInitialSeContext(calypsoPo)
                .withUserInputData(new UserInput().setUserId(user1.getUserId()))
                .build(),
            TransactionResult.class);

    // validate result
    assertThat(output.isSuccessful()).isTrue();
    assertThat(output.getUserId()).isEqualTo(user1.getUserId());
  }

  void execute2_defaultSelection_onMatched_transaction_successful(
      NativeSeClientService nativeService, ReaderEventFilter eventFilter) {
    // execute remote service to create observable virtual reader
    ConfigurationResult configurationResult =
        nativeService.executeRemoteService(
            RemoteServiceParameters.builder(SERVICE_ID_2, nativeReader)
                .withUserInputData(new DeviceInput().setDeviceId(DEVICE_ID))
                .build(),
            ConfigurationResult.class);

    assertThat(configurationResult.isSuccessful()).isTrue();
    assertThat(configurationResult.getDeviceId()).isEqualTo(DEVICE_ID);

    eventFilter.setUser(user1);

    // user1 insert SE , SE event should be sent to server
    nativeReader.insertSe(new StubCalypsoClassic());
    logger.info(
        "1 - Verify User Transaction is successful for first user {}",
        eventFilter.user.getUserId());
    // filter
    await().atMost(2, TimeUnit.SECONDS).until(verifyUserTransaction(eventFilter, user1));

    nativeReader.removeSe();

    await().atMost(1, TimeUnit.SECONDS).until(seRemoved(nativeReader));

    UserInput user2 = new UserInput().setUserId(UUID.randomUUID().toString());
    eventFilter.setUser(user2);
    eventFilter.transactionResult = null;

    // user2 insert SE , SE event should be sent to server
    nativeReader.insertSe(new StubCalypsoClassic());
    logger.info(
        "2 - Verify User Transaction is successful for second user {}",
        eventFilter.user.getUserId());
    // filter
    await().atMost(2, TimeUnit.SECONDS).until(verifyUserTransaction(eventFilter, user2));

    nativeReader.removeSe();
    await().atMost(1, TimeUnit.SECONDS).until(seRemoved(nativeReader));

    /*
     * on the 2nd event, the virtual reader should be cleaned on native and virtual environment
     */
    assertThat(remoteSePlugin.getReaders()).isEmpty();

    assertThat(NativeSeClientServiceTest.getVirtualReaders(nativeService)).isEmpty();
  }

  void execute3_remoteselection_remoteTransaction_successful(NativeSeClientService nativeService) {
    // insert stub SE into stub
    nativeReader.insertSe(new StubCalypsoClassic());

    // execute remote service
    TransactionResult output =
        nativeService.executeRemoteService(
            RemoteServiceParameters.builder(SERVICE_ID_3, nativeReader)
                .withUserInputData(new UserInput().setUserId(user1.getUserId()))
                .build(),
            TransactionResult.class);

    // validate result
    assertThat(output.isSuccessful()).isTrue();
    assertThat(output.getUserId()).isEqualTo(user1.getUserId());
  }
}
