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

import java.util.Collections;
import java.util.Random;
import org.ethereum.beacon.core.BeaconBlockBody;
import org.ethereum.beacon.core.BeaconBlockHeader;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.state.Eth1Data;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.core.types.SlotNumber;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.Bytes96;
import tech.pegasys.artemis.util.uint.UInt64;

public class BeaconBlockTestUtil {

  public static BeaconBlockHeader createRandomHeader(Random random) {
    return new BeaconBlockHeader(
        SlotNumber.ZERO,
        Hash32.random(random),
        Hash32.random(random),
        Hash32.random(random),
        BLSSignature.wrap(Bytes96.random(random)));
  }

  public static BeaconBlockBody createRandomBodyWithNoOperations(
      Random random, SpecConstants constants) {
    return new BeaconBlockBody(
        BLSSignature.wrap(Bytes96.random(random)),
        new Eth1Data(
            Hash32.wrap(Bytes32.random(random)),
            UInt64.valueOf(random.nextInt()),
            Hash32.wrap(Bytes32.random(random))),
        Bytes32.random(random),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        constants);
  }
}
