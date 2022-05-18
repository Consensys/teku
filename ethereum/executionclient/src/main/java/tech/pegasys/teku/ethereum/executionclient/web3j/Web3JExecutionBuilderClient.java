/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.ethereum.executionclient.web3j;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.executionclient.ExecutionBuilderClient;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.SignedBuilderBidV1;
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistrationV1;

public class Web3JExecutionBuilderClient implements ExecutionBuilderClient {
  @SuppressWarnings("unused") // will be removed in upcoming PRs
  private final Web3JClient web3JClient;

  public Web3JExecutionBuilderClient(final Web3JClient web3JClient) {
    this.web3JClient = web3JClient;
  }

  @Override
  public SafeFuture<Response<Void>> status() {
    return SafeFuture.failedFuture(new UnsupportedOperationException("Deprecated"));
  }

  @Override
  public SafeFuture<Response<Void>> registerValidator(
      UInt64 slot, final SignedValidatorRegistrationV1 signedValidatorRegistrationV1) {
    return SafeFuture.failedFuture(new UnsupportedOperationException("Deprecated"));
  }

  @Override
  public SafeFuture<Response<SignedBuilderBidV1>> getHeader(
      final UInt64 slot, final BLSPublicKey pubKey, final Bytes32 parentHash) {
    return SafeFuture.failedFuture(new UnsupportedOperationException("Deprecated"));
  }

  @Override
  public SafeFuture<Response<ExecutionPayload>> getPayload(
      SignedBeaconBlock signedBlindedBeaconBlock) {
    return SafeFuture.failedFuture(new UnsupportedOperationException("Deprecated"));
  }
}
