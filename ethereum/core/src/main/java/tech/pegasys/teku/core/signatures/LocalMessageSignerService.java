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

package tech.pegasys.teku.core.signatures;

import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class LocalMessageSignerService implements MessageSignerService {
  private final BLSKeyPair keypair;
  private final AsyncRunner asyncRunner;

  public LocalMessageSignerService(final BLSKeyPair keypair, final AsyncRunner asyncRunner) {
    this.keypair = keypair;
    this.asyncRunner = asyncRunner;
  }

  @Override
  public SafeFuture<BLSSignature> signBlock(
      final Bytes signingRoot, final Map<String, Object> additionalProperties) {
    return sign(signingRoot);
  }

  @Override
  public SafeFuture<BLSSignature> signAttestation(
      final Bytes signingRoot, final Map<String, Object> additionalProperties) {
    return sign(signingRoot);
  }

  @Override
  public SafeFuture<BLSSignature> signAggregationSlot(
      final Bytes signingRoot, final Map<String, Object> additionalProperties) {
    return sign(signingRoot);
  }

  @Override
  public SafeFuture<BLSSignature> signAggregateAndProof(
      final Bytes signingRoot, final Map<String, Object> additionalProperties) {
    return sign(signingRoot);
  }

  @Override
  public SafeFuture<BLSSignature> signRandaoReveal(
      final Bytes signingRoot, final Map<String, Object> additionalProperties) {
    return sign(signingRoot);
  }

  @Override
  public SafeFuture<BLSSignature> signVoluntaryExit(
      final Bytes signingRoot, final Map<String, Object> additionalProperties) {
    return sign(signingRoot);
  }

  @Override
  public boolean isLocal() {
    return true;
  }

  private SafeFuture<BLSSignature> sign(final Bytes signing_root) {
    return asyncRunner.runAsync(
        () -> SafeFuture.completedFuture(BLS.sign(keypair.getSecretKey(), signing_root)));
  }
}
