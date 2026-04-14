/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.reference.phase0.gossip;

import com.google.common.collect.ImmutableMap;
import tech.pegasys.teku.reference.TestExecutor;

/** Executable gossip validations */
public class GossipTests {

  public static final ImmutableMap<String, TestExecutor> GOSSIP_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .put("networking/gossip_attester_slashing", new GossipAttesterSlashingTestExecutor())
          .put("networking/gossip_beacon_aggregate_and_proof", TestExecutor.IGNORE_TESTS)
          .put("networking/gossip_beacon_attestation", TestExecutor.IGNORE_TESTS)
          .put("networking/gossip_beacon_block", TestExecutor.IGNORE_TESTS)
          .put("networking/gossip_proposer_slashing", new GossipProposerSlashingTestExecutor())
          .put("networking/gossip_voluntary_exit", new GossipVoluntaryExitTestExecutor())
          .build();
}
