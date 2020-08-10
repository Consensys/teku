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

package tech.pegasys.teku.reference.phase0.genesis;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.initialize_beacon_state_from_eth1;
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadBytes32FromSsz;
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadSsz;
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadStateFromSsz;
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadUInt64FromYaml;
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadYaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.phase0.TestExecutor;

public class GenesisInitializationTestExecutor implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    final BeaconState expectedGenesisState = loadStateFromSsz(testDefinition, "state.ssz");
    final UInt64 eth1Timestamp = loadUInt64FromYaml(testDefinition, "eth1_timestamp.yaml");
    final Bytes32 eth1BlockHash = loadBytes32FromSsz(testDefinition, "eth1_block_hash.ssz");
    final GenesisMetaData metaData = loadYaml(testDefinition, "meta.yaml", GenesisMetaData.class);
    final List<Deposit> deposits =
        IntStream.range(0, metaData.getDepositsCount())
            .mapToObj(index -> loadSsz(testDefinition, "deposits_" + index + ".ssz", Deposit.class))
            .collect(Collectors.toList());

    final BeaconState result =
        initialize_beacon_state_from_eth1(eth1BlockHash, eth1Timestamp, deposits);
    assertThat(result).isEqualTo(expectedGenesisState);
  }

  private static class GenesisMetaData {
    @JsonProperty(value = "deposits_count", required = true)
    private int depositsCount;

    public int getDepositsCount() {
      return depositsCount;
    }
  }
}
