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

package tech.pegasys.teku.weaksubjectivity.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;

public class WeakSubjectivityConfigTest {

  @Test
  public void build_withStringParameters() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final Checkpoint checkpoint = dataStructureUtil.randomCheckpoint();
    final String checkpointString =
        checkpoint.getRoot().toHexString() + ":" + checkpoint.getEpoch();

    WeakSubjectivityConfig config =
        WeakSubjectivityConfig.builder().weakSubjectivityCheckpoint(checkpointString).build();

    assertThat(config.getWeakSubjectivityCheckpoint()).contains(checkpoint);
  }

  @Test
  public void build_withParsedParameters() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final Checkpoint checkpoint = dataStructureUtil.randomCheckpoint();

    WeakSubjectivityConfig config =
        WeakSubjectivityConfig.builder().weakSubjectivityCheckpoint(checkpoint).build();

    assertThat(config.getWeakSubjectivityCheckpoint()).contains(checkpoint);
  }

  @Test
  public void build_withNoWeakSubjectivityCheckpoint() {
    WeakSubjectivityConfig config = WeakSubjectivityConfig.builder().build();
    assertThat(config.getWeakSubjectivityCheckpoint()).isEmpty();
  }
}
