/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.constants.ValidatorConstants;

public class SpecConfigData {
  private static final Logger LOG = LogManager.getLogger();
  private final SpecConfig specConfig;

  public SpecConfigData(final SpecConfig specConfig) {
    this.specConfig = specConfig;
  }

  public Map<String, Object> getConfigMap() {
    final Map<String, Object> configAttributes = new HashMap<>();
    specConfig
        .getRawConfig()
        .forEach(
            (name, value) -> {
              if (value instanceof List<?>) {
                LOG.debug("Config field {} is a list", name);
                configAttributes.put(name, value);
              } else if (value != null) {
                configAttributes.put(name, ConfigProvider.formatValue(value));
              } else {
                LOG.warn("Config field {} was set to null in runtime configuration", name);
              }
            });

    configAttributes.put("BLS_WITHDRAWAL_PREFIX", getBlsWithdrawalPrefix().toHexString());
    configAttributes.put(
        "DOMAIN_BLS_TO_EXECUTION_CHANGE", getDomainBlsToExecutionChange().toHexString());
    configAttributes.put("TARGET_AGGREGATORS_PER_COMMITTEE", getTargetAggregatorsPerCommittee());
    configAttributes.put("DOMAIN_BEACON_PROPOSER", getDomainBeaconProposer().toHexString());
    configAttributes.put("DOMAIN_BEACON_ATTESTER", getDomainBeaconAttester().toHexString());
    configAttributes.put("DOMAIN_RANDAO", getDomainRandao().toHexString());
    configAttributes.put("DOMAIN_DEPOSIT", getDomainDeposit().toHexString());
    configAttributes.put("DOMAIN_VOLUNTARY_EXIT", getDomainVoluntaryExit().toHexString());
    configAttributes.put("DOMAIN_SELECTION_PROOF", getDomainSelectionProof().toHexString());
    configAttributes.put("DOMAIN_AGGREGATE_AND_PROOF", getDomainAggregateAndProof().toHexString());
    configAttributes.put("DOMAIN_APPLICATION_BUILDER", getDomainApplicationBuilder().toHexString());

    getDomainSyncCommittee()
        .ifPresent(
            committee -> configAttributes.put("DOMAIN_SYNC_COMMITTEE", committee.toHexString()));
    getDomainSyncCommitteeSelectionProof()
        .ifPresent(
            proof ->
                configAttributes.put("DOMAIN_SYNC_COMMITTEE_SELECTION_PROOF", proof.toHexString()));
    getDomainContributionAndProof()
        .ifPresent(
            contribution ->
                configAttributes.put("DOMAIN_CONTRIBUTION_AND_PROOF", contribution.toHexString()));
    getTargetAggregatorsPerSyncSubcommittee()
        .ifPresent(
            targetAggregators ->
                configAttributes.put(
                    "TARGET_AGGREGATORS_PER_SYNC_SUBCOMMITTEE", targetAggregators));
    getSyncCommitteeSubnetCount()
        .ifPresent(subnetCount -> configAttributes.put("SYNC_COMMITTEE_SUBNET_COUNT", subnetCount));

    return configAttributes;
  }

  private Bytes getBlsWithdrawalPrefix() {
    return specConfig.getBlsWithdrawalPrefix();
  }

  private String getTargetAggregatorsPerCommittee() {
    return Integer.toString(ValidatorConstants.TARGET_AGGREGATORS_PER_COMMITTEE);
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

  public Bytes4 getDomainApplicationBuilder() {
    return Domain.APPLICATION_BUILDER;
  }

  public Bytes4 getDomainBlsToExecutionChange() {
    return Domain.BLS_TO_EXECUTION_CHANGE;
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

  private <T> Optional<T> getLegacyAltairConstant(final T value) {
    return specConfig.toVersionAltair().isPresent() ? Optional.of(value) : Optional.empty();
  }
}
