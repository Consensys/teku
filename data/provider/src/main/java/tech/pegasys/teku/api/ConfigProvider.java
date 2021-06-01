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

package tech.pegasys.teku.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tech.pegasys.teku.api.response.v1.config.GetForkScheduleResponse;
import tech.pegasys.teku.api.response.v1.config.GetSpecResponse;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;

public class ConfigProvider {
  final Spec spec;

  public ConfigProvider(final Spec spec) {
    this.spec = spec;
  }

  public GetSpecResponse getConfig() {
    final Map<String, String> configAttributes = new HashMap<>();
    final SpecConfig config =
        spec
            // Display genesis spec, for now
            .atEpoch(UInt64.ZERO)
            .getConfig();

    config
        .getRawConfig()
        .forEach(
            (k, v) -> {
              configAttributes.put(k, "" + v);
            });

    // For the time being, manually add legacy constants for compatibility reasons
    // These constants are no longer defined in newer config files, but may be required by consumers
    configAttributes.put("BLS_WITHDRAWAL_PREFIX", config.getBlsWithdrawalPrefix().toHexString());
    configAttributes.put(
        "TARGET_AGGREGATORS_PER_COMMITTEE",
        Integer.toString(config.getTargetAggregatorsPerCommittee(), 10));
    configAttributes.put(
        "RANDOM_SUBNETS_PER_VALIDATOR",
        Integer.toString(config.getRandomSubnetsPerValidator(), 10));
    configAttributes.put(
        "EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION",
        Integer.toString(config.getEpochsPerRandomSubnetSubscription(), 10));
    configAttributes.put("DOMAIN_BEACON_PROPOSER", config.getDomainBeaconProposer().toHexString());
    configAttributes.put("DOMAIN_BEACON_ATTESTER", config.getDomainBeaconAttester().toHexString());
    configAttributes.put("DOMAIN_RANDAO", config.getDomainRandao().toHexString());
    configAttributes.put("DOMAIN_DEPOSIT", config.getDomainDeposit().toHexString());
    configAttributes.put("DOMAIN_VOLUNTARY_EXIT", config.getDomainVoluntaryExit().toHexString());
    configAttributes.put("DOMAIN_SELECTION_PROOF", config.getDomainSelectionProof().toHexString());
    configAttributes.put(
        "DOMAIN_AGGREGATE_AND_PROOF", config.getDomainAggregateAndProof().toHexString());
    // Manually add legacy altair constants
    config
        .toVersionAltair()
        .ifPresent(
            altairConfig -> {
              configAttributes.put(
                  "DOMAIN_SYNC_COMMITTEE", altairConfig.getDomainSyncCommittee().toHexString());
              configAttributes.put(
                  "DOMAIN_SYNC_COMMITTEE_SELECTION_PROOF",
                  altairConfig.getDomainSyncCommitteeSelectionProof().toHexString());
              configAttributes.put(
                  "DOMAIN_CONTRIBUTION_AND_PROOF",
                  altairConfig.getDomainContributionAndProof().toHexString());
              configAttributes.put(
                  "TARGET_AGGREGATORS_PER_SYNC_SUBCOMMITTEE",
                  Integer.toString(altairConfig.getTargetAggregatorsPerSyncSubcommittee(), 10));
            });

    return new GetSpecResponse(configAttributes);
  }

  public GetForkScheduleResponse getForkSchedule() {
    final List<Fork> forkList =
        spec.getForkSchedule().getForks().stream().map(Fork::new).collect(Collectors.toList());
    return new GetForkScheduleResponse(forkList);
  }

  public SpecConfig getGenesisSpecConfig() {
    return spec.getGenesisSpecConfig();
  }

  public UInt64 computeEpochAtSlot(final UInt64 slot) {
    return spec.computeEpochAtSlot(slot);
  }
}
