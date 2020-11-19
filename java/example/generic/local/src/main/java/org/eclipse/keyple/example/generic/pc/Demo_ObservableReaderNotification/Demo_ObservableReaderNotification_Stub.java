/* **************************************************************************************
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.eclipse.keyple.example.generic.pc.Demo_ObservableReaderNotification;

import org.eclipse.keyple.core.service.Plugin;
import org.eclipse.keyple.core.service.SmartCardService;
import org.eclipse.keyple.example.generic.pc.common.StubSmartCard1;
import org.eclipse.keyple.example.generic.pc.common.StubSmartCard2;
import org.eclipse.keyple.plugin.stub.StubPlugin;
import org.eclipse.keyple.plugin.stub.StubPluginFactory;
import org.eclipse.keyple.plugin.stub.StubReader;
import org.eclipse.keyple.plugin.stub.StubSmartCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Demo_ObservableReaderNotification_Stub {
  private static final Logger logger =
      LoggerFactory.getLogger(Demo_ObservableReaderNotification_Stub.class);

  public static void main(String[] args) throws Exception {

    // Set Stub plugin
    SmartCardService smartCardService = SmartCardService.getInstance();

    final String STUB_PLUGIN_NAME = "stub1";
    final String READER1_NAME = "Reader1";
    final String READER2_NAME = "Reader2";

    // Register Stub plugin in the platform
    Plugin stubPlugin =
        smartCardService.registerPlugin(new StubPluginFactory(STUB_PLUGIN_NAME, null, null));

    // Set observers
    logger.info("Set plugin observer.");
    PluginConfig.initObservers();

    logger.info("Wait a little to see the \"no reader available message\".");
    Thread.sleep(200);

    logger.info("Plug reader 1.");
    ((StubPlugin) stubPlugin).plugStubReader(READER1_NAME, true);

    Thread.sleep(100);

    logger.info("Plug reader 2.");
    ((StubPlugin) stubPlugin).plugStubReader(READER2_NAME, true);

    Thread.sleep(1000);

    StubReader reader1 = (StubReader) (stubPlugin.getReader(READER1_NAME));

    StubReader reader2 = (StubReader) (stubPlugin.getReader(READER2_NAME));

    // Create 'virtual' Hoplink and SAM card
    StubSmartCard se1 = new StubSmartCard1();
    StubSmartCard se2 = new StubSmartCard2();

    logger.info("Insert card into reader 1.");
    reader1.insertCard(se1);

    Thread.sleep(100);

    logger.info("Insert card into reader 2.");
    reader2.insertCard(se2);

    Thread.sleep(100);

    logger.info("Remove card from reader 1.");
    reader1.removeCard();

    Thread.sleep(100);

    logger.info("Remove card from reader 2.");
    reader2.removeCard();

    Thread.sleep(100);

    logger.info("Plug reader 1 again (twice).");
    ((StubPlugin) stubPlugin).plugStubReader(READER1_NAME, true);

    logger.info("Unplug reader 1.");
    ((StubPlugin) stubPlugin).unplugStubReader(READER1_NAME, true);

    Thread.sleep(100);

    logger.info("Plug reader 1 again.");
    ((StubPlugin) stubPlugin).plugStubReader(READER1_NAME, true);

    Thread.sleep(100);

    logger.info("Unplug reader 1.");
    ((StubPlugin) stubPlugin).unplugStubReader(READER1_NAME, true);

    Thread.sleep(100);

    logger.info("Unplug reader 2.");
    ((StubPlugin) stubPlugin).unplugStubReader(READER2_NAME, true);

    logger.info("END.");

    System.exit(0);
  }
}
