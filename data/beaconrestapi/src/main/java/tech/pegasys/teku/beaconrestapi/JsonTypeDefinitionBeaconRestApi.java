/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.beaconrestapi;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNSUPPORTED_MEDIA_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.StringUtils;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ExecutionClientDataProvider;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.exceptions.ServiceUnavailableException;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin.Liveness;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin.PutLogLevel;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin.Readiness;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetAllBlocksAtSlot;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetDepositSnapshot;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetDeposits;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetEth1Data;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetEth1DataCache;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetEth1VotingSummary;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetProposersData;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetStateByBlockRoot;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.node.GetPeersScore;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.validatorInclusion.GetGlobalValidatorInclusion;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.validatorInclusion.GetValidatorInclusion;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetAttestations;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetAttesterSlashings;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlindedBlock;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlock;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockAttestations;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockHeader;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockHeaders;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlockRoot;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlsToExecutionChanges;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetFinalizedBlockRoot;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetFinalizedCheckpointState;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetGenesis;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetProposerSlashings;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateCommittees;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateFinalityCheckpoints;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateFork;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateRandao;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateRoot;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateSyncCommittees;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateValidator;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateValidatorBalances;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateValidators;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetVoluntaryExits;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostAttestation;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostAttesterSlashing;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostBlindedBlock;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostBlock;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostBlsToExecutionChanges;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostProposerSlashing;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostSyncCommittees;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostVoluntaryExit;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.lightclient.GetLightClientBootstrap;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.lightclient.GetLightClientUpdatesByRange;
import tech.pegasys.teku.beaconrestapi.handlers.v1.config.GetDepositContract;
import tech.pegasys.teku.beaconrestapi.handlers.v1.config.GetForkSchedule;
import tech.pegasys.teku.beaconrestapi.handlers.v1.config.GetSpec;
import tech.pegasys.teku.beaconrestapi.handlers.v1.debug.GetChainHeadsV1;
import tech.pegasys.teku.beaconrestapi.handlers.v1.debug.GetForkChoice;
import tech.pegasys.teku.beaconrestapi.handlers.v1.events.GetEvents;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetHealth;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetIdentity;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeerById;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeerCount;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetPeers;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetSyncing;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetVersion;
import tech.pegasys.teku.beaconrestapi.handlers.v1.rewards.GetBlockRewards;
import tech.pegasys.teku.beaconrestapi.handlers.v1.rewards.GetSyncCommitteeRewards;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetAggregateAttestation;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetAttestationData;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetNewBlindedBlock;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetProposerDuties;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetSyncCommitteeContribution;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostAggregateAndProofs;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostAttesterDuties;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostContributionAndProofs;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostPrepareBeaconProposer;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostRegisterValidator;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostSubscribeToBeaconCommitteeSubnet;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostSyncCommitteeSubscriptions;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostSyncDuties;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostValidatorLiveness;
import tech.pegasys.teku.beaconrestapi.handlers.v2.debug.GetChainHeadsV2;
import tech.pegasys.teku.beaconrestapi.handlers.v2.debug.GetState;
import tech.pegasys.teku.beaconrestapi.handlers.v2.validator.GetNewBlock;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.http.ContentTypeNotSupportedException;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.restapi.RestApi;
import tech.pegasys.teku.infrastructure.restapi.RestApiBuilder;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.validator.api.NodeSyncingException;
import tech.pegasys.teku.validator.coordinator.Eth1DataProvider;
import tech.pegasys.teku.validator.coordinator.MissingDepositsException;

public class JsonTypeDefinitionBeaconRestApi implements BeaconRestApi {

  private final RestApi restApi;

  public JsonTypeDefinitionBeaconRestApi(
      final DataProvider dataProvider,
      final Eth1DataProvider eth1DataProvider,
      final BeaconRestApiConfig config,
      final EventChannels eventChannels,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final ExecutionClientDataProvider executionClientDataProvider,
      final Spec spec) {
    restApi =
        create(
            config,
            dataProvider,
            eth1DataProvider,
            eventChannels,
            asyncRunner,
            timeProvider,
            executionClientDataProvider,
            spec);
  }

  @Override
  public SafeFuture<?> start() {
    return restApi.start();
  }

  @Override
  public SafeFuture<?> stop() {
    return restApi.stop();
  }

  @Override
  public int getListenPort() {
    return restApi.getListenPort();
  }

  private static RestApi create(
      final BeaconRestApiConfig config,
      final DataProvider dataProvider,
      final Eth1DataProvider eth1DataProvider,
      final EventChannels eventChannels,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final ExecutionClientDataProvider executionClientDataProvider,
      final Spec spec) {
    final SchemaDefinitionCache schemaCache = new SchemaDefinitionCache(spec);
    RestApiBuilder builder =
        new RestApiBuilder()
            .openApiInfo(
                openApi ->
                    openApi
                        .title(StringUtils.capitalize(VersionProvider.CLIENT_IDENTITY))
                        .version(VersionProvider.IMPLEMENTATION_VERSION)
                        .description(
                            "A minimal API specification for the beacon node, which enables a validator "
                                + "to connect and perform its obligations on the Ethereum beacon chain.")
                        .license("Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0.html"))
            .openApiDocsEnabled(config.isRestApiDocsEnabled())
            .listenAddress(config.getRestApiInterface())
            .port(config.getRestApiPort())
            .maxUrlLength(config.getMaxUrlLength())
            .corsAllowedOrigins(config.getRestApiCorsAllowedOrigins())
            .hostAllowlist(config.getRestApiHostAllowlist())
            .exceptionHandler(
                ChainDataUnavailableException.class, (throwable) -> HttpErrorResponse.noContent())
            .exceptionHandler(
                NodeSyncingException.class, (throwable) -> HttpErrorResponse.serviceUnavailable())
            .exceptionHandler(
                ServiceUnavailableException.class,
                (throwable) -> HttpErrorResponse.serviceUnavailable())
            .exceptionHandler(
                ContentTypeNotSupportedException.class,
                (throwable) ->
                    new HttpErrorResponse(SC_UNSUPPORTED_MEDIA_TYPE, throwable.getMessage()))
            .exceptionHandler(
                MissingDepositsException.class,
                (throwable) ->
                    new HttpErrorResponse(SC_INTERNAL_SERVER_ERROR, throwable.getMessage()))
            .exceptionHandler(
                BadRequestException.class,
                (throwable) -> HttpErrorResponse.badRequest(throwable.getMessage()))
            .exceptionHandler(
                JsonProcessingException.class,
                (throwable) -> HttpErrorResponse.badRequest(throwable.getMessage()))
            .exceptionHandler(
                IllegalArgumentException.class,
                (throwable) -> HttpErrorResponse.badRequest(throwable.getMessage()))
            // Beacon Handlers
            .endpoint(new GetGenesis(dataProvider))
            .endpoint(new GetStateRoot(dataProvider))
            .endpoint(new GetStateFork(dataProvider))
            .endpoint(new GetStateFinalityCheckpoints(dataProvider))
            .endpoint(new GetStateValidators(dataProvider))
            .endpoint(new GetStateValidator(dataProvider))
            .endpoint(new GetStateValidatorBalances(dataProvider))
            .endpoint(new GetStateCommittees(dataProvider))
            .endpoint(new GetStateSyncCommittees(dataProvider))
            .endpoint(new GetStateRandao(dataProvider))
            .endpoint(new GetBlockHeaders(dataProvider))
            .endpoint(new GetBlockHeader(dataProvider))
            .endpoint(new PostBlock(dataProvider, spec, schemaCache))
            .endpoint(new PostBlindedBlock(dataProvider, spec, schemaCache))
            .endpoint(new GetBlock(dataProvider, schemaCache))
            .endpoint(
                new tech.pegasys.teku.beaconrestapi.handlers.v2.beacon.GetBlock(
                    dataProvider, schemaCache))
            .endpoint(new GetBlindedBlock(dataProvider, schemaCache))
            .endpoint(new GetFinalizedCheckpointState(dataProvider, spec))
            .endpoint(new GetBlockRoot(dataProvider))
            .endpoint(new GetFinalizedBlockRoot(dataProvider))
            .endpoint(new GetBlockAttestations(dataProvider, spec))
            .endpoint(new GetAttestations(dataProvider, spec))
            .endpoint(new PostAttestation(dataProvider, schemaCache))
            .endpoint(new GetAttesterSlashings(dataProvider, spec))
            .endpoint(new PostAttesterSlashing(dataProvider, spec))
            .endpoint(new GetProposerSlashings(dataProvider))
            .endpoint(new PostProposerSlashing(dataProvider))
            .endpoint(new GetVoluntaryExits(dataProvider))
            .endpoint(new PostVoluntaryExit(dataProvider))
            .endpoint(new PostSyncCommittees(dataProvider))
            .endpoint(new PostValidatorLiveness(dataProvider))
            .endpoint(new PostBlsToExecutionChanges(dataProvider, schemaCache))
            .endpoint(new GetBlsToExecutionChanges(dataProvider, schemaCache))
            // Event Handler
            .endpoint(
                new GetEvents(
                    dataProvider,
                    eventChannels,
                    asyncRunner,
                    timeProvider,
                    config.getMaxPendingEvents()))
            // Node Handlers
            .endpoint(new GetHealth(dataProvider))
            .endpoint(new GetIdentity(dataProvider))
            .endpoint(new GetPeers(dataProvider))
            .endpoint(new GetPeerCount(dataProvider))
            .endpoint(new GetPeerById(dataProvider))
            .endpoint(new GetSyncing(dataProvider))
            .endpoint(new GetVersion())
            // Rewards Handlers
            .endpoint(new GetSyncCommitteeRewards(dataProvider))
            .endpoint(new GetBlockRewards())
            // Validator Handlers
            .endpoint(new PostAttesterDuties(dataProvider))
            .endpoint(new GetProposerDuties(dataProvider))
            .endpoint(
                new tech.pegasys.teku.beaconrestapi.handlers.v1.validator.GetNewBlock(
                    dataProvider, schemaCache))
            .endpoint(new GetNewBlock(dataProvider, spec, schemaCache))
            .endpoint(new GetNewBlindedBlock(dataProvider, spec, schemaCache))
            .endpoint(new GetAttestationData(dataProvider))
            .endpoint(new GetAggregateAttestation(dataProvider, spec))
            .endpoint(new PostAggregateAndProofs(dataProvider, spec.getGenesisSchemaDefinitions()))
            .endpoint(new PostSubscribeToBeaconCommitteeSubnet(dataProvider))
            .endpoint(new PostSyncDuties(dataProvider))
            .endpoint(new GetSyncCommitteeContribution(dataProvider, schemaCache))
            .endpoint(new PostSyncCommitteeSubscriptions(dataProvider))
            .endpoint(new PostContributionAndProofs(dataProvider, schemaCache))
            .endpoint(new PostPrepareBeaconProposer(dataProvider))
            .endpoint(new PostRegisterValidator(dataProvider))
            // Config Handlers
            .endpoint(
                new GetDepositContract(
                    config.getEth1DepositContractAddress(), dataProvider.getConfigProvider()))
            .endpoint(new GetForkSchedule(dataProvider))
            .endpoint(new GetSpec(dataProvider))
            // Debug Handlers
            .endpoint(new GetChainHeadsV1(dataProvider))
            .endpoint(new GetChainHeadsV2(dataProvider))
            .endpoint(
                new tech.pegasys.teku.beaconrestapi.handlers.v1.debug.GetState(
                    dataProvider, spec, schemaCache))
            .endpoint(new GetState(dataProvider, schemaCache))
            .endpoint(new GetForkChoice(dataProvider))
            // Teku Specific Handlers
            .endpoint(new PutLogLevel())
            .endpoint(new GetStateByBlockRoot(dataProvider, spec))
            .endpoint(new Liveness(dataProvider))
            .endpoint(new Readiness(dataProvider, executionClientDataProvider))
            .endpoint(new GetAllBlocksAtSlot(dataProvider, schemaCache))
            .endpoint(new GetPeersScore(dataProvider))
            .endpoint(new GetProposersData(dataProvider))
            .endpoint(new GetDeposits(eth1DataProvider))
            .endpoint(new GetEth1Data(dataProvider, eth1DataProvider))
            .endpoint(new GetEth1DataCache(eth1DataProvider))
            .endpoint(new GetEth1VotingSummary(dataProvider, eth1DataProvider))
            .endpoint(new GetDepositSnapshot(eth1DataProvider))
            .endpoint(new GetGlobalValidatorInclusion(dataProvider))
            .endpoint(new GetValidatorInclusion(dataProvider));

    if (config.isRestApiLightClientEnabled()) {
      builder =
          builder
              .endpoint(new GetLightClientBootstrap(dataProvider, schemaCache))
              .endpoint(new GetLightClientUpdatesByRange(schemaCache));
    }

    return builder.build();
  }
}
