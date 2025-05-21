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

import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.statetransition.attestation.utils.AggregatingAttestationPoolProfiler;
import tech.pegasys.teku.storage.client.RecentChainData;

@TestSpecContext(milestone = {PHASE0, ELECTRA})
public class AggregatingAttestationPoolV1Test extends AggregatingAttestationPoolTest {

  @Override
  AggregatingAttestationPool instantiatePool(
      final Spec spec, final RecentChainData recentChainData, final int maxAttestations) {
    return new AggregatingAttestationPoolV1(
        spec,
        recentChainData,
        new NoOpMetricsSystem(),
        AggregatingAttestationPoolProfiler.NOOP,
        maxAttestations);
  }
}
