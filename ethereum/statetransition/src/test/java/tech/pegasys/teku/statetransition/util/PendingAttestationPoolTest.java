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

package tech.pegasys.teku.statetransition.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

public class PendingAttestationPoolTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final PendingAttestationPool pendingAttestationPool =
      new PoolFactory(new StubMetricsSystem()).createPendingAttestationPool(spec, 10);
  private final UInt64 currentSlot = UInt64.valueOf(10);

  @BeforeEach
  public void setup() {
    pendingAttestationPool.onSlot(currentSlot);
  }

  @Test
  public void fullPayloadVotes_shouldNotCollideWhenBlockRootsSharePrefix() {
    final Bytes sharedPrefix = Bytes.repeat((byte) 1, 16);
    final Bytes32 blockRoot1 =
        Bytes32.wrap(Bytes.concatenate(sharedPrefix, Bytes.repeat((byte) 2, 16)));
    final Bytes32 blockRoot2 =
        Bytes32.wrap(Bytes.concatenate(sharedPrefix, Bytes.repeat((byte) 3, 16)));
    final UInt64 validatorIndex = UInt64.valueOf(42);

    pendingAttestationPool.addVoteForMissingFullPayload(currentSlot, blockRoot1, validatorIndex);
    pendingAttestationPool.addVoteForMissingFullPayload(currentSlot, blockRoot2, validatorIndex);

    assertThat(pendingAttestationPool.removeVotesWaitingForFullPayload(blockRoot1))
        .containsExactly(new PendingFullPayloadVote(currentSlot, blockRoot1, validatorIndex));
    assertThat(pendingAttestationPool.removeVotesWaitingForFullPayload(blockRoot2))
        .containsExactly(new PendingFullPayloadVote(currentSlot, blockRoot2, validatorIndex));
  }
}
