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

package tech.pegasys.teku.bls.impl.mikuli.hash2g2;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.FP;
import org.apache.milagro.amcl.BLS381.ROM;
import org.apache.tuweni.bytes.Bytes;

class Util {

  private static final int SIZE_OF_BIG = BIG.MODBYTES;

  // The field modulus
  private static final BIG P = new BIG(ROM.Modulus);

  /**
   * Create a BIG from a hex string.
   *
   * <p>Must by 98 characters: "0x" followed by 96 hex chars/48 bytes.
   *
   * @param hex the 98 character hexadecimal string
   * @return a BIG with the value provided
   */
  static BIG bigFromHex(String hex) {
    checkArgument(
        hex.length() == 2 + 2 * SIZE_OF_BIG,
        "Expected %s chars, received %s.",
        2 + 2 * SIZE_OF_BIG,
        hex.length());
    return BIG.fromBytes(Bytes.fromHexString(hex).toArray());
  }

  /**
   * Create a FP from a hex string.
   *
   * <p>Must by 98 characters: "0x" followed by 96 hex chars/48 bytes.
   *
   * @param hex the 98 character hexadecimal string
   * @return a FP with the value provided
   */
  static FP fpFromHex(String hex) {
    return new FP(bigFromHex(hex));
  }

  /**
   * Negate a field point (convenience method)
   *
   * @param a the field point to negate
   * @return the negated field point
   */
  static FP negate(FP a) {
    FP aNeg = new FP(a);
    aNeg.neg();
    return aNeg;
  }

  /**
   * Big-endian conversion of byte array into a BIG, modulo the field modulus.
   *
   * <p>Based on https://tools.ietf.org/html/rfc3447#section-4.2
   *
   * @param b octet string to be converted
   * @return corresponding nonnegative integer
   */
  static FP os2ip_modP(Bytes b) {
    return new FP(new DBIGExtended(b.toArray()).mod(P));
  }
}
