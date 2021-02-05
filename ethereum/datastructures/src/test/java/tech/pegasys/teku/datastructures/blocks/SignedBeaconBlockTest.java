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

package tech.pegasys.teku.datastructures.blocks;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;

class SignedBeaconBlockTest {
  @Test
  public void shouldRoundTripViaSsz() {
    final SignedBeaconBlock block = new DataStructureUtil().randomSignedBeaconBlock(1);
    final Bytes ssz = block.sszSerialize();
    final SignedBeaconBlock result = SignedBeaconBlock.getSszType().sszDeserialize(ssz);
    assertThat(result).isEqualTo(block);
  }
}
