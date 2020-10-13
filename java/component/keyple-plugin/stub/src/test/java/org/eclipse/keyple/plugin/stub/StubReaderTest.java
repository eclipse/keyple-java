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

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.keyple.calypso.command.PoClass;
import org.eclipse.keyple.calypso.command.po.builder.IncreaseCmdBuild;
import org.eclipse.keyple.calypso.command.po.builder.ReadRecordsCmdBuild;
import org.eclipse.keyple.calypso.transaction.PoSelectionRequest;
import org.eclipse.keyple.calypso.transaction.PoSelector;
import org.eclipse.keyple.core.selection.AbstractMatchingSe;
import org.eclipse.keyple.core.selection.AbstractSeSelectionRequest;
import org.eclipse.keyple.core.selection.SeSelection;
import org.eclipse.keyple.core.selection.SelectionsResult;
import org.eclipse.keyple.core.seproxy.MultiSeRequestProcessing;
import org.eclipse.keyple.core.seproxy.Reader;
import org.eclipse.keyple.core.seproxy.SeSelector;
import org.eclipse.keyple.core.seproxy.event.*;
import org.eclipse.keyple.core.seproxy.exception.KeypleException;
import org.eclipse.keyple.core.seproxy.exception.KeypleReaderException;
import org.eclipse.keyple.core.seproxy.exception.KeypleReaderIOException;
import org.eclipse.keyple.core.seproxy.message.*;
import org.eclipse.keyple.core.seproxy.message.ChannelControl;
import org.eclipse.keyple.core.seproxy.plugin.reader.util.ContactlessCardCommonProtocols;
import org.eclipse.keyple.core.util.ByteArrayUtil;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("PMD.SignatureDeclareThrowsException")
@RunWith(MockitoJUnitRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class StubReaderTest extends BaseStubTest {

  // TODO Add tests to check the CONTINUE and STOP behaviors
  // TODO Add tests to check the setThreadTimeout method and its effect

  Logger logger = LoggerFactory.getLogger(StubReaderTest.class);

  ObservableReader.ReaderObserver readerObs;

  // init before each test
  @Before
  public void SetUp() throws Exception {

    this.setupStub();
  }

  @After
  public void tearDown() {
    final StubReader reader = (StubReader) stubPlugin.getReader("StubReaderTest");
    stubPlugin.clearObservers();
    reader.removeObserver(readerObs);
    readerObs = null;
    Assert.assertEquals(0, ((ObservableReader) reader).countObservers());
    stubPlugin.unplugStubReader("StubReaderTest", true);
  }

  /*
   * TEST
   *
   * EVENT
   *
   */

  /**
   * Insert card check : event and card presence
   *
   * @throws InterruptedException
   */
  @Test
  public void testInsert() throws Exception {

    stubPlugin.plugStubReader("StubReaderTest", true);
    Assert.assertEquals(1, stubPlugin.getReaders().size());

    final StubReader reader = (StubReader) stubPlugin.getReader("StubReaderTest");
    Assert.assertEquals(false, reader.isSePresent());

    // CountDown lock
    final CountDownLatch lock = new CountDownLatch(2);

    readerObs =
        new ObservableReader.ReaderObserver() {
          @Override
          public void update(ReaderEvent event) {
            if (event.getEventType() == ReaderEvent.EventType.SE_INSERTED) {
              Assert.assertEquals(event.getReaderName(), reader.getName());
              Assert.assertEquals(event.getPluginName(), stubPlugin.getName());
              Assert.assertEquals(ReaderEvent.EventType.SE_INSERTED, event.getEventType());

              logger.debug("testInsert event is correct");

              lock.countDown();
            } else if (event.getEventType() == ReaderEvent.EventType.SE_REMOVED) {
              lock.countDown();
            }
          }
        };

    // add observer
    reader.addObserver(readerObs);

    reader.startSeDetection(ObservableReader.PollingMode.SINGLESHOT);

    // test
    reader.insertSe(hoplinkSE());

    // lock thread for 2 seconds max to wait for the event
    lock.await(2, TimeUnit.SECONDS);

    reader.stopSeDetection();

    reader.removeObserver(readerObs);

    Assert.assertEquals(0, lock.getCount()); // should be 0 because countDown is called by
    Assert.assertTrue(reader.isSePresent());
  }

  /**
   * Remove card check : event and card presence
   *
   * @throws InterruptedException
   */
  @Test
  public void testRemove() throws Exception {
    stubPlugin.plugStubReader("StubReaderTest", true);
    Assert.assertEquals(1, stubPlugin.getReaders().size());
    final StubReader reader = (StubReader) stubPlugin.getReader("StubReaderTest");

    // CountDown lock
    final CountDownLatch insertLock = new CountDownLatch(1);
    final CountDownLatch removeLock = new CountDownLatch(1);

    // add observer
    readerObs =
        new ObservableReader.ReaderObserver() {
          int event_i = 1;

          @Override
          public void update(ReaderEvent event) {
            logger.info("event {}", event.getEventType());
            // first event
            if (event_i == 1) {
              Assert.assertEquals(event.getReaderName(), reader.getName());
              Assert.assertEquals(event.getPluginName(), stubPlugin.getName());
              Assert.assertEquals(ReaderEvent.EventType.SE_INSERTED, event.getEventType());
              insertLock.countDown();
            }

            // analyze the second event, should be a SE_REMOVED
            if (event_i == 2) {
              Assert.assertEquals(event.getReaderName(), reader.getName());
              Assert.assertEquals(event.getPluginName(), stubPlugin.getName());
              Assert.assertEquals(ReaderEvent.EventType.SE_REMOVED, event.getEventType());
              removeLock.countDown();
            }
            event_i++;
          }
        };

    // add observer
    reader.addObserver(readerObs);

    reader.startSeDetection(ObservableReader.PollingMode.SINGLESHOT);

    // test
    reader.insertSe(hoplinkSE());

    // lock thread for 2 seconds max to wait for the event SE_INSERTED
    insertLock.await(2, TimeUnit.SECONDS);

    Assert.assertEquals(0, insertLock.getCount()); // should be 0 because insertLock is
    // countDown by obs

    ((ProxyReader) reader).releaseChannel();
    reader.removeSe();

    // lock thread for 2 seconds max to wait for the event SE_REMOVED
    removeLock.await(2, TimeUnit.SECONDS);

    Assert.assertEquals(0, removeLock.getCount()); // should be 0 because removeLock is
    // countDown by obs

    Assert.assertFalse(reader.isSePresent());
  }

  /**
   * Remove card check : event and card presence
   *
   * @throws InterruptedException
   */
  @Test
  public void A_testInsertRemoveTwice() throws Exception {
    stubPlugin.plugStubReader("StubReaderTest", true);
    Assert.assertEquals(1, stubPlugin.getReaders().size());
    final StubReader reader = (StubReader) stubPlugin.getReader("StubReaderTest");

    // CountDown lock
    final CountDownLatch firstInsertLock = new CountDownLatch(1);
    final CountDownLatch firstRemoveLock = new CountDownLatch(1);
    final CountDownLatch secondInsertLock = new CountDownLatch(1);
    final CountDownLatch secondRemoveLock = new CountDownLatch(1);

    // add observer
    readerObs =
        new ObservableReader.ReaderObserver() {
          int event_i = 1;

          @Override
          public void update(ReaderEvent event) {
            logger.info("event {}", event.getEventType());
            // first event
            if (event_i == 1) {
              Assert.assertEquals(event.getReaderName(), reader.getName());
              Assert.assertEquals(event.getPluginName(), stubPlugin.getName());
              Assert.assertEquals(ReaderEvent.EventType.SE_INSERTED, event.getEventType());
              firstInsertLock.countDown();
            }

            // analyze the second event, should be a SE_REMOVED
            if (event_i == 2) {
              Assert.assertEquals(event.getReaderName(), reader.getName());
              Assert.assertEquals(event.getPluginName(), stubPlugin.getName());
              Assert.assertEquals(ReaderEvent.EventType.SE_REMOVED, event.getEventType());
              firstRemoveLock.countDown();
            }
            if (event_i == 3) {
              Assert.assertEquals(event.getReaderName(), reader.getName());
              Assert.assertEquals(event.getPluginName(), stubPlugin.getName());
              Assert.assertEquals(ReaderEvent.EventType.SE_INSERTED, event.getEventType());
              secondInsertLock.countDown();
            }
            if (event_i == 4) {
              Assert.assertEquals(event.getReaderName(), reader.getName());
              Assert.assertEquals(event.getPluginName(), stubPlugin.getName());
              Assert.assertEquals(ReaderEvent.EventType.SE_REMOVED, event.getEventType());
              secondRemoveLock.countDown();
            }
            event_i++;
          }
        };

    // add observer
    reader.addObserver(readerObs);

    // set PollingMode to Continue
    reader.startSeDetection(ObservableReader.PollingMode.REPEATING);

    // test first sequence
    reader.insertSe(hoplinkSE());

    // Thread.sleep(200);

    // lock thread for 2 seconds max to wait for the event SE_INSERTED
    firstInsertLock.await(2, TimeUnit.SECONDS);
    Assert.assertEquals(0, firstInsertLock.getCount()); // should be 0 because insertLock is
    // countDown by obs
    // Thread.sleep(1000);

    ((ProxyReader) reader).releaseChannel();
    reader.removeSe();

    // lock thread for 2 seconds max to wait for the event SE_REMOVED
    firstRemoveLock.await(2, TimeUnit.SECONDS);
    Assert.assertEquals(0, firstRemoveLock.getCount()); // should be 0 because removeLock is
    // countDown by obs

    // BUG, insert event is not throw without (1)
    // BUG (1) make thread sleep
    // BUG, solved by setting a lower threadWaitTimeout (100ms)
    // Thread.sleep(1000);

    // test second sequence
    reader.insertSe(hoplinkSE());

    // lock thread for 2 seconds max to wait for the event SE_INSERTED
    secondInsertLock.await(2, TimeUnit.SECONDS);

    Assert.assertEquals(0, secondInsertLock.getCount()); // should be 0 because insertLock is
    // countDown by obs

    ((ProxyReader) reader).releaseChannel();

    // Thread.sleep(1000);
    reader.stopSeDetection();

    reader.removeSe();

    // lock thread for 2 seconds max to wait for the event SE_REMOVED
    secondRemoveLock.await(2, TimeUnit.SECONDS);
    Assert.assertEquals(0, secondRemoveLock.getCount()); // should be 0 because removeLock is
    // countDown by obs

    Assert.assertFalse(reader.isSePresent());
  }

  @Test
  public void A_testInsertRemoveTwiceFast() throws Exception {
    stubPlugin.plugStubReader("StubReaderTest", true);
    Assert.assertEquals(1, stubPlugin.getReaders().size());
    final StubReader reader = (StubReader) stubPlugin.getReader("StubReaderTest");

    // CountDown lock
    final CountDownLatch firstInsertLock = new CountDownLatch(1);
    final CountDownLatch firstRemoveLock = new CountDownLatch(1);
    final CountDownLatch secondInsertLock = new CountDownLatch(1);
    final CountDownLatch secondRemoveLock = new CountDownLatch(1);

    // add observer
    readerObs =
        new ObservableReader.ReaderObserver() {
          int event_i = 1;

          @Override
          public void update(ReaderEvent event) {
            logger.info("event {}", event.getEventType());
            // first event
            if (event_i == 1) {
              Assert.assertEquals(event.getReaderName(), reader.getName());
              Assert.assertEquals(event.getPluginName(), stubPlugin.getName());
              Assert.assertEquals(ReaderEvent.EventType.SE_INSERTED, event.getEventType());
              firstInsertLock.countDown();
            }

            // analyze the second event, should be a SE_REMOVED
            if (event_i == 2) {
              Assert.assertEquals(event.getReaderName(), reader.getName());
              Assert.assertEquals(event.getPluginName(), stubPlugin.getName());
              Assert.assertEquals(ReaderEvent.EventType.SE_REMOVED, event.getEventType());
              firstRemoveLock.countDown();
            }
            if (event_i == 3) {
              Assert.assertEquals(event.getReaderName(), reader.getName());
              Assert.assertEquals(event.getPluginName(), stubPlugin.getName());
              Assert.assertEquals(ReaderEvent.EventType.SE_INSERTED, event.getEventType());
              secondInsertLock.countDown();
            }
            if (event_i == 4) {
              Assert.assertEquals(event.getReaderName(), reader.getName());
              Assert.assertEquals(event.getPluginName(), stubPlugin.getName());
              Assert.assertEquals(ReaderEvent.EventType.SE_REMOVED, event.getEventType());
              secondRemoveLock.countDown();
            }
            event_i++;
          }
        };

    // add observer
    reader.addObserver(readerObs);

    // set PollingMode to Continue
    reader.startSeDetection(ObservableReader.PollingMode.REPEATING);

    // test first sequence
    reader.insertSe(hoplinkSE());

    // lock thread for 2 seconds max to wait for the event SE_INSERTED
    firstInsertLock.await(2, TimeUnit.SECONDS);
    Assert.assertEquals(0, firstInsertLock.getCount()); // should be 0 because insertLock is
    // countDown by obs

    ((ProxyReader) reader).releaseChannel();
    reader.removeSe();

    // lock thread for 2 seconds max to wait for the event SE_REMOVED
    firstRemoveLock.await(2, TimeUnit.SECONDS);
    Assert.assertEquals(0, firstRemoveLock.getCount()); // should be 0 because removeLock is
    // countDown by obs

    // BUG, insert event is not throw without (1)
    // BUG (1) make thread sleep
    // BUG, solved by setting a lower threadWaitTimeout (100ms)

    // test second sequence
    reader.insertSe(hoplinkSE());

    // lock thread for 2 seconds max to wait for the event SE_INSERTED
    secondInsertLock.await(2, TimeUnit.SECONDS);

    reader.stopSeDetection();

    Assert.assertEquals(0, secondInsertLock.getCount()); // should be 0 because insertLock is
    // countDown by obs
    ((ProxyReader) reader).releaseChannel();
    reader.removeSe();

    // lock thread for 2 seconds max to wait for the event SE_REMOVED
    secondRemoveLock.await(2, TimeUnit.SECONDS);
    Assert.assertEquals(0, secondRemoveLock.getCount()); // should be 0 because removeLock is
    // countDown by obs

    Assert.assertFalse(reader.isSePresent());
  }

  @Test
  public void testInsertMatchingSe() throws Exception {
    stubPlugin.plugStubReader("StubReaderTest", true);
    Assert.assertEquals(1, stubPlugin.getReaders().size());
    final StubReader reader = (StubReader) stubPlugin.getReader("StubReaderTest");

    // CountDown lock
    final CountDownLatch lock = new CountDownLatch(1);
    final String poAid = "A000000291A000000191";

    // activate ISO_14443_4
    reader.activateProtocol(
        StubSupportedProtocols.ISO_14443_4.name(),
        ContactlessCardCommonProtocols.ISO_14443_4.name());

    // add observer
    readerObs =
        new ObservableReader.ReaderObserver() {
          @Override
          public void update(ReaderEvent event) {
            if (event.getEventType() == ReaderEvent.EventType.SE_MATCHED) {
              Assert.assertEquals(event.getReaderName(), reader.getName());
              Assert.assertEquals(event.getPluginName(), stubPlugin.getName());
              Assert.assertEquals(ReaderEvent.EventType.SE_MATCHED, event.getEventType());
              Assert.assertTrue(
                  ((DefaultSelectionsResponse) event.getDefaultSelectionsResponse())
                      .getSelectionSeResponses()
                      .get(0)
                      .getSelectionStatus()
                      .hasMatched());
              Assert.assertArrayEquals(
                  ((DefaultSelectionsResponse) event.getDefaultSelectionsResponse())
                      .getSelectionSeResponses()
                      .get(0)
                      .getSelectionStatus()
                      .getAtr()
                      .getBytes(),
                  hoplinkSE().getATR());

              // retrieve the expected FCI from the card Stub running the select application command
              byte[] aid = ByteArrayUtil.fromHex(poAid);
              byte[] selectApplicationCommand = new byte[6 + aid.length];
              selectApplicationCommand[0] = (byte) 0x00; // CLA
              selectApplicationCommand[1] = (byte) 0xA4; // INS
              selectApplicationCommand[2] = (byte) 0x04; // P1: select by name
              selectApplicationCommand[3] = (byte) 0x00; // P2: requests the first
              selectApplicationCommand[4] = (byte) (aid.length); // Lc
              System.arraycopy(aid, 0, selectApplicationCommand, 5, aid.length); // data

              selectApplicationCommand[5 + aid.length] = (byte) 0x00; // Le
              byte[] fci = null;
              try {
                fci = hoplinkSE().processApdu(selectApplicationCommand);
              } catch (KeypleReaderIOException e) {
                e.printStackTrace();
              }

              Assert.assertArrayEquals(
                  ((DefaultSelectionsResponse) event.getDefaultSelectionsResponse())
                      .getSelectionSeResponses()
                      .get(0)
                      .getSelectionStatus()
                      .getFci()
                      .getBytes(),
                  fci);

              logger.debug("match event is correct");
              // unlock thread
              lock.countDown();
            }
          }
        };

    // add observer
    reader.addObserver(readerObs);

    SeSelection seSelection = new SeSelection();

    PoSelectionRequest poSelectionRequest =
        new PoSelectionRequest(
            PoSelector.builder()
                .seProtocol(ContactlessCardCommonProtocols.ISO_14443_4.name())
                .aidSelector(SeSelector.AidSelector.builder().aidToSelect(poAid).build())
                .invalidatedPo(PoSelector.InvalidatedPo.REJECT)
                .build());

    seSelection.prepareSelection(poSelectionRequest);

    ((ObservableReader) reader)
        .setDefaultSelectionRequest(
            seSelection.getSelectionOperation(), ObservableReader.NotificationMode.MATCHED_ONLY);

    // set PollingMode to Continue
    reader.startSeDetection(ObservableReader.PollingMode.SINGLESHOT);

    lock.await(5, TimeUnit.SECONDS);

    // test
    reader.insertSe(hoplinkSE());

    // lock thread for 2 seconds max to wait for the event
    lock.await(5, TimeUnit.SECONDS);
    Assert.assertEquals(0, lock.getCount()); // should be 0 because countDown is called by
    // observer
    reader.stopSeDetection();
    lock.await(5, TimeUnit.SECONDS);

    reader.removeObserver(readerObs);
  }

  @Test
  public void testInsertNotMatching_MatchedOnly() throws Exception {
    stubPlugin.plugStubReader("StubReaderTest", true);
    Assert.assertEquals(1, stubPlugin.getReaders().size());
    final StubReader reader = (StubReader) stubPlugin.getReader("StubReaderTest");

    // CountDown lock
    final CountDownLatch lock = new CountDownLatch(1);

    // add observer
    readerObs =
        new ObservableReader.ReaderObserver() {
          @Override
          public void update(ReaderEvent event) {
            // only SE_REMOVED event should be thrown
            if (event.getEventType() != ReaderEvent.EventType.SE_REMOVED) {
              lock.countDown(); // should not be called
            }
          }
        };

    // activate ISO_14443_4
    reader.activateProtocol(
        StubSupportedProtocols.ISO_14443_4.name(),
        ContactlessCardCommonProtocols.ISO_14443_4.name());

    // add observer
    reader.addObserver(readerObs);

    String poAid = "A000000291A000000192"; // not matching poAid

    SeSelection seSelection = new SeSelection();

    PoSelectionRequest poSelectionRequest =
        new PoSelectionRequest(
            PoSelector.builder()
                .seProtocol(ContactlessCardCommonProtocols.ISO_14443_4.name())
                .aidSelector(SeSelector.AidSelector.builder().aidToSelect(poAid).build())
                .invalidatedPo(PoSelector.InvalidatedPo.REJECT)
                .build());

    seSelection.prepareSelection(poSelectionRequest);

    ((ObservableReader) reader)
        .setDefaultSelectionRequest(
            seSelection.getSelectionOperation(), ObservableReader.NotificationMode.MATCHED_ONLY);

    reader.startSeDetection(ObservableReader.PollingMode.SINGLESHOT);

    // test
    reader.insertSe(hoplinkSE());

    Thread.sleep(100);
    reader.removeSe();

    reader.stopSeDetection();

    // lock thread for 2 seconds max to wait for the event
    lock.await(100, TimeUnit.MILLISECONDS);

    Assert.assertEquals(1, lock.getCount()); // should be 1 because countDown is never called
  }

  @Test
  public void testInsertNotMatching_Always() throws Exception {
    stubPlugin.plugStubReader("StubReaderTest", true);
    Assert.assertEquals(1, stubPlugin.getReaders().size());
    final StubReader reader = (StubReader) stubPlugin.getReader("StubReaderTest");

    // CountDown lock
    final CountDownLatch lock = new CountDownLatch(1);

    // activate ISO_14443_4
    reader.activateProtocol(
        StubSupportedProtocols.ISO_14443_4.name(),
        ContactlessCardCommonProtocols.ISO_14443_4.name());

    // add observer
    readerObs =
        new ObservableReader.ReaderObserver() {
          @Override
          public void update(ReaderEvent event) {
            // Only SE_INSERTED/SE_REMOVED event are thrown
            if (event.getEventType() == ReaderEvent.EventType.SE_INSERTED) {
              Assert.assertEquals(event.getReaderName(), reader.getName());
              Assert.assertEquals(event.getPluginName(), stubPlugin.getName());

              // card has not match
              Assert.assertFalse(
                  ((DefaultSelectionsResponse) event.getDefaultSelectionsResponse())
                      .getSelectionSeResponses()
                      .get(0)
                      .getSelectionStatus()
                      .hasMatched());

              lock.countDown(); // should be called
            }
          }
        };

    // add observer
    reader.addObserver(readerObs);

    String poAid = "A000000291A000000192"; // not matching poAid

    SeSelection seSelection = new SeSelection();

    PoSelectionRequest poSelectionRequest =
        new PoSelectionRequest(
            PoSelector.builder()
                .seProtocol(ContactlessCardCommonProtocols.ISO_14443_4.name())
                .aidSelector(SeSelector.AidSelector.builder().aidToSelect(poAid).build())
                .invalidatedPo(PoSelector.InvalidatedPo.REJECT)
                .build());

    seSelection.prepareSelection(poSelectionRequest);

    ((ObservableReader) reader)
        .setDefaultSelectionRequest(
            seSelection.getSelectionOperation(), ObservableReader.NotificationMode.ALWAYS);

    reader.startSeDetection(ObservableReader.PollingMode.SINGLESHOT);

    // test
    reader.insertSe(hoplinkSE());

    // lock thread for 2 seconds max to wait for the event
    lock.await(2, TimeUnit.SECONDS);

    reader.stopSeDetection();

    Assert.assertEquals(0, lock.getCount()); // should be 0 because countDown is called by
    // observer
    reader.removeObserver(readerObs);
  }

  @Test
  public void testExplicitSelection_onEvent() throws Exception {
    stubPlugin.plugStubReader("StubReaderTest", true);
    Assert.assertEquals(1, stubPlugin.getReaders().size());
    final StubReader reader = (StubReader) stubPlugin.getReader("StubReaderTest");

    // activate CALYPSO_OLD_CARD_PRIME
    reader.activateProtocol(
        StubSupportedProtocols.CALYPSO_OLD_CARD_PRIME.name(),
        ContactlessCardCommonProtocols.CALYPSO_OLD_CARD_PRIME.name());

    // CountDown lock
    final CountDownLatch lock = new CountDownLatch(1);

    // add observer
    readerObs =
        new ObservableReader.ReaderObserver() {
          @Override
          public void update(ReaderEvent event) {
            SeSelection seSelection = new SeSelection();

            PoSelectionRequest poSelectionRequest =
                new PoSelectionRequest(
                    PoSelector.builder()
                        .seProtocol(ContactlessCardCommonProtocols.CALYPSO_OLD_CARD_PRIME.name())
                        .atrFilter(new PoSelector.AtrFilter("3B.*"))
                        .invalidatedPo(PoSelector.InvalidatedPo.REJECT)
                        .build());

            /* Prepare selector, ignore AbstractMatchingSe here */
            seSelection.prepareSelection(poSelectionRequest);

            try {
              SelectionsResult selectionsResult = seSelection.processExplicitSelection(reader);

              AbstractMatchingSe matchingSe = selectionsResult.getActiveMatchingSe();

              Assert.assertNotNull(matchingSe);

            } catch (KeypleReaderException e) {
              Assert.fail("Unexcepted exception");
            } catch (KeypleException e) {
              Assert.fail("Unexcepted exception");
            }
            // unlock thread
            lock.countDown();
          }
        };

    // add observer
    reader.addObserver(readerObs);

    reader.startSeDetection(ObservableReader.PollingMode.SINGLESHOT);

    // test
    reader.insertSe(revision1SE());

    // lock thread for 2 seconds max to wait for the event
    lock.await(2, TimeUnit.SECONDS);

    Assert.assertEquals(0, lock.getCount()); // should be 0 because countDown is called by

    reader.stopSeDetection();
    // observer
    reader.removeObserver(readerObs);
  }

  @Test
  public void testReleaseSeChannel() throws InterruptedException {
    stubPlugin.plugStubReader("StubReaderTest", true);
    Assert.assertEquals(1, stubPlugin.getReaders().size());
    final StubReader reader = (StubReader) stubPlugin.getReader("StubReaderTest");

    reader.startSeDetection(ObservableReader.PollingMode.SINGLESHOT);

    // CountDown lock
    final CountDownLatch lock = new CountDownLatch(1);
    final String poAid = "A000000291A000000191";

    // activate ISO_14443_4
    reader.activateProtocol(
        StubSupportedProtocols.ISO_14443_4.name(),
        ContactlessCardCommonProtocols.ISO_14443_4.name());

    // add observer
    readerObs =
        new ObservableReader.ReaderObserver() {
          @Override
          public void update(ReaderEvent event) {

            if (ReaderEvent.EventType.SE_MATCHED == event.getEventType()) {
              logger.info("SE_MATCHED event received");
              logger.info("Notify card processed after 0ms");
              ((ProxyReader) reader).releaseChannel();
              lock.countDown();
            }
          }
        };

    // add observer
    reader.addObserver(readerObs);

    SeSelection seSelection = new SeSelection();

    PoSelectionRequest poSelectionRequest =
        new PoSelectionRequest(
            PoSelector.builder()
                .seProtocol(ContactlessCardCommonProtocols.ISO_14443_4.name())
                .aidSelector(SeSelector.AidSelector.builder().aidToSelect(poAid).build())
                .invalidatedPo(PoSelector.InvalidatedPo.REJECT)
                .build());

    seSelection.prepareSelection(poSelectionRequest);

    reader.setDefaultSelectionRequest(
        seSelection.getSelectionOperation(), ObservableReader.NotificationMode.MATCHED_ONLY);

    reader.startSeDetection(ObservableReader.PollingMode.SINGLESHOT);

    // test
    reader.insertSe(hoplinkSE());

    // lock thread for 2 seconds max to wait for the event
    lock.await(2, TimeUnit.SECONDS);

    reader.stopSeDetection();

    Assert.assertEquals(0, lock.getCount()); // should be 0 because countDown is called by
    // observer

  }

  /*
   * TEST
   *
   * TRANSMIT
   *
   */

  @Test
  public void transmit_Hoplink_Successful() {
    stubPlugin.plugStubReader("StubReaderTest", true);
    Assert.assertEquals(1, stubPlugin.getReaders().size());
    StubReader reader = (StubReader) stubPlugin.getReader("StubReaderTest");
    // init Request
    List<CardRequest> requests = getRequestIsoDepSetSample();

    // init card
    reader.insertSe(hoplinkSE());

    // activate ISO_14443_4
    reader.activateProtocol(
        StubSupportedProtocols.ISO_14443_4.name(),
        ContactlessCardCommonProtocols.ISO_14443_4.name());

    // send the selection request
    genericSelectSe(reader);

    // test
    List<SeResponse> seResponse =
        ((ProxyReader) reader)
            .transmitSeRequests(
                requests, MultiSeRequestProcessing.FIRST_MATCH, ChannelControl.KEEP_OPEN);

    // assert
    Assert.assertTrue(seResponse.get(0).getApduResponses().get(0).isSuccessful());
  }

  @Test(expected = KeypleReaderException.class)
  public void transmit_no_response() throws Exception {
    stubPlugin.plugStubReader("StubReaderTest", true);
    Assert.assertEquals(1, stubPlugin.getReaders().size());
    StubReader reader = (StubReader) stubPlugin.getReader("StubReaderTest");
    // init Request
    List<CardRequest> requests = getNoResponseRequest();

    // init card
    reader.insertSe(noApduResponseSE());

    // activate ISO_14443_4
    reader.activateProtocol(
        StubSupportedProtocols.ISO_14443_4.name(),
        ContactlessCardCommonProtocols.ISO_14443_4.name());

    // send the selection request
    genericSelectSe(reader);

    // test
    List<SeResponse> seResponse =
        ((ProxyReader) reader)
            .transmitSeRequests(
                requests, MultiSeRequestProcessing.FIRST_MATCH, ChannelControl.KEEP_OPEN);
  }

  @Test
  public void transmit_partial_response_set_0() {
    stubPlugin.plugStubReader("StubReaderTest", true);
    Assert.assertEquals(1, stubPlugin.getReaders().size());
    StubReader reader = (StubReader) stubPlugin.getReader("StubReaderTest");
    // init Request
    List<CardRequest> cardRequests = getPartialRequestList(0);

    // init card
    reader.insertSe(partialSE());

    // activate ISO_14443_4
    reader.activateProtocol(
        StubSupportedProtocols.ISO_14443_4.name(),
        ContactlessCardCommonProtocols.ISO_14443_4.name());

    // send the selection request
    genericSelectSe(reader);

    // test
    try {
      List<SeResponse> seResponses =
          ((ProxyReader) reader)
              .transmitSeRequests(
                  cardRequests, MultiSeRequestProcessing.FIRST_MATCH, ChannelControl.KEEP_OPEN);
      Assert.fail("Should throw exception");

    } catch (KeypleReaderIOException ex) {
      Assert.assertEquals(1, ex.getSeResponses().size());
      Assert.assertEquals(2, ex.getSeResponses().get(0).getApduResponses().size());
    }
  }

  // @Test
  public void transmit_partial_response_set_1() {
    stubPlugin.plugStubReader("StubReaderTest", true);
    Assert.assertEquals(1, stubPlugin.getReaders().size());
    StubReader reader = (StubReader) stubPlugin.getReader("StubReaderTest");
    // init Request
    List<CardRequest> cardRequests = getPartialRequestList(1);

    // init card
    reader.insertSe(partialSE());

    // activate ISO_14443_4
    reader.activateProtocol(
        StubSupportedProtocols.ISO_14443_4.name(),
        ContactlessCardCommonProtocols.ISO_14443_4.name());

    // send the selection request
    genericSelectSe(reader);

    // test
    try {
      List<SeResponse> seResponses =
          ((ProxyReader) reader)
              .transmitSeRequests(
                  cardRequests, MultiSeRequestProcessing.FIRST_MATCH, ChannelControl.KEEP_OPEN);
      Assert.fail("Should throw exception");

    } catch (KeypleReaderIOException ex) {
      Assert.assertEquals(2, ex.getSeResponses().size());
      Assert.assertEquals(4, ex.getSeResponses().get(0).getApduResponses().size());
      Assert.assertEquals(2, ex.getSeResponses().get(1).getApduResponses().size());
      Assert.assertEquals(2, ex.getSeResponses().get(1).getApduResponses().size());
    }
  }

  // @Test
  public void transmit_partial_response_set_2() {
    stubPlugin.plugStubReader("StubReaderTest", true);
    Assert.assertEquals(1, stubPlugin.getReaders().size());
    StubReader reader = (StubReader) stubPlugin.getReader("StubReaderTest");
    // init Request
    List<CardRequest> cardRequests = getPartialRequestList(2);

    // init card
    reader.insertSe(partialSE());

    // activate ISO_14443_4
    reader.activateProtocol(
        StubSupportedProtocols.ISO_14443_4.name(),
        ContactlessCardCommonProtocols.ISO_14443_4.name());

    // send the selection request
    genericSelectSe(reader);

    // test
    try {
      List<SeResponse> seResponses =
          ((ProxyReader) reader)
              .transmitSeRequests(
                  cardRequests, MultiSeRequestProcessing.FIRST_MATCH, ChannelControl.KEEP_OPEN);
      Assert.fail("Should throw exception");

    } catch (KeypleReaderIOException ex) {
      Assert.assertEquals(3, ex.getSeResponses().size());
      Assert.assertEquals(4, ex.getSeResponses().get(0).getApduResponses().size());
      Assert.assertEquals(4, ex.getSeResponses().get(1).getApduResponses().size());
      Assert.assertEquals(2, ex.getSeResponses().get(2).getApduResponses().size());
    }
  }

  // @Test
  public void transmit_partial_response_set_3() {
    stubPlugin.plugStubReader("StubReaderTest", true);
    Assert.assertEquals(1, stubPlugin.getReaders().size());
    StubReader reader = (StubReader) stubPlugin.getReader("StubReaderTest");
    // init Request
    List<CardRequest> cardRequests = getPartialRequestList(3);

    // init card
    reader.insertSe(partialSE());

    // activate ISO_14443_4
    reader.activateProtocol(
        StubSupportedProtocols.ISO_14443_4.name(),
        ContactlessCardCommonProtocols.ISO_14443_4.name());

    // send the selection request
    genericSelectSe(reader);

    // test
    try {
      List<SeResponse> seResponses =
          ((ProxyReader) reader)
              .transmitSeRequests(
                  cardRequests, MultiSeRequestProcessing.FIRST_MATCH, ChannelControl.KEEP_OPEN);
      Assert.assertEquals(3, seResponses.size());
      Assert.assertEquals(4, seResponses.get(0).getApduResponses().size());
      Assert.assertEquals(4, seResponses.get(1).getApduResponses().size());
      Assert.assertEquals(4, seResponses.get(2).getApduResponses().size());
    } catch (KeypleReaderException ex) {
      Assert.fail("Should not throw exception");
    }
  }

  @Test
  public void transmit_partial_response_0() {
    stubPlugin.plugStubReader("StubReaderTest", true);
    Assert.assertEquals(1, stubPlugin.getReaders().size());
    StubReader reader = (StubReader) stubPlugin.getReader("StubReaderTest");
    // init Request
    CardRequest cardRequest = getPartialRequest(0);

    // init card
    reader.insertSe(partialSE());

    // activate ISO_14443_4
    reader.activateProtocol(
        StubSupportedProtocols.ISO_14443_4.name(),
        ContactlessCardCommonProtocols.ISO_14443_4.name());

    // send the selection request
    genericSelectSe(reader);

    // test
    try {
      SeResponse seResponse =
          ((ProxyReader) reader).transmitSeRequest(cardRequest, ChannelControl.KEEP_OPEN);
      Assert.fail("Should throw exception");

    } catch (KeypleReaderIOException ex) {
      Assert.assertEquals(0, ex.getSeResponse().getApduResponses().size());
    }
  }

  @Test
  public void transmit_partial_response_1() {
    stubPlugin.plugStubReader("StubReaderTest", true);
    Assert.assertEquals(1, stubPlugin.getReaders().size());
    StubReader reader = (StubReader) stubPlugin.getReader("StubReaderTest");
    // init Request
    CardRequest cardRequest = getPartialRequest(1);

    // init card
    reader.insertSe(partialSE());

    // activate ISO_14443_4
    reader.activateProtocol(
        StubSupportedProtocols.ISO_14443_4.name(),
        ContactlessCardCommonProtocols.ISO_14443_4.name());

    // send the selection request
    genericSelectSe(reader);

    // test
    try {
      SeResponse seResponse =
          ((ProxyReader) reader).transmitSeRequest(cardRequest, ChannelControl.KEEP_OPEN);
      Assert.fail("Should throw exception");

    } catch (KeypleReaderIOException ex) {
      Assert.assertEquals(1, ex.getSeResponse().getApduResponses().size());
    }
  }

  @Test
  public void transmit_partial_response_2() {
    stubPlugin.plugStubReader("StubReaderTest", true);
    Assert.assertEquals(1, stubPlugin.getReaders().size());
    StubReader reader = (StubReader) stubPlugin.getReader("StubReaderTest");
    // init Request
    CardRequest cardRequest = getPartialRequest(2);

    // init card
    reader.insertSe(partialSE());

    // activate ISO_14443_4
    reader.activateProtocol(
        StubSupportedProtocols.ISO_14443_4.name(),
        ContactlessCardCommonProtocols.ISO_14443_4.name());

    // send the selection request
    genericSelectSe(reader);

    // test
    try {
      SeResponse seResponse =
          ((ProxyReader) reader).transmitSeRequest(cardRequest, ChannelControl.KEEP_OPEN);
      Assert.fail("Should throw exception");

    } catch (KeypleReaderIOException ex) {
      Assert.assertEquals(2, ex.getSeResponse().getApduResponses().size());
    }
  }

  @Test
  public void transmit_partial_response_3() {
    stubPlugin.plugStubReader("StubReaderTest", true);
    Assert.assertEquals(1, stubPlugin.getReaders().size());
    StubReader reader = (StubReader) stubPlugin.getReader("StubReaderTest");
    // init Request
    CardRequest cardRequest = getPartialRequest(3);

    // init card
    reader.insertSe(partialSE());

    // activate ISO_14443_4
    reader.activateProtocol(
        StubSupportedProtocols.ISO_14443_4.name(),
        ContactlessCardCommonProtocols.ISO_14443_4.name());

    // send the selection request
    genericSelectSe(reader);

    // test
    try {
      SeResponse seResponse =
          ((ProxyReader) reader).transmitSeRequest(cardRequest, ChannelControl.KEEP_OPEN);
      Assert.assertEquals(3, seResponse.getApduResponses().size());
    } catch (KeypleReaderException ex) {
      Assert.fail("Should not throw exception");
    }
  }

  /*
   * NAME and PARAMETERS
   */

  @Test
  public void testGetName() {
    stubPlugin.plugStubReader("StubReaderTest", true);
    Assert.assertEquals(1, stubPlugin.getReaders().size());
    StubReader reader = (StubReader) stubPlugin.getReader("StubReaderTest");
    Assert.assertNotNull(reader.getName());
  }

  /*
   * HELPER METHODS
   */

  public static List<CardRequest> getRequestIsoDepSetSample() {
    String poAid = "A000000291A000000191";
    ReadRecordsCmdBuild poReadRecordCmd_T2Env =
        new ReadRecordsCmdBuild(PoClass.ISO, 0x14, 1, ReadRecordsCmdBuild.ReadMode.ONE_RECORD, 32);

    List<ApduRequest> poApduRequests = Arrays.asList(poReadRecordCmd_T2Env.getApduRequest());

    CardRequest cardRequest = new CardRequest(poApduRequests);

    List<CardRequest> cardRequests = new ArrayList<CardRequest>();

    cardRequests.add(cardRequest);

    return cardRequests;
  }

  /*
   * No Response: increase command is not defined in the StubSE
   *
   * An Exception will be thrown.
   */
  public static List<CardRequest> getNoResponseRequest() {

    IncreaseCmdBuild poIncreaseCmdBuild =
        new IncreaseCmdBuild(PoClass.ISO, (byte) 0x14, (byte) 0x01, 0);

    List<ApduRequest> poApduRequests = Arrays.asList(poIncreaseCmdBuild.getApduRequest());

    CardRequest cardRequest = new CardRequest(poApduRequests);

    List<CardRequest> cardRequests = new ArrayList<CardRequest>();

    cardRequests.add(cardRequest);

    return cardRequests;
  }

  /*
   * Partial response set: multiple read records commands, one is not defined in the StubSE
   *
   * An Exception will be thrown.
   */
  public static List<CardRequest> getPartialRequestList(int scenario) {
    String poAid = "A000000291A000000191";
    ReadRecordsCmdBuild poReadRecord1CmdBuild =
        new ReadRecordsCmdBuild(PoClass.ISO, 0x14, 1, ReadRecordsCmdBuild.ReadMode.ONE_RECORD, 0);

    /* this command doesn't in the PartialSE */
    ReadRecordsCmdBuild poReadRecord2CmdBuild =
        new ReadRecordsCmdBuild(PoClass.ISO, 0x1E, 1, ReadRecordsCmdBuild.ReadMode.ONE_RECORD, 0);

    List<ApduRequest> poApduRequests1 = new ArrayList<ApduRequest>();
    poApduRequests1.add(poReadRecord1CmdBuild.getApduRequest());
    poApduRequests1.add(poReadRecord1CmdBuild.getApduRequest());
    poApduRequests1.add(poReadRecord1CmdBuild.getApduRequest());
    poApduRequests1.add(poReadRecord1CmdBuild.getApduRequest());

    List<ApduRequest> poApduRequests2 = new ArrayList<ApduRequest>();
    poApduRequests2.add(poReadRecord1CmdBuild.getApduRequest());
    poApduRequests2.add(poReadRecord1CmdBuild.getApduRequest());
    poApduRequests2.add(poReadRecord1CmdBuild.getApduRequest());
    poApduRequests2.add(poReadRecord1CmdBuild.getApduRequest());

    List<ApduRequest> poApduRequests3 = new ArrayList<ApduRequest>();
    poApduRequests3.add(poReadRecord1CmdBuild.getApduRequest());
    poApduRequests3.add(poReadRecord1CmdBuild.getApduRequest());
    poApduRequests3.add(poReadRecord2CmdBuild.getApduRequest());
    poApduRequests3.add(poReadRecord1CmdBuild.getApduRequest());

    CardRequest cardRequest1 = new CardRequest(poApduRequests1);

    CardRequest cardRequest2 = new CardRequest(poApduRequests2);

    /* This CardRequest fails at step 3 */
    CardRequest cardRequest3 = new CardRequest(poApduRequests3);

    CardRequest cardRequest4 = new CardRequest(poApduRequests1);

    List<CardRequest> cardRequests = new ArrayList<CardRequest>();

    switch (scenario) {
      case 0:
        /* 0 response */
        cardRequests.add(cardRequest3); // fails
        cardRequests.add(cardRequest1); // succeeds
        cardRequests.add(cardRequest2); // succeeds
        break;
      case 1:
        /* 1 response */
        cardRequests.add(cardRequest1); // succeeds
        cardRequests.add(cardRequest3); // fails
        cardRequests.add(cardRequest2); // succeeds
        break;
      case 2:
        /* 2 responses */
        cardRequests.add(cardRequest1); // succeeds
        cardRequests.add(cardRequest2); // succeeds
        cardRequests.add(cardRequest3); // fails
        break;
      case 3:
        /* 3 responses */
        cardRequests.add(cardRequest1); // succeeds
        cardRequests.add(cardRequest2); // succeeds
        cardRequests.add(cardRequest4); // succeeds
        break;
      default:
    }

    return cardRequests;
  }

  /*
   * Partial response: multiple read records commands, one is not defined in the StubSE
   *
   * An Exception will be thrown.
   */
  public static CardRequest getPartialRequest(int scenario) {
    String poAid = "A000000291A000000191";

    ReadRecordsCmdBuild poReadRecord1CmdBuild =
        new ReadRecordsCmdBuild(PoClass.ISO, 0x14, 1, ReadRecordsCmdBuild.ReadMode.ONE_RECORD, 0);

    /* this command doesn't in the PartialSE */
    ReadRecordsCmdBuild poReadRecord2CmdBuild =
        new ReadRecordsCmdBuild(PoClass.ISO, 0x1E, 1, ReadRecordsCmdBuild.ReadMode.ONE_RECORD, 0);

    List<ApduRequest> poApduRequests = new ArrayList<ApduRequest>();

    switch (scenario) {
      case 0:
        poApduRequests.add(poReadRecord2CmdBuild.getApduRequest()); // fails
        poApduRequests.add(poReadRecord1CmdBuild.getApduRequest()); // succeeds
        poApduRequests.add(poReadRecord1CmdBuild.getApduRequest()); // succeeds
        break;
      case 1:
        poApduRequests.add(poReadRecord1CmdBuild.getApduRequest()); // succeeds
        poApduRequests.add(poReadRecord2CmdBuild.getApduRequest()); // fails
        poApduRequests.add(poReadRecord1CmdBuild.getApduRequest()); // succeeds
        break;
      case 2:
        poApduRequests.add(poReadRecord1CmdBuild.getApduRequest()); // succeeds
        poApduRequests.add(poReadRecord1CmdBuild.getApduRequest()); // succeeds
        poApduRequests.add(poReadRecord2CmdBuild.getApduRequest()); // fails
        break;
      case 3:
        poApduRequests.add(poReadRecord1CmdBuild.getApduRequest()); // succeeds
        poApduRequests.add(poReadRecord1CmdBuild.getApduRequest()); // succeeds
        poApduRequests.add(poReadRecord1CmdBuild.getApduRequest()); // succeeds
        break;
      default:
        break;
    }

    return new CardRequest(poApduRequests);
  }

  public static StubSecureElement hoplinkSE() {

    return new StubSecureElement() {

      @Override
      public byte[] processApdu(byte[] apduIn) {
        addHexCommand(
            "00 A4 04 00 0A A0 00 00 02 91 A0 00 00 01 91 00",
            "6F25840BA000000291A00000019102A516BF0C13C70800000000C0E11FA653070A3C230C1410019000");

        addHexCommand("00 A4 04 00 0A A0 00 00 02 91 A0 00 00 01 92 00", "6A82");

        addHexCommand(
            "00 B2 01 A4 20",
            "00000000000000000000000000000000000000000000000000000000000000009000");

        return super.processApdu(apduIn);
      }

      @Override
      public byte[] getATR() {
        return ByteArrayUtil.fromHex("3B 8E 80 01 80 31 80 66 40 90 89 12 08 02 83 01 90 00 0B");
      }

      @Override
      public String getSeProtocol() {
        return "ISO_14443_4";
      }
    };
  }

  public static StubSecureElement revision1SE() {
    return new StubSecureElement() {
      @Override
      public byte[] processApdu(byte[] apduIn) {
        addHexCommand(
            "00 A4 04 00 0A A0 00 00 02 91 A0 00 00 01 91 00",
            "6F25840BA000000291A00000019102A516BF0C13C70800000000C0E11FA653070A3C230C1410019000");

        addHexCommand("00 A4 04 00 0A A0 00 00 02 91 A0 00 00 01 92 00", "6A82");

        addHexCommand(
            "00 B2 01 A4 20",
            "00000000000000000000000000000000000000000000000000000000000000009000");

        return super.processApdu(apduIn);
      }

      @Override
      public byte[] getATR() {
        return ByteArrayUtil.fromHex("3b 8f 80 01 80 5a 08 03 04 00 02 00 21 72 90 ff 82 90 00 f3");
      }

      @Override
      public String getSeProtocol() {
        return "CALYPSO_OLD_CARD_PRIME";
      }
    };
  }

  public static StubSecureElement noApduResponseSE() {
    return new StubSecureElement() {

      @Override
      public byte[] processApdu(byte[] apduIn) {

        addHexCommand(
            "00 A4 04 00 0A A0 00 00 02 91 A0 00 00 01 91 00",
            "6F25840BA000000291A00000019102A516BF0C13C70800000000C0E11FA653070A3C230C1410019000");

        return super.processApdu(apduIn);
      }

      @Override
      public byte[] getATR() {
        return ByteArrayUtil.fromHex("3B 8E 80 01 80 31 80 66 40 90 89 12 08 02 83 01 90 00 0B");
      }

      @Override
      public String getSeProtocol() {
        return "ISO_14443_4";
      }
    };
  }

  public static StubSecureElement partialSE() {
    return new StubSecureElement() {
      @Override
      public byte[] processApdu(byte[] apduIn) {

        addHexCommand(
            "00 A4 04 00 0A A0 00 00 02 91 A0 00 00 01 91 00",
            "6F25840BA000000291A00000019102A516BF0C13C70800000000C0E11FA653070A3C230C1410019000");
        addHexCommand(
            "00 B2 01 A4 00", "00000000000000000000000000000000000000000000000000000000009000");

        return super.processApdu(apduIn);
      }

      @Override
      public byte[] getATR() {
        return ByteArrayUtil.fromHex("3B 8E 80 01 80 31 80 66 40 90 89 12 08 02 83 01 90 00 0B");
      }

      @Override
      public String getSeProtocol() {
        return "ISO_14443_4";
      }
    };
  }

  public static StubSecureElement getSENoconnection() {
    return new StubSecureElement() {
      @Override
      public byte[] getATR() {
        return new byte[0];
      }

      @Override
      public boolean isPhysicalChannelOpen() {
        return false;
      }

      // override methods to fail open connection
      @Override
      public void openPhysicalChannel() {
        throw new KeypleReaderIOException("Impossible to establish connection");
      }

      @Override
      public void closePhysicalChannel() {
        throw new KeypleReaderIOException("Channel is not open");
      }

      @Override
      public byte[] processApdu(byte[] apduIn) {
        throw new KeypleReaderIOException("Error while transmitting apdu");
      }

      @Override
      public String getSeProtocol() {
        return "ISO_14443_4";
      }
    };
  }

  public static ApduRequest getApduSample() {
    return new ApduRequest(ByteArrayUtil.fromHex("FEDCBA98 9005h"), false);
  }

  public static void genericSelectSe(Reader reader) {
    /** Create a new local class extending AbstractSeSelectionRequest */
    class GenericSeSelectionRequest extends AbstractSeSelectionRequest {

      public GenericSeSelectionRequest(SeSelector seSelector) {
        super(seSelector);
      }

      @Override
      protected AbstractMatchingSe parse(SeResponse seResponse) {
        class GenericMatchingSe extends AbstractMatchingSe {
          public GenericMatchingSe(SeResponse selectionResponse) {
            super(selectionResponse);
          }
        }
        return new GenericMatchingSe(seResponse);
      }
    }

    SeSelection seSelection = new SeSelection();
    // SeSelection seSelection = new SeSelection(MultiSeRequestProcessing.PROCESS_ALL,
    // ChannelControl.CLOSE_AFTER);
    GenericSeSelectionRequest genericSeSelectionRequest =
        new GenericSeSelectionRequest(
            SeSelector.builder()
                .seProtocol(ContactlessCardCommonProtocols.ISO_14443_4.name())
                .atrFilter(new SeSelector.AtrFilter("3B.*"))
                .build());

    /* Prepare selector, ignore AbstractMatchingSe here */
    seSelection.prepareSelection(genericSeSelectionRequest);

    try {
      seSelection.processExplicitSelection(reader);
    } catch (KeypleException e) {
      Assert.fail("Unexpected exception");
    }
  }
}
