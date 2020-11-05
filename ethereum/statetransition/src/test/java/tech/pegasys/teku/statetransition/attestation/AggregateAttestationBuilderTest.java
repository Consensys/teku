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

package tech.pegasys.teku.statetransition.attestation;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.util.config.Constants.MAX_VALIDATORS_PER_COMMITTEE;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;

class AggregateAttestationBuilderTest {

  public static final int BITLIST_SIZE = 10;
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final AttestationData attestationData = dataStructureUtil.randomAttestationData();

  private final AggregateAttestationBuilder builder =
      new AggregateAttestationBuilder(attestationData);

  @Test
  public void canAggregate_shouldBeTrueForFirstAttestation() {
    assertThat(builder.canAggregate(createAttestation(1, 2, 3, 4, 5, 6, 7, 8, 9))).isTrue();
  }

  @Test
  public void canAggregate_shouldBeTrueWhenValidatorsDoNotOverlap() {
    builder.aggregate(createAttestation(1, 3, 5));
    assertThat(builder.canAggregate(createAttestation(0, 2, 4))).isTrue();
  }

  @Test
  public void canAggregate_shouldBeFalseWhenValidatorsDoOverlap() {
    builder.aggregate(createAttestation(1, 3, 5));
    assertThat(builder.canAggregate(createAttestation(1, 2, 4))).isFalse();
  }

  @Test
  public void aggregate_shouldTrackIncludedAggregations() {
    final ValidateableAttestation attestation1 = createAttestation(1);
    final ValidateableAttestation attestation2 = createAttestation(2);
    final ValidateableAttestation attestation3 = createAttestation(3);
    builder.aggregate(attestation1);
    builder.aggregate(attestation2);
    builder.aggregate(attestation3);

    assertThat(builder.getIncludedAttestations())
        .containsExactlyInAnyOrder(attestation1, attestation2, attestation3);
  }

  @Test
  public void aggregate_shouldCombineBitsetsAndSignatures() {
    final ValidateableAttestation attestation1 = createAttestation(1);
    final ValidateableAttestation attestation2 = createAttestation(2);
    final ValidateableAttestation attestation3 = createAttestation(3);
    builder.aggregate(attestation1);
    builder.aggregate(attestation2);
    builder.aggregate(attestation3);

    final Bitlist expectedAggregationBits = new Bitlist(BITLIST_SIZE, MAX_VALIDATORS_PER_COMMITTEE);
    expectedAggregationBits.setBit(1);
    expectedAggregationBits.setBit(2);
    expectedAggregationBits.setBit(3);

    final BLSSignature expectedSignature =
        BLS.aggregate(
            asList(
                attestation1.getAttestation().getAggregate_signature(),
                attestation2.getAttestation().getAggregate_signature(),
                attestation3.getAttestation().getAggregate_signature()));

    assertThat(builder.buildAggregate())
        .isEqualTo(
            ValidateableAttestation.from(
                new Attestation(expectedAggregationBits, attestationData, expectedSignature)));
  }

  @Test
  public void buildAggregate_shouldThrowExceptionIfNoAttestationsAggregated() {
    assertThatThrownBy(builder::buildAggregate).isInstanceOf(IllegalStateException.class);
  }

  private ValidateableAttestation createAttestation(final int... validators) {
    final Bitlist aggregationBits = new Bitlist(BITLIST_SIZE, MAX_VALIDATORS_PER_COMMITTEE);
    IntStream.of(validators).forEach(aggregationBits::setBit);
    return ValidateableAttestation.from(
        new Attestation(aggregationBits, attestationData, dataStructureUtil.randomSignature()));
  }
}
