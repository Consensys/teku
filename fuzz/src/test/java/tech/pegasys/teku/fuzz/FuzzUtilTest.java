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

package tech.pegasys.teku.fuzz;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.SpecDependent;
import tech.pegasys.teku.fuzz.input.AttestationFuzzInput;
import tech.pegasys.teku.fuzz.input.AttesterSlashingFuzzInput;
import tech.pegasys.teku.fuzz.input.BlockFuzzInput;
import tech.pegasys.teku.fuzz.input.BlockHeaderFuzzInput;
import tech.pegasys.teku.fuzz.input.DepositFuzzInput;
import tech.pegasys.teku.fuzz.input.ProposerSlashingFuzzInput;
import tech.pegasys.teku.fuzz.input.VoluntaryExitFuzzInput;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;
import tech.pegasys.teku.util.config.Constants;

@ExtendWith(BouncyCastleExtension.class)
class FuzzUtilTest {

  // Basic sanity tests for Fuzzing Harnesses
  // NOTE: for the purposes of this class, we don't care so much that operation is
  // correct/equivalent according to the spec
  // (the reference tests cover this), but that the Fuzz harness is equivalent to the behavior of
  // the internal process.
  // e.g. These tests don't care whether process_deposits is correct, but that the harness correctly
  // uses process_deposits.

  // *************** START Deposit Tests *****************

  @AfterEach
  public void cleanup() {
    Constants.setConstants("minimal");
    SpecDependent.resetAll();
  }

  @Test
  public void fuzzAttestation_minimal() {
    final FuzzUtil fuzzUtil = new FuzzUtil(false, true);

    final Path testCaseDir = Path.of("minimal/operations/attestation/pyspec_tests/success");
    final Attestation data = loadSsz(testCaseDir.resolve("attestation.ssz"), Attestation.TYPE);
    final BeaconState preState = loadSsz(testCaseDir.resolve("pre.ssz"), BeaconState.getSszType());
    final BeaconState postState =
        loadSsz(testCaseDir.resolve("post.ssz"), BeaconState.getSszType());

    AttestationFuzzInput input = new AttestationFuzzInput(preState, data);
    byte[] rawInput = input.sszSerialize().toArrayUnsafe();
    Optional<Bytes> result = fuzzUtil.fuzzAttestation(rawInput).map(Bytes::wrap);

    Bytes expected = postState.sszSerialize();
    assertThat(result).isNotEmpty();
    assertThat(result.get()).isEqualTo(expected);
  }

  @Test
  public void fuzzAttesterSlashing_minimal() {
    final FuzzUtil fuzzUtil = new FuzzUtil(false, true);

    final Path testCaseDir =
        Path.of("minimal/operations/attester_slashing/pyspec_tests/success_surround");
    final AttesterSlashing data =
        loadSsz(testCaseDir.resolve("attester_slashing.ssz"), AttesterSlashing.TYPE);
    final BeaconState preState = loadSsz(testCaseDir.resolve("pre.ssz"), BeaconState.getSszType());
    final BeaconState postState =
        loadSsz(testCaseDir.resolve("post.ssz"), BeaconState.getSszType());

    AttesterSlashingFuzzInput input = new AttesterSlashingFuzzInput(preState, data);
    byte[] rawInput = input.sszSerialize().toArrayUnsafe();
    Optional<Bytes> result = fuzzUtil.fuzzAttesterSlashing(rawInput).map(Bytes::wrap);

    Bytes expected = postState.sszSerialize();
    assertThat(result).isNotEmpty();
    assertThat(result.get()).isEqualTo(expected);
  }

  @Test
  public void fuzzBlock_minimal() {
    final FuzzUtil fuzzUtil = new FuzzUtil(false, true);

    final Path testCaseDir = Path.of("minimal/sanity/blocks/pyspec_tests/attestation");
    final SignedBeaconBlock block0 =
        loadSsz(testCaseDir.resolve("blocks_0.ssz"), SignedBeaconBlock.getSszType());
    final SignedBeaconBlock block1 =
        loadSsz(testCaseDir.resolve("blocks_1.ssz"), SignedBeaconBlock.getSszType());
    final List<SignedBeaconBlock> blocks = List.of(block0, block1);
    final BeaconState preState = loadSsz(testCaseDir.resolve("pre.ssz"), BeaconState.getSszType());
    final BeaconState postState =
        loadSsz(testCaseDir.resolve("post.ssz"), BeaconState.getSszType());

    BeaconState currentState = preState;
    for (SignedBeaconBlock block : blocks) {
      BlockFuzzInput input = new BlockFuzzInput(currentState, block);
      byte[] rawInput = input.sszSerialize().toArrayUnsafe();
      Optional<Bytes> result = fuzzUtil.fuzzBlock(rawInput).map(Bytes::wrap);
      assertThat(result).isNotEmpty();
      currentState = BeaconState.getSszType().sszDeserialize(result.get());
    }

    assertThat(currentState).isNotNull();
    assertThat(currentState).isEqualTo(postState);
  }

  @Test
  public void fuzzBlockHeader_minimal() {
    final FuzzUtil fuzzUtil = new FuzzUtil(false, true);

    final Path testCaseDir =
        Path.of("minimal/operations/block_header/pyspec_tests/success_block_header");
    final BeaconBlock data = loadSsz(testCaseDir.resolve("block.ssz"), BeaconBlock.getSszType());
    final BeaconState preState = loadSsz(testCaseDir.resolve("pre.ssz"), BeaconState.getSszType());
    final BeaconState postState =
        loadSsz(testCaseDir.resolve("post.ssz"), BeaconState.getSszType());

    BlockHeaderFuzzInput input = new BlockHeaderFuzzInput(preState, data);
    byte[] rawInput = input.sszSerialize().toArrayUnsafe();
    Optional<Bytes> result = fuzzUtil.fuzzBlockHeader(rawInput).map(Bytes::wrap);

    Bytes expected = postState.sszSerialize();
    assertThat(result).isNotEmpty();
    assertThat(result.get()).isEqualTo(expected);
  }

  @Test
  public void fuzzDeposit_minimal() {
    final FuzzUtil fuzzUtil = new FuzzUtil(false, true);

    final Path testCaseDir = Path.of("minimal/operations/deposit/pyspec_tests/success_top_up");
    final Deposit data = loadSsz(testCaseDir.resolve("deposit.ssz"), Deposit.TYPE);
    final BeaconState preState = loadSsz(testCaseDir.resolve("pre.ssz"), BeaconState.getSszType());
    final BeaconState postState =
        loadSsz(testCaseDir.resolve("post.ssz"), BeaconState.getSszType());

    DepositFuzzInput input = new DepositFuzzInput(preState, data);
    byte[] rawInput = input.sszSerialize().toArrayUnsafe();
    Optional<Bytes> result = fuzzUtil.fuzzDeposit(rawInput).map(Bytes::wrap);

    Bytes expected = postState.sszSerialize();
    assertThat(result).isNotEmpty();
    assertThat(result.get()).isEqualTo(expected);
  }

  @Test
  public void fuzzProposerSlashing_minimal() {
    final FuzzUtil fuzzUtil = new FuzzUtil(false, true);

    final Path testCaseDir = Path.of("minimal/operations/proposer_slashing/pyspec_tests/success");
    final ProposerSlashing data =
        loadSsz(testCaseDir.resolve("proposer_slashing.ssz"), ProposerSlashing.TYPE);
    final BeaconState preState = loadSsz(testCaseDir.resolve("pre.ssz"), BeaconState.getSszType());
    final BeaconState postState =
        loadSsz(testCaseDir.resolve("post.ssz"), BeaconState.getSszType());

    ProposerSlashingFuzzInput input = new ProposerSlashingFuzzInput(preState, data);
    byte[] rawInput = input.sszSerialize().toArrayUnsafe();
    Optional<Bytes> result = fuzzUtil.fuzzProposerSlashing(rawInput).map(Bytes::wrap);

    Bytes expected = postState.sszSerialize();
    assertThat(result).isNotEmpty();
    assertThat(result.get()).isEqualTo(expected);
  }

  @Test
  public void fuzzVoluntaryExit_minimal() {
    final FuzzUtil fuzzUtil = new FuzzUtil(false, true);

    final Path testCaseDir = Path.of("minimal/operations/voluntary_exit/pyspec_tests/success");
    final SignedVoluntaryExit data =
        loadSsz(testCaseDir.resolve("voluntary_exit.ssz"), SignedVoluntaryExit.TYPE);
    final BeaconState preState = loadSsz(testCaseDir.resolve("pre.ssz"), BeaconState.getSszType());
    final BeaconState postState =
        loadSsz(testCaseDir.resolve("post.ssz"), BeaconState.getSszType());

    VoluntaryExitFuzzInput input = new VoluntaryExitFuzzInput(preState, data);
    byte[] rawInput = input.sszSerialize().toArrayUnsafe();
    Optional<Bytes> result = fuzzUtil.fuzzVoluntaryExit(rawInput).map(Bytes::wrap);

    Bytes expected = postState.sszSerialize();
    assertThat(result).isNotEmpty();
    assertThat(result.get()).isEqualTo(expected);
  }

  @Test
  void fuzzShuffle_insufficientInput() {
    final FuzzUtil fuzzUtil = new FuzzUtil(true, true);

    byte[] emptyInput = new byte[0];
    assertThat(fuzzUtil.fuzzShuffle(emptyInput)).isEmpty();
    assertThat(fuzzUtil.fuzzShuffle(Bytes.random(15).toArrayUnsafe())).isEmpty();
    // minimum length is 34
    assertThat(fuzzUtil.fuzzShuffle(Bytes.random(33).toArrayUnsafe())).isEmpty();
  }

  @Test
  void fuzzShuffle_sufficientInput() {
    final FuzzUtil fuzzUtil = new FuzzUtil(true, true);

    // minimum length is 34, and should succeed
    assertThat(fuzzUtil.fuzzShuffle(Bytes.random(34).toArrayUnsafe())).isPresent();
    assertThat(fuzzUtil.fuzzShuffle(Bytes.random(80).toArrayUnsafe())).isPresent();

    // first 2 bytes (little endian) % 100 provide the count, the rest the seed
    // 1000 = 16 (little endian)
    byte[] input =
        Bytes.fromHexString(
                "0x10000000000000000000000000000000000000000000000000000000000000000000")
            .toArrayUnsafe();
    assertThat(fuzzUtil.fuzzShuffle(input))
        .hasValueSatisfying(
            b -> {
              assertThat(b).hasSize(16 * Long.BYTES);
            });
  }

  public <T extends SszData> T loadSsz(final Path path, final SszSchema<T> type) {
    try {
      final byte[] data =
          getClass().getClassLoader().getResourceAsStream(path.toString()).readAllBytes();
      return type.sszDeserialize(Bytes.wrap(data));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final NullPointerException e) {
      throw new RuntimeException("Unable to locate file at " + path.toString(), e);
    }
  }
}
