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
package org.eclipse.keyple.calypso.command.po.parser;

import java.util.HashMap;
import java.util.Map;
import org.eclipse.keyple.calypso.command.po.AbstractPoResponseParser;
import org.eclipse.keyple.calypso.command.po.builder.WriteRecordCmdBuild;
import org.eclipse.keyple.calypso.command.po.exception.*;
import org.eclipse.keyple.core.card.command.AbstractApduResponseParser;
import org.eclipse.keyple.core.card.message.ApduResponse;

/** Write Record response parser. See specs: Calypso 3.2 / page 99 / 9.4.13 - Write Record */
public final class WriteRecordRespPars extends AbstractPoResponseParser {

  private static final Map<Integer, StatusProperties> STATUS_TABLE;

  static {
    Map<Integer, StatusProperties> m =
        new HashMap<Integer, StatusProperties>(AbstractApduResponseParser.STATUS_TABLE);
    m.put(
        0x6400,
        new StatusProperties(
            "Too many modifications in session", CalypsoPoSessionBufferOverflowException.class));
    m.put(
        0x6700, new StatusProperties("Lc value not supported", CalypsoPoDataAccessException.class));
    m.put(
        0x6981,
        new StatusProperties(
            "Wrong EF type (not a Linear EF, or Cyclic EF with Record Number 01h).",
            CalypsoPoDataAccessException.class));
    m.put(
        0x6982,
        new StatusProperties(
            "Security conditions not fulfilled (no session, wrong key, encryption required)",
            CalypsoPoSecurityContextException.class));
    m.put(
        0x6985,
        new StatusProperties(
            "Access forbidden (Never access mode, DF is invalidated, etc..)",
            CalypsoPoAccessForbiddenException.class));
    m.put(
        0x6986,
        new StatusProperties(
            "Command not allowed (no current EF)", CalypsoPoDataAccessException.class));
    m.put(0x6A82, new StatusProperties("File not found", CalypsoPoDataAccessException.class));
    m.put(
        0x6A83,
        new StatusProperties(
            "Record is not found (record index is 0 or above NumRec)",
            CalypsoPoDataAccessException.class));
    m.put(
        0x6B00,
        new StatusProperties("P2 value not supported", CalypsoPoIllegalParameterException.class));
    STATUS_TABLE = m;
  }

  @Override
  protected Map<Integer, StatusProperties> getStatusTable() {
    return STATUS_TABLE;
  }

  /**
   * Instantiates a new WriteRecordRespPars
   *
   * @param response the response from the PO
   * @param builder the reference to the builder that created this parser
   */
  public WriteRecordRespPars(ApduResponse response, WriteRecordCmdBuild builder) {
    super(response, builder);
  }
}
