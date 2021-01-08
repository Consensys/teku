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

package tech.pegasys.teku.datastructures.eth1;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;

public class Eth1Address {
  /** The number of bytes in this value - i.e. 20 */
  private static final int SIZE = 20;

  private final Bytes bytes;

  public static Eth1Address ZERO =
      Eth1Address.fromHexString("0x0000000000000000000000000000000000000000");

  public Eth1Address(Bytes bytes) {
    checkArgument(
        bytes.size() == SIZE,
        "Bytes%s should be %s bytes, but was %s bytes.",
        SIZE,
        SIZE,
        bytes.size());
    this.bytes = bytes;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Eth1Address address = (Eth1Address) o;
    return bytes.equals(address.bytes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bytes);
  }

  @Override
  public String toString() {
    return bytes.toString();
  }

  public static Eth1Address fromHexString(String value) {
    Bytes bytes = Bytes.fromHexString(value);
    return new Eth1Address(bytes);
  }

  public String toHexString() {
    return bytes.toHexString();
  }

  public Bytes toBytes() {
    return bytes;
  }
}
