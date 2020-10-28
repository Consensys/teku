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

package tech.pegasys.teku.bls.impl.mikuli;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BatchSemiAggregate;
import tech.pegasys.teku.bls.impl.Signature;

/**
 * Opaque data class which contains intermediate calculation results for batch BLS verification
 *
 * @see MikuliBLS12381#prepareBatchVerify(int, List, Bytes, Signature)
 * @see MikuliBLS12381#prepareBatchVerify2(int, List, Bytes, Signature, List, Bytes, Signature)
 * @see MikuliBLS12381#completeBatchVerify(List)
 */
public final class MukuliBatchSemiAggregate implements BatchSemiAggregate {
  private final G2Point sigPoint;
  private final GTPoint msgPubKeyPairing;

  MukuliBatchSemiAggregate(G2Point sigPoint, GTPoint msgPubKeyPairing) {
    this.sigPoint = sigPoint;
    this.msgPubKeyPairing = msgPubKeyPairing;
  }

  G2Point getSigPoint() {
    return sigPoint;
  }

  GTPoint getMsgPubKeyPairing() {
    return msgPubKeyPairing;
  }
}
