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

package org.ethereum.beacon.crypto.bls.milagro;

import java.math.BigInteger;
import org.apache.milagro.amcl.BLS381.BIG;
import org.bouncycastle.util.Arrays;
import tech.pegasys.artemis.util.bytes.BytesValue;

/**
 * Various utility methods to work with Milagro big number implementation.
 *
 * @see BIG
 */
public abstract class BIGs {
  private BIGs() {}

  /** Indicates that all data are written in big endian notation by default. */
  private static final boolean BIG_ENDIAN = true;

  /**
   * Converts {@link BIG} to byte array.
   *
   * @param big a number.
   * @param bigEndian whether the output array should be big endian or not.
   * @return a byte array.
   */
  public static byte[] toByteArray(BIG big, boolean bigEndian) {
    byte[] bytes = new byte[BIG.MODBYTES];
    big.toBytes(bytes);
    return bigEndian ? bytes : Arrays.reverse(bytes);
  }

  /**
   * A shortcut to {@link #toByteArray(BIG, boolean)} with second parameter set to {@link
   * #BIG_ENDIAN}.
   *
   * @param big a value.
   * @return a byte array.
   */
  public static byte[] toByteArray(BIG big) {
    return toByteArray(big, BIG_ENDIAN);
  }

  /**
   * Works in the same way as {@link #toByteArray(BIG)} but returns an array as a {@link BytesValue}
   * instance.
   *
   * @param big a value.
   * @return a bytes sequence.
   */
  public static BytesValue toBytes(BIG big) {
    return BytesValue.wrap(toByteArray(big, BIG_ENDIAN));
  }

  /**
   * Instantiates {@link BIG} from a byte sequence.
   *
   * @param bytes byte sequence.
   * @param bigEndian whether input sequence should be treated as a big endian number or not.
   * @return a value of {@link BIG} type.
   * @throws AssertionError if sequence length is higher than {@link BIG#BASEBITS}.
   */
  public static BIG fromByteArray(byte[] bytes, boolean bigEndian) {
    assert bytes.length <= BIG.BASEBITS;

    byte[] fixed = bigEndian ? bytes : Arrays.reverse(bytes);

    if (bytes.length < BIG.MODBYTES) {
      byte[] prepended = new byte[BIG.MODBYTES];
      System.arraycopy(bytes, 0, prepended, BIG.MODBYTES - bytes.length, bytes.length);
      return BIG.fromBytes(prepended);
    } else {
      return BIG.fromBytes(fixed);
    }
  }

  /**
   * A shortcut to {@link #fromByteArray(byte[], boolean)} called with a second argument equal to
   * {@link #BIG_ENDIAN}.
   *
   * @param bytes byte sequence.
   * @return a value of {@link BIG} type.
   */
  public static BIG fromByteArray(byte[] bytes) {
    return fromByteArray(bytes, BIG_ENDIAN);
  }

  /**
   * Works in the same way as {@link #fromByteArray(byte[])} but consumes an input as {@link
   * BytesValue} instance.
   *
   * @param bytes input bytes.
   * @return a value of {@link BIG} type.
   */
  public static BIG fromBytes(BytesValue bytes) {
    return fromByteArray(bytes.getArrayUnsafe(), BIG_ENDIAN);
  }

  /**
   * Works in the same way as {@link #fromByteArray(byte[])} but consumes an input as {@link
   * BigInteger} instance.
   *
   * @param value big integer value.
   * @return a value of {@link BIG} type.
   */
  public static BIG fromBigInteger(BigInteger value) {
    return fromByteArray(value.toByteArray(), true);
  }
}
