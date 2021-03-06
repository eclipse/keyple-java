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
package org.eclipse.keyple.plugin.stub;

import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.keyple.core.plugin.AbstractObservableLocalReader;
import org.eclipse.keyple.core.plugin.WaitForCardInsertionBlocking;
import org.eclipse.keyple.core.plugin.WaitForCardRemovalBlocking;
import org.eclipse.keyple.core.plugin.WaitForCardRemovalDuringProcessing;
import org.eclipse.keyple.core.service.event.ReaderEvent;
import org.eclipse.keyple.core.service.event.ReaderObservationExceptionHandler;
import org.eclipse.keyple.core.service.exception.KeypleReaderException;
import org.eclipse.keyple.core.service.exception.KeypleReaderIOException;
import org.eclipse.keyple.core.service.exception.KeypleReaderProtocolNotFoundException;
import org.eclipse.keyple.core.service.exception.KeypleReaderProtocolNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simulates communication with a {@link StubSmartCard}. StubReader is observable, it raises {@link
 * ReaderEvent} : CARD_INSERTED, CARD_REMOVED
 */
class StubReaderImpl extends AbstractObservableLocalReader
    implements StubReader,
        WaitForCardInsertionBlocking,
        WaitForCardRemovalDuringProcessing,
        WaitForCardRemovalBlocking {

  private static final Logger logger = LoggerFactory.getLogger(StubReaderImpl.class);

  private StubSmartCard card;
  private boolean isContactless = true;

  private final AtomicBoolean loopWaitCard = new AtomicBoolean();
  private final AtomicBoolean loopWaitCardRemoval = new AtomicBoolean();

  ReaderObservationExceptionHandler readerObservationExceptionHandler;

  /**
   * Constructor
   *
   * @param readerName name of the reader
   * @param pluginName name of the plugin
   */
  StubReaderImpl(String pluginName, String readerName) {
    super(pluginName, readerName);
    readerObservationExceptionHandler =
        new ReaderObservationExceptionHandler() {
          @Override
          public void onReaderObservationError(String pluginName, String readerName, Throwable e) {
            logger.error("Unexpected exception {}:{}", pluginName, readerName, e);
          }
        };
  }

  /**
   * Specify
   *
   * @param readerName name of the reader
   * @param pluginName name of the plugin
   * @param isContactless true if this reader should be contactless
   */
  StubReaderImpl(String pluginName, String readerName, boolean isContactless) {
    this(pluginName, readerName);
    this.isContactless = isContactless;
  }

  @Override
  protected ReaderObservationExceptionHandler getObservationExceptionHandler() {
    return readerObservationExceptionHandler;
  }

  @Override
  protected void onStartDetection() {
    logger.trace("Detection has been started on reader {}", this.getName());
  }

  @Override
  protected void onStopDetection() {
    logger.trace("Detection has been stopped on reader {}", this.getName());
  }

  @Override
  protected byte[] getATR() {
    return card.getATR();
  }

  /**
   * {@inheritDoc}
   *
   * @since 0.9
   */
  @Override
  protected boolean isPhysicalChannelOpen() {
    return card != null && card.isPhysicalChannelOpen();
  }

  /**
   * {@inheritDoc}
   *
   * @since 0.9
   */
  @Override
  protected void openPhysicalChannel() {
    if (card != null) {
      card.openPhysicalChannel();
    }
  }

  /**
   * {@inheritDoc}
   *
   * @since 0.9
   */
  @Override
  public void closePhysicalChannel() {
    if (card != null) {
      card.closePhysicalChannel();
    }
  }

  /**
   * {@inheritDoc}
   *
   * @since 0.9
   */
  @Override
  public byte[] transmitApdu(byte[] apduIn) {
    if (card == null) {
      throw new KeypleReaderIOException("No card available.");
    }
    return card.processApdu(apduIn);
  }

  @Override
  protected boolean isCurrentProtocol(String readerProtocolName) {
    if (card != null && card.getCardProtocol() != null) {
      return card.getCardProtocol().equals(readerProtocolName);
    } else {
      return false;
    }
  }

  @Override
  protected synchronized boolean checkCardPresence() {
    return card != null;
  }

  @Override
  protected final void activateReaderProtocol(String readerProtocolName) {

    if (!StubProtocolSetting.getSettings().containsKey(readerProtocolName)) {
      throw new KeypleReaderProtocolNotSupportedException(readerProtocolName);
    }

    if (logger.isDebugEnabled()) {
      logger.debug(
          "{}: Activate protocol {} with rule \"{}\".",
          getName(),
          readerProtocolName,
          StubProtocolSetting.getSettings().get(readerProtocolName));
    }
  }

  @Override
  protected final void deactivateReaderProtocol(String readerProtocolName) {

    if (!StubProtocolSetting.getSettings().containsKey(readerProtocolName)) {
      throw new KeypleReaderProtocolNotSupportedException(readerProtocolName);
    }

    if (logger.isDebugEnabled()) {
      logger.debug("{}: Deactivate protocol {}.", getName(), readerProtocolName);
    }
  }

  /** @return the current transmission mode */
  @Override
  public boolean isContactless() {
    return isContactless;
  }

  /*
   * STATE CONTROLLERS FOR INSERTING AND REMOVING CARD
   */

  /**
   * Inserts the provided card.<br>
   *
   * @param smartCard stub card to be inserted in the reader
   * @throws KeypleReaderProtocolNotFoundException if the card protocol is not found
   */
  public synchronized void insertCard(StubSmartCard smartCard) {
    logger.debug("Insert card {}", smartCard);
    /* clean channels status */
    if (isPhysicalChannelOpen()) {
      try {
        closePhysicalChannel();
      } catch (KeypleReaderException e) {
        logger.error("Error while closing channel reader", e);
      }
    }
    if (smartCard != null) {
      card = smartCard;
    }
  }

  public synchronized void removeCard() {
    logger.debug("Remove card {}", card != null ? card : "none");
    card = null;
  }

  public StubSmartCard getSmartcard() {
    return card;
  }

  /**
   * This method is called by the monitoring thread to check the card presence
   *
   * @return true if the card is present
   */
  @Override
  public boolean waitForCardPresent() {
    loopWaitCard.set(true);
    while (loopWaitCard.get()) {
      if (checkCardPresence()) {
        return true;
      }
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        logger.debug("Sleep was interrupted");
        // Restore interrupted state...
        Thread.currentThread().interrupt();
      }
    }
    return false;
  }

  @Override
  public void stopWaitForCard() {
    loopWaitCard.set(false);
  }

  /**
   * Defined in the {@link WaitForCardRemovalBlocking} interface, this method is called by the
   * monitoring thread to check the card absence
   *
   * @return true if the card is absent
   */
  @Override
  public boolean waitForCardAbsentNative() {
    loopWaitCardRemoval.set(true);
    while (loopWaitCardRemoval.get()) {
      if (!checkCardPresence()) {
        logger.trace("[{}] card removed", this.getName());
        return true;
      }
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        logger.debug("Sleep was interrupted");
        // Restore interrupted state...
        Thread.currentThread().interrupt();
      }
    }
    return false;
  }

  @Override
  public void stopWaitForCardRemoval() {
    loopWaitCardRemoval.set(false);
  }
}
