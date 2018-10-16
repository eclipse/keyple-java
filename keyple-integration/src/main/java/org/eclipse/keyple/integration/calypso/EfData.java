/*
 * Copyright (c) 2018 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License version 2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.keyple.integration.calypso;

public class EfData {

    private int recNumb;

    private byte sfi;

    private int recSize;

    public EfData(int recNumb, byte sfi, int recSize) {
        this.recNumb = recNumb;
        this.sfi = sfi;
        this.recSize = recSize;
    }

    public int getRecNumb() {
        return recNumb;
    }

    public byte getSfi() {
        return sfi;
    }

    public int getRecSize() {
        return recSize;
    }
}
