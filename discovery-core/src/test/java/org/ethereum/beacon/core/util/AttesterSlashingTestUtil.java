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
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.ethereum.beacon.core.operations.slashing.AttesterSlashing;
import org.ethereum.beacon.core.operations.slashing.IndexedAttestation;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.types.BLSSignature;
import tech.pegasys.artemis.util.bytes.Bytes96;

public abstract class AttesterSlashingTestUtil {
  private AttesterSlashingTestUtil() {}

  public static List<AttesterSlashing> createRandomList(
      Random random, int maxCount, SpecConstants specConstants) {
    return Stream.generate(() -> createRandom(random, specConstants))
        .limit(Math.abs(random.nextInt()) % maxCount + 1)
        .collect(Collectors.toList());
  }

  public static AttesterSlashing createRandom(Random random, SpecConstants specConstants) {
    return new AttesterSlashing(
        new IndexedAttestation(
            Collections.emptyList(),
            Collections.emptyList(),
            AttestationTestUtil.createRandomAttestationData(random),
            BLSSignature.wrap(Bytes96.random(random)),
            specConstants),
        new IndexedAttestation(
            Collections.emptyList(),
            Collections.emptyList(),
            AttestationTestUtil.createRandomAttestationData(random),
            BLSSignature.wrap(Bytes96.random(random)),
            specConstants));
  }
}
