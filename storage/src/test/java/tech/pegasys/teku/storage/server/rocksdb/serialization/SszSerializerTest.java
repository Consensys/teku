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

package tech.pegasys.teku.storage.server.rocksdb.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;

public class SszSerializerTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  private final SszSerializer<SignedBeaconBlock> blockSerializer =
      new SszSerializer<>(SignedBeaconBlock.class);
  private final SszSerializer<BeaconState> stateSerializer =
      new SszSerializer<>(BeaconStateImpl.class);
  private final SszSerializer<Checkpoint> checkpointSerializer =
      new SszSerializer<>(Checkpoint.class);

  @Test
  public void roundTrip_block() {
    final SignedBeaconBlock value = dataStructureUtil.randomSignedBeaconBlock(11);
    final byte[] bytes = blockSerializer.serialize(value);
    final SignedBeaconBlock deserialized = blockSerializer.deserialize(bytes);
    assertThat(deserialized).isEqualTo(value);
  }

  @Test
  public void roundTrip_state() {
    final BeaconState value = dataStructureUtil.randomBeaconState(11);
    final byte[] bytes = stateSerializer.serialize(value);
    final BeaconState deserialized = stateSerializer.deserialize(bytes);
    assertThat(deserialized).isEqualTo(value);
  }

  @Test
  public void roundTrip_checkpoint() {
    final Checkpoint value = dataStructureUtil.randomCheckpoint();
    final byte[] bytes = checkpointSerializer.serialize(value);
    final Checkpoint deserialized = checkpointSerializer.deserialize(bytes);
    assertThat(deserialized).isEqualTo(value);
  }
}
