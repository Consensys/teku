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

import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ROM;

/** Various {@code BLS12} elliptic curve parameters written with Milagro types and classes. */
public interface MilagroParameters {

  BIG ORDER = new BIG(ROM.CURVE_Order);

  abstract class Fp2 {
    private Fp2() {}

    public static final org.apache.milagro.amcl.BLS381.FP2 ONE =
        new org.apache.milagro.amcl.BLS381.FP2(1);
  }
}
