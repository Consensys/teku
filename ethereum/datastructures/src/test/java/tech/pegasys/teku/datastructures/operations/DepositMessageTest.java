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

package tech.pegasys.teku.datastructures.operations;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;

class DepositMessageTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private BLSPublicKey pubkey = dataStructureUtil.randomPublicKey();
  private Bytes32 withdrawalCredentials = dataStructureUtil.randomBytes32();
  private UnsignedLong amount = dataStructureUtil.randomUnsignedLong();

  @Test
  public void shouldRoundTripViaSsz() {
    final DepositMessage message = new DepositMessage(pubkey, withdrawalCredentials, amount);
    final Bytes ssz = SimpleOffsetSerializer.serialize(message);
    final DepositMessage result = SimpleOffsetSerializer.deserialize(ssz, DepositMessage.class);

    assertThat(result).isEqualTo(message);
  }
}
