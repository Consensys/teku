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

package tech.pegasys.teku.spec.datastructures.eth1;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public class Eth1Address extends Bytes20 {
  private static final DeserializableTypeDefinition<Eth1Address> ETH1ADDRESS_TYPE =
      DeserializableTypeDefinition.string(Eth1Address.class)
          .formatter(Eth1Address::toHexString)
          .parser(Eth1Address::fromHexString)
          .example("0x1Db3439a222C519ab44bb1144fC28167b4Fa6EE6")
          .description("Hex encoded deposit contract address with 0x prefix")
          .format("byte")
          .build();

  public static final Eth1Address ZERO =
      new Eth1Address("0x0000000000000000000000000000000000000000");

  private final String address;

  private Eth1Address(String value) {
    super(Bytes.fromHexString(value));
    this.address = value;
  }

  private static void checkAddress(String value) {
    checkArgument(value.startsWith("0x"), "Eth1Address (%s) should start with \"0x\".", value);
    Bytes bytes = Bytes.fromHexString(value);
    checkArgument(
        bytes.size() == SIZE, "Eth1Address should be 20 bytes, but was %s bytes.", bytes.size());
  }

  public static Eth1Address fromHexString(String value) {
    checkAddress(value);
    Eth1Address address = new Eth1Address(value);
    Eth1Address encodedAddress = fromHexStringWithChecksum(value);
    checkArgument(
        address.equals(encodedAddress),
        "Eth1Address fails checksum:\n got: %s\n exp: %s",
        address,
        encodedAddress);
    return address;
  }

  /**
   * Produce an encoded address according to <a
   * href="https://eips.ethereum.org/EIPS/eip-55">EIP-55</a>.
   *
   * @param value The string representation of an Ethereum address.
   * @return The encoded address with mixed-case checksum.
   */
  public static Eth1Address fromHexStringWithChecksum(String value) {
    checkAddress(value);
    String lowerCaseAddrWithoutPrefix = value.substring(2).toLowerCase();
    String hashString =
        Hash.keccak256(Bytes.wrap(lowerCaseAddrWithoutPrefix.getBytes(StandardCharsets.US_ASCII)))
            .toString();
    String ret = "0x";
    for (int i = 2; i < value.length(); i++) {
      String letter = String.valueOf(hashString.charAt(i));
      if (Integer.parseInt(letter, 16) >= 8) {
        ret += Character.toUpperCase(value.charAt(i));
      } else {
        ret += Character.toLowerCase(value.charAt(i));
      }
    }
    return new Eth1Address(ret);
  }

  public static DeserializableTypeDefinition<Eth1Address> getJsonTypeDefinition() {
    return ETH1ADDRESS_TYPE;
  }

  @Override
  public String toHexString() {
    return address;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Eth1Address a = (Eth1Address) o;
    return address.equals(a.address);
  }

  @Override
  public int hashCode() {
    return Objects.hash(address);
  }

  @Override
  public String toUnprefixedHexString() {
    return toHexString().substring(2);
  }

  @Override
  public String toString() {
    return toHexString();
  }
}
