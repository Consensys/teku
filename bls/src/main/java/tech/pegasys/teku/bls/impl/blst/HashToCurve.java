/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.bls.impl.blst;

import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.impl.blst.swig.blst;
import tech.pegasys.teku.bls.impl.blst.swig.p2;

class HashToCurve {
  // The ciphersuite defined in the Eth2 specification which also serves as domain separation tag
  // https://github.com/ethereum/eth2.0-specs/blob/v0.12.0/specs/phase0/beacon-chain.md#bls-signatures
  static final Bytes ETH2_DST =
      Bytes.wrap("BLS_SIG_BLS12381G2_XMD:SHA-256_SSWU_RO_POP_".getBytes(StandardCharsets.US_ASCII));

  static p2 hashToG2(Bytes message) {
    return hashToG2(message, ETH2_DST);
  }

  static p2 hashToG2(Bytes message, Bytes dst) {
    p2 p2Hash = new p2();
    try {
      blst.hash_to_g2(p2Hash, message.toArray(), dst.toArray(), new byte[0]);
      return p2Hash;
    } catch (Exception e) {
      p2Hash.delete();
      throw e;
    }
  }
}
