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

package tech.pegasys.artemis.datastructures.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomBytes32;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomUnsignedLong;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

class DepositMessageTest {
  private int seed = 94385;
  private BLSPublicKey pubkey = BLSPublicKey.random(seed++);
  private Bytes32 withdrawalCredentials = randomBytes32(seed++);
  private UnsignedLong amount = randomUnsignedLong(seed++);

  @Test
  public void shouldRoundTripViaSsz() {
    final DepositMessage message = new DepositMessage(pubkey, withdrawalCredentials, amount);
    final Bytes ssz = SimpleOffsetSerializer.serialize(message);
    final DepositMessage result = SimpleOffsetSerializer.deserialize(ssz, DepositMessage.class);

    assertThat(result).isEqualTo(message);
  }
}
