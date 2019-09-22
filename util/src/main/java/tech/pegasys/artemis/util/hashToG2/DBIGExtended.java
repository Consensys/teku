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

import org.apache.milagro.amcl.BLS381.DBIG;

/** Extend Milagro's DBIG class to add a couple of useful methods. */
public class DBIGExtended extends DBIG {

  DBIGExtended(DBIG dbig) {
    super(new DBIG(dbig));
  }

  /**
   * Calculate the parity of the DBIG (oddness or evenness).
   *
   * @return 1 if the DBIG is odd, 0 if the DBIG is even.
   */
  int parity() {
    return (int) (w[0] % 2);
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
