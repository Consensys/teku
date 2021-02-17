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

package tech.pegasys.teku.statetransition.genesis;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.datastructures.interop.MockStartDepositGenerator;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.util.DepositGenerator;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.event.Deposit;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.exception.InvalidDepositEventsException;
import tech.pegasys.teku.spec.SpecProvider;
import tech.pegasys.teku.spec.StubSpecProvider;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.util.config.Constants;

public class GenesisHandlerTest {
  private static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(3);
  private SpecProvider specProvider;

  private static final List<DepositData> INITIAL_DEPOSIT_DATA =
      new MockStartDepositGenerator(new DepositGenerator(true)).createDeposits(VALIDATOR_KEYS);
  private static final List<Deposit> INITIAL_DEPOSITS =
      IntStream.range(0, INITIAL_DEPOSIT_DATA.size())
          .mapToObj(
              index -> {
                final DepositData data = INITIAL_DEPOSIT_DATA.get(index);
                return new Deposit(
                    data.getPubkey(),
                    data.getWithdrawal_credentials(),
                    data.getSignature(),
                    data.getAmount(),
                    UInt64.valueOf(index));
              })
          .collect(toList());

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault();
  private final TimeProvider timeProvider = mock(TimeProvider.class);
  private GenesisHandler genesisHandler;

  @BeforeEach
  public void setup() {
    specProvider =
        StubSpecProvider.create(
            config -> config.minGenesisActiveValidatorCount(VALIDATOR_KEYS.size()));
    genesisHandler =
        new GenesisHandler(storageSystem.recentChainData(), timeProvider, specProvider);
    when(timeProvider.getTimeInSeconds()).thenReturn(UInt64.ZERO);
  }

  @AfterEach
  public void tearDown() {
    Constants.setConstants("minimal");
  }

  @Test
  public void onDepositsFromBlock_shouldInitializeGenesis() {
    final UInt64 genesisTime = Constants.MIN_GENESIS_TIME;
    final int batchSize = INITIAL_DEPOSIT_DATA.size() / 2;

    final DepositsFromBlockEvent event1 =
        DepositsFromBlockEvent.create(
            UInt64.valueOf(100),
            dataStructureUtil.randomBytes32(),
            UInt64.ZERO,
            INITIAL_DEPOSITS.stream().limit(batchSize));

    final DepositsFromBlockEvent event2 =
        DepositsFromBlockEvent.create(
            UInt64.valueOf(100),
            dataStructureUtil.randomBytes32(),
            genesisTime,
            INITIAL_DEPOSITS.stream().skip(batchSize));

    assertThat(storageSystem.recentChainData().isPreGenesis()).isTrue();
    genesisHandler.onDepositsFromBlock(event1);
    assertThat(storageSystem.recentChainData().isPreGenesis()).isTrue();
    genesisHandler.onDepositsFromBlock(event2);
    assertThat(storageSystem.recentChainData().isPreGenesis()).isFalse();
  }

  @Test
  public void onDepositsFromBlock_missingFirstEvent() {
    final DepositsFromBlockEvent event =
        dataStructureUtil.randomDepositsFromBlockEvent(UInt64.ONE, 1, 11);

    assertThatThrownBy(() -> genesisHandler.onDepositsFromBlock(event))
        .isInstanceOf(InvalidDepositEventsException.class)
        .hasMessageContaining("Expected next deposit at index 0, but got 1");
  }

  @Test
  public void onDepositsFromBlock_missingRangeOfEvents() {
    final DepositsFromBlockEvent event1 =
        dataStructureUtil.randomDepositsFromBlockEvent(UInt64.valueOf(10), 0, 10);
    final DepositsFromBlockEvent event3 =
        dataStructureUtil.randomDepositsFromBlockEvent(UInt64.valueOf(12), 15, 20);

    genesisHandler.onDepositsFromBlock(event1);
    assertThatThrownBy(() -> genesisHandler.onDepositsFromBlock(event3))
        .isInstanceOf(InvalidDepositEventsException.class)
        .hasMessageContaining("Expected next deposit at index 10, but got 15");
  }
}
