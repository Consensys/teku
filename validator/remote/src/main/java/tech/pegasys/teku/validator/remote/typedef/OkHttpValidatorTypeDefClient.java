/*
 * Copyright Consensys Software Inc., 2022
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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorData;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.required.SyncingStatus;
import tech.pegasys.teku.validator.remote.typedef.handlers.CreateAttestationDataRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.CreateBlockRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.GetGenesisRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.GetProposerDutiesRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.GetStateValidatorsRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.GetSyncingStatusRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.ProduceBlockRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.RegisterValidatorsRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.SendSignedBlockRequest;

public class OkHttpValidatorTypeDefClient extends OkHttpValidatorMinimalTypeDefClient {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final boolean preferSszBlockEncoding;
  private final GetSyncingStatusRequest getSyncingStatusRequest;
  private final GetGenesisRequest getGenesisRequest;
  private final GetProposerDutiesRequest getProposerDutiesRequest;
  private final GetStateValidatorsRequest getStateValidatorsRequest;
  private final SendSignedBlockRequest sendSignedBlockRequest;
  private final RegisterValidatorsRequest registerValidatorsRequest;
  private final CreateAttestationDataRequest createAttestationDataRequest;

  public OkHttpValidatorTypeDefClient(
      final OkHttpClient okHttpClient,
      final HttpUrl baseEndpoint,
      final Spec spec,
      final boolean preferSszBlockEncoding) {
    super(okHttpClient, baseEndpoint);
    this.spec = spec;
    this.preferSszBlockEncoding = preferSszBlockEncoding;
    this.getSyncingStatusRequest = new GetSyncingStatusRequest(okHttpClient, baseEndpoint);
    this.getGenesisRequest = new GetGenesisRequest(okHttpClient, baseEndpoint);
    this.getProposerDutiesRequest = new GetProposerDutiesRequest(baseEndpoint, okHttpClient);
    this.getStateValidatorsRequest = new GetStateValidatorsRequest(baseEndpoint, okHttpClient);
    this.sendSignedBlockRequest =
        new SendSignedBlockRequest(spec, baseEndpoint, okHttpClient, preferSszBlockEncoding);
    this.registerValidatorsRequest =
        new RegisterValidatorsRequest(baseEndpoint, okHttpClient, false);
    this.createAttestationDataRequest =
        new CreateAttestationDataRequest(baseEndpoint, okHttpClient);
  }

  public SyncingStatus getSyncingStatus() {
    return getSyncingStatusRequest.getSyncingStatus();
  }

  public Optional<GenesisData> getGenesis() {
    return getGenesisRequest
        .getGenesisData()
        .map(
            response ->
                new GenesisData(response.getGenesisTime(), response.getGenesisValidatorsRoot()));
  }

  public Optional<ProposerDuties> getProposerDuties(final UInt64 epoch) {
    return getProposerDutiesRequest.getProposerDuties(epoch);
  }

  public Optional<List<StateValidatorData>> getStateValidators(final List<String> validatorIds) {
    return getStateValidatorsRequest
        .getStateValidators(validatorIds)
        .map(ObjectAndMetaData::getData);
  }

  public void getAttestationDuties(final UInt64 epoch, final Collection<Integer> validatorIndices) {
    // not implemented
  }

  public SendSignedBlockResult sendSignedBlock(final SignedBlockContainer blockContainer) {
    return sendSignedBlockRequest.sendSignedBlock(blockContainer);
  }

  @Deprecated
  public Optional<BlockContainerAndMetaData> createUnsignedBlock(
      final UInt64 slot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final boolean blinded) {
    final CreateBlockRequest createBlockRequest =
        new CreateBlockRequest(
            getBaseEndpoint(), getOkHttpClient(), spec, slot, blinded, preferSszBlockEncoding);
    try {
      return createBlockRequest.createUnsignedBlock(randaoReveal, graffiti);
    } catch (final BlindedBlockEndpointNotAvailableException ex) {
      LOG.warn(
          "Beacon Node {} does not support blinded block production. Falling back to normal block production.",
          getBaseEndpoint());
      return createUnsignedBlock(slot, randaoReveal, graffiti, false);
    }
  }

  public Optional<BlockContainerAndMetaData> createUnsignedBlock(
      final UInt64 slot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final Optional<UInt64> requestedBuilderBoostFactor) {
    final ProduceBlockRequest produceBlockRequest =
        new ProduceBlockRequest(
            getBaseEndpoint(), getOkHttpClient(), spec, slot, preferSszBlockEncoding);
    try {
      return produceBlockRequest.createUnsignedBlock(
          randaoReveal, graffiti, requestedBuilderBoostFactor);
    } catch (final BlockProductionV3FailedException ex) {
      LOG.warn("Produce Block V3 request failed at slot {}. Retrying with Block V2", slot);

      // Falling back to V2, we have to request a blinded block to be able to support both local and
      // builder flow.
      return createUnsignedBlock(slot, randaoReveal, graffiti, true);
    }
  }

  public void registerValidators(
      final SszList<SignedValidatorRegistration> validatorRegistrations) {
    registerValidatorsRequest.registerValidators(validatorRegistrations);
  }

  public Optional<AttestationData> createAttestationData(
      final UInt64 slot, final int committeeIndex) {
    return createAttestationDataRequest.createAttestationData(slot, committeeIndex);
  }
}
