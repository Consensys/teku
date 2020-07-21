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

package tech.pegasys.teku.bls.impl.noop;

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BatchSemiAggregate;
import tech.pegasys.teku.bls.impl.PublicKey;
import tech.pegasys.teku.bls.impl.Signature;
import tech.pegasys.teku.bls.impl.mikuli.MikuliBLS12381;
import tech.pegasys.teku.bls.impl.mikuli.MikuliSignature;

/** BLS implementation the same as Mikuli which omit signature validations */
public class NoopBLS12381 extends MikuliBLS12381 {
  private static final Logger LOG = LogManager.getLogger();

  @Override
  public MikuliSignature signatureFromCompressed(Bytes compressedSignatureBytes) {
    return new NoopSignature(super.signatureFromCompressed(compressedSignatureBytes));
  }

  @Override
  public MikuliSignature aggregateSignatures(List<? extends Signature> signatures) {
    return new NoopSignature(super.aggregateSignatures(signatures));
  }

  @Override
  public BatchSemiAggregate prepareBatchVerify(
      int index, List<? extends PublicKey> publicKeys, Bytes message, Signature signature) {
    return new BatchSemiAggregate() {};
  }

  @Override
  public BatchSemiAggregate prepareBatchVerify2(
      int index,
      List<? extends PublicKey> publicKeys1,
      Bytes message1,
      Signature signature1,
      List<? extends PublicKey> publicKeys2,
      Bytes message2,
      Signature signature2) {
    return new BatchSemiAggregate() {};
  }

  @Override
  public boolean completeBatchVerify(List<? extends BatchSemiAggregate> preparedList) {
    LOG.warn("BLS verification is disabled");
    return true;
  }

  @Override
  public Signature randomSignature(int seed) {
    return new NoopSignature((MikuliSignature) super.randomSignature(seed));
  }
}
