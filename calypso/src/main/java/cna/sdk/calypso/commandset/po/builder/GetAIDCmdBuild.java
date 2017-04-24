package cna.sdk.calypso.commandset.po.builder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cna.sdk.calypso.commandset.CalypsoCommands;
import cna.sdk.calypso.commandset.InconsistentCommandException;
import cna.sdk.calypso.commandset.RequestUtils;
import cna.sdk.calypso.commandset.enumTagUtils;
import cna.sdk.calypso.commandset.dto.CalypsoRequest;
import cna.sdk.calypso.commandset.po.PoCommandBuilder;
import cna.sdk.calypso.commandset.po.PoRevision;
import cna.sdk.calypso.commandset.po.SendableInSession;
import cna.sdk.seproxy.APDURequest;

/**
 * This class implements SendableInSession, it provides the dedicated
 * constructor to build the Get data APDU commands.
 *
 *
 * @author Ixxi
 */
public class GetAIDCmdBuild extends PoCommandBuilder implements SendableInSession {

    /** The Constant logger. */
    static final Logger logger = LoggerFactory.getLogger(GetAIDCmdBuild.class);

    private static CalypsoCommands defaultCommandReference = CalypsoCommands.PO_GET_DATA_FCI;

    GetAIDCmdBuild() {
        commandReference = defaultCommandReference;
    }

    GetAIDCmdBuild(APDURequest request) throws InconsistentCommandException {
        super(defaultCommandReference, request);
    }

    /**
     * Instantiates a new GetDataFciCmdBuild.
     *
     * @param revision
     *            PO revision
     */
    public GetAIDCmdBuild(PoRevision revision) {
        super(revision, defaultCommandReference);
        byte cla = poRevision.getCla();
        CalypsoRequest calypsoRequest = new CalypsoRequest(cla, commandReference, enumTagUtils.AID_OF_CURRENT_DF.getTagbyte1(),
                enumTagUtils.AID_OF_CURRENT_DF.getTagbyte2(), null, (byte) 0x00);
        request = RequestUtils.constructAPDURequest(calypsoRequest);
    }

    /*
     * (non-Javadoc)
     *
     * @see cna.sdk.calypso.commandset.po.SendableInSession#getAPDURequest()
     */
    @Override
    public APDURequest getAPDURequest() {
        return request;
    }
}
