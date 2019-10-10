/********************************************************************************
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information regarding copyright
 * ownership.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.keyple.core.seproxy.plugin;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.keyple.core.seproxy.ChannelState;
import org.eclipse.keyple.core.seproxy.MultiSeRequestProcessing;
import org.eclipse.keyple.core.seproxy.SeSelector;
import org.eclipse.keyple.core.seproxy.event.AbstractDefaultSelectionsRequest;
import org.eclipse.keyple.core.seproxy.event.ObservableReader;
import org.eclipse.keyple.core.seproxy.event.ReaderEvent;
import org.eclipse.keyple.core.seproxy.exception.*;
import org.eclipse.keyple.core.seproxy.message.*;
import org.eclipse.keyple.core.seproxy.protocol.SeProtocol;
import org.eclipse.keyple.core.util.ByteArrayUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Manage the loop processing for SeRequest transmission in a set and for SeResponse reception in a
 * set
 */
@SuppressWarnings({"PMD.TooManyMethods", "PMD.CyclomaticComplexity"})
public abstract class AbstractLocalReader extends AbstractReader {

    /** logger */
    private static final Logger logger = LoggerFactory.getLogger(AbstractLocalReader.class);

    /** predefined "get response" byte array */
    private static final byte[] getResponseHackRequestBytes = ByteArrayUtil.fromHex("00C0000000");

    /** logical channel status flag */
    private boolean logicalChannelIsOpen = false;

    /** current AID if any */
    private SeSelector.AidSelector.IsoAid aidCurrentlySelected;

    /** current selection status */
    private SelectionStatus currentSelectionStatus;

    /** Timestamp recorder */
    private long before;

    /** ==== Constructor =================================================== */

    /**
     * Reader constructor
     * <p>
     * Force the definition of a name through the use of super method.
     * <p>
     * Initialize the time measurement
     *
     * @param pluginName the name of the plugin that instantiated the reader
     * @param readerName the name of the reader
     */
    public AbstractLocalReader(String pluginName, String readerName) {
        super(pluginName, readerName);
        this.before = System.nanoTime(); /*
                                          * provides an initial value for measuring the
                                          * inter-exchange time. The first measurement gives the
                                          * time elapsed since the plugin was loaded.
                                          */
    }

    /** ==== Card presence management ====================================== */

    /**
     * Check the presence of a SE
     * <p>
     * This method is recommended for non-observable readers.
     * <p>
     * When the card is not present the logical and physical channels status may be refreshed
     * through a call to the processSeRemoved method.
     *
     * @return true if the SE is present
     */
    public final boolean isSePresent() throws NoStackTraceThrowable {
        if (checkSePresence()) {
            return true;
        } else {
            /*
             * if the SE is no longer present but one of the channels is still open, then the
             * SE_REMOVED notification is performed and the channels are closed.
             */
            if (isLogicalChannelOpen() || isPhysicalChannelOpen()) {
                processSeRemoved();
            }
            return false;
        }
    }

    /**
     * Wrapper for the native method of the plugin specific local reader to verify the presence of
     * the SE.
     * <p>
     * This method must be implemented by the ProxyReader plugin (e.g. Pcsc reader plugin).
     * <p>
     * This method is invoked by isSePresent.
     *
     * @return true if the SE is present
     * @throws NoStackTraceThrowable exception without stack trace
     */
    protected abstract boolean checkSePresence() throws NoStackTraceThrowable;

    /**
     * This method is invoked when a SE is inserted in the case of an observable reader.
     * <p>
     * e.g. from the monitoring thread in the case of a Pcsc plugin or from the NfcAdapter callback
     * method onTagDiscovered in the case of a Android NFC plugin.
     * <p>
     * It will fire an ReaderEvent in the following cases:
     * <ul>
     * <li>SE_INSERTED: if no default selection request was defined</li>
     * <li>SE_MATCHED: if a default selection request was defined in any mode and a SE matched the
     * selection</li>
     * <li>SE_INSERTED: if a default selection request was defined in ALWAYS mode but no SE matched
     * the selection (the DefaultSelectionsResponse is however transmitted)</li>
     * </ul>
     * <p>
     * It will do nothing if a default selection is defined in MATCHED_ONLY mode but no SE matched
     * the selection.
     * 
     * @return true if the notification was actually sent to the application, false if not
     */
    protected final boolean processSeInsertion() {
        boolean presenceNotified = false;
        if (defaultSelectionsRequest == null) {
            /* no default request is defined, just notify the SE insertion */
            notifyObservers(new ReaderEvent(this.pluginName, this.name,
                    ReaderEvent.EventType.SE_INSERTED, null));
            presenceNotified = true;
        } else {
            /*
             * a default request is defined, send it a notify according to the notification mode and
             * the selection status
             */
            boolean aSeMatched = false;
            try {
                List<SeResponse> seResponseList =
                        transmitSet(defaultSelectionsRequest.getSelectionSeRequestSet(),
                                defaultSelectionsRequest.getMultiSeRequestProcessing(),
                                defaultSelectionsRequest.getChannelState());

                for (SeResponse seResponse : seResponseList) {
                    if (seResponse != null && seResponse.getSelectionStatus().hasMatched()) {
                        aSeMatched = true;
                        break;
                    }
                }
                if (notificationMode == ObservableReader.NotificationMode.MATCHED_ONLY) {
                    /* notify only if a SE matched the selection, just ignore if not */
                    if (aSeMatched) {
                        notifyObservers(new ReaderEvent(this.pluginName, this.name,
                                ReaderEvent.EventType.SE_MATCHED,
                                new DefaultSelectionsResponse(seResponseList)));
                        presenceNotified = true;
                    }
                } else {
                    if (aSeMatched) {
                        /* The SE matched, notify an SE_MATCHED event with the received response */
                        notifyObservers(new ReaderEvent(this.pluginName, this.name,
                                ReaderEvent.EventType.SE_MATCHED,
                                new DefaultSelectionsResponse(seResponseList)));
                    } else {
                        /*
                         * The SE didn't match, notify an SE_INSERTED event with the received
                         * response
                         */
                        notifyObservers(new ReaderEvent(this.pluginName, this.name,
                                ReaderEvent.EventType.SE_INSERTED,
                                new DefaultSelectionsResponse(seResponseList)));
                    }
                    presenceNotified = true;
                }
            } catch (KeypleReaderException e) {
                /* the last transmission failed, close the logical and physical channels */
                closeLogicalAndPhysicalChannels();
                logger.debug("An IO Exception occurred while processing the default selection. {}",
                        e.getMessage());
                // in this case the card has been removed or not read correctly, do not throw event
            }
        }

        if (!presenceNotified) {
            // We close here the physical channel in case it has been opened for a SE outside the
            // expected SEs
            try {
                closePhysicalChannel();
            } catch (KeypleChannelStateException e) {
                logger.error("Error while closing physical channel. {}", e.getMessage());
            }
        }

        return presenceNotified;
    }


    /**
     * This method is invoked when a SE is removed in the case of an observable reader.
     * <p>
     * It will also be invoked if isSePresent is called and at least one of the physical or logical
     * channels is still open (case of a non-observable reader)
     * <p>
     * The SE will be notified removed only if it has been previously notified present (observable
     * reader only)
     *
     */
    protected final void processSeRemoved() {
        closeLogicalAndPhysicalChannels();
        notifyObservers(new ReaderEvent(this.pluginName, this.name,
                ReaderEvent.EventType.SE_REMOVED, null));
    }

    /** ==== Physical and logical channels management ====================== */

    /**
     * Close both logical and physical channels
     */
    private void closeLogicalAndPhysicalChannels() {
        closeLogicalChannel();
        try {
            closePhysicalChannel();
        } catch (KeypleChannelStateException e) {
            logger.trace("[{}] Exception occured in closeLogicalAndPhysicalChannels. Message: {}",
                    this.getName(), e.getMessage());
        }
    }

    /**
     * This abstract method must be implemented by the derived class in order to provide the SE ATR
     * when available.
     * <p>
     * Gets the SE Answer to reset
     *
     * @return ATR returned by the SE or reconstructed by the reader (contactless)
     */
    protected abstract byte[] getATR();

    /** ==== Physical and logical channels management ====================== */
    /* Selection management */

    /**
     * This method is dedicated to the case where no FCI data is available in return for the select
     * command.
     * <p>
     *
     * @param aidSelector used to retrieve the successful status codes from the main AidSelector
     * @return a ApduResponse containing the FCI
     */
    private ApduResponse recoverSelectionFciData(SeSelector.AidSelector aidSelector)
            throws KeypleIOReaderException {
        ApduResponse fciResponse;
        // Get Data APDU: CLA, INS, P1: always 0, P2: 0x6F FCI for the current DF, LC: 0
        byte[] getDataCommand = {(byte) 0x00, (byte) 0xCA, (byte) 0x00, (byte) 0x6F, (byte) 0x00};

        /*
         * The successful status codes list for this command is provided.
         */
        fciResponse = processApduRequest(new ApduRequest("Internal Get Data", getDataCommand, false,
                aidSelector.getSuccessfulSelectionStatusCodes()));

        if (!fciResponse.isSuccessful()) {
            logger.trace("[{}] selectionGetData => Get data failed. SELECTOR = {}", this.getName(),
                    aidSelector);
        }
        return fciResponse;
    }

    /**
     * Executes the selection application command and returns the requested data according to
     * AidSelector attributes.
     *
     * @param aidSelector the selection parameters
     * @return the response to the select application command
     * @throws KeypleIOReaderException if a reader error occurs
     */
    private ApduResponse processExplicitAidSelection(SeSelector.AidSelector aidSelector)
            throws KeypleIOReaderException {
        ApduResponse fciResponse;
        final byte[] aid = aidSelector.getAidToSelect().getValue();
        if (aid == null) {
            throw new IllegalArgumentException("AID must not be null for an AidSelector.");
        }
        if (logger.isTraceEnabled()) {
            logger.trace("[{}] openLogicalChannel => Select Application with AID = {}",
                    this.getName(), ByteArrayUtil.toHex(aid));
        }
        /*
         * build a get response command the actual length expected by the SE in the get response
         * command is handled in transmitApdu
         */
        byte[] selectApplicationCommand = new byte[6 + aid.length];
        selectApplicationCommand[0] = (byte) 0x00; // CLA
        selectApplicationCommand[1] = (byte) 0xA4; // INS
        selectApplicationCommand[2] = (byte) 0x04; // P1: select by name
        // P2: b0,b1 define the File occurrence, b2,b3 define the File control information
        // we use the bitmask defined in the respective enums
        selectApplicationCommand[3] = (byte) (aidSelector.getFileOccurrence().getIsoBitMask()
                | aidSelector.getFileControlInformation().getIsoBitMask());
        selectApplicationCommand[4] = (byte) (aid.length); // Lc
        System.arraycopy(aid, 0, selectApplicationCommand, 5, aid.length); // data
        selectApplicationCommand[5 + aid.length] = (byte) 0x00; // Le

        /*
         * we use here processApduRequest to manage case 4 hack. The successful status codes list
         * for this command is provided.
         */
        fciResponse = processApduRequest(new ApduRequest("Internal Select Application",
                selectApplicationCommand, true, aidSelector.getSuccessfulSelectionStatusCodes()));

        if (!fciResponse.isSuccessful()) {
            logger.trace("[{}] openLogicalChannel => Application Selection failed. SELECTOR = {}",
                    this.getName(), aidSelector);
        }
        return fciResponse;
    }

    /**
     * This abstract method must be implemented by the derived class in order to provide a selection
     * and ATR filtering mechanism.
     * <p>
     * The Selector provided in argument holds all the needed data to handle the Application
     * Selection and ATR matching process and build the resulting SelectionStatus.
     *
     * @param seSelector the SE selector
     * @return the SelectionStatus
     */

    /** ==== ATR filtering and application selection by AID ================ */

    /**
     * Build a select application command, transmit it to the SE and deduct the SelectionStatus.
     *
     * @param seSelector the targeted application SE selector
     * @return the SelectionStatus containing the actual selection result (ATR and/or FCI and the
     *         matching status flag).
     * @throws KeypleIOReaderException if a reader error occurs
     * @throws KeypleChannelStateException if a channel state exception occurs
     * @throws KeypleApplicationSelectionException if a selection exception occurs
     */
    protected SelectionStatus openLogicalChannel(SeSelector seSelector)
            throws KeypleIOReaderException, KeypleChannelStateException,
            KeypleApplicationSelectionException {
        byte[] atr = getATR();
        boolean selectionHasMatched = true;
        SelectionStatus selectionStatus;

        /** Perform ATR filtering if requested */
        if (seSelector.getAtrFilter() != null) {
            if (atr == null) {
                throw new KeypleIOReaderException("Didn't get an ATR from the SE.");
            }

            if (logger.isTraceEnabled()) {
                logger.trace("[{}] openLogicalChannel => ATR = {}", this.getName(),
                        ByteArrayUtil.toHex(atr));
            }
            if (!seSelector.getAtrFilter().atrMatches(atr)) {
                logger.info("[{}] openLogicalChannel => ATR didn't match. SELECTOR = {}, ATR = {}",
                        this.getName(), seSelector, ByteArrayUtil.toHex(atr));
                selectionHasMatched = false;
            }
        }

        /**
         * Perform application selection if requested and if ATR filtering matched or was not
         * requested
         */
        if (selectionHasMatched && seSelector.getAidSelector() != null) {
            ApduResponse fciResponse;
            if (this instanceof SmartSelectionReader) {
                fciResponse = ((SmartSelectionReader) this)
                        .openChannelForAid(seSelector.getAidSelector());
            } else {
                fciResponse = processExplicitAidSelection(seSelector.getAidSelector());
            }

            if (fciResponse.isSuccessful() && fciResponse.getDataOut().length == 0) {
                /**
                 * The selection didn't provide data (e.g. OMAPI), we get the FCI using a Get Data
                 * command.
                 * <p>
                 * The AID selector is provided to handle successful status word in the Get Data
                 * command.
                 */
                fciResponse = recoverSelectionFciData(seSelector.getAidSelector());
            }

            /*
             * The ATR filtering matched or was not requested. The selection status is determined by
             * the answer to the select application command.
             */
            selectionStatus = new SelectionStatus(new AnswerToReset(atr), fciResponse,
                    fciResponse.isSuccessful());
        } else {
            /*
             * The ATR filtering didn't match or no AidSelector was provided. The selection status
             * is determined by the ATR filtering.
             */
            selectionStatus = new SelectionStatus(new AnswerToReset(atr),
                    new ApduResponse(null, null), selectionHasMatched);
        }
        return selectionStatus;
    }


    /**
     * Open (if needed) a physical channel and try to establish a logical channel.
     * <p>
     * The logical opening is done either by sending a Select Application command (AID based
     * selection) or by checking the current ATR received from the SE (ATR based selection).
     * <p>
     * If the selection is successful, the logical channel is considered open. On the contrary, if
     * the selection fails, the logical channel remains closed.
     * <p>
     *
     * @param seSelector the SE Selector: either the AID of the application to select or an ATR
     *        selection regular expression
     * @return a {@link SelectionStatus} object containing the SE ATR, the SE FCI and a flag giving
     *         the selection process result. When ATR or FCI are not available, they are set to null
     *         but they can't be both null at the same time.
     * @throws KeypleIOReaderException if a reader error occurs
     * @throws KeypleChannelStateException if a channel state exception occurs
     * @throws KeypleApplicationSelectionException if a selection exception occurs
     */
    protected final SelectionStatus openLogicalChannelAndSelect(SeSelector seSelector)
            throws KeypleChannelStateException, KeypleIOReaderException,
            KeypleApplicationSelectionException {

        SelectionStatus selectionStatus;

        if (seSelector == null) {
            throw new KeypleChannelStateException("Try to open logical channel without selector.");
        }

        if (!isLogicalChannelOpen()) {
            /*
             * init of the physical SE channel: if not yet established, opening of a new physical
             * channel
             */
            if (!isPhysicalChannelOpen()) {
                openPhysicalChannel();
            }
            if (!isPhysicalChannelOpen()) {
                throw new KeypleChannelStateException("Fail to open physical channel.");
            }
        }

        selectionStatus = openLogicalChannel(seSelector);

        return selectionStatus;
    }

    /**
     * Attempts to open the physical channel
     *
     * @throws KeypleChannelStateException if the channel opening fails
     */
    protected abstract void openPhysicalChannel() throws KeypleChannelStateException;

    /**
     * Closes the current physical channel.
     * <p>
     * This method must be implemented by the ProxyReader plugin (e.g. Pcsc/Nfc/Omapi Reader).
     *
     * @throws KeypleChannelStateException if a reader error occurs
     */
    protected abstract void closePhysicalChannel() throws KeypleChannelStateException;

    /**
     * Tells if the physical channel is open or not
     * <p>
     * This method must be implemented by the ProxyReader plugin (e.g. Pcsc/Nfc/Omapi Reader).
     *
     * @return true is the channel is open
     */
    protected abstract boolean isPhysicalChannelOpen();

    /**
     * Tells if a logical channel is open
     *
     * @return true if the logical channel is open
     */
    final boolean isLogicalChannelOpen() {
        return logicalChannelIsOpen;
    }

    /**
     * Close the logical channel.
     */
    private void closeLogicalChannel() {
        logger.trace("[{}] closeLogicalChannel => Closing of the logical channel.", this.getName());
        logicalChannelIsOpen = false;
        aidCurrentlySelected = null;
        currentSelectionStatus = null;
    }

    /** ==== Protocol management =========================================== */

    /**
     * PO selection map associating seProtocols and selection strings.
     * <p>
     * The String associated with a particular protocol can be anything that is relevant to be
     * interpreted by reader plugins implementing protocolFlagMatches (e.g. ATR regex for Pcsc
     * plugins, technology name for Nfc plugins, etc).
     */
    protected final Map<SeProtocol, String> protocolsMap = new HashMap<SeProtocol, String>();

    /**
     * Defines the protocol setting Map to allow SE to be differentiated according to their
     * communication protocol.
     *
     * @param seProtocol the protocol key identifier to be added to the plugin internal list
     * @param protocolRule a string use to define how to identify the protocol
     */
    @Override
    public void addSeProtocolSetting(SeProtocol seProtocol, String protocolRule) {
        this.protocolsMap.put(seProtocol, protocolRule);
    }

    /**
     * Complete the current setting map with the provided map
     * 
     * @param protocolSetting the protocol setting map
     */
    public void setSeProtocolSetting(Map<SeProtocol, String> protocolSetting) {
        this.protocolsMap.putAll(protocolSetting);
    }

    /**
     * Test if the current protocol matches the provided protocol flag.
     * <p>
     * The method must be implemented by the ProxyReader plugin.
     * <p>
     * The protocol flag is used to retrieve from the protocolsMap the String used to differentiate
     * this particular protocol. (e.g. in PC/SC the only way to identify the SE protocol is to
     * analyse the ATR returned by the reader [ISO SE and memory card SE have specific ATR], in
     * Android Nfc the SE protocol can be deduced with the TagTechnology interface).
     * 
     * @param protocolFlag the protocol flag
     * @return true if the current protocol matches the provided protocol flag
     * @throws KeypleReaderException in case of a reader exception
     */
    protected abstract boolean protocolFlagMatches(SeProtocol protocolFlag)
            throws KeypleReaderException;

    /** ==== SeRequestSe and SeRequest transmission management ============= */

    /**
     * Do the transmission of all needed requestSet requests contained in the provided requestSet
     * according to the protocol flag selection logic. The responseSet responses are returned in the
     * responseSet object. The requestSet requests are ordered at application level and the
     * responses match this order. When a requestSet is not matching the current PO, the responseSet
     * responses pushed in the responseSet object is set to null.
     *
     * @param requestSet the request set
     * @param multiSeRequestProcessing the multi se processing mode
     * @param channelState indicates if the channel has to be closed at the end of the processing
     * @return the response list
     * @throws KeypleIOReaderException if a reader error occurs
     */
    protected final List<SeResponse> processSeRequestSet(Set<SeRequest> requestSet,
            MultiSeRequestProcessing multiSeRequestProcessing, ChannelState channelState)
            throws KeypleReaderException {

        boolean requestMatchesProtocol[] = new boolean[requestSet.size()];
        int requestIndex = 0, lastRequestIndex;

        // Determine which requests are matching the current ATR
        // All requests without selector are considered matching
        for (SeRequest request : requestSet) {
            SeSelector seSelector = request.getSeSelector();
            if (seSelector != null) {
                requestMatchesProtocol[requestIndex] =
                        protocolFlagMatches(request.getSeSelector().getSeProtocol());
            } else {
                requestMatchesProtocol[requestIndex] = true;
            }
            requestIndex++;
        }

        /*
         * we have now an array of booleans saying whether the corresponding request and the current
         * SE match or not
         */

        lastRequestIndex = requestIndex;
        requestIndex = 0;

        /*
         * The current requestSet is possibly made of several APDU command lists.
         *
         * If the requestMatchesProtocol is true we process the requestSet.
         *
         * If the requestMatchesProtocol is false we skip to the next requestSet.
         *
         * If keepChannelOpen is false, we close the physical channel for the last request.
         */
        List<SeResponse> responses = new ArrayList<SeResponse>();
        boolean stopProcess = false;
        for (SeRequest request : requestSet) {

            if (!stopProcess) {
                if (requestMatchesProtocol[requestIndex]) {
                    logger.debug("[{}] processSeRequestSet => transmit {}", this.getName(),
                            request);
                    SeResponse response;
                    try {
                        response = processSeRequestLogical(request);
                    } catch (KeypleReaderException ex) {
                        /*
                         * The process has been interrupted. We launch a KeypleReaderException with
                         * the responses collected so far.
                         */
                        /* Add the latest (and partial) SeResponse to the current list. */
                        responses.add(ex.getSeResponse());
                        /* Build a List of SeResponse with the available data. */
                        ex.setSeResponseSet(responses);
                        logger.debug(
                                "[{}] processSeRequestSet => transmit : process interrupted, collect previous responses {}",
                                this.getName(), responses);
                        throw ex;
                    }
                    responses.add(response);
                    logger.debug("[{}] processSeRequestSet => receive {}", this.getName(),
                            response);
                } else {
                    /*
                     * in case the protocolFlag of a SeRequest doesn't match the reader status, a
                     * null SeResponse is added to the SeResponse List.
                     */
                    responses.add(null);
                }
                if (multiSeRequestProcessing == MultiSeRequestProcessing.PROCESS_ALL) {
                    // multi SeRequest case: just close the logical channel and go on with the next
                    // selection.
                    closeLogicalChannel();
                } else {
                    if (isLogicalChannelOpen()) {
                        // the current PO matches the selection case, we stop here.
                        stopProcess = true;
                    }
                }
                requestIndex++;
                if (lastRequestIndex == requestIndex) {
                    if (!(channelState == ChannelState.KEEP_OPEN)) {
                        // close logical channel unconditionally
                        closeLogicalChannel();
                        if (!(this instanceof ObservableReader)
                                || (((ObservableReader) this).countObservers() == 0)) {
                            /*
                             * Not observable/observed: close immediately the physical channel if
                             * requested
                             */
                            closePhysicalChannel();
                        }

                        if (this instanceof ThreadedMonitoringReader) {
                            /*
                             * request the removal sequence when the reader is monitored by a thread
                             */
                            if (thread != null) {
                                thread.startRemoval(channelState);
                            }
                        }
                    }
                }
            }
        }
        return responses;
    }

    /**
     * Executes a request made of one or more Apdus and receives their answers. The selection of the
     * application is handled.
     * <p>
     * The physical channel is closed if requested.
     *
     * @param seRequest the SeRequest (null if only the closing of the physical channel is
     *        requested)
     * @param channelState indicates if the channel has to be closed at the end of the processing
     * @return the SeResponse to the SeRequest
     * @throws KeypleReaderException if a transmission fails
     */
    @SuppressWarnings({"PMD.ModifiedCyclomaticComplexity", "PMD.CyclomaticComplexity",
            "PMD.StdCyclomaticComplexity", "PMD.NPathComplexity", "PMD.ExcessiveMethodLength"})
    protected final SeResponse processSeRequest(SeRequest seRequest, ChannelState channelState)
            throws IllegalStateException, KeypleReaderException {

        SeResponse seResponse = null;

        /* The SeRequest may be null when we just need to close the physical channel */
        if (seRequest != null) {
            seResponse = processSeRequestLogical(seRequest);
        }

        if (!(channelState == ChannelState.KEEP_OPEN)) {
            // close logical channel unconditionally
            closeLogicalChannel();
            if (!(this instanceof ObservableReader)
                    || (((ObservableReader) this).countObservers() == 0)) {
                /* Not observable/observed: close immediately the physical channel if requested */
                closePhysicalChannel();
            }

            if (this instanceof ThreadedMonitoringReader) {
                /* request the removal sequence when the reader is monitored by a thread */
                if (thread != null) {
                    thread.startRemoval(channelState);
                }
            }
        }

        return seResponse;
    }

    /**
     * Implements the logical processSeRequest.
     * <p>
     * This method is called by processSeRequestSet and processSeRequest.
     * <p>
     * It opens both physical and logical channels if needed.
     * <p>
     * The logical channel is closed when requested.
     *
     * @param seRequest the {@link SeRequest} to be sent
     * @return seResponse
     * @throws IllegalStateException
     * @throws KeypleReaderException
     */
    private SeResponse processSeRequestLogical(SeRequest seRequest)
            throws IllegalStateException, KeypleReaderException {
        boolean previouslyOpen = true;
        SelectionStatus selectionStatus = null;

        List<ApduResponse> apduResponseList = new ArrayList<ApduResponse>();

        logger.trace("[{}] processSeRequest => Logical channel open = {}", this.getName(),
                isLogicalChannelOpen());
        /*
         * unless the selector is null, we try to open a logical channel; if the channel was open
         * and the PO is still matching we won't redo the selection and just use the current
         * selection status
         */
        if (seRequest.getSeSelector() != null) {
            /* check if AID changed if the channel is already open */
            if (isLogicalChannelOpen() && seRequest.getSeSelector().getAidSelector() != null) {
                /*
                 * AID comparison hack: we check here if the initial selection AID matches the
                 * beginning of the AID provided in the SeRequest (coming from FCI data and supposed
                 * to be longer than the selection AID).
                 *
                 * The current AID (selector) length must be at least equal or greater than the
                 * selection AID. All bytes of the selection AID must match the beginning of the
                 * current AID.
                 */
                if (aidCurrentlySelected == null) {
                    throw new IllegalStateException("AID currently selected shouldn't be null.");
                }
                if (seRequest.getSeSelector().getAidSelector()
                        .getFileOccurrence() == SeSelector.AidSelector.FileOccurrence.NEXT) {
                    if (logger.isTraceEnabled()) {
                        logger.trace(
                                "[{}] processSeRequest => The current selection is a next selection, close the "
                                        + "logical channel.",
                                this.getName());
                    }
                    /* close the channel (will reset the current selection status) */
                    closeLogicalChannel();
                } else if (!aidCurrentlySelected
                        .startsWith(seRequest.getSeSelector().getAidSelector().getAidToSelect())) {
                    // the AID changed (longer or different), close the logical channel
                    if (logger.isTraceEnabled()) {
                        logger.trace(
                                "[{}] processSeRequest => The AID changed, close the logical channel. AID = {}, EXPECTEDAID = {}",
                                this.getName(),
                                ByteArrayUtil.toHex(aidCurrentlySelected.getValue()),
                                seRequest.getSeSelector());
                    }
                    /* close the channel (will reset the current selection status) */
                    closeLogicalChannel();
                }
                /* keep the current selection status (may be null if the current PO didn't match) */
                selectionStatus = currentSelectionStatus;
            }

            /* open the channel and do the selection if needed */
            if (!isLogicalChannelOpen()) {
                previouslyOpen = false;

                try {
                    selectionStatus = openLogicalChannelAndSelect(seRequest.getSeSelector());
                    logger.trace("[{}] processSeRequest => Logical channel opening success.",
                            this.getName());
                } catch (KeypleApplicationSelectionException e) {
                    logger.trace("[{}] processSeRequest => Logical channel opening failure",
                            this.getName());
                    closeLogicalChannel();
                    /* return a null SeResponse when the opening of the logical channel failed */
                    return null;
                }

                if (selectionStatus.hasMatched()) {
                    /* The selection process succeeded, the logical channel is open */
                    logicalChannelIsOpen = true;

                    if (selectionStatus.getFci().isSuccessful()) {
                        /* the selection AID based was successful, keep the aid */
                        aidCurrentlySelected =
                                seRequest.getSeSelector().getAidSelector().getAidToSelect();
                    }
                    currentSelectionStatus = selectionStatus;
                } else {
                    /* The selection process failed, close the logical channel */
                    closeLogicalChannel();
                }
            }
        } else {
            /* selector is null, we expect that the logical channel was previously opened */
            if (!isLogicalChannelOpen()) {
                throw new IllegalStateException(
                        "[" + this.getName() + "] processSeRequest => No logical channel opened!");
            }
        }

        /* process request if not empty */
        if (seRequest.getApduRequests() != null) {
            for (ApduRequest apduRequest : seRequest.getApduRequests()) {
                try {
                    apduResponseList.add(processApduRequest(apduRequest));
                } catch (KeypleIOReaderException ex) {
                    /*
                     * The process has been interrupted. We close the logical channel and launch a
                     * KeypleReaderException with the Apdu responses collected so far.
                     */
                    logger.debug(
                            "The process has been interrupted, collect Apdu responses collected so far");
                    closeLogicalAndPhysicalChannels();
                    ex.setSeResponse(new SeResponse(false, previouslyOpen, selectionStatus,
                            apduResponseList));
                    throw ex;
                }
            }
        }

        return new SeResponse(isLogicalChannelOpen(), previouslyOpen, selectionStatus,
                apduResponseList);
    }

    /** ==== APDU transmission management ================================== */

    /**
     * Transmits an ApduRequest and receives the ApduResponse
     * <p>
     * The time measurement is carried out and logged with the detailed information of the exchanges
     * (TRACE level).
     *
     * @param apduRequest APDU request
     * @return APDU response
     * @throws KeypleIOReaderException Exception faced
     */
    protected final ApduResponse processApduRequest(ApduRequest apduRequest)
            throws KeypleIOReaderException {
        ApduResponse apduResponse;
        if (logger.isTraceEnabled()) {
            long timeStamp = System.nanoTime();
            double elapsedMs = (double) ((timeStamp - before) / 100000) / 10;
            this.before = timeStamp;
            logger.trace("[{}] processApduRequest => {}, elapsed {} ms.", this.getName(),
                    apduRequest, elapsedMs);
        }

        byte[] buffer = apduRequest.getBytes();
        apduResponse =
                new ApduResponse(transmitApdu(buffer), apduRequest.getSuccessfulStatusCodes());

        if (apduRequest.isCase4() && apduResponse.getDataOut().length == 0
                && apduResponse.isSuccessful()) {
            // do the get response command but keep the original status code
            apduResponse = case4HackGetResponse(apduResponse.getStatusCode());
        }

        if (logger.isTraceEnabled()) {
            long timeStamp = System.nanoTime();
            double elapsedMs = (double) ((timeStamp - before) / 100000) / 10;
            this.before = timeStamp;
            logger.trace("[{}] processApduRequest => {}, elapsed {} ms.", this.getName(),
                    apduResponse, elapsedMs);
        }
        return apduResponse;
    }

    /**
     * Execute a get response command in order to get outgoing data from specific cards answering
     * 9000 with no data although the command has outgoing data. Note that this method relies on the
     * right get response management by transmitApdu
     *
     * @param originalStatusCode the status code of the command that didn't returned data
     * @return ApduResponse the response to the get response command
     * @throws KeypleIOReaderException if the transmission fails.
     */
    private ApduResponse case4HackGetResponse(int originalStatusCode)
            throws KeypleIOReaderException {
        /*
         * build a get response command the actual length expected by the SE in the get response
         * command is handled in transmitApdu
         */
        if (logger.isTraceEnabled()) {
            long timeStamp = System.nanoTime();
            double elapsedMs = (double) ((timeStamp - this.before) / 100000) / 10;
            this.before = timeStamp;
            logger.trace(
                    "[{}] case4HackGetResponse => ApduRequest: NAME = \"Internal Get Response\", RAWDATA = {}, elapsed = {}",
                    this.getName(), ByteArrayUtil.toHex(getResponseHackRequestBytes), elapsedMs);
        }

        byte[] getResponseHackResponseBytes = transmitApdu(getResponseHackRequestBytes);

        /* we expect here a 0x9000 status code */
        ApduResponse getResponseHackResponse = new ApduResponse(getResponseHackResponseBytes, null);

        if (logger.isTraceEnabled()) {
            long timeStamp = System.nanoTime();
            double elapsedMs = (double) ((timeStamp - this.before) / 100000) / 10;
            this.before = timeStamp;
            logger.trace("[{}] case4HackGetResponse => Internal {}, elapsed {} ms.", this.getName(),
                    getResponseHackResponseBytes, elapsedMs);
        }

        if (getResponseHackResponse.isSuccessful()) {
            // replace the two last status word bytes by the original status word
            getResponseHackResponseBytes[getResponseHackResponseBytes.length - 2] =
                    (byte) (originalStatusCode >> 8);
            getResponseHackResponseBytes[getResponseHackResponseBytes.length - 1] =
                    (byte) (originalStatusCode & 0xFF);
        }
        return getResponseHackResponse;
    }

    /**
     * Transmits a single APDU and receives its response.
     * <p>
     * This abstract method must be implemented by the ProxyReader plugin (e.g. Pcsc, Nfc). The
     * implementation must handle the case where the SE response is 61xy and execute the appropriate
     * get response command.
     *
     * @param apduIn byte buffer containing the ingoing data
     * @return apduResponse byte buffer containing the outgoing data.
     * @throws KeypleIOReaderException if the transmission fails
     */
    protected abstract byte[] transmitApdu(byte[] apduIn) throws KeypleIOReaderException;

    /** ==== SE detection and default selection assignment ================================== */

    /**
     * Start of SE detection with advanced selection mechanism.
     * <p>
     * If defined, the prepared DefaultSelectionRequest will be processed as soon as a SE is
     * inserted. The result of this request set will be added to the reader event notified to the
     * application.
     * <p>
     * If it is not defined (set to null), a simple SE detection will be notified in the end.
     * <p>
     * Depending on the notification mode, the observer will be notified whenever an SE is inserted,
     * regardless of the selection status, or only if the current SE matches the selection criteria.
     * <p>
     * In addition, in the case of a {@link ThreadedMonitoringReader} the observation thread will be
     * notified of request to start the SE insertion monitoring (change from the
     * WAIT_FOR_START_DETECTION state to WAIT_FOR_SE_INSERTION).
     * <p>
     * An {@link java.lang.IllegalStateException} exception will be thrown if no observers have been
     * recorded for this reader (see startMonitoring).
     *
     * @param defaultSelectionsRequest the {@link AbstractDefaultSelectionsRequest} to be executed
     *        when a SE is inserted
     * @param notificationMode the notification mode enum (ALWAYS or MATCHED_ONLY)
     */
    public void setDefaultSelectionRequest(
            AbstractDefaultSelectionsRequest defaultSelectionsRequest,
            ObservableReader.NotificationMode notificationMode) {
        this.defaultSelectionsRequest = (DefaultSelectionsRequest) defaultSelectionsRequest;
        this.notificationMode = notificationMode;
        // unleash the monitoring thread to initiate SE detection (if available and needed)
        if (this instanceof ThreadedMonitoringReader && thread != null) {
            thread.startDetection();
        }
    }

    /** ==== Observability management ======================================================= */

    private static final AtomicInteger threadCount = new AtomicInteger();

    /**
     * Add a reader observer.
     * <p>
     * The observer will receive all the events produced by this reader (card insertion, removal,
     * etc.)
     * <p>
     * In the case of a {@link ThreadedMonitoringReader}, a thread is created if it does not already
     * exist (when the first observer is added).
     *
     * @param observer the observer object
     */
    public final void addObserver(ObservableReader.ReaderObserver observer) {
        super.addObserver(observer);
        // if an observer is added to an empty list, start the observation
        if (super.countObservers() == 1) {
            if (this instanceof ThreadedMonitoringReader) {
                logger.debug("Start monitoring the reader {}", this.getName());
                thread = new EventThread(this.getPluginName(), this.getName());
                thread.start();
            }
        }
    }

    /**
     * Remove a reader observer.
     * <p>
     * The observer will not receive any of the events produced by this reader.
     * <p>
     * Terminate the monitoring thread if {@link ThreadedMonitoringReader}.
     * <p>
     * The thread is created if it does not already exist
     *
     * @param observer the observer object
     */
    public final void removeObserver(ObservableReader.ReaderObserver observer) {
        if (super.countObservers() == 0) {
            if (this instanceof ThreadedMonitoringReader) {
                if (thread != null) {
                    logger.debug("Stop the reader monitoring.");
                    thread.end();
                }
            }
        }
        super.removeObserver(observer);
    }

    /**
     * Remove all the observers of the reader
     */
    @Override
    public void clearObservers() {
        super.clearObservers();
        if (thread != null) {
            logger.debug("Stop the reader monitoring.");
            thread.end();
        }
    }

    /*
     * ===========================================================================================
     * Monitoring Thread (used by readers who implement the ThreadedMonitoringReader interface)
     *
     * The following fields and methods are dedicated to the plugin readers implementing the
     * ThreadedMonitoringReader interface
     * ===========================================================================================
     */

    private EventThread thread;

    /**
     * Thread wait timeout in ms
     * <p>
     * This value will be used to avoid infinite waiting time. When set to 0 (default), no timeout
     * check is performed.
     * <p>
     * See setThreadWaitTimeout method.
     */
    private long threadWaitTimeout = 0;


    /**
     * Setter to fix the wait timeout in ms.
     * <p>
     * It is advised to set a relatively high value (e. g. 120000) to avoid disturbing the nominal
     * operation.
     * 
     * @param timeout Timeout to use when the monitoring thread is in the WAIT_FOR_SE_PROCESSING and
     *        WAIT_FOR_SE_REMOVAL states.
     */
    protected final void setThreadWaitTimeout(long timeout) {
        this.threadWaitTimeout = timeout;
    }

    private final int WAIT_FOR_SE_DETECTION_EXIT_LATENCY = 10; // TODO make it configurable
    private final int WAIT_FOR_SE_INSERTION_EXIT_LATENCY = 10; // TODO make it configurable
    private final int WAIT_FOR_SE_PROCESSING_EXIT_LATENCY = 10; // TODO make it configurable
    private final int WAIT_FOR_SE_REMOVAL_EXIT_LATENCY = 10; // TODO make it configurable



    /* The states that a ThreadedMonitoringReader can have */
    private enum MonitoringState {
        WAIT_FOR_START_DETECTION, WAIT_FOR_SE_INSERTION, WAIT_FOR_SE_PROCESSING, WAIT_FOR_SE_REMOVAL
    }

    /**
     * Thread in charge of reporting live events about reader events such as card insertion and
     * removal.
     *
     * The thread state machine is necessarily in one of these four states:
     *
     * 1. WAIT_FOR_START_DETECTION:
     *
     * infinitely waiting for a signal from the application to start SE detection by changing to
     * WAIT_FOR_SE_INSERTION state. This signal is given by calling the setDefaultSelectionRequest
     * method. Note: The system always starts directly in the WAIT_FOR_SE_INSERTION state.
     *
     * 2. WAIT_FOR_SE_INSERTION:
     *
     * awaiting the SE insertion. After insertion, the processSeInsertion method is called.
     * 
     * A number of cases arise:
     *
     * # A default selection is defined: in this case it is played and its result leads to an event
     * notification SE_INSERTED or SE_MATCHED or no event (see setDefaultSelectionRequest)
     *
     * # There is no default selection: a SE_INSERTED event is then notified.
     *
     * In the case where an event has been notified to the application, the state machine changes to
     * the WAIT_FOR_SE_PROCESSING state otherwise it changes to the WAIT_FOR_SE_REMOVAL state.
     *
     * The notification consists in calling the "update" methods of the defined observers. In the
     * case where several observers have been defined, it is up to the application developer to
     * ensure that there is no long processing in these methods, by making their execution
     * asynchronous for example.
     *
     * 3. WAIT_FOR_SE_PROCESSING:
     *
     * waiting for the end of processing by the application. The end signal is triggered either by a
     * transmission made with a CLOSE_AND_CONTINUE or CLOSE_AND_AND_STOP parameter, or by an
     * explicit call to the notifySeProcessed method (if the latter is called when a "CLOSE"
     * transmission has already been made, it will do nothing, otherwise it will make a pseudo
     * transmission intended only for closing channels).
     *
     * If the instruction given is CLOSE_AND_STOP then the logical and physical channels are closed
     * immediately and the Machine to state changes to WAIT_FOR_START_DETECTION state.
     *
     * If the instruction given is CLOSE_AND_CONTINUE then the state machine changes to
     * WAIT_FOR_SE_REMOVAL.
     *
     * A timeout management is also optionally present in order to avoid a lock in this waiting
     * state due to a failure of the application that would have prevented it from notifying the end
     * of SE processing (see setThreadWaitTimeout).
     *
     * 4. WAIT_FOR_SE_REMOVAL:
     *
     * attente du retrait du SE. À l'issue de cette attente un événement SE_REMOVED est notifié à
     * l'application et la machine à état passe à l'état WAIT_FOR_SE_INSERTION
     *
     * A timeout management is also optionally present in order to avoid a lock in this waiting
     * state due to a SE forgotten on the reader.
     */
    private class EventThread extends Thread {
        /**
         * Plugin name
         */
        private final String pluginName;

        /**
         * Reader that we'll report about
         */
        private final String readerName;


        /**
         * If the thread should be kept a alive
         */
        private volatile boolean running = true;

        /**
         * Current reader state
         */
        private MonitoringState monitoringState = MonitoringState.WAIT_FOR_SE_INSERTION;

        /**
         * previous state (logging purposes)
         */
        private MonitoringState previousState = MonitoringState.WAIT_FOR_SE_INSERTION;

        /**
         * Synchronization objects and flags TODO Improve this mechanism by using classes and
         * methods of the java.util.concurrent package
         */
        private final Object waitForStartDetectionSync = new Object();
        private final Object waitForSeProcessing = new Object();

        // these flags help to distinguish notify and timeout when wait is exited.
        private boolean startDetectionNotified = false;
        private boolean seProcessingNotified = false;

        ChannelState channelStateAction = ChannelState.KEEP_OPEN;

        /**
         * Constructor
         *
         * @param pluginName name of the plugin that instantiated the reader
         * @param readerName name of the reader who owns this thread
         */
        EventThread(String pluginName, String readerName) {
            super("observable-reader-events-" + threadCount.addAndGet(1));
            setDaemon(true);
            this.pluginName = pluginName;
            this.readerName = readerName;
        }

        /**
         * Marks the thread as one that should end when the last cardWaitTimeout occurs
         */
        void end() {
            running = false;
            this.interrupt(); // exit io wait if needed
        }

        /**
         * Makes the current change from WAIT_FOR_START_DETECTION to WAIT_FOR_SE_INSERTION
         */
        void startDetection() {
            startDetectionNotified = true;
            synchronized (waitForStartDetectionSync) {
                waitForStartDetectionSync.notify();
            }
        }

        /**
         * Makes the current change from WAIT_FOR_SE_PROCESSING to WAIT_FOR_SE_REMOVAL Handle the
         * signal from the application to terminate the operations with the current SE (ChannelState
         * set to CLOSE_AND_CONTINUE or CLOSE_AND_STOP).
         * <p>
         * We handle here two different cases:
         * </p>
         * <ul>
         * <li>the notification is executed in the same thread (reader monitoring thread): in this
         * case the seProcessingNotified flag is set when processSeInsertion/notifyObservers/update
         * ends. The monitoring thread can continue without having to wait for the end of the SE
         * processing.</li>
         * <li>the notification is executed in a separate thread: in this case the
         * processSeInsertion method will have finished before the end of the SE processing and the
         * reader monitoring thread is already waiting with the waitForRemovalSync object. Here we
         * release the waitForRemovalSync object by calling its notify method.</li>
         * </ul>
         */
        void startRemoval(ChannelState channelState) {
            channelStateAction = channelState;
            seProcessingNotified = true;
            synchronized (waitForSeProcessing) {
                waitForSeProcessing.notify();
            }
        }

        /**
         * Thread loop
         */
        public void run() {
            long startTime; // timeout management
            while (running) {
                logger.trace("Reader state machine: previous {}, new {}", previousState,
                        monitoringState);
                previousState = monitoringState;
                try {
                    switch (monitoringState) {
                        case WAIT_FOR_START_DETECTION:
                            // We are waiting for the application to start monitoring SE insertions
                            // with
                            // the call to setDefaultSelectionRequest.
                            // We notify the application of the current state.
                            // notifyObservers(
                            // new ReaderEvent(this.pluginName, AbstractLocalReader.this.name,
                            // ReaderEvent.EventType.AWAITING_SE_START_DETECTION, null));

                            // to distinguish between timeout and notification
                            startDetectionNotified = false;

                            // Loop until we are notified (call to setDefaultSelectionRequest) or
                            // interrupted (call to end)
                            while (true) {
                                synchronized (waitForStartDetectionSync) {
                                    // sleep a little
                                    waitForStartDetectionSync
                                            .wait(WAIT_FOR_SE_DETECTION_EXIT_LATENCY);
                                }
                                if (startDetectionNotified) {
                                    // the application has requested the start of monitoring
                                    monitoringState = MonitoringState.WAIT_FOR_SE_INSERTION;
                                    // exit loop
                                    break;
                                }
                                if (Thread.interrupted()) {
                                    // a request to stop the thread has been made
                                    running = false;
                                    // exit loop
                                    break;
                                }
                            }
                            // exit switch
                            break;
                        case WAIT_FOR_SE_INSERTION:
                            // We are waiting for the reader to inform us that a card is inserted.
                            while (true) {
                                if (((SmartInsertionReader) AbstractLocalReader.this)
                                        .waitForCardPresent(WAIT_FOR_SE_INSERTION_EXIT_LATENCY)) {
                                    seProcessingNotified = false;
                                    // a SE has been inserted, the following process
                                    // (processSeInsertion) will end with a SE_INSERTED or
                                    // SE_MATCHED notification according to the
                                    // DefaultSelectionRequest.
                                    // If a DefaultSelectionRequest is set with the MATCHED_ONLY
                                    // flag and the SE presented does not match, then the
                                    // processSeInsertion method will return false to indicate
                                    // that this SE can be ignored.
                                    if (processSeInsertion()) {
                                        // Note: the notification to the application was made by
                                        // processSeInsertion
                                        // We'll wait for the end of its processing
                                        monitoringState = MonitoringState.WAIT_FOR_SE_PROCESSING;
                                    } else {
                                        // An unexpected SE has been detected, we wait for its
                                        // removal
                                        monitoringState = MonitoringState.WAIT_FOR_SE_REMOVAL;
                                    }
                                    // exit loop
                                    break;
                                }
                                if (Thread.interrupted()) {
                                    // a request to stop the thread has been made
                                    running = false;
                                    // exit loop
                                    break;
                                }
                            }
                            // exit switch
                            break;
                        case WAIT_FOR_SE_PROCESSING:
                            // loop until notification of the end of the SE processing operation, an
                            // SE withdrawal or a request to stop is made.
                            // An global timeout period is also checked to avoid infinite waiting;
                            // exceeding the time limit leads to the notification of an
                            // TIMEOUT_ERROR
                            // event and stops monitoring
                            startTime = System.currentTimeMillis();
                            while (true) {
                                if (seProcessingNotified) {
                                    // the application has completed the processing, we move to the
                                    // SE
                                    switch (channelStateAction) {
                                        case CLOSE_AND_CONTINUE:
                                            monitoringState = MonitoringState.WAIT_FOR_SE_REMOVAL;
                                            break;
                                        case CLOSE_AND_STOP:
                                            // We close the channels now and notify the application
                                            // of the SE_REMOVED event.
                                            processSeRemoved();
                                            monitoringState =
                                                    MonitoringState.WAIT_FOR_START_DETECTION;
                                            break;
                                        case KEEP_OPEN:
                                            throw new IllegalStateException(
                                                    "Unexcepted KEEP_OPEN action.");
                                    }
                                    // exit loop
                                    break;
                                }
                                if (AbstractLocalReader.this instanceof SmartPresenceReader
                                        && !isSePresent()) {
                                    // the SE has been removed, we return to the state of waiting
                                    // for insertion
                                    // We notify the application of the SE_REMOVED event.
                                    processSeRemoved();
                                    monitoringState = MonitoringState.WAIT_FOR_SE_INSERTION;
                                    // exit loop
                                    break;
                                }
                                if (Thread.interrupted()) {
                                    // a request to stop the thread has been made
                                    running = false;
                                    // exit loop
                                    break;
                                }
                                if (threadWaitTimeout != 0 && System.currentTimeMillis()
                                        - startTime > threadWaitTimeout) {
                                    // We notify the application of the TIMEOUT_ERROR event.
                                    notifyObservers(new ReaderEvent(this.pluginName,
                                            AbstractLocalReader.this.name,
                                            ReaderEvent.EventType.TIMEOUT_ERROR, null));
                                    logger.error(
                                            "The SE's processing time has exceeded the specified limit.");
                                    monitoringState = MonitoringState.WAIT_FOR_START_DETECTION;
                                    // exit loop
                                    break;
                                }
                                synchronized (waitForSeProcessing) {
                                    // sleep a little
                                    waitForSeProcessing.wait(WAIT_FOR_SE_PROCESSING_EXIT_LATENCY);
                                }
                            }
                            // exit switch
                            break;
                        case WAIT_FOR_SE_REMOVAL:
                            // We are waiting for the reader to inform us when a card is inserted.
                            // An global timeout period is also checked to avoid infinite waiting;
                            // exceeding the time limit leads to the notification of an
                            // TIMEOUT_ERROR
                            // event and stops monitoring
                            startTime = System.currentTimeMillis();
                            while (true) {
                                if (((AbstractLocalReader.this instanceof SmartPresenceReader)
                                        && ((SmartPresenceReader) AbstractLocalReader.this)
                                                .waitForCardAbsentNative(
                                                        WAIT_FOR_SE_REMOVAL_EXIT_LATENCY))
                                        || (!(AbstractLocalReader.this instanceof SmartPresenceReader))
                                                && isSePresentPing()) {
                                    // the SE has been removed, we close all channels and return to
                                    // the state of waiting
                                    // for insertion
                                    // We notify the application of the SE_REMOVED event.
                                    processSeRemoved();
                                    monitoringState = MonitoringState.WAIT_FOR_SE_INSERTION;
                                    // exit loop
                                    break;
                                }
                                if (threadWaitTimeout != 0 && System.currentTimeMillis()
                                        - startTime > threadWaitTimeout) {
                                    // We notify the application of the TIMEOUT_ERROR event.
                                    notifyObservers(new ReaderEvent(this.pluginName,
                                            AbstractLocalReader.this.name,
                                            ReaderEvent.EventType.TIMEOUT_ERROR, null));
                                    monitoringState = MonitoringState.WAIT_FOR_START_DETECTION;
                                    logger.error(
                                            "The time limit for the removal of the SE has been exceeded.");
                                    // exit loop
                                    break;
                                }
                            }
                            // exit switch
                            break;
                    }
                } catch (InterruptedException ex) {
                    logger.debug("Exiting monitoring thread.");
                    running = false;
                } catch (NoStackTraceThrowable e) {
                    logger.trace("[{}] Exception occurred in monitoring thread: {}", readerName,
                            e.getMessage());
                    running = false;
                }
            }
        }
    }

    /**
     * Sends a neutral APDU to the SE to check its presence
     * <p>
     * This method has to be called regularly until the SE no longer respond.
     * 
     * @return true if the SE still responds, false if not
     */
    protected boolean isSePresentPing() {
        // APDU sent to check the communication with the PO
        final byte[] apdu = {(byte) 0x00, (byte) 0xC0, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        // transmits the APDU and checks for the IO exception.
        try {
            transmitApdu(apdu);
        } catch (KeypleIOReaderException e) {
            logger.trace("[{}] Exception occured in isSePresentPing. Message: {}", this.getName(),
                    e.getMessage());
            return false;
        }
        // in case the communication is successful we sleep a little to avoid too intensive
        // processing.
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            // forwards the exception upstairs
            Thread.currentThread().interrupt();
        }
        return true;
    }

    /**
     * Called when the class is unloaded. Attempt to do a clean exit.
     *
     * @throws Throwable a generic exception
     */
    @Override
    protected void finalize() throws Throwable {
        thread.end();
        thread = null;
        logger.trace("[{}] Observable Reader thread ended.", this.getName());
        super.finalize();
    }
}
