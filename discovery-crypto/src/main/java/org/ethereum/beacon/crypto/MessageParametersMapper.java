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

package org.ethereum.beacon.crypto;

import org.ethereum.beacon.crypto.bls.milagro.MilagroMessageMapper;

/**
 * An interface of a mapper that coverts message to {@code BLS12} elliptic curve point.
 *
 * <p>One of its possible implementations described in the spec <a
 * href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/bls_signature.md#hash_to_g2"></a>
 *
 * @param <P> point type.
 * @see MessageParameters
 * @see MilagroMessageMapper
 */
public interface MessageParametersMapper<P> {

  /**
   * Calculates a message representation on elliptic curve.
   *
   * @param parameters parameters of the message.
   * @return point on elliptic curve.
   */
  P map(MessageParameters parameters);
}
