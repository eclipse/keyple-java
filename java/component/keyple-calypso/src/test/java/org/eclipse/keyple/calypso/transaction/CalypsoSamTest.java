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
package org.eclipse.keyple.calypso.transaction;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.assertj.core.api.Java6Assertions.shouldHaveThrown;
import static org.eclipse.keyple.calypso.command.sam.SamRevision.*;

import org.assertj.core.api.Assertions;
import org.eclipse.keyple.core.card.message.AnswerToReset;
import org.eclipse.keyple.core.card.message.CardSelectionResponse;
import org.eclipse.keyple.core.card.message.SelectionStatus;
import org.eclipse.keyple.core.util.ByteArrayUtil;
import org.eclipse.keyple.core.util.json.KeypleGsonParser;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CalypsoSamTest {
  private static final Logger logger = LoggerFactory.getLogger(CalypsoSamTest.class);

  public static String ATR1 = "3B001122805A0180D002030411223344829000";
  public static String ATR2 = "3B001122805A0180D102030411223344829000";
  public static String ATR3 = "3B001122805A0180D202030411223344829000";
  public static String ATR4 = "3B001122805A0180C102030411223344829000";
  public static String ATR5 = "3B001122805A0180E102030411223344829000";
  public static String ATR6 = "3B001122805A0180E202030411223344829000";
  public static String ATR7 = "3B001122805A0180E202030411223344820000";

  /** basic CalypsoSam test: nominal ATR parsing */
  @Test
  public void test_CalypsoSam_1() {
    SamSelector samSelector = SamSelector.builder().samRevision(AUTO).build();
    SamSelection samSelection = new SamSelection(samSelector);
    SelectionStatus selectionStatus =
        new SelectionStatus(new AnswerToReset(ByteArrayUtil.fromHex(ATR1)), null, true);
    CalypsoSam calypsoSam = new CalypsoSam(new CardSelectionResponse(selectionStatus, null));
    assertThat(S1D).isEqualTo(calypsoSam.getSamRevision());
    assertThat((byte) 0x80).isEqualTo(calypsoSam.getApplicationType());
    assertThat((byte) 0xD0).isEqualTo(calypsoSam.getApplicationSubType());
    assertThat((byte) 0x01).isEqualTo(calypsoSam.getPlatform());
    assertThat((byte) 0x02).isEqualTo(calypsoSam.getSoftwareIssuer());
    assertThat((byte) 0x03).isEqualTo(calypsoSam.getSoftwareVersion());
    assertThat((byte) 0x04).isEqualTo(calypsoSam.getSoftwareRevision());
    assertThat("11223344").isEqualTo(ByteArrayUtil.toHex(calypsoSam.getSerialNumber()));
  }

  /* S1D D1 */
  @Test
  public void test_CalypsoSam_2() {
    SamSelector samSelector = SamSelector.builder().samRevision(AUTO).build();
    SamSelection samSelection = new SamSelection(samSelector);
    SelectionStatus selectionStatus =
        new SelectionStatus(new AnswerToReset(ByteArrayUtil.fromHex(ATR2)), null, true);
    CalypsoSam calypsoSam = new CalypsoSam(new CardSelectionResponse(selectionStatus, null));
    assertThat(S1D).isEqualTo(calypsoSam.getSamRevision());
    assertThat((byte) 0xD1).isEqualTo(calypsoSam.getApplicationSubType());
  }

  /* S1D D2 */
  @Test
  public void test_CalypsoSam_3() {
    SamSelector samSelector = SamSelector.builder().samRevision(AUTO).build();
    SamSelection samSelection = new SamSelection(samSelector);
    SelectionStatus selectionStatus =
        new SelectionStatus(new AnswerToReset(ByteArrayUtil.fromHex(ATR3)), null, true);
    CalypsoSam calypsoSam = new CalypsoSam(new CardSelectionResponse(selectionStatus, null));
    assertThat(S1D).isEqualTo(calypsoSam.getSamRevision());
    assertThat((byte) 0xD2).isEqualTo(calypsoSam.getApplicationSubType());
  }

  /* C1 */
  @Test
  public void test_CalypsoSam_4() {
    SamSelector samSelector = SamSelector.builder().samRevision(AUTO).build();
    SamSelection samSelection = new SamSelection(samSelector);
    SelectionStatus selectionStatus =
        new SelectionStatus(new AnswerToReset(ByteArrayUtil.fromHex(ATR4)), null, true);
    CalypsoSam calypsoSam = new CalypsoSam(new CardSelectionResponse(selectionStatus, null));
    assertThat(C1).isEqualTo(calypsoSam.getSamRevision());
    assertThat((byte) 0xC1).isEqualTo(calypsoSam.getApplicationSubType());
  }

  /* E1 */
  @Test
  public void test_CalypsoSam_5() {
    SamSelector samSelector = SamSelector.builder().samRevision(AUTO).build();
    SamSelection samSelection = new SamSelection(samSelector);
    SelectionStatus selectionStatus =
        new SelectionStatus(new AnswerToReset(ByteArrayUtil.fromHex(ATR5)), null, true);
    CalypsoSam calypsoSam = new CalypsoSam(new CardSelectionResponse(selectionStatus, null));
    assertThat(S1E).isEqualTo(calypsoSam.getSamRevision());
    assertThat((byte) 0xE1).isEqualTo(calypsoSam.getApplicationSubType());
  }

  /* Unrecognized E2 */
  @Test(expected = IllegalStateException.class)
  public void test_CalypsoSam_6() {
    SamSelector samSelector = SamSelector.builder().samRevision(AUTO).build();
    SamSelection samSelection = new SamSelection(samSelector);
    SelectionStatus selectionStatus =
        new SelectionStatus(new AnswerToReset(ByteArrayUtil.fromHex(ATR6)), null, true);
    CalypsoSam calypsoSam = new CalypsoSam(new CardSelectionResponse(selectionStatus, null));
    shouldHaveThrown(IllegalArgumentException.class);
  }

  /* Bad Calypso SAM ATR (0000 instead of 9000) */
  @Test(expected = IllegalStateException.class)
  public void test_CalypsoSam_7() {
    SamSelector samSelector = SamSelector.builder().samRevision(AUTO).build();
    SamSelection samSelection = new SamSelection(samSelector);
    SelectionStatus selectionStatus =
        new SelectionStatus(new AnswerToReset(ByteArrayUtil.fromHex(ATR7)), null, true);
    CalypsoSam calypsoSam = new CalypsoSam(new CardSelectionResponse(selectionStatus, null));
    shouldHaveThrown(IllegalArgumentException.class);
  }

  /* Bad Calypso SAM ATR (empty array) */
  @Test(expected = IllegalStateException.class)
  public void test_CalypsoSam_8() {
    SamSelector samSelector = SamSelector.builder().samRevision(AUTO).build();
    SamSelection samSelection = new SamSelection(samSelector);
    SelectionStatus selectionStatus =
        new SelectionStatus(new AnswerToReset(ByteArrayUtil.fromHex("")), null, true);
    CalypsoSam calypsoSam = new CalypsoSam(new CardSelectionResponse(selectionStatus, null));
    shouldHaveThrown(IllegalArgumentException.class);
  }

  @Test
  public void json_fromJson() {
    SelectionStatus selectionStatus =
        new SelectionStatus(new AnswerToReset(ByteArrayUtil.fromHex(ATR1)), null, true);
    CalypsoSam calypsoSam = new CalypsoSam(new CardSelectionResponse(selectionStatus, null));
    String json = KeypleGsonParser.getParser().toJson(calypsoSam);
    logger.debug(json);
    Assertions.assertThat(KeypleGsonParser.getParser().fromJson(json, CalypsoSam.class))
        .isEqualToComparingFieldByFieldRecursively(calypsoSam);
  }
}
