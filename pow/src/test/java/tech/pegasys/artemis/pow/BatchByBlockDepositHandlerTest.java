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

package tech.pegasys.artemis.pow;

import static java.util.Arrays.asList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.primitives.UnsignedLong;
import java.util.Collections;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import org.web3j.protocol.core.methods.response.EthLog.LogObject;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.pow.contract.DepositContract.DepositEventEventResponse;
import tech.pegasys.artemis.pow.event.Deposit;
import tech.pegasys.artemis.pow.event.DepositsFromBlockEvent;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

class BatchByBlockDepositHandlerTest {
  @SuppressWarnings("unchecked")
  private final Consumer<DepositsFromBlockEvent> eventPublisher = mock(Consumer.class);

  private byte depositIndex = 0;
  private final BatchByBlockDepositHandler batcher = new BatchByBlockDepositHandler(eventPublisher);

  @Test
  public void shouldPublishSingleDepositWhenDepositFromNextBlockReceived() {
    final Block block1 = block(1);
    final Block block2 = block(2);
    final Deposit event1 = depositInBlock(block1);
    final Deposit event2 = depositInBlock(block2);
    batcher.onDepositEvent(block1, event1);
    batcher.onDepositEvent(block2, event2);

    verify(eventPublisher).accept(eventContaining(block1, event1));
    verifyNoMoreInteractions(eventPublisher);
  }

  @Test
  public void shouldPublishMultipleEventsFromSameBlockTogether() {
    final Block block1 = block(1);
    final Block block2 = block(2);
    final Deposit event1 = depositInBlock(block1);
    final Deposit event2 = depositInBlock(block1);
    final Deposit event3 = depositInBlock(block2);
    batcher.onDepositEvent(block1, event1);
    batcher.onDepositEvent(block1, event2);
    batcher.onDepositEvent(block2, event3);

    verify(eventPublisher).accept(eventContaining(block1, event1, event2));
    verifyNoMoreInteractions(eventPublisher);
  }

  @Test
  public void shouldPublishEventsFromSequenceOfBlocks() {
    final Block block1 = block(1);
    final Block block2 = block(2);
    final Block block3 = block(3);
    final Deposit event1 = depositInBlock(block1);
    final Deposit event2 = depositInBlock(block1);
    final Deposit event3 = depositInBlock(block2);
    final Deposit event4 = depositInBlock(block3);
    batcher.onDepositEvent(block1, event1);
    batcher.onDepositEvent(block1, event2);
    batcher.onDepositEvent(block2, event3);
    batcher.onDepositEvent(block3, event4);

    verify(eventPublisher).accept(eventContaining(block1, event1, event2));
    verify(eventPublisher).accept(eventContaining(block2, event3));
    verifyNoMoreInteractions(eventPublisher);
  }

  private DepositsFromBlockEvent eventContaining(Block block, final Deposit... deposits) {
    return new DepositsFromBlockEvent(
        UnsignedLong.valueOf(block.getNumber()),
        Bytes32.fromHexString(block.getHash()),
        UnsignedLong.valueOf(block.getTimestamp()),
        asList(deposits));
  }

  private Block block(int blockNumber) {
    final Bytes32 blockNumberBytes =
        Bytes32.leftPad(Bytes.fromHexStringLenient(Integer.toHexString(blockNumber)));
    final UnsignedLong blockTimestamp = UnsignedLong.valueOf(blockNumber + 10000);
    final Block block = new Block();
    block.setNumber(blockNumberBytes.toShortHexString());
    block.setHash(blockNumberBytes.toHexString());
    block.setTimestamp("0x" + blockTimestamp.toString(16));
    return block;
  }

  private Deposit depositInBlock(Block block) {
    final DepositEventEventResponse event = new DepositEventEventResponse();

    event.index = new byte[] {depositIndex, 0, 0, 0};
    depositIndex++;
    event.pubkey = BLSPublicKey.empty().toBytesCompressed().toArrayUnsafe();
    event.withdrawal_credentials = Bytes32.ZERO.toArrayUnsafe();
    event.signature = BLSSignature.empty().toBytes().toArrayUnsafe();
    event.amount = new byte[] {0, 0, 0, 0};
    event.log =
        new LogObject(
            false,
            "1",
            "1",
            "1",
            block.getHash(),
            block.getNumberRaw(),
            "",
            "",
            DepositContract.DEPOSITEVENT_EVENT.getName(),
            Collections.singletonList(DepositContract.DEPOSITEVENT_EVENT.getName()));
    return new Deposit(event);
  }
}
