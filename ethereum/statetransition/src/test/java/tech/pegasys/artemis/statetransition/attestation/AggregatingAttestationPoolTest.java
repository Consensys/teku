/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.statetransition.attestation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.bls.BLS;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.Constants;

class AggregatingAttestationPoolTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final AggregatingAttestationPool aggregatingPool = new AggregatingAttestationPool();

  @Test
  public void shouldNotAggregateAttestationsWithDifferentData() {
    final Attestation attestation1 =
        addAttestationFromValidators(dataStructureUtil.randomAttestationData(), 1);
    final Attestation attestation2 =
        addAttestationFromValidators(dataStructureUtil.randomAttestationData(), 2);

    assertAttestations(attestation1, attestation2);
  }

  @Test
  public void shouldAggregateAttestationsWithSameDataAndNonOverlappingValidators() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 2);

    assertAttestations(aggregateAttestations(attestation1, attestation2));
  }

  @Test
  public void shouldNotAggregateAttestationsWithSameDataAndOverlappingValidators() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 5);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 1, 2);

    assertAttestations(attestation1, attestation2);
  }

  @Test
  public void shouldAggregateAttestationsWithSameDataAndDifferentNumbersOfValidators() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Attestation attestation1 = addAttestationFromValidators(attestationData, 1, 2, 3);
    final Attestation attestation2 = addAttestationFromValidators(attestationData, 6, 7);

    assertAttestations(aggregateAttestations(attestation1, attestation2));
  }

  private void assertAttestations(final Attestation... expectedAttestations) {
    assertThat(aggregatingPool.stream()).containsExactly(expectedAttestations);
  }

  private Attestation addAttestationFromValidators(
      final AttestationData data, final int... validators) {
    final Bitlist bitlist = new Bitlist(20, Constants.MAX_VALIDATORS_PER_COMMITTEE);
    IntStream.of(validators).forEach(bitlist::setBit);
    final Attestation attestation =
        new Attestation(bitlist, data, dataStructureUtil.randomSignature());
    aggregatingPool.add(attestation);
    return attestation;
  }

  public static Attestation aggregateAttestations(
      final Attestation firstAttestation, final Attestation... attestations) {
    final Bitlist aggregateBits = firstAttestation.getAggregation_bits().copy();
    final List<BLSSignature> signatures = new ArrayList<>();
    signatures.add(firstAttestation.getAggregate_signature());

    for (Attestation attestation : attestations) {
      aggregateBits.setAllBits(attestation.getAggregation_bits());
      signatures.add(attestation.getAggregate_signature());
    }
    return new Attestation(aggregateBits, firstAttestation.getData(), BLS.aggregate(signatures));
  }
}
