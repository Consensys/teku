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

package tech.pegasys.artemis.statetransition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.util.config.Constants.DEPOSIT_CONTRACT_TREE_DEPTH;

import com.google.common.eventbus.EventBus;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.MerkleTree;
import tech.pegasys.artemis.events.EventChannel;
import tech.pegasys.artemis.pow.api.DepositEventChannel;
import tech.pegasys.artemis.pow.event.Deposit;
import tech.pegasys.artemis.pow.event.DepositsFromBlockEvent;
import tech.pegasys.artemis.statetransition.deposit.PostGenesisDepositHandler;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.EventSink;

public class PostGenesisDepositHandlerTest {

  private final ChainStorageClient chainStorageClient = mock(ChainStorageClient.class);
  private int seed = 0;
  private EventBus eventBus;
  private EventChannel<DepositEventChannel> eventChannel;
  private MerkleTree<Bytes32> merkleTree;
  private PostGenesisDepositHandler handler;
  private List<DepositWithIndex> eventsPostedOnEventBus;

  @BeforeEach
  void setUp() {
    eventChannel = EventChannel.create(DepositEventChannel.class);
    eventBus = new EventBus();
    eventsPostedOnEventBus = EventSink.capture(eventBus, DepositWithIndex.class);
    merkleTree = new MerkleTree<>(DEPOSIT_CONTRACT_TREE_DEPTH);
    handler = new PostGenesisDepositHandler(merkleTree, chainStorageClient, eventBus);
    eventChannel.subscribe(handler);
  }

  @Test
  void allDepositsGetProcessed() {
    when(chainStorageClient.isPreGenesis()).thenReturn(false);
    publishDeposits(eventChannel, createDepositEvents(10));
    assertThat(eventsPostedOnEventBus.size()).isEqualTo(10);
  }

  @Test
  void beforeGenesis_noDepositsGetProcessed() {
    when(chainStorageClient.isPreGenesis()).thenReturn(true);
    publishDeposits(eventChannel, createDepositEvents(10));
    assertThat(eventsPostedOnEventBus.size()).isEqualTo(0);
  }

  public void publishDeposits(
      EventChannel<DepositEventChannel> eventChannel, List<Deposit> deposits) {
    eventChannel
        .getPublisher()
        .notifyDepositsFromBlock(
            new DepositsFromBlockEvent(
                DataStructureUtil.randomUnsignedLong(seed++),
                DataStructureUtil.randomBytes32(seed++),
                DataStructureUtil.randomUnsignedLong(seed++),
                deposits));
  }

  public List<Deposit> createDepositEvents(int n) {
    return DataStructureUtil.randomDeposits(n, seed++).stream()
        .map(this::convertDepositOperationToEvent)
        .sorted(Comparator.comparing(Deposit::getMerkle_tree_index))
        .collect(Collectors.toList());
  }

  public Deposit convertDepositOperationToEvent(DepositWithIndex depositWithIndex) {
    DepositData data = depositWithIndex.getData();
    return new Deposit(
        data.getPubkey(),
        data.getWithdrawal_credentials(),
        data.getSignature(),
        data.getAmount(),
        depositWithIndex.getIndex());
  }
}
