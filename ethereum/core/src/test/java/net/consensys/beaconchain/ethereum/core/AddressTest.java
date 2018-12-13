/*
 * Copyright 2018 ConsenSys AG.
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

package net.consensys.artemis.ethereum.vm;

import net.consensys.artemis.ethereum.core.Address;
import net.consensys.artemis.util.bytes.BytesValue;

import org.junit.Assert;
import org.junit.Test;

public class AddressTest {

  @Test
  public void accountAddressToString() {
    Address addr =
        Address.wrap(BytesValue.fromHexString("0x0000000000000000000000000000000000101010"));
    Assert.assertEquals("0x0000000000000000000000000000000000101010", addr.toString());
  }

  @Test
  public void accountAddressEquals() {
    Address addr =
        Address.wrap(BytesValue.fromHexString("0x0000000000000000000000000000000000101010"));
    Address addr2 =
        Address.wrap(BytesValue.fromHexString("0x0000000000000000000000000000000000101010"));

    Assert.assertEquals(addr, addr2);
  }

  @Test
  public void accountAddresHashCode() {
    Address addr =
        Address.wrap(BytesValue.fromHexString("0x0000000000000000000000000000000000101010"));
    Address addr2 =
        Address.wrap(BytesValue.fromHexString("0x0000000000000000000000000000000000101010"));

    Assert.assertEquals(addr.hashCode(), addr2.hashCode());
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidAccountAddress() {
    Address.wrap(BytesValue.fromHexString("0x00101010"));
  }
}
