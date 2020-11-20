/* **************************************************************************************
 * Copyright (c) 2020 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.eclipse.keyple.example.generic.pc.UseCase2_DefaultSelectionNotification;

import org.eclipse.keyple.core.card.selection.CardSelection;
import org.eclipse.keyple.core.card.selection.CardSelector;
import org.eclipse.keyple.core.service.util.ContactlessCardCommonProtocols;
import org.eclipse.keyple.example.generic.pc.common.GenericCardSelectionRequest;

class CardSelectionConfig {

  private static CardSelection cardSelection;

  static CardSelection getDefaultSelection() {
    if (cardSelection != null) {
      return cardSelection;
    }
    // Prepare a card selection
    cardSelection = new CardSelection();

    // Setting of an AID based selection
    //
    // Select the first application matching the selection AID whatever the card communication
    // protocol keep the logical channel open after the selection

    // Generic selection: configures a CardSelector with all the desired attributes to make the
    // selection
    GenericCardSelectionRequest cardSelector =
        new GenericCardSelectionRequest(
            CardSelector.builder()
                .cardProtocol(ContactlessCardCommonProtocols.ISO_14443_4.name())
                .aidSelector(
                    CardSelector.AidSelector.builder()
                        .aidToSelect(DefaultSelectionNotification_Pcsc.cardAid)
                        .build())
                .build());

    // Add the selection case to the current selection (we could have added other cases here)
    cardSelection.prepareSelection(cardSelector);

    return cardSelection;
  }
}