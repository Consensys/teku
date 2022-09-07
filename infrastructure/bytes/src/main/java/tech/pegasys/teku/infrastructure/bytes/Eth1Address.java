/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.infrastructure.bytes;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isMixedCase;

import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.crypto.Hash;

public class Eth1Address extends Bytes20 {

  public static final Eth1Address ZERO =
      new Eth1Address("0x0000000000000000000000000000000000000000");

  private final String encodedAddress;

  private Eth1Address(String value) {
    super(Bytes.fromHexString(value));
    if (!value.startsWith("0x")) {
      value = "0x" + value;
    }
    this.encodedAddress = toChecksumAddress(value);
    validate(value);
  }

  private Eth1Address(Bytes bytes) {
    super(bytes);
    String value = bytes.toHexString();
    this.encodedAddress = toChecksumAddress(value);
    validate(value);
  }

  private void validate(String value) {
    if (isMixedCase(value.substring("0x".length()))) {
      checkArgument(
          value.equals(encodedAddress),
          "Eth1Address fails checksum:\n got: %s\n exp: %s",
          value,
          encodedAddress);
    }
  }

  public static Eth1Address fromBytes(Bytes value) {
    return new Eth1Address(value);
  }

  public static Eth1Address fromHexString(String value) {
    try {
      return new Eth1Address(value);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("Invalid Ethereum Address: " + ex.getMessage(), ex);
    }
  }

  /**
   * Produce an address with a mixed-case checksum as defined by <a
   * href="https://eips.ethereum.org/EIPS/eip-55">EIP-55</a>.
   *
   * @param value The string representation of an Ethereum address.
   * @return The encoded address with mixed-case checksum.
   */
  private static String toChecksumAddress(String value) {
    String address = value.replace("0x", "").toLowerCase();
    String hashString =
        Hash.keccak256(Bytes.wrap(address.getBytes(StandardCharsets.US_ASCII)))
            .toString()
            .replace("0x", "");
    String ret = "0x";
    for (int i = 0; i < address.length(); i++) {
      String letter = String.valueOf(hashString.charAt(i));
      if (Integer.parseInt(letter, 16) >= 8) {
        ret += Character.toUpperCase(address.charAt(i));
      } else {
        ret += address.charAt(i);
      }
    }
    return ret;
  }

  @Override
  public String toString() {
    return encodedAddress;
  }

  @Override
  public String toHexString() {
    return encodedAddress;
  }

  @Override
  public String toUnprefixedHexString() {
    return encodedAddress.substring(2);
  }
}
