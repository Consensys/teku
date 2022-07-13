/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.api.ssz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.data.DepositTreeSnapshotData;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DepositTreeSnapshotSerializerTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  public void roundTrip_tree() {
    final DepositTreeSnapshotData depositTreeSnapshotData =
        new DepositTreeSnapshotData(
            IntStream.range(0, 10)
                .mapToObj(__ -> dataStructureUtil.randomBytes32())
                .collect(Collectors.toList()),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32());
    final byte[] bytes = DepositTreeSnapshotSerializer.INSTANCE.serialize(depositTreeSnapshotData);
    final DepositTreeSnapshotData deserialized =
        DepositTreeSnapshotSerializer.INSTANCE.deserialize(bytes);
    assertThat(deserialized).isEqualTo(depositTreeSnapshotData);
  }

  @Test
  public void roundTrip_emptyTree() {
    final DepositTreeSnapshotData depositTreeSnapshotData =
        new DepositTreeSnapshotData(
            Collections.emptyList(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32());
    final byte[] bytes = DepositTreeSnapshotSerializer.INSTANCE.serialize(depositTreeSnapshotData);
    final DepositTreeSnapshotData deserialized =
        DepositTreeSnapshotSerializer.INSTANCE.deserialize(bytes);
    assertThat(deserialized).isEqualTo(depositTreeSnapshotData);
  }
}
