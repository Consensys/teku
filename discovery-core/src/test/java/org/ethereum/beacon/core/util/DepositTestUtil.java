/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.ethereum.beacon.core.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.ethereum.beacon.core.operations.Deposit;
import org.ethereum.beacon.core.operations.deposit.DepositData;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.types.BLSPubkey;
import org.ethereum.beacon.core.types.BLSSignature;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes48;
import tech.pegasys.artemis.util.bytes.Bytes96;

public abstract class DepositTestUtil {
  private DepositTestUtil() {}

  public static List<Deposit> createRandomList(Random random, SpecConstants spec, int maxCount) {
    List<Deposit> deposits = new ArrayList<>();
    int count = Math.abs(random.nextInt()) % maxCount + 1;
    for (int i = 0; i < count; i++) {
      deposits.add(createRandom(random, spec));
    }
    return deposits;
  }

  public static Deposit createRandom(Random random, SpecConstants spec) {
    DepositData depositData =
        new DepositData(
            BLSPubkey.wrap(Bytes48.random(random)),
            Hash32.random(random),
            spec.getMaxEffectiveBalance(),
            BLSSignature.wrap(Bytes96.random(random)));

    return Deposit.create(
        Collections.nCopies(spec.getDepositContractTreeDepth().getIntValue(), Hash32.ZERO),
        depositData);
  }
}
