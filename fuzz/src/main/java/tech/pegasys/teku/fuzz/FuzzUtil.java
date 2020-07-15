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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSConstants;
import tech.pegasys.teku.core.BlockProcessorUtil;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.StateTransitionException;
import tech.pegasys.teku.core.exceptions.BlockProcessingException;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.datastructures.util.CommitteeUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.fuzz.input.AttestationFuzzInput;
import tech.pegasys.teku.fuzz.input.AttesterSlashingFuzzInput;
import tech.pegasys.teku.fuzz.input.BlockFuzzInput;
import tech.pegasys.teku.fuzz.input.BlockHeaderFuzzInput;
import tech.pegasys.teku.fuzz.input.DepositFuzzInput;
import tech.pegasys.teku.fuzz.input.ProposerSlashingFuzzInput;
import tech.pegasys.teku.fuzz.input.VoluntaryExitFuzzInput;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.sos.ReflectionInformation;
import tech.pegasys.teku.util.config.Constants;

// TODO a Java FuzzHarness interface? - that way type safety can be checked at compile time
// JNI removes type safety
public class FuzzUtil {
  // TODO set config as initialization param here? see
  // util/src/main/java/tech/pegasys/artemis/util/config/Constants.java setConstants
  // though is a global setting so kinda weird to allow that side-effect within a constructor
  // a static "initialize" function could make more sense, but doesn't set a requirement that it is
  // called before any
  // fuzzing harness
  //
  // Could also have these all in separate classes, which implement a "FuzzHarness" interface

  // Size of ValidatorIndex returned by shuffle
  private static final int OUTPUT_INDEX_BYTES = Long.BYTES;

  private boolean disable_bls;

  // NOTE: this uses primitive values as parameters to more easily call via JNI
  public FuzzUtil(final boolean useMainnetConfig, final boolean disable_bls) {
    // NOTE: makes global Constants/config changes
    if (useMainnetConfig) {
      Constants.setConstants("mainnet");
    } else {
      Constants.setConstants("minimal");
    }
    // guessing this might be necessary soon?
    SimpleOffsetSerializer.setConstants();
    SimpleOffsetSerializer.classReflectionInfo.put(
        AttestationFuzzInput.class, new ReflectionInformation(AttestationFuzzInput.class));
    SimpleOffsetSerializer.classReflectionInfo.put(
        AttesterSlashingFuzzInput.class,
        new ReflectionInformation(AttesterSlashingFuzzInput.class));
    SimpleOffsetSerializer.classReflectionInfo.put(
        BlockFuzzInput.class, new ReflectionInformation(BlockFuzzInput.class));
    SimpleOffsetSerializer.classReflectionInfo.put(
        BlockHeaderFuzzInput.class, new ReflectionInformation(BlockHeaderFuzzInput.class));
    SimpleOffsetSerializer.classReflectionInfo.put(
        DepositFuzzInput.class, new ReflectionInformation(DepositFuzzInput.class));
    SimpleOffsetSerializer.classReflectionInfo.put(
        ProposerSlashingFuzzInput.class,
        new ReflectionInformation(ProposerSlashingFuzzInput.class));
    SimpleOffsetSerializer.classReflectionInfo.put(
        VoluntaryExitFuzzInput.class, new ReflectionInformation(VoluntaryExitFuzzInput.class));

    this.disable_bls = disable_bls;
    if (disable_bls) {
      BLSConstants.disableBLSVerification();
    }
  }

  public Optional<byte[]> fuzzAttestation(final byte[] input) {
    AttestationFuzzInput structuredInput = deserialize(input, AttestationFuzzInput.class);

    // process and return post state
    try {
      BeaconState postState =
          structuredInput
              .getState()
              .updated(
                  state -> {
                    BlockProcessorUtil.process_attestations(
                        state, SSZList.singleton(structuredInput.getAttestation()));
                  });
      Bytes output = SimpleOffsetSerializer.serialize(postState);
      return Optional.of(output.toArrayUnsafe());
    } catch (BlockProcessingException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  public Optional<byte[]> fuzzAttesterSlashing(final byte[] input) {
    AttesterSlashingFuzzInput structuredInput = deserialize(input, AttesterSlashingFuzzInput.class);

    // process and return post state
    try {
      BeaconState postState =
          structuredInput
              .getState()
              .updated(
                  state -> {
                    BlockProcessorUtil.process_attester_slashings(
                        state, SSZList.singleton(structuredInput.getAttester_slashing()));
                  });
      Bytes output = SimpleOffsetSerializer.serialize(postState);
      return Optional.of(output.toArrayUnsafe());
    } catch (BlockProcessingException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  public Optional<byte[]> fuzzBlock(final byte[] input) {
    BlockFuzzInput structuredInput = deserialize(input, BlockFuzzInput.class);

    // TODO this currently will disable state root validation and some (not all) sig validation,
    // would be preferable to control each individually
    // this currently causes some blocks differences to be detected
    boolean validate_root_and_sigs = !disable_bls;
    try {
      StateTransition transition = new StateTransition();
      BeaconState postState =
          transition.initiate(
              structuredInput.getState(),
              structuredInput.getSigned_block(),
              validate_root_and_sigs);
      Bytes output = SimpleOffsetSerializer.serialize(postState);
      return Optional.of(output.toArrayUnsafe());
    } catch (StateTransitionException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  public Optional<byte[]> fuzzBlockHeader(final byte[] input) {
    BlockHeaderFuzzInput structuredInput = deserialize(input, BlockHeaderFuzzInput.class);

    try {
      BeaconState postState =
          structuredInput
              .getState()
              .updated(
                  state -> {
                    BlockProcessorUtil.process_block_header(state, structuredInput.getBlock());
                  });
      Bytes output = SimpleOffsetSerializer.serialize(postState);
      return Optional.of(output.toArrayUnsafe());
    } catch (BlockProcessingException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  public Optional<byte[]> fuzzDeposit(final byte[] input) {
    DepositFuzzInput structuredInput = deserialize(input, DepositFuzzInput.class);

    // TODO confirm deposit is a fixed size container
    // process and return post state
    try {
      BeaconState postState =
          structuredInput
              .getState()
              .updated(
                  state -> {
                    BlockProcessorUtil.process_deposits(
                        state, SSZList.singleton(structuredInput.getDeposit()));
                  });
      Bytes output = SimpleOffsetSerializer.serialize(postState);
      return Optional.of(output.toArrayUnsafe());
    } catch (BlockProcessingException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  public Optional<byte[]> fuzzProposerSlashing(final byte[] input) {
    ProposerSlashingFuzzInput structuredInput = deserialize(input, ProposerSlashingFuzzInput.class);

    // process and return post state
    try {
      BeaconState postState =
          structuredInput
              .getState()
              .updated(
                  state -> {
                    BlockProcessorUtil.process_proposer_slashings(
                        state, SSZList.singleton(structuredInput.getProposer_slashing()));
                  });
      Bytes output = SimpleOffsetSerializer.serialize(postState);
      return Optional.of(output.toArrayUnsafe());
    } catch (BlockProcessingException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  public Optional<byte[]> fuzzShuffle(final byte[] input) {
    if (input.length < (32 + 2)) {
      return Optional.empty();
    }
    // Mask it to make ensure positive before using remainder.
    int count = ((int) (0xFFFFFFFFL & BeaconStateUtil.bytes_to_int(Bytes.wrap(input, 0, 2)))) % 100;

    Bytes32 seed = Bytes32.wrap(input, 2);

    // NOTE: could use the following, but that is not used by the current implementation
    // int[] shuffled = BeaconStateUtil.shuffle(count, seed);
    // TODO shuffle returns an int (int32), but should be uint64 == (java long is int64)
    // so does this break if validator indexes are negative integers?
    // use a google UnsignedLong?
    // anything weird with signedness here?
    // any risk here? - not for this particular fuzzing as we only count <= 100

    // NOTE: although compute_shuffled_index returns an int, we save as a long for consistency
    ByteBuffer result_bb = ByteBuffer.allocate(count * OUTPUT_INDEX_BYTES);
    // Convert to little endian bytes
    result_bb.order(ByteOrder.LITTLE_ENDIAN);

    for (int i = 0; i < count; i++) {
      result_bb.putLong(CommitteeUtil.compute_shuffled_index(i, count, seed));
    }
    return Optional.of(result_bb.array());
  }

  public Optional<byte[]> fuzzVoluntaryExit(final byte[] input) {
    VoluntaryExitFuzzInput structuredInput = deserialize(input, VoluntaryExitFuzzInput.class);

    // TODO confirm exit is a fixed container
    // process and return post state
    try {
      BeaconState postState =
          structuredInput
              .getState()
              .updated(
                  state -> {
                    BlockProcessorUtil.process_voluntary_exits(
                        state, SSZList.singleton(structuredInput.getExit()));
                  });
      Bytes output = SimpleOffsetSerializer.serialize(postState);
      return Optional.of(output.toArrayUnsafe());
    } catch (BlockProcessingException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  private <T> T deserialize(byte[] data, Class<T> type) {
    // allow exception to propagate on failure - indicates a preprocessing or deserializing error
    T structuredInput = SimpleOffsetSerializer.deserialize(Bytes.wrap(data), type);
    if (structuredInput == null) {
      throw new RuntimeException(
          "Failed to deserialize input. Likely a preprocessing or deserialization bug.");
    }
    return structuredInput;
  }
}
