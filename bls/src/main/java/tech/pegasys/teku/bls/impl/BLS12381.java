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

package tech.pegasys.teku.bls.impl;

import java.util.List;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BatchSemiAggregate;

public interface BLS12381 {

  KeyPair generateKeyPair(Random random);

  default KeyPair generateKeyPair(long seed) {
    return generateKeyPair(new Random(seed));
  }

  PublicKey publicKeyFromCompressed(Bytes compressedPublicKeyBytes);

  Signature signatureFromCompressed(Bytes compressedSignatureBytes);

  SecretKey secretKeyFromBytes(Bytes secretKeyBytes);

  PublicKey aggregatePublicKeys(List<? extends PublicKey> publicKeys);

  Signature aggregateSignatures(List<? extends Signature> signatures);

  BatchSemiAggregate prepareBatchVerify(
      int index, List<? extends PublicKey> publicKeys, Bytes message, Signature signature);

  boolean completeBatchVerify(List<? extends BatchSemiAggregate> preparedList);
}
