/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationForkChecker;

class OptimizedAttestationAggregationStrategyTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);
  private final BeaconState state = mock(BeaconState.class);
  
  private OptimizedAttestationAggregationStrategy strategy;
  
  @BeforeEach
  void setUp() {
    strategy = new OptimizedAttestationAggregationStrategy(spec, attestationPool);
    
    // Setup common mocks
    when(state.getSlot()).thenReturn(UInt64.valueOf(100));
    
    // Mock spec config for max attestations
    final SpecConfig specConfig = mock(SpecConfig.class);
    when(specConfig.getMaxAttestations()).thenReturn(UInt64.valueOf(128));
    when(specConfig.getMaxAttestationInclusionDelay()).thenReturn(UInt64.valueOf(32));
    when(spec.getSpecConfig(any(UInt64.class))).thenReturn(specConfig);
  }
  
  @Test
  void shouldReturnEmptyListWhenNoAttestationsAvailable() {
    // Given
    when(attestationPool.getAttestationsForBlock(any(), any())).thenReturn(List.of());
    
    // When
    final List<Attestation> result = strategy.selectOptimizedAttestations(state);
    
    // Then
    assertThat(result).isEmpty();
  }
  
  @Test
  void shouldOptimizeAttestationsWithSameAttestationData() {
    // Given
    final AttestationData attestationData = createAttestationData(UInt64.valueOf(99));
    
    // Create attestations with the same data but different aggregation bits
    final List<Attestation> attestations = new ArrayList<>();
    
    // First attestation has bits 0 and 1 set
    final SszBitlist bits1 = createBitlist(10, 0, 1);
    attestations.add(new Attestation(bits1, attestationData, BLSSignature.empty()));
    
    // Second attestation has bits 2 and 3 set
    final SszBitlist bits2 = createBitlist(10, 2, 3);
    attestations.add(new Attestation(bits2, attestationData, BLSSignature.empty()));
    
    // Third attestation has bits 0 and 4 set (overlapping with first attestation)
    final SszBitlist bits3 = createBitlist(10, 0, 4);
    attestations.add(new Attestation(bits3, attestationData, BLSSignature.empty()));
    
    when(attestationPool.getAttestationsForBlock(any(), any())).thenReturn(attestations);
    
    // Mock committee size for this attestation
    when(spec.getBeaconStateUtil(any()))
        .thenReturn(mock(tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil.class));
    when(spec.getBeaconStateUtil(any()).getBeaconCommitteeSize(any(), any(), any()))
        .thenReturn(10);
    
    // When
    final List<Attestation> result = strategy.selectOptimizedAttestations(state);
    
    // Then
    assertThat(result).hasSize(1);
    
    // The result should have bits 0, 1, 2, 3, and 4 set (the union of all attestations)
    final SszBitlist expectedBits = createBitlist(10, 0, 1, 2, 3, 4);
    assertThat(result.get(0).getAggregationBits()).isEqualTo(expectedBits);
  }
  
  @Test
  void shouldKeepAttestationsWithDifferentAttestationDataSeparate() {
    // Given
    final AttestationData data1 = createAttestationData(UInt64.valueOf(99));
    final AttestationData data2 = createAttestationData(UInt64.valueOf(98));
    
    // Create attestations with different data
    final List<Attestation> attestations = new ArrayList<>();
    
    // First attestation with data1
    final SszBitlist bits1 = createBitlist(10, 0, 1);
    attestations.add(new Attestation(bits1, data1, BLSSignature.empty()));
    
    // Second attestation with data2
    final SszBitlist bits2 = createBitlist(10, 2, 3);
    attestations.add(new Attestation(bits2, data2, BLSSignature.empty()));
    
    when(attestationPool.getAttestationsForBlock(any(), any())).thenReturn(attestations);
    
    // Mock committee size for these attestations
    when(spec.getBeaconStateUtil(any()))
        .thenReturn(mock(tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil.class));
    when(spec.getBeaconStateUtil(any()).getBeaconCommitteeSize(any(), any(), any()))
        .thenReturn(10);
    
    // When
    final List<Attestation> result = strategy.selectOptimizedAttestations(state);
    
    // Then
    assertThat(result).hasSize(2);
    
    // Verify that both attestations are included
    assertThat(result).anySatisfy(att -> {
      assertThat(att.getData()).isEqualTo(data1);
      assertThat(att.getAggregationBits()).isEqualTo(bits1);
    });
    
    assertThat(result).anySatisfy(att -> {
      assertThat(att.getData()).isEqualTo(data2);
      assertThat(att.getAggregationBits()).isEqualTo(bits2);
    });
  }
  
  @Test
  void shouldPrioritizeAttestationsBasedOnAggregationAndDelay() {
    // Given
    final AttestationData data1 = createAttestationData(UInt64.valueOf(70)); // Older attestation
    final AttestationData data2 = createAttestationData(UInt64.valueOf(99)); // Newer attestation
    
    // Create attestations with different data
    final List<Attestation> attestations = new ArrayList<>();
    
    // First attestation with data1 (older, fewer validators)
    final SszBitlist bits1 = createBitlist(10, 0, 1);
    attestations.add(new Attestation(bits1, data1, BLSSignature.empty()));
    
    // Second attestation with data2 (newer, more validators)
    final SszBitlist bits2 = createBitlist(10, 0, 1, 2, 3, 4);
    attestations.add(new Attestation(bits2, data2, BLSSignature.empty()));
    
    when(attestationPool.getAttestationsForBlock(any(), any())).thenReturn(attestations);
    
    // Mock committee size for these attestations
    when(spec.getBeaconStateUtil(any()))
        .thenReturn(mock(tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil.class));
    when(spec.getBeaconStateUtil(any()).getBeaconCommitteeSize(any(), any(), any()))
        .thenReturn(10);
    
    // When
    final List<Attestation> result = strategy.selectOptimizedAttestations(state);
    
    // Then
    assertThat(result).hasSize(2);
    
    // The newer attestation with more validators should be first
    assertThat(result.get(0).getData()).isEqualTo(data2);
    assertThat(result.get(0).getAggregationBits()).isEqualTo(bits2);
    
    // The older attestation with fewer validators should be second
    assertThat(result.get(1).getData()).isEqualTo(data1);
    assertThat(result.get(1).getAggregationBits()).isEqualTo(bits1);
  }
  
  private AttestationData createAttestationData(final UInt64 slot) {
    return new AttestationData(
        slot,
        UInt64.ZERO,
        Bytes32.ZERO,
        new Checkpoint(UInt64.ZERO, Bytes32.ZERO),
        new Checkpoint(UInt64.ZERO, Bytes32.ZERO));
  }
  
  private SszBitlist createBitlist(final int size, final int... setBits) {
    final SszBitlist bitlist = dataStructureUtil.randomBitlist(size);
    
    // Clear all bits
    for (int i = 0; i < size; i++) {
      bitlist.setBit(i, false);
    }
    
    // Set the specified bits
    for (int bit : setBits) {
      bitlist.setBit(bit, true);
    }
    
    return bitlist;
  }
} 
