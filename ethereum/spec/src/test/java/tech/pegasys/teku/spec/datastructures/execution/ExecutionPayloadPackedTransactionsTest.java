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

package tech.pegasys.teku.spec.datastructures.execution;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.SszPackedByteListsNode;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class ExecutionPayloadPackedTransactionsTest {

  @Test
  public void executionPayloadTransactions_shouldRoundTripWithPackedBacking() {
    final Spec spec = TestSpecFactory.createMinimalBellatrix();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(1, spec);
    final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
    assertThat(payload.getTransactions()).isNotEmpty();

    final Bytes serialized = payload.sszSerialize();
    final ExecutionPayload deserialized = payload.getSchema().sszDeserialize(serialized);

    assertThat(deserialized.hashTreeRoot()).isEqualTo(payload.hashTreeRoot());
    assertThat(deserialized.sszSerialize()).isEqualTo(serialized);
    assertThat(deserialized.getTransactions().getBackingNode().get(GIndexUtil.LEFT_CHILD_G_INDEX))
        .isInstanceOf(SszPackedByteListsNode.class);
  }
}
