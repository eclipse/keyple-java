/* **************************************************************************************
 * Copyright (c) 2019 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.eclipse.keyple.core.seproxy.plugin.reader;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.eclipse.keyple.core.seproxy.exception.KeypleReaderIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the internal state of an AbstractObservableLocalReader Process InternalEvent against the
 * current state
 *
 * @since 0.9
 */
public class ObservableReaderStateService {

  /** logger */
  private static final Logger logger = LoggerFactory.getLogger(ObservableReaderStateService.class);

  /* AbstractObservableLocalReader to manage event and states */
  private final AbstractObservableLocalReader reader;

  /* Map of all instantiated states possible */
  private final Map<AbstractObservableState.MonitoringState, AbstractObservableState> states;

  /* Current currentState of the Observable Reader */
  private AbstractObservableState currentState;

  /* Single Executor to run State Service watch */
  private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

  private ObservableReaderStateService(
      AbstractObservableLocalReader reader,
      Map<AbstractObservableState.MonitoringState, AbstractObservableState> states,
      AbstractObservableState.MonitoringState initState) {
    this.states = states;
    this.reader = reader;
    switchState(initState);
  }

  /**
   * Thread safe method to communicate an internal event to this reader Use this method to inform
   * the reader of external event like a tag discovered or a Se inserted
   *
   * @param event internal event
   * @since 0.9
   */
  protected final synchronized void onEvent(AbstractObservableLocalReader.InternalEvent event) {
    this.currentState.onEvent(event);
  }

  /**
   * Thread safe method to switch the state of this reader should only be invoked by this reader or
   * its state
   *
   * @param stateId : next state to onActivate
   * @since 0.9
   */
  public final synchronized void switchState(AbstractObservableState.MonitoringState stateId) {

    if (currentState != null) {
      if (logger.isTraceEnabled()) {
        logger.trace(
            "[{}] Switch currentState from {} to {}",
            this.reader.getName(),
            this.currentState.getMonitoringState(),
            stateId);
      }
      currentState.onDeactivate();
    } else {
      if (logger.isTraceEnabled()) {
        logger.trace("[{}] Switch to a new currentState {}", this.reader.getName(), stateId);
      }
    }

    // switch currentState
    currentState = this.states.get(stateId);

    if (logger.isTraceEnabled()) {
      logger.trace(
          "[{}] New currentState {}", this.reader.getName(), currentState.getMonitoringState());
    }
    // onActivate the new current state
    currentState.onActivate();
  }

  /**
   * Get reader current state
   *
   * @return reader current state
   * @since 0.9
   */
  protected final synchronized AbstractObservableState getCurrentState() {
    return currentState;
  }

  /**
   * Get the reader current monitoring state
   *
   * @return current monitoring state
   * @since 0.9
   */
  public final synchronized AbstractObservableState.MonitoringState getCurrentMonitoringState() {
    return this.currentState.getMonitoringState();
  }

  /**
   * Builder porividing steps to configure ObservableReaderStateService
   *
   * @since 1.0
   */
  public static StartWithStep builder(AbstractObservableLocalReader reader) {
    return new Builder(reader);
  }

  /**
   * Provide step to setup starting state. Will be replaced soon
   *
   * @deprecated
   * @since 1.0
   */
  public interface StartWithStep {
    SeInsertionStep startWithStateWaitForStart();

    SeInsertionStep startWithStateWaitForSeInsertion();
  }

  /**
   * Provide step to setup the type of SeInsertion State detection
   *
   * @since 1.0
   */
  public interface SeInsertionStep {
    SeProcessingStep waitForSeInsertionWithNativeDetection();

    SeProcessingStep waitForSeInsertionWithSmartDetection();

    SeProcessingStep waitForSeInsertionWithPollingDetection();
  }

  /**
   * Provide step to setup the type of SeProcessing State detection
   *
   * @since 1.0
   */
  public interface SeProcessingStep {
    SeRemovalStep waitForSeProcessingWithNativeDetection();

    SeRemovalStep waitForSeProcessingWithSmartDetection();
  }

  /**
   * Provide step to setup the type of SeRemovale State detection
   *
   * @since 1.0
   */
  public interface SeRemovalStep {
    BuilderStep waitForSeRemovalWithNativeDetection();

    BuilderStep waitForSeRemovalWithSmartDetection();

    BuilderStep waitForSeRemovalWithPollingDetection();
  }

  /**
   * Provide the last step to build ObservableReaderStateService
   *
   * @since 1.0
   */
  public interface BuilderStep {
    ObservableReaderStateService build();
  }

  private static class Builder
      implements SeInsertionStep, SeProcessingStep, SeRemovalStep, BuilderStep, StartWithStep {

    private Map<AbstractObservableState.MonitoringState, AbstractObservableState> states =
        new HashMap<AbstractObservableState.MonitoringState, AbstractObservableState>();

    private AbstractObservableLocalReader reader;
    private AbstractObservableState.MonitoringState startState;

    private Builder(AbstractObservableLocalReader reader) {
      this.reader = reader;
      this.states.put(
          AbstractObservableState.MonitoringState.WAIT_FOR_START_DETECTION,
          new WaitForStartDetectState(this.reader));
    }

    /** @since 1.0 */
    @Override
    public SeInsertionStep startWithStateWaitForStart() {
      startState = AbstractObservableState.MonitoringState.WAIT_FOR_START_DETECTION;
      return this;
    }

    /** @since 1.0 */
    @Override
    public SeInsertionStep startWithStateWaitForSeInsertion() {
      startState = AbstractObservableState.MonitoringState.WAIT_FOR_SE_INSERTION;
      return this;
    }

    /**
     * Set up SeInsertionDetection with Native reader ability
     *
     * @return A non null reference
     * @since 1.0
     */
    @Override
    public SeProcessingStep waitForSeInsertionWithNativeDetection() {
      states.put(
          AbstractObservableState.MonitoringState.WAIT_FOR_SE_INSERTION,
          new WaitForSeInsertionState(this.reader));
      return this;
    }

    /**
     * Set up SeRemovalDetection with CardPresentMonitoringJob
     *
     * @return A non null reference
     * @since 1.0
     */
    @Override
    public SeProcessingStep waitForSeInsertionWithPollingDetection() {
      CardPresentMonitoringJob cardPresentMonitoringJob =
          new CardPresentMonitoringJob(reader, 200, true);
      states.put(
          AbstractObservableState.MonitoringState.WAIT_FOR_SE_INSERTION,
          new WaitForStartDetectState(this.reader, cardPresentMonitoringJob, executorService));
      return this;
    }

    /**
     * Set up SeInsertionDetection with SmartInsertionMonitoringJob
     *
     * @return A non null reference
     * @throws KeypleReaderIOException if reader does not implements SmartInsertionReader
     * @since 1.0
     */
    @Override
    public SeProcessingStep waitForSeInsertionWithSmartDetection() {
      if (!(reader instanceof SmartInsertionReader))
        throw new KeypleReaderIOException("Reader should implement SmartInsertionReader");
      final SmartInsertionMonitoringJob smartInsertionMonitoringJob =
          new SmartInsertionMonitoringJob((SmartInsertionReader) reader);
      states.put(
          AbstractObservableState.MonitoringState.WAIT_FOR_SE_INSERTION,
          new WaitForStartDetectState(this.reader, smartInsertionMonitoringJob, executorService));
      return this;
    }

    /**
     * Set up SeProcessingDetection with Native reader ability
     *
     * @return A non null reference
     * @since 1.0
     */
    @Override
    public SeRemovalStep waitForSeProcessingWithNativeDetection() {
      this.states.put(
          AbstractObservableState.MonitoringState.WAIT_FOR_SE_PROCESSING,
          new WaitForSeProcessingState(this.reader));
      return this;
    }

    /**
     * Set up SeProcessingDetection with SmartRemovalMonitoringJob
     *
     * @return A non null reference
     * @throws KeypleReaderIOException if reader does not implements SmartRemovalReader
     * @since 1.0
     */
    @Override
    public SeRemovalStep waitForSeProcessingWithSmartDetection() {
      if (!(reader instanceof SmartRemovalReader))
        throw new KeypleReaderIOException("Reader should implement SmartRemovalReader");
      final SmartRemovalMonitoringJob smartRemovalMonitoringJob =
          new SmartRemovalMonitoringJob((SmartRemovalReader) reader);
      this.states.put(
          AbstractObservableState.MonitoringState.WAIT_FOR_SE_PROCESSING,
          new WaitForSeProcessingState(this.reader, smartRemovalMonitoringJob, executorService));
      return this;
    }

    /**
     * Set up SeRemovalDetection with Native reader ability
     *
     * @return A non null reference
     * @since 1.0
     */
    @Override
    public BuilderStep waitForSeRemovalWithNativeDetection() {
      states.put(
          AbstractObservableState.MonitoringState.WAIT_FOR_SE_REMOVAL,
          new WaitForSeRemovalState(this.reader));
      return this;
    }

    /**
     * Set up SeRemovalDetection with CardAbsentPingMonitoringJob
     *
     * @return A non null reference
     * @since 1.0
     */
    @Override
    public BuilderStep waitForSeRemovalWithPollingDetection() {
      CardAbsentPingMonitoringJob cardAbsentPingMonitoringJob =
          new CardAbsentPingMonitoringJob(this.reader);
      states.put(
          AbstractObservableState.MonitoringState.WAIT_FOR_SE_REMOVAL,
          new WaitForSeRemovalState(this.reader, cardAbsentPingMonitoringJob, executorService));
      return this;
    }

    /**
     * Set up SeRemovalDetection with SmartRemovalMonitoringJob
     *
     * @return A non null reference
     * @throws KeypleReaderIOException if reader does not implements SmartRemovalReader
     * @since 1.0
     */
    @Override
    public BuilderStep waitForSeRemovalWithSmartDetection() {
      if (!(reader instanceof SmartRemovalReader))
        throw new KeypleReaderIOException("Reader should implement SmartRemovalReader");
      final SmartRemovalMonitoringJob smartRemovalMonitoringJob =
          new SmartRemovalMonitoringJob((SmartRemovalReader) reader);
      states.put(
          AbstractObservableState.MonitoringState.WAIT_FOR_SE_REMOVAL,
          new WaitForSeRemovalState(this.reader, smartRemovalMonitoringJob, executorService));
      return this;
    }

    /**
     * Build instance of ObservableReaderStateService
     *
     * @since 1.0
     */
    @Override
    public ObservableReaderStateService build() {
      if (startState == null) {
        startState = AbstractObservableState.MonitoringState.WAIT_FOR_START_DETECTION;
      }
      return new ObservableReaderStateService(reader, states, startState);
    }
  }
}
