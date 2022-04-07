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

package tech.pegasys.teku.beaconrestapi.handlers.v1.config;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES4_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.api.ConfigProvider;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableObjectTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringValueTypeDefinition;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.constants.ValidatorConstants;

public class GetSpecResponse {
  private static final StringValueTypeDefinition<Bytes> WITHDRAWAL_PREFIX_TYPE =
      DeserializableTypeDefinition.string(Bytes.class)
          .formatter(Bytes::toHexString)
          .parser(str -> Bytes.fromHexStringLenient(str, 1))
          .example("0x01")
          .description("Withdrawal prefix hexadecimal")
          .format("byte")
          .build();

  private final SpecConfig specConfig;

  GetSpecResponse(SpecConfig specConfig) {
    this.specConfig = specConfig;
  }

  public static SerializableTypeDefinition<GetSpecResponse> getSpecTypeDefinition(
      SpecConfig config) {
    final SerializableObjectTypeDefinitionBuilder<GetSpecResponse> builder =
        SerializableTypeDefinition.object(GetSpecResponse.class);

    config
        .getRawConfig()
        .forEach(
            (name, value) ->
                builder.withField(name, STRING_TYPE, (__) -> ConfigProvider.formatValue(value)));

    // For the time being, manually add legacy constants for compatibility reasons
    // These constants are no longer defined in newer config files, but may be required by consumers
    builder
        .withField(
            "BLS_WITHDRAWAL_PREFIX",
            WITHDRAWAL_PREFIX_TYPE,
            GetSpecResponse::getBlsWithdrawalPrefix)
        .withField(
            "TARGET_AGGREGATORS_PER_COMMITTEE",
            STRING_TYPE,
            GetSpecResponse::getTargetAggregatorsPerCommittee)
        .withField(
            "RANDOM_SUBNETS_PER_VALIDATOR",
            STRING_TYPE,
            GetSpecResponse::getRandomSubnetsPerValidator)
        .withField(
            "EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION",
            STRING_TYPE,
            GetSpecResponse::getEpochsPerRandomSubnetSubscription)
        .withField("DOMAIN_BEACON_PROPOSER", BYTES4_TYPE, GetSpecResponse::getDomainBeaconProposer)
        .withField("DOMAIN_BEACON_ATTESTER", BYTES4_TYPE, GetSpecResponse::getDomainBeaconAttester)
        .withField("DOMAIN_RANDAO", BYTES4_TYPE, GetSpecResponse::getDomainRandao)
        .withField("DOMAIN_DEPOSIT", BYTES4_TYPE, GetSpecResponse::getDomainDeposit)
        .withField("DOMAIN_VOLUNTARY_EXIT", BYTES4_TYPE, GetSpecResponse::getDomainVoluntaryExit)
        .withField("DOMAIN_SELECTION_PROOF", BYTES4_TYPE, GetSpecResponse::getDomainSelectionProof)
        .withField(
            "DOMAIN_AGGREGATE_AND_PROOF", BYTES4_TYPE, GetSpecResponse::getDomainAggregateAndProof)
        .withOptionalField(
            "DOMAIN_SYNC_COMMITTEE", BYTES4_TYPE, GetSpecResponse::getDomainSyncCommittee)
        .withOptionalField(
            "DOMAIN_SYNC_COMMITTEE_SELECTION_PROOF",
            BYTES4_TYPE,
            GetSpecResponse::getDomainSyncCommitteeSelectionProof)
        .withOptionalField(
            "DOMAIN_CONTRIBUTION_AND_PROOF",
            BYTES4_TYPE,
            GetSpecResponse::getDomainContributionAndProof)
        .withOptionalField(
            "TARGET_AGGREGATORS_PER_SYNC_SUBCOMMITTEE",
            STRING_TYPE,
            GetSpecResponse::getTargetAggregatorsPerSyncSubcommittee)
        .withOptionalField(
            "SYNC_COMMITTEE_SUBNET_COUNT",
            STRING_TYPE,
            GetSpecResponse::getSyncCommitteeSubnetCount);

    return SerializableTypeDefinition.object(GetSpecResponse.class)
        .name("GetSpecResponse")
        .withField("data", builder.build(), Function.identity())
        .build();
  }

  private Bytes getBlsWithdrawalPrefix() {
    return specConfig.getBlsWithdrawalPrefix();
  }

  private String getTargetAggregatorsPerCommittee() {
    return Integer.toString(ValidatorConstants.TARGET_AGGREGATORS_PER_COMMITTEE);
  }

  private String getRandomSubnetsPerValidator() {
    return Integer.toString(ValidatorConstants.RANDOM_SUBNETS_PER_VALIDATOR);
  }

  private String getEpochsPerRandomSubnetSubscription() {
    return Integer.toString(ValidatorConstants.EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION);
  }

  private Bytes4 getDomainBeaconProposer() {
    return Domain.BEACON_PROPOSER;
  }

  private Bytes4 getDomainBeaconAttester() {
    return Domain.BEACON_ATTESTER;
  }

  private Bytes4 getDomainRandao() {
    return Domain.RANDAO;
  }

  private Bytes4 getDomainDeposit() {
    return Domain.DEPOSIT;
  }

  private Bytes4 getDomainVoluntaryExit() {
    return Domain.VOLUNTARY_EXIT;
  }

  private Bytes4 getDomainSelectionProof() {
    return Domain.SELECTION_PROOF;
  }

  private Bytes4 getDomainAggregateAndProof() {
    return Domain.AGGREGATE_AND_PROOF;
  }

  private Optional<Bytes4> getDomainSyncCommittee() {
    return getLegacyAltairConstant(Domain.SYNC_COMMITTEE);
  }

  private Optional<Bytes4> getDomainSyncCommitteeSelectionProof() {
    return getLegacyAltairConstant(Domain.SYNC_COMMITTEE_SELECTION_PROOF);
  }

  private Optional<Bytes4> getDomainContributionAndProof() {
    return getLegacyAltairConstant(Domain.CONTRIBUTION_AND_PROOF);
  }

  private Optional<String> getTargetAggregatorsPerSyncSubcommittee() {
    return getLegacyAltairConstant(
        Integer.toString(ValidatorConstants.TARGET_AGGREGATORS_PER_SYNC_SUBCOMMITTEE));
  }

  private Optional<String> getSyncCommitteeSubnetCount() {
    return getLegacyAltairConstant(Integer.toString(NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT));
  }

  private <T> Optional<T> getLegacyAltairConstant(T value) {
    if (specConfig.toVersionAltair().isPresent()) {
      return Optional.of(value);
    }

    return Optional.empty();
  }
}
