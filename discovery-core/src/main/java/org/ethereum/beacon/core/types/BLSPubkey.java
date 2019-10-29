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

package org.ethereum.beacon.core.types;

import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.util.bytes.Bytes48;
import tech.pegasys.artemis.util.bytes.DelegatingBytes48;

@SSZSerializable(serializeAs = Bytes48.class)
public class BLSPubkey extends DelegatingBytes48 {

  public static final BLSPubkey ZERO = new BLSPubkey(Bytes48.ZERO);

  public BLSPubkey(final Bytes48 bytes) {
    super(bytes);
  }

  public static BLSPubkey wrap(final Bytes48 bytes) {
    return new BLSPubkey(bytes);
  }

  /**
   * Parse an hexadecimal string representing a hash value.
   *
   * @param str An hexadecimal string (with or without the leading '0x') representing a valid hash
   *     value.
   * @return The parsed hash.
   * @throws NullPointerException if the provided string is {@code null}.
   * @throws IllegalArgumentException if the string is either not hexadecimal, or not the valid
   *     representation of a hash (not 32 bytes).
   */
  public static BLSPubkey fromHexString(final String str) {
    return new BLSPubkey(Bytes48.fromHexStringStrict(str));
  }

  public static BLSPubkey fromHexStringLenient(final String str) {
    return new BLSPubkey(Bytes48.fromHexStringLenient(str));
  }

  @Override
  public String toString() {
    String s = super.toString();
    return s.substring(2, 6) + "..." + s.substring(94);
  }

  public String toHexString() {
    return super.toString();
  }
}
