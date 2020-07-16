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
import org.apache.milagro.amcl.BLS381.DBIG;

/** Extend Milagro's DBIG class to add a couple of useful methods. */
class DBIGExtended extends DBIG {

  /**
   * Construct from a DBIG
   *
   * @param dbig the DBIG value to copy
   */
  DBIGExtended(DBIG dbig) {
    super(new DBIG(dbig));
  }

  /**
   * Construct from a byte array (Big-Endian)
   *
   * @param bytes the Big-Endian bytes. Maximum 96 of them.
   */
  DBIGExtended(byte[] bytes) {
    super(0);
    checkArgument(bytes.length % 4 == 0, "The number of bytes must be a multiple of 4.");
    checkArgument(bytes.length <= 2 * BIG.MODBYTES, "Too many bytes to store in a DBIG.");
    // Unroll the loop as an optimization that reduces the number of shl() operations
    w[0] |=
        ((((long) bytes[0] & 0xff) << 24)
            | (((long) bytes[1] & 0xff) << 16)
            | (((long) bytes[2] & 0xff) << 8)
            | ((long) bytes[3] & 0xff));
    for (int i = 4; i < bytes.length; i += 4) {
      shl(32);
      w[0] |=
          ((((long) bytes[i] & 0xff) << 24)
              | (((long) bytes[i + 1] & 0xff) << 16)
              | (((long) bytes[i + 2] & 0xff) << 8)
              | ((long) bytes[i + 3] & 0xff));
    }
  }

  /**
   * Calculate the parity of the DBIG (oddness or evenness).
   *
   * @return true if the DBIG is odd, false if the DBIG is even.
   */
  boolean isOdd() {
    return (w[0] & 1) == 1;
  }

  /**
   * A version of DBIG.shr() that returns the result rather than void.
   *
   * @param k the number of bits to shift the DBIG to the right
   * @return the result of the shift-right operation
   */
  DBIGExtended fshr(int k) {
    this.shr(k);
    return this;
  }
}
