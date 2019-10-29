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

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.core.operations.Attestation;
import org.ethereum.beacon.core.operations.attestation.AttestationData;
import org.ethereum.beacon.core.operations.attestation.Crosslink;
import org.ethereum.beacon.core.state.Checkpoint;
import org.ethereum.beacon.core.types.BLSSignature;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes96;
import tech.pegasys.artemis.util.collections.Bitlist;

public abstract class AttestationTestUtil {
  private AttestationTestUtil() {}

  public static List<Attestation> createRandomList(Random random, int maxCount) {
    return Stream.generate(() -> createRandomAttestation(random))
        .limit(Math.abs(random.nextInt()) % maxCount + 1)
        .collect(Collectors.toList());
  }

  public static Attestation createRandomAttestation(Random random) {
    return new Attestation(
        Bitlist.of(64),
        createRandomAttestationData(random),
        Bitlist.of(64),
        BLSSignature.wrap(Bytes96.random(random)),
        BeaconChainSpec.DEFAULT_CONSTANTS);
  }

  public static AttestationData createRandomAttestationData(Random random) {
    return new AttestationData(
        Hash32.random(random),
        new Checkpoint(BeaconChainSpec.DEFAULT_CONSTANTS.getGenesisEpoch(), Hash32.random(random)),
        new Checkpoint(
            BeaconChainSpec.DEFAULT_CONSTANTS.getGenesisEpoch().increment(), Hash32.random(random)),
        Crosslink.EMPTY);
  }
}
