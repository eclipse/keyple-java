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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.eclipse.keyple.core.seproxy.ReaderPlugin;
import org.eclipse.keyple.core.seproxy.SeProxyService;
import org.eclipse.keyple.core.seproxy.SeReader;
import org.eclipse.keyple.core.seproxy.event.ObservableReader;
import org.eclipse.keyple.core.seproxy.exception.KeypleException;
import org.eclipse.keyple.core.util.ContactlessCardCommonProtocols;
import org.eclipse.keyple.example.common.ReaderUtilities;
import org.eclipse.keyple.plugin.pcsc.PcscPluginFactory;
import org.eclipse.keyple.plugin.pcsc.PcscReader;
import org.eclipse.keyple.plugin.pcsc.PcscSupportedProtocols;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class handles the reader events generated by the SeProxyService */
public class Demo_SeProtocolDetection_Pcsc {
  private static final Logger logger = LoggerFactory.getLogger(Demo_SeProtocolDetection_Pcsc.class);

  public Demo_SeProtocolDetection_Pcsc() {
    super();
  }

  /**
   * Application entry
   *
   * @param args the program arguments
   * @throws IllegalArgumentException in case of a bad argument
   * @throws KeypleException if a reader error occurs
   */
  public static void main(String[] args) {
    // get the SeProxyService instance
    SeProxyService seProxyService = SeProxyService.getInstance();

    // Register the PcscPlugin with SeProxyService, get the corresponding generic ReaderPlugin in
    // return
    ReaderPlugin readerPlugin = seProxyService.registerPlugin(new PcscPluginFactory());

    // Get and configure the PO reader
    SeReader poReader = readerPlugin.getReader(ReaderUtilities.getContactlessReaderName());
    ((PcscReader) poReader).setContaclessMode(true).setIsoProtocol(PcscReader.IsoProtocol.T1);

    logger.info("PO Reader  : {}", poReader.getName());

    // create an observer class to handle the SE operations
    SeProtocolDetectionEngine observer = new SeProtocolDetectionEngine();

    observer.setReader(poReader);

    // configure reader
    ((PcscReader) poReader).setContaclessMode(false).setIsoProtocol(PcscReader.IsoProtocol.T1);

    /* Activate protocols */
    poReader.activateProtocol(
        PcscSupportedProtocols.ISO_14443_4.name(),
        ContactlessCardCommonProtocols.ISO_14443_4.name());
    poReader.activateProtocol(
        PcscSupportedProtocols.MIFARE_CLASSIC.name(),
        ContactlessCardCommonProtocols.MIFARE_CLASSIC.name());
    poReader.activateProtocol(
        PcscSupportedProtocols.MEMORY_ST25.name(),
        ContactlessCardCommonProtocols.MEMORY_ST25.name());

    // Set terminal as Observer of the first reader
    ((ObservableReader) poReader).addObserver(observer);

    // Set Default selection
    ((ObservableReader) poReader)
        .setDefaultSelectionRequest(
            observer.prepareSeSelection(),
            ObservableReader.NotificationMode.ALWAYS,
            ObservableReader.PollingMode.REPEATING);

    // wait for Enter key to exit.
    logger.info("Press Enter to exit");
    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    while (true) {
      int c = 0;
      try {
        c = br.read();
      } catch (IOException e) {
        logger.error("IO Exception: {}", e.getMessage());
      }
      if (c == 0x0A) {
        logger.info("Exiting...");
        System.exit(0);
      }
    }
  }
}
