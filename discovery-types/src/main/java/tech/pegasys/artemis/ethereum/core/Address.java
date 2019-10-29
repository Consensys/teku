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

package tech.pegasys.artemis.ethereum.core;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.bytes.DelegatingBytesValue;

/** A 160-bits account address. */
public class Address extends DelegatingBytesValue {

  public static final int SIZE = 20;

  protected Address(final BytesValue bytes) {
    super(bytes);
    checkArgument(
        bytes.size() == SIZE,
        "An account address must be be %s bytes long, got %s",
        SIZE,
        bytes.size());
  }

  public static Address wrap(final BytesValue value) {
    return new Address(value);
  }

  /**
   * @param hash32 A hash that has been obtained through hashing the return of the <code>
   *     ECDSARECOVER
   *     </code> function from Appendix F (Signing Transactions) of the Ethereum Yellow Paper.
   * @return The ethereum address from the provided hash.
   */
  public static Address extract(final Hash32 hash32) {
    return wrap(hash32.slice(12, 20));
  }

  /**
   * Parse an hexadecimal string representing an account address.
   *
   * @param str An hexadecimal string (with or without the leading '0x') representing a valid
   *     account address.
   * @return The parsed address.
   * @throws NullPointerException if the provided string is {@code null}.
   * @throws IllegalArgumentException if the string is either not hexadecimal, or not the valid
   *     representation of an address.
   */
  public static Address fromHexString(final String str) {
    if (str == null) return null;

    return new Address(BytesValue.fromHexStringLenient(str, SIZE));
  }

  @Override
  public Address copy() {
    final BytesValue copiedStorage = wrapped.copy();
    return Address.wrap(copiedStorage);
  }
}
