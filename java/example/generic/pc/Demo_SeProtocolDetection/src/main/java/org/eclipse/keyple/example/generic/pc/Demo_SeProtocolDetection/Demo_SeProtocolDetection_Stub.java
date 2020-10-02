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
package org.eclipse.keyple.example.generic.pc.Demo_SeProtocolDetection;

import org.eclipse.keyple.core.seproxy.ReaderPlugin;
import org.eclipse.keyple.core.seproxy.SeProxyService;
import org.eclipse.keyple.core.seproxy.exception.KeyplePluginInstantiationException;
import org.eclipse.keyple.core.seproxy.exception.KeyplePluginNotFoundException;
import org.eclipse.keyple.core.seproxy.exception.KeypleReaderNotFoundException;
import org.eclipse.keyple.core.util.SeCommonProtocols;
import org.eclipse.keyple.example.common.generic.stub.StubMifareClassic;
import org.eclipse.keyple.example.common.generic.stub.StubMifareDesfire;
import org.eclipse.keyple.example.common.generic.stub.StubMifareUL;
import org.eclipse.keyple.plugin.stub.StubPlugin;
import org.eclipse.keyple.plugin.stub.StubPluginFactory;
import org.eclipse.keyple.plugin.stub.StubReader;

/** This class handles the reader events generated by the SeProxyService */
public class Demo_SeProtocolDetection_Stub {

  public Demo_SeProtocolDetection_Stub() {
    super();
  }

  /**
   * Application entry
   *
   * @param args the program arguments
   * @throws IllegalArgumentException in case of a bad argument
   * @throws InterruptedException if thread error occurs
   */
  public static void main(String[] args)
      throws InterruptedException, KeyplePluginNotFoundException,
          KeyplePluginInstantiationException {
    // get the SeProxyService instance
    SeProxyService seProxyService = SeProxyService.getInstance();

    final String STUB_PLUGIN_NAME = "stub1";

    // Register Stub plugin in the platform
    ReaderPlugin stubPlugin =
        seProxyService.registerPlugin(new StubPluginFactory(STUB_PLUGIN_NAME));

    // create an observer class to handle the SE operations
    SeProtocolDetectionEngine observer = new SeProtocolDetectionEngine();

    // Plug PO reader.
    ((StubPlugin) stubPlugin).plugStubReader("poReader", true);

    Thread.sleep(200);

    StubReader poReader = null;
    try {
      poReader = (StubReader) (stubPlugin.getReader("poReader"));
    } catch (KeypleReaderNotFoundException e) {
      e.printStackTrace();
    }

    observer.setReader(poReader);

    /* Activate additional protocol */
    poReader.activateProtocol(SeCommonProtocols.PROTOCOL_MIFARE_CLASSIC.getDescriptor());
    poReader.activateProtocol(SeCommonProtocols.PROTOCOL_MEMORY_ST25.getDescriptor());

    // Set terminal as Observer of the first reader
    poReader.addObserver(observer);

    Thread.sleep(300);

    poReader.removeSe();

    Thread.sleep(100);

    poReader.insertSe(new StubMifareClassic());

    Thread.sleep(300);

    poReader.removeSe();

    Thread.sleep(100);

    // insert Mifare UltraLight
    poReader.insertSe(new StubMifareUL());

    Thread.sleep(300);

    poReader.removeSe();

    Thread.sleep(100);

    // insert Mifare Desfire
    poReader.insertSe(new StubMifareDesfire());

    Thread.sleep(300);

    poReader.removeSe();

    Thread.sleep(100);

    System.exit(0);
  }
}
