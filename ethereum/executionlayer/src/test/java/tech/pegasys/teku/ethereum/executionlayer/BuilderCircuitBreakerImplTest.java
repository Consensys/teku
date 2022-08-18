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

package tech.pegasys.teku.ethereum.executionlayer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BuilderCircuitBreakerImplTest {
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final BuilderCircuitBreakerImpl builderCircuitBreaker =
      new BuilderCircuitBreakerImpl(spec, INSPECTION_WINDOW, ALLOWED_FAULTS);

  private static final int INSPECTION_WINDOW = 10;
  private static final int ALLOWED_FAULTS = 5;

  @Test
  @BeforeEach
  void check() {
    // all tests assume 64 block roots history
    assertThat(spec.getGenesisSpec().getSlotsPerHistoricalRoot()).isEqualTo(64);
  }

  @Test
  void shouldNotEngage_noMissedSlots() {
    final UInt64 slot = UInt64.valueOf(100);

    // fill blockRoots with random roots and have a different latestBlockHeader root too
    final List<Bytes32> blockRoots =
        Stream.generate(dataStructureUtil::randomBytes32)
            .limit(spec.getGenesisSpec().getSlotsPerHistoricalRoot())
            .collect(Collectors.toList());
    final BeaconBlock latestBlock = dataStructureUtil.randomBeaconBlock(slot);
    final BeaconBlockHeader latestBlockHeader = BeaconBlockHeader.fromBlock(latestBlock);
    final BeaconState state = prepareState(slot, latestBlockHeader, blockRoots);

    assertThat(builderCircuitBreaker.isEngaged(state)).isFalse();
    assertThat(builderCircuitBreaker.getLatestUniqueBlockRootsCount(state)).isEqualTo(10);
  }

  @Test
  void shouldNotEngage_minimalAllowedFaults() {
    final UInt64 stateSlot = UInt64.valueOf(69);

    final BeaconBlock latestBlock = dataStructureUtil.randomBeaconBlock(68);
    final BeaconBlockHeader latestBlockHeader = BeaconBlockHeader.fromBlock(latestBlock);

    // fill blockRoots [59-63] with 2 unique roots
    // fill blockRoots [0-3] (slot 64 to 67) with 2 unique roots
    // a different latestBlockHeader root too (slot 68)
    // for a total of 5 unique

    final List<Bytes32> uniqueRoots =
        Stream.generate(dataStructureUtil::randomBytes32).limit(4).collect(Collectors.toList());

    final List<Bytes32> blockRoots =
        Stream.concat(
                // 4 elements - from slot 64 to 67
                Stream.of(
                    uniqueRoots.get(0), uniqueRoots.get(1), uniqueRoots.get(0), uniqueRoots.get(1)),
                Stream.concat(
                    // 55 elements
                    Stream.generate(() -> Bytes32.ZERO)
                        .limit(spec.getGenesisSpec().getSlotsPerHistoricalRoot() - 9),
                    // 5 elements - from slot 59 to 63
                    Stream.of(
                        uniqueRoots.get(2),
                        uniqueRoots.get(3),
                        uniqueRoots.get(2),
                        uniqueRoots.get(3),
                        uniqueRoots.get(2))))
            .collect(Collectors.toList());
    final BeaconState state = prepareState(stateSlot, latestBlockHeader, blockRoots);

    assertThat(builderCircuitBreaker.isEngaged(state)).isFalse();
    assertThat(builderCircuitBreaker.getLatestUniqueBlockRootsCount(state)).isEqualTo(5);
  }

  @Test
  void shouldEngage_belowAllowedFaults() {
    final UInt64 stateSlot = UInt64.valueOf(69);

    final BeaconBlock latestBlock = dataStructureUtil.randomBeaconBlock(67);
    final BeaconBlockHeader latestBlockHeader = BeaconBlockHeader.fromBlock(latestBlock);

    // fill blockRoots [59-63] with 2 unique roots
    // fill blockRoots [0-3] (slot 64 to 67) with 2 unique roots
    // latestBlockHeader same as slot 67
    // for a total of 4 unique

    final List<Bytes32> uniqueRoots =
        Stream.generate(dataStructureUtil::randomBytes32).limit(4).collect(Collectors.toList());

    final List<Bytes32> blockRoots =
        Stream.concat(
                // 4 elements - from slot 64 to 67
                Stream.of(
                    uniqueRoots.get(0),
                    uniqueRoots.get(0),
                    uniqueRoots.get(0),
                    latestBlockHeader.getRoot()),
                Stream.concat(
                    // 55 elements
                    Stream.generate(() -> Bytes32.ZERO)
                        .limit(spec.getGenesisSpec().getSlotsPerHistoricalRoot() - 9),
                    // 5 elements - from slot 59 to 63
                    Stream.of(
                        uniqueRoots.get(2),
                        uniqueRoots.get(3),
                        uniqueRoots.get(2),
                        uniqueRoots.get(3),
                        uniqueRoots.get(2))))
            .collect(Collectors.toList());

    final BeaconState state = prepareState(stateSlot, latestBlockHeader, blockRoots);

    assertThat(builderCircuitBreaker.isEngaged(state)).isTrue();
    assertThat(builderCircuitBreaker.getLatestUniqueBlockRootsCount(state)).isEqualTo(4);
  }

  @Test
  void shouldEngage_belowAllowedFaults_onlyLastBlockHeaderPresent() {
    final UInt64 stateSlot = UInt64.valueOf(69);

    final BeaconBlock latestBlock = dataStructureUtil.randomBeaconBlock(59);
    final BeaconBlockHeader latestBlockHeader = BeaconBlockHeader.fromBlock(latestBlock);

    // fill blockRoots with random roots and have a different latestBlockHeader root too
    final List<Bytes32> blockRoots =
        Stream.generate(latestBlockHeader::getRoot)
            .limit(spec.getGenesisSpec().getSlotsPerHistoricalRoot())
            .collect(Collectors.toList());

    final BeaconState state = prepareState(stateSlot, latestBlockHeader, blockRoots);

    assertThat(builderCircuitBreaker.isEngaged(state)).isTrue();
    assertThat(builderCircuitBreaker.getLatestUniqueBlockRootsCount(state)).isEqualTo(1);
  }

  @Test
  void shouldEngage_belowAllowedFaults_lastBlockHeaderWellOff() {
    final UInt64 stateSlot = UInt64.valueOf(69);

    final BeaconBlock latestBlock = dataStructureUtil.randomBeaconBlock(40);
    final BeaconBlockHeader latestBlockHeader = BeaconBlockHeader.fromBlock(latestBlock);

    // fill blockRoots with random roots and have a different latestBlockHeader root too
    final List<Bytes32> blockRoots =
        Stream.generate(latestBlockHeader::getRoot)
            .limit(spec.getGenesisSpec().getSlotsPerHistoricalRoot())
            .collect(Collectors.toList());

    final BeaconState state = prepareState(stateSlot, latestBlockHeader, blockRoots);

    assertThat(builderCircuitBreaker.isEngaged(state)).isTrue();
    assertThat(builderCircuitBreaker.getLatestUniqueBlockRootsCount(state)).isEqualTo(0);
  }

  private BeaconState prepareState(
      final UInt64 slot,
      final BeaconBlockHeader latestBlockHeader,
      final List<Bytes32> blockRoots) {
    return dataStructureUtil
        .stateBuilder(SpecMilestone.BELLATRIX, 0, 0)
        .slot(slot)
        .latestBlockHeader(latestBlockHeader)
        .blockRoots(prepareBlockRoots(blockRoots))
        .build();
  }

  private SszBytes32Vector prepareBlockRoots(List<Bytes32> blockRoots) {
    final SszBytes32VectorSchema<?> blockRootsSchema =
        spec.getGenesisSpec().getSchemaDefinitions().getBeaconStateSchema().getBlockRootsSchema();
    return blockRoots.stream().collect(blockRootsSchema.collectorUnboxed());
  }
}
