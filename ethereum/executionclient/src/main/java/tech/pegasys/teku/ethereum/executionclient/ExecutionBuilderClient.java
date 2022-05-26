/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.ethereum.executionclient;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistration;

public interface ExecutionBuilderClient {

  SafeFuture<Response<Void>> status();

  SafeFuture<Response<Void>> registerValidator(
      UInt64 slot, SszList<SignedValidatorRegistration> signedValidatorRegistrations);

  SafeFuture<Response<SignedBuilderBid>> getHeader(
      UInt64 slot, BLSPublicKey pubKey, Bytes32 parentHash);

  SafeFuture<Response<ExecutionPayload>> getPayload(SignedBeaconBlock signedBlindedBeaconBlock);
}
