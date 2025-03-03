/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BeaconStateChunkingSerializerTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final KvStoreChunkingSerializer<BeaconState> stateSerializer =
      new BeaconStateChunkingSerializer(spec);

  @Test
  public void roundTrip_state() {
    final BeaconState value = dataStructureUtil.randomBeaconState(11);
    final List<byte[]> bytes = stateSerializer.serialize(value);
    final BeaconState deserialized = stateSerializer.deserialize(bytes);
    assertThat(deserialized).isEqualTo(value);
  }
}
