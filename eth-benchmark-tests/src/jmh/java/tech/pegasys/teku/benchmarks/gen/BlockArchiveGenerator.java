/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.benchmarks.gen;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.AttestationGenerator;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.util.EpochAttestationSchedule;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockArchiveGenerator {
  public static void main(String[] args) throws Exception {
    int validatorCount = 32_768;
    int epochCount = 50;
    if (args.length == 2) {
      // read cli positional args
      try {
        validatorCount = Integer.parseInt(args[0]);
      } catch (Exception e) {
        dieUsage(Optional.of("Failed to parse validatorCount: " + e.getMessage()));
      }
      try {
        epochCount = Integer.parseInt(args[1]);
      } catch (Exception e) {
        dieUsage(Optional.of("Failed to parse epochCount: " + e.getMessage()));
      }
    } else if (args.length != 0) {
      dieUsage(Optional.empty());
    }

    if (validatorCount < 1 || validatorCount > 3_276_800) {
      dieUsage(
          Optional.of(
              "Expected validator count ("
                  + validatorCount
                  + ") to be in range 0 < validatorCount < 3_276_801"));
    } else if (epochCount < 1 || epochCount > 100) {
      dieUsage(
          Optional.of(
              "Expected epoch count (" + epochCount + ") to be in range 0 < epochCount < 100"));
    }
    System.out.println("Validator count: " + validatorCount);
    System.out.println("Epochs: " + epochCount);

    generateBlocks(validatorCount, epochCount);
  }

  private static void dieUsage(final Optional<String> maybeContext) {
    maybeContext.ifPresent(System.out::println);
    System.out.println("Usage: blockArchiveGenerator <validatorCount> <epochs>");
    System.exit(2);
  }

  private static void generateBlocks(final int validatorsCount, final int epochLimit)
      throws Exception {
    final Spec spec = TestSpecFactory.createMainnetAltair();

    AbstractBlockProcessor.depositSignatureVerifier = BLSSignatureVerifier.NO_OP;

    final List<BLSKeyPair> validatorKeys = KeyFileGenerator.readValidatorKeys(validatorsCount);
    final RecentChainData localStorage = MemoryOnlyRecentChainData.create(spec);
    final AttestationGenerator attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    final int slotsPerEpoch = spec.getGenesisSpecConfig().getSlotsPerEpoch();
    final String blocksFile =
        String.format(
            "blocks_%sEpochs_%sBlocksPerEpoch_%sValidators.ssz.gz",
            epochLimit, slotsPerEpoch, validatorsCount);
    final BeaconChainUtil localChain =
        BeaconChainUtil.builder()
            .specProvider(spec)
            .recentChainData(localStorage)
            .validatorKeys(validatorKeys)
            .signDeposits(false)
            .build();
    localChain.initializeStorage();

    UInt64 currentSlot = localStorage.getHeadSlot();

    System.out.printf(
        "Generating blocks for %s epochs, %s slots per epoch.%n", epochLimit, slotsPerEpoch);

    final SystemTimeProvider timeProvider = new SystemTimeProvider();
    try (BlockIO.Writer writer = BlockIO.createFileWriter(blocksFile)) {

      for (int j = 0; j < epochLimit; j++) {
        System.out.println(" => Processing epoch " + j);
        final UInt64 epoch = UInt64.valueOf(j);
        final BeaconState epochState =
            localStorage
                .retrieveBlockState(localStorage.getBestBlockRoot().orElse(null))
                .join()
                .orElseThrow();
        final UInt64 committeCountPerSlot =
            spec.atEpoch(UInt64.ZERO)
                .beaconStateAccessors()
                .getCommitteeCountPerSlot(epochState, epoch);
        final EpochAttestationSchedule attestationCommitteeAssignments =
            spec.atEpoch(epoch)
                .getValidatorsUtil()
                .getAttestationCommitteesAtEpoch(epochState, epoch, committeCountPerSlot);
        for (int i = 0; i < slotsPerEpoch; i++) {
          final UInt64 slotStart = timeProvider.getTimeInMillis();
          currentSlot = currentSlot.plus(UInt64.ONE);
          final StateAndBlockSummary preState =
              localStorage
                  .getStore()
                  .retrieveStateAndBlockSummary(localStorage.getBestBlockRoot().orElseThrow())
                  .get()
                  .orElseThrow();

          final List<Attestation> attestations =
              UInt64.ONE.equals(currentSlot)
                  ? Collections.emptyList()
                  : attestationGenerator.getAttestationsForSlot(
                      preState, currentSlot.decrement(), attestationCommitteeAssignments);
          List<Attestation> aggregate =
              AttestationGenerator.groupAndAggregateAttestations(attestations);

          final SignedBeaconBlock block =
              localChain.createAndImportBlockAtSlotWithAttestations(currentSlot, aggregate);
          writer.accept(block);

          System.out.println(
              "   -> Processed: "
                  + currentSlot
                  + ", "
                  + (timeProvider.getTimeInMillis().minusMinZero(slotStart))
                  + " ms");
        }

        final Optional<BeaconState> bestState =
            localStorage.retrieveBlockState(localStorage.getBestBlockRoot().orElse(null)).join();
        bestState.ifPresent(
            beaconState ->
                System.out.printf(
                    " => Epoch done: %s, Best State slot: %s, state hash: %s%n",
                    epoch, beaconState.getSlot(), beaconState.hashTreeRoot()));
      }
    } catch (IllegalArgumentException e) {
      System.out.println("Failed");
    }
  }
}
