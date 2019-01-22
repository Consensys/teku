/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.datastructures.beaconchainstate;

import static org.junit.Assert.assertEquals;

import com.google.common.primitives.UnsignedLong;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import org.junit.Test;

public class ValidatorRegistryDeltaBlockTest {

  @Test
  public void roundtripSSZ() {
    ValidatorRegistryDeltaBlock block =
        new ValidatorRegistryDeltaBlock(
            UnsignedLong.valueOf(123),
            Bytes32.random(),
            Bytes48.random(),
            UnsignedLong.valueOf(456),
            23);
    Bytes encoded = block.toBytes();
    assertEquals(block, ValidatorRegistryDeltaBlock.fromBytes(encoded));
  }
}
