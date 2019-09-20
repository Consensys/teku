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

package tech.pegasys.artemis.util.hashToG2;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.System.arraycopy;

import java.math.BigInteger;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.tuweni.bytes.Bytes;

public class Util {

  /**
   * Create a BIG from a hex string.
   *
   * <p>Must by 98 characters: "0x" followed by 96 hex chars/48 bytes.
   *
   * @param hex the 98 character hexadecimal string
   * @return a BIG with the value provided
   */
  static BIG bigFromHex(String hex) {
    checkArgument(hex.length() == 98, "Expected 98 chars, received %s.", hex.length());
    return BIG.fromBytes(Bytes.fromHexString(hex).toArray());
  }

  /**
   * Create a BIG from a BigInteger.
   *
   * @param bigInt the BigInteger to convert to a BIG
   * @return a BIG with the value provided
   */
  static BIG bigFromBigInt(BigInteger bigInt) {
    byte[] bytes = new byte[48];
    byte[] inputBytes = bigInt.toByteArray();
    checkArgument(
        inputBytes.length <= 48,
        "The BigInteger is too large to convert to a BIG: "
            + bigInt
            + ". It needs "
            + inputBytes.length
            + " bytes.");
    arraycopy(inputBytes, 0, bytes, 48 - inputBytes.length, inputBytes.length);
    return BIG.fromBytes(bytes);
  }
}
