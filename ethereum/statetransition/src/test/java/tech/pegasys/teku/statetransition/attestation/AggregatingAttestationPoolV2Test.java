/*
 * Copyright Consensys Software Inc., 2025
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.statetransition.attestation.AggregatorUtil.aggregateAttestations;
import static tech.pegasys.teku.statetransition.attestation.utils.RewardBasedAttestationSorter.NOOP;

import java.util.Optional;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.statetransition.attestation.utils.RewardBasedAttestationSorter.RewardBasedAttestationSorterFactory;
import tech.pegasys.teku.storage.client.RecentChainData;

@TestSpecContext(milestone = {PHASE0, ELECTRA})
public class AggregatingAttestationPoolV2Test extends AggregatingAttestationPoolTest {

  @Override
  AggregatingAttestationPool instantiatePool(
      final Spec spec, final RecentChainData recentChainData, final int maxAttestations) {
    final RewardBasedAttestationSorterFactory sorterFactory =
        mock(RewardBasedAttestationSorterFactory.class);
    when(sorterFactory.create(any())).thenReturn(NOOP);

    return new AggregatingAttestationPoolV2(
        spec,
        recentChainData,
        new NoOpMetricsSystem(),
        maxAttestations,
        System::nanoTime,
        sorterFactory);
  }

  @TestTemplate
  @Override
  public void createAggregateFor_shouldAggregateAttestationsWithMatchingData() {
    // This test is replaced by the one below
  }

  @TestTemplate
  @Override
  public void createAggregateFor_shouldReturnBestAggregateForMatchingDataWhenSomeOverlap() {}

  @TestTemplate
  public void createAggregateFor_shouldReturnAggregateSingleAttestations() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Attestation attestation1 =
        addAttestationFromValidators(attestationData, 1).getAttestation();
    final Attestation attestation2 =
        addAttestationFromValidators(attestationData, 2).getAttestation();

    final Optional<Attestation> result =
        aggregatingPool.createAggregateFor(attestationData.hashTreeRoot(), committeeIndex);
    assertThat(result).contains(aggregateAttestations(attestation1, attestation2));
  }
}
