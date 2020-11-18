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
package org.eclipse.keyple.example.generic.pc.Demo_CardProtocolDetection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.keyple.core.service.Plugin;
import org.eclipse.keyple.core.service.Reader;
import org.eclipse.keyple.core.service.SmartCardService;
import org.eclipse.keyple.core.service.event.ObservableReader;
import org.eclipse.keyple.core.service.event.ReaderObservationExceptionHandler;
import org.eclipse.keyple.core.service.exception.KeypleException;
import org.eclipse.keyple.core.service.util.ContactlessCardCommonProtocols;
import org.eclipse.keyple.example.generic.pc.common.PcscReaderUtilities;
import org.eclipse.keyple.plugin.pcsc.PcscPluginFactory;
import org.eclipse.keyple.plugin.pcsc.PcscReader;
import org.eclipse.keyple.plugin.pcsc.PcscSupportedContactlessProtocols;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This code demonstrates the multi-protocols capability of the Keyple SmartCardService
 *
 * <ul>
 *   <li>instantiates a PC/SC plugin for a reader which name matches the regular expression provided
 *       by poReaderName.
 *   <li>uses the observable mechanism to handle card insertion/detection
 *   <li>expects card with various protocols (technologies)
 *   <li>shows the identified protocol when a card is detected
 *   <li>executes a simple Hoplink reading when a Hoplink card is identified
 * </ul>
 *
 * The program spends most of its time waiting for a Enter key before exit. The actual card
 * processing is mainly event driven through the observability.
 */
public class Demo_CardProtocolDetection_Pcsc {
  private static final Logger logger =
      LoggerFactory.getLogger(Demo_CardProtocolDetection_Pcsc.class);

  private static final AtomicBoolean waitForEnd = new AtomicBoolean();

  public Demo_CardProtocolDetection_Pcsc() {
    super();
  }

  static class ExceptionHandlerImpl implements ReaderObservationExceptionHandler {
    final Logger logger = LoggerFactory.getLogger(ExceptionHandlerImpl.class);

    @Override
    public void onReaderObservationError(
        String pluginName, String readerName, Throwable throwable) {
      logger.error("An unexpected reader error occurred: {}:{}", pluginName, readerName, throwable);
      synchronized (waitForEnd) {
        waitForEnd.set(false);
      }
    }
  }

  /**
   * Application entry
   *
   * @param args the program arguments
   * @throws IllegalArgumentException in case of a bad argument
   * @throws KeypleException if a reader error occurs
   */
  public static void main(String[] args) {
    // get the SmartCardService instance
    SmartCardService smartCardService = SmartCardService.getInstance();

    ExceptionHandlerImpl exceptionHandler = new ExceptionHandlerImpl();

    // Register the PcscPlugin with SmartCardService, get the corresponding generic Plugin in
    // return
    Plugin plugin = smartCardService.registerPlugin(new PcscPluginFactory(null, exceptionHandler));

    // Get and configure the PO reader
    Reader poReader = plugin.getReader(PcscReaderUtilities.getContactlessReaderName());
    ((PcscReader) poReader).setContactless(true).setIsoProtocol(PcscReader.IsoProtocol.T1);

    logger.info("PO Reader  : {}", poReader.getName());

    // create an observer class to handle the card operations
    ObserversConfiguration observer = new ObserversConfiguration();

    // configure reader
    ((PcscReader) poReader).setContactless(false).setIsoProtocol(PcscReader.IsoProtocol.T1);

    /* Activate protocols */
    poReader.activateProtocol(
        PcscSupportedContactlessProtocols.ISO_14443_4.name(),
        ContactlessCardCommonProtocols.ISO_14443_4.name());
    poReader.activateProtocol(
        PcscSupportedContactlessProtocols.MIFARE_CLASSIC.name(), "MIFARE_CLASSIC");
    poReader.activateProtocol(PcscSupportedContactlessProtocols.MEMORY_ST25.name(), "MEMORY_ST25");

    // Set terminal as Observer of the first reader
    ((ObservableReader) poReader).addObserver(new ObserversConfiguration.ReaderObserver());

    // Set Default selection
    ((ObservableReader) poReader)
        .setDefaultSelectionRequest(
                SelectionConfiguration.prepareCardSelection().getSelectionOperation(),
            ObservableReader.NotificationMode.ALWAYS,
            ObservableReader.PollingMode.REPEATING);

    // wait for Enter key to exit.
    logger.info("Press Enter to exit");
    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    waitForEnd.set(true);
    while (waitForEnd.get()) {
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
