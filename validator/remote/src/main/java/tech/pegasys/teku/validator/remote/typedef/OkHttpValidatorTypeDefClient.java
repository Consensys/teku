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

package tech.pegasys.teku.validator.remote.typedef;

import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.validator.remote.typedef.handlers.CreateBlockRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.GetGenesisRequest;

public class OkHttpValidatorTypeDefClient {

  private final OkHttpClient okHttpClient;
  private final HttpUrl baseEndpoint;

  private final Spec spec;

  public OkHttpValidatorTypeDefClient(
      final OkHttpClient okHttpClient, final HttpUrl baseEndpoint, final Spec spec) {
    this.okHttpClient = okHttpClient;
    this.baseEndpoint = baseEndpoint;
    this.spec = spec;
  }

  public Optional<GenesisData> getGenesis() {
    final GetGenesisRequest request = new GetGenesisRequest(okHttpClient, baseEndpoint);
    return request.getGenesisData();
  }

  public Optional<BeaconBlock> createUnsignedBlock(
      final UInt64 slot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final boolean blinded) {
    final CreateBlockRequest createBlockRequest =
        new CreateBlockRequest(baseEndpoint, okHttpClient, spec, slot, blinded);
    return createBlockRequest.createUnsignedBlock(randaoReveal, graffiti);
  }
}
