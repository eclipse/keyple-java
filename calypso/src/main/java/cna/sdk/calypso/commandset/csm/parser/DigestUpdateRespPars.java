package cna.sdk.calypso.commandset.csm.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cna.sdk.calypso.commandset.ApduResponseParser;
import cna.sdk.seproxy.APDUResponse;

/**
 * This class provides status code properties and the getters to access to the
 * structured fields of a Digest Update response.
 *
 * @author Ixxi
 *
 */
public class DigestUpdateRespPars extends ApduResponseParser {

    Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * Instantiates a new DigestUpdateRespPars.
     *
     * @param response
     *            the response
     */
    public DigestUpdateRespPars(APDUResponse response) {
        super(response);
        initStatusTable();
        logger.debug(this.getStatusInformation());
    }

    /**
     * Initializes the status table.
     */
    private void initStatusTable() {
        statusTable.put(new byte[] { (byte) 0x90, (byte) 0x00 }, new StatusProperties(true, "Successful execution."));
    }

}
