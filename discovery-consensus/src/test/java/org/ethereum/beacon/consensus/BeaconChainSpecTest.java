/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.consensus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.ethereum.beacon.consensus.hasher.ObjectHasher;
import org.ethereum.beacon.consensus.transition.InitialStateTransition;
import org.ethereum.beacon.consensus.util.CachingBeaconChainSpec;
import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.core.BeaconBlockBody;
import org.ethereum.beacon.core.BeaconBlockHeader;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.MutableBeaconState;
import org.ethereum.beacon.core.operations.Deposit;
import org.ethereum.beacon.core.operations.deposit.DepositData;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.state.Eth1Data;
import org.ethereum.beacon.core.types.BLSPubkey;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.core.types.EpochNumber;
import org.ethereum.beacon.core.types.Gwei;
import org.ethereum.beacon.core.types.ShardNumber;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.SlotNumber.EpochLength;
import org.ethereum.beacon.core.types.Time;
import org.ethereum.beacon.core.types.ValidatorIndex;
import org.ethereum.beacon.core.util.AttestationTestUtil;
import org.ethereum.beacon.core.util.BeaconBlockTestUtil;
import org.ethereum.beacon.crypto.Hashes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes3;
import tech.pegasys.artemis.util.bytes.Bytes48;
import tech.pegasys.artemis.util.bytes.Bytes96;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.uint.UInt64;
import tech.pegasys.artemis.util.uint.UInt64s;

public class BeaconChainSpecTest {

  @Test
  public void shuffleTest0() throws Exception {
    BeaconChainSpec spec = BeaconChainSpec.createWithDefaults();

    int[] sample = new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

    BytesValue bytes = BytesValue.wrap(new byte[] {(byte) 1, (byte) 256, (byte) 65656});

    int expectedInt = 817593;
    Hash32 hash = Hashes.sha256(bytes);
    int res = Bytes3.wrap(hash, 0).asUInt24BigEndian().getValue();

    //    int[] actual = spec.shuffle(sample, Hashes.sha256(bytes));
    //    int[] expected = new int[]{2, 4, 10, 7, 5, 6, 9, 8, 1, 3};
    //
    //    Assert.assertArrayEquals(expected, actual);
  }

  @Test
  public void shuffleTest1() throws Exception {
    int[] statuses =
        new int[] {
          2, 4, 0, 0, 2, 2, 4, 2, 3, 1, 0, 3, 3, 4, 4, 4, 1, 1, 1, 1, 3, 2, 3, 0, 2, 4, 0, 2, 4, 0,
          0, 4, 2, 1, 4, 1, 4, 2, 2, 1, 2, 4, 0, 4, 0, 3, 0, 4, 4, 0, 0, 1, 3, 3, 0, 4, 3, 1, 1, 3,
          1, 0, 0, 1, 0, 0, 4, 1, 2, 0, 1, 4, 2, 1, 1, 4, 1, 1, 1, 1, 0, 4, 4, 0, 1, 3, 4, 2, 0, 1,
          4, 3, 1, 2, 4, 2, 2, 2, 3, 3, 3, 0, 2, 0, 4, 1, 1, 3, 0, 3, 1, 3, 4, 3, 3, 4, 0, 1, 0, 3,
          3, 1, 4, 2, 0, 3, 2, 3, 0, 4, 3, 1, 3, 3, 4, 3, 0, 0, 1, 0, 2, 4, 1, 3, 1, 3, 2, 4, 2, 2,
          0, 3, 2, 3, 1, 3, 0, 2, 1, 3, 2, 2, 1, 3, 0, 2, 1, 3, 2, 2, 2, 0, 0, 0, 3, 4, 1, 4, 4, 3,
          3, 0, 1, 2, 4, 1, 4, 0, 0, 4, 3, 2, 4, 3, 1, 2, 0, 4, 4, 2, 0, 4, 4, 4, 4, 0, 1, 4, 4, 3,
          0, 3, 2, 1, 4, 3, 0, 3, 0, 3, 1, 3, 3, 2, 3, 2, 2, 2, 1, 0, 4, 2, 0, 4, 2, 2, 0, 1, 0, 0,
          2, 0, 3, 3, 2, 4, 0, 3, 1, 0, 3, 4, 2, 4, 0, 1, 4, 1, 0, 0, 4, 3, 3, 1, 1, 4, 1, 3, 1, 0,
          4, 3, 3, 0, 2, 1, 3, 4, 1, 3, 3, 3, 0, 4, 2, 3, 0, 0, 0, 1, 4, 3, 1, 4, 2, 0, 4, 2, 3, 0,
          1, 2, 0, 4, 0, 4, 4, 2, 1, 3, 4, 3, 2, 3, 3, 4, 3, 2, 2, 1, 3, 0, 3, 2, 1, 0, 1, 3, 2, 0,
          0, 0, 1, 1, 2, 2, 0, 3, 1, 0, 3, 2, 0, 0, 2, 3, 0, 0, 4, 4, 2, 0, 1, 1, 3, 0, 1, 0, 1, 1,
          3, 4, 0, 0, 3, 4, 4, 4, 0, 2, 4, 4, 1, 0, 2, 2, 3, 4, 4, 0, 1, 3, 2, 4, 0, 1, 2, 1, 3, 3,
          0, 3, 4, 1, 3, 1, 0, 1, 0, 4, 4, 3, 4, 1, 0, 3, 1, 3
        };
    // 148

    List<Integer> activeValidatorIndices = new ArrayList<>();
    for (int i = 0; i < statuses.length; i++) {
      if (statuses[i] == 1 || statuses[i] == 2) {
        activeValidatorIndices.add(i);
      }
    }

    BeaconChainSpec spec = BeaconChainSpec.createWithDefaults();

    Map<Integer, Long> map =
        Arrays.stream(statuses)
            .boxed()
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

    System.out.println(map);
  }

  private DepositData createDepositData() {
    return new DepositData(
        BLSPubkey.wrap(Bytes48.TRUE),
        Hashes.sha256(BytesValue.fromHexString("aa")),
        Gwei.ZERO,
        BLSSignature.wrap(Bytes96.ZERO));
  }

  @Test
  public void testHashTreeRoot1() {
    BeaconChainSpec spec = BeaconChainSpec.createWithDefaults();
    //    Hash32 expected =
    //
    // Hash32.fromHexString("0x1a2017aea008e5bb8b3eb79d031f14347018353f1c58fc3a54e9fc7af7ab2fe1");
    Hash32 actual = spec.hash_tree_root(createDepositData());
    //    assertEquals(expected, actual);
  }

  @Test
  public void headerAndBlockHashesAreEqual() {
    Random rnd = new Random(1);
    BeaconChainSpec spec = BeaconChainSpec.createWithDefaults();
    BeaconBlock emptyBlock = spec.get_empty_block();
    BeaconBlockBody body =
        new BeaconBlockBody(
            emptyBlock.getBody().getRandaoReveal(),
            emptyBlock.getBody().getEth1Data(),
            emptyBlock.getBody().getGraffiti(),
            emptyBlock.getBody().getProposerSlashings().listCopy(),
            emptyBlock.getBody().getAttesterSlashings().listCopy(),
            AttestationTestUtil.createRandomList(rnd, 10),
            emptyBlock.getBody().getDeposits().listCopy(),
            emptyBlock.getBody().getVoluntaryExits().listCopy(),
            emptyBlock.getBody().getTransfers().listCopy(),
            spec.getConstants());
    BeaconBlock block =
        new BeaconBlock(
            emptyBlock.getSlot(),
            emptyBlock.getParentRoot(),
            emptyBlock.getStateRoot(),
            body,
            emptyBlock.getSignature());
    BeaconBlockHeader header = spec.get_block_header(block);
    assertEquals(spec.signing_root(block), spec.signing_root(header));
  }

  //  @Ignore
  //  @Test
  public void committeeTest1() {
    int validatorCount = 4;
    int epochLength = 4;
    int shardCount = 8;
    int targetCommitteeSize = 2;
    SlotNumber genesisSlot = SlotNumber.of(1_000_000);
    Random rnd = new Random(1);
    Time genesisTime = Time.of(10 * 60);

    SpecConstants specConstants =
        new SpecConstants() {
          @Override
          public SlotNumber.EpochLength getSlotsPerEpoch() {
            return new SlotNumber.EpochLength(UInt64.valueOf(epochLength));
          }

          @Override
          public SlotNumber getGenesisSlot() {
            return genesisSlot;
          }

          @Override
          public ValidatorIndex getTargetCommitteeSize() {
            return ValidatorIndex.of(targetCommitteeSize);
          }

          @Override
          public ShardNumber getShardCount() {
            return ShardNumber.of(shardCount);
          }
        };
    BeaconChainSpec spec =
        new CachingBeaconChainSpec(
            specConstants,
            Hashes::sha256,
            ObjectHasher.createSSZOverSHA256(specConstants),
            false,
            false,
            false,
            true);

    System.out.println("Generating deposits...");
    List<Deposit> deposits = TestUtils.generateRandomDepositsWithoutSig(rnd, spec, validatorCount);
    Eth1Data eth1Data =
        new Eth1Data(Hash32.random(rnd), UInt64.valueOf(deposits.size()), Hash32.random(rnd));
    InitialStateTransition initialStateTransition =
        new InitialStateTransition(new ChainStart(genesisTime, eth1Data, deposits), spec);

    System.out.println("Applying initial state transition...");
    BeaconState initialState = initialStateTransition.apply(spec.get_empty_block());
    MutableBeaconState state = initialState.createMutableCopy();

    System.out.println(
        "get_committee_count() = "
            + spec.get_committee_count(state, spec.getConstants().getGenesisEpoch()));

    for (SlotNumber slot : genesisSlot.iterateTo(genesisSlot.plus(SlotNumber.of(epochLength)))) {
      System.out.println(
          "Slot #"
              + slot
              + " beacon proposer: "
              + spec.get_beacon_proposer_index(state)
              + " committee: "
              + spec.get_crosslink_committees_at_slot(state, slot));
      System.out.println(
          "Slot #"
              + slot
              + " beacon proposer: "
              + spec.get_beacon_proposer_index(state)
              + " committee: "
              + spec.get_crosslink_committees_at_slot(state, slot).stream()
                  .map(c -> c.getShard() + ": [" + c.getCommittee().size() + "]")
                  .collect(Collectors.joining(",")));
    }
  }

  @Test
  public void computeCommittee2ProducesCorrectResult() {
    BeaconChainSpec spec = BeaconChainSpec.createWithDefaults();

    int committeeSize = 128;
    int totalCommittees = 64;
    int validatorCount = committeeSize * totalCommittees + 11;
    List<ValidatorIndex> validatorIndices =
        IntStream.range(0, validatorCount)
            .mapToObj(ValidatorIndex::new)
            .collect(Collectors.toList());
    Hash32 seed = Hash32.random(new Random());

    List<ValidatorIndex> actualIndices = new ArrayList<>();
    for (UInt64 i : UInt64s.iterate(UInt64.ZERO, UInt64.valueOf(totalCommittees))) {
      List<ValidatorIndex> committee =
          spec.compute_committee(validatorIndices, seed, i, UInt64.valueOf(totalCommittees));
      List<ValidatorIndex> committee2 =
          spec.compute_committee2(validatorIndices, seed, i, UInt64.valueOf(totalCommittees));
      assertEquals(committee, committee2);

      actualIndices.addAll(committee);
    }

    actualIndices.sort(ValidatorIndex::compareTo);
    assertEquals(validatorIndices, actualIndices);
  }

  @Test
  public void edgeCaseWithGetSeed() {
    BeaconChainSpec spec =
        BeaconChainSpec.createWithDefaultHasher(
            new SpecConstants() {
              @Override
              public EpochLength getSlotsPerEpoch() {
                return new EpochLength(UInt64.valueOf(4));
              }
            });
    Random rnd = new Random();

    MutableBeaconState state = BeaconState.getEmpty().createMutableCopy();

    EpochNumber startEpoch = spec.get_current_epoch(state);
    spec.get_seed(state, startEpoch);
    Hash32 nextEpochSeed = spec.get_seed(state, startEpoch.increment());

    for (EpochNumber epoch = startEpoch;
        epoch.less(startEpoch.plus(10));
        epoch = epoch.increment()) {
      BeaconBlockBody body =
          BeaconBlockTestUtil.createRandomBodyWithNoOperations(rnd, spec.getConstants());
      spec.process_randao(state, body);
      spec.process_final_updates(state);
      state.setSlot(state.getSlot().plus(spec.getConstants().getSlotsPerEpoch()));

      EpochNumber currentEpoch = spec.get_current_epoch(state);

      assertEquals(nextEpochSeed, spec.get_seed(state, currentEpoch));
      nextEpochSeed = spec.get_seed(state, currentEpoch.increment());
    }
  }
}
