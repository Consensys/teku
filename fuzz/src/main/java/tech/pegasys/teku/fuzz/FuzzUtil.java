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

package tech.pegasys.teku.fuzz;

import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.bytesToUInt64;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSConstants;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.fuzz.input.AttestationFuzzInput;
import tech.pegasys.teku.fuzz.input.AttesterSlashingFuzzInput;
import tech.pegasys.teku.fuzz.input.BlockFuzzInput;
import tech.pegasys.teku.fuzz.input.BlockHeaderFuzzInput;
import tech.pegasys.teku.fuzz.input.BlsToExecutionChangeFuzzInput;
import tech.pegasys.teku.fuzz.input.DepositFuzzInput;
import tech.pegasys.teku.fuzz.input.ExecutionPayloadFuzzInput;
import tech.pegasys.teku.fuzz.input.ProposerSlashingFuzzInput;
import tech.pegasys.teku.fuzz.input.SyncAggregateFuzzInput;
import tech.pegasys.teku.fuzz.input.VoluntaryExitFuzzInput;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodySchemaCapella;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.spec.logic.versions.deneb.block.KzgCommitmentsProcessor;

public class FuzzUtil {
  // NOTE: alternatively could also have these all in separate classes, which implement a
  // "FuzzHarness" interface

  private final Spec spec;
  private final BeaconBlockBodySchemaCapella<?> beaconBlockBodySchema;
  private final SpecVersion specVersion;

  // Size of ValidatorIndex returned by shuffle
  private static final int OUTPUT_INDEX_BYTES = Long.BYTES;

  private final BLSSignatureVerifier signatureVerifier;

  // NOTE: this uses primitive values as parameters to more easily call via JNI
  public FuzzUtil(final boolean useMainnetConfig, final boolean disableBls) {
    spec =
        useMainnetConfig
            ? TestSpecFactory.createMainnetCapella()
            : TestSpecFactory.createMinimalCapella();
    specVersion = spec.forMilestone(SpecMilestone.CAPELLA);
    beaconBlockBodySchema =
        (BeaconBlockBodySchemaCapella<?>)
            specVersion.getSchemaDefinitions().getBeaconBlockBodySchema();
    initialize(disableBls);
    this.signatureVerifier = disableBls ? BLSSignatureVerifier.NO_OP : BLSSignatureVerifier.SIMPLE;
  }

  public static void initialize(final boolean disableBls) {
    if (disableBls) {
      BLSConstants.disableBLSVerification();
    }
  }

  public Optional<byte[]> fuzzAttestation(final byte[] input) {
    AttestationFuzzInput structuredInput =
        deserialize(input, AttestationFuzzInput.createSchema(specVersion));
    SszList<Attestation> attestations =
        beaconBlockBodySchema.getAttestationsSchema().of(structuredInput.getAttestation());
    // process and return post state
    try {
      BeaconState postState =
          structuredInput
              .getState()
              .updated(
                  state ->
                      spec.getBlockProcessor(state.getSlot())
                          .processAttestations(state, attestations, signatureVerifier));
      Bytes output = postState.sszSerialize();
      return Optional.of(output.toArrayUnsafe());
    } catch (BlockProcessingException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  public Optional<byte[]> fuzzAttesterSlashing(final byte[] input) {
    AttesterSlashingFuzzInput structuredInput =
        deserialize(input, AttesterSlashingFuzzInput.createType(spec.getGenesisSpec()));
    SszList<AttesterSlashing> slashings =
        beaconBlockBodySchema
            .getAttesterSlashingsSchema()
            .of(structuredInput.getAttesterSlashing());

    // process and return post state
    try {
      BeaconState postState =
          structuredInput
              .getState()
              .updated(
                  state ->
                      spec.getBlockProcessor(state.getSlot())
                          .processAttesterSlashings(state, slashings));
      Bytes output = postState.sszSerialize();
      return Optional.of(output.toArrayUnsafe());
    } catch (BlockProcessingException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  public Optional<byte[]> fuzzBlock(final byte[] input) {
    BlockFuzzInput structuredInput = deserialize(input, BlockFuzzInput.createSchema(specVersion));

    try {
      BeaconState postState =
          spec.processBlock(
              structuredInput.getState(),
              structuredInput.getSignedBlock(),
              signatureVerifier,
              Optional.empty(),
              KzgCommitmentsProcessor.create(specVersion.miscHelpers()),
              BlobsSidecarAvailabilityChecker.NOOP);
      Bytes output = postState.sszSerialize();
      return Optional.of(output.toArrayUnsafe());
    } catch (StateTransitionException e) {
      e.printStackTrace();
      // "expected error"
      return Optional.empty();
    }
  }

  public Optional<byte[]> fuzzBlockHeader(final byte[] input) {
    BlockHeaderFuzzInput structuredInput =
        deserialize(input, BlockHeaderFuzzInput.createType(specVersion));

    try {
      BeaconState postState =
          structuredInput
              .getState()
              .updated(
                  state ->
                      spec.getBlockProcessor(state.getSlot())
                          .processBlockHeader(state, structuredInput.getBlock()));
      Bytes output = postState.sszSerialize();
      return Optional.of(output.toArrayUnsafe());
    } catch (BlockProcessingException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  public Optional<byte[]> fuzzDeposit(final byte[] input) {
    DepositFuzzInput structuredInput =
        deserialize(input, DepositFuzzInput.createSchema(specVersion));
    SszList<Deposit> deposits =
        beaconBlockBodySchema.getDepositsSchema().of(structuredInput.getDeposit());

    try {
      BeaconState postState =
          structuredInput
              .getState()
              .updated(
                  state ->
                      spec.getBlockProcessor(state.getSlot()).processDeposits(state, deposits));
      Bytes output = postState.sszSerialize();
      return Optional.of(output.toArrayUnsafe());
    } catch (BlockProcessingException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  public Optional<byte[]> fuzzProposerSlashing(final byte[] input) {
    ProposerSlashingFuzzInput structuredInput =
        deserialize(input, ProposerSlashingFuzzInput.createType(specVersion));
    SszList<ProposerSlashing> proposerSlashings =
        beaconBlockBodySchema
            .getProposerSlashingsSchema()
            .of(structuredInput.getProposerSlashing());

    // process and return post state
    try {
      BeaconState postState =
          structuredInput
              .getState()
              .updated(
                  state ->
                      spec.getBlockProcessor(state.getSlot())
                          .processProposerSlashings(state, proposerSlashings, signatureVerifier));
      Bytes output = postState.sszSerialize();
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
    int count = bytesToUInt64(Bytes.wrap(input, 0, 2)).mod(100).intValue();

    Bytes32 seed = Bytes32.wrap(input, 2);

    // NOTE: could also use the following, but that is not used by the current implementation
    // int[] shuffled = BeaconStateUtil.shuffle(count, seed);

    // NOTE: although compute_shuffled_index returns an int, we save as a long for consistency
    ByteBuffer resultBuffer = ByteBuffer.allocate(count * OUTPUT_INDEX_BYTES);
    // Convert to little endian bytes
    resultBuffer.order(ByteOrder.LITTLE_ENDIAN);

    for (int i = 0; i < count; i++) {
      // NOTE: shuffle returns an int (int32), but should be uint64 to be fully consistent with spec
      // (java long is int64)
      // no risk of inconsistency for this particular fuzzing as we only count <= 100
      // inconsistencies would require a validator count > MAX_INT32
      resultBuffer.putLong(
          spec.atSlot(UInt64.ZERO).miscHelpers().computeShuffledIndex(i, count, seed));
    }
    return Optional.of(resultBuffer.array());
  }

  public Optional<byte[]> fuzzVoluntaryExit(final byte[] input) {
    VoluntaryExitFuzzInput structuredInput =
        deserialize(input, VoluntaryExitFuzzInput.createSchema(specVersion));
    SszList<SignedVoluntaryExit> voluntaryExits =
        beaconBlockBodySchema.getVoluntaryExitsSchema().of(structuredInput.getExit());

    try {
      BeaconState postState =
          structuredInput
              .getState()
              .updated(
                  state ->
                      spec.getBlockProcessor(state.getSlot())
                          .processVoluntaryExits(state, voluntaryExits, signatureVerifier));
      Bytes output = postState.sszSerialize();
      return Optional.of(output.toArrayUnsafe());
    } catch (BlockProcessingException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  public Optional<byte[]> fuzzSyncAggregate(final byte[] input) {
    SyncAggregateFuzzInput structuredInput =
        deserialize(input, SyncAggregateFuzzInput.createSchema(specVersion));
    SyncAggregate syncAggregate = structuredInput.getSyncAggregate();

    try {
      BeaconState postState =
          structuredInput
              .getState()
              .updated(
                  state ->
                      spec.getBlockProcessor(state.getSlot())
                          .processSyncAggregate(state, syncAggregate, signatureVerifier));
      Bytes output = postState.sszSerialize();
      return Optional.of(output.toArrayUnsafe());
    } catch (BlockProcessingException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  public Optional<byte[]> fuzzExecutionPayload(final byte[] input) {
    ExecutionPayloadFuzzInput structuredPayloadInput =
        deserialize(input, ExecutionPayloadFuzzInput.createSchema(specVersion));

    ExecutionPayload executionPayload = structuredPayloadInput.getExecutionPayload();
    ExecutionPayloadHeader executionPayloadHeader =
        specVersion
            .getSchemaDefinitions()
            .toVersionCapella()
            .orElseThrow()
            .getExecutionPayloadHeaderSchema()
            .createFromExecutionPayload(executionPayload);

    try {
      BeaconState postState =
          structuredPayloadInput
              .getState()
              .updated(
                  state ->
                      spec.getBlockProcessor(state.getSlot())
                          .processExecutionPayload(
                              state,
                              executionPayloadHeader,
                              Optional.of(executionPayload),
                              Optional.empty()));
      Bytes output = postState.sszSerialize();
      return Optional.of(output.toArrayUnsafe());
    } catch (BlockProcessingException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  public Optional<byte[]> fuzzBlsToExecutionChange(final byte[] input) {
    BlsToExecutionChangeFuzzInput structuredInput =
        deserialize(input, BlsToExecutionChangeFuzzInput.createSchema(specVersion));
    SszList<SignedBlsToExecutionChange> blsToExecutionChanges =
        beaconBlockBodySchema
            .getBlsToExecutionChangesSchema()
            .of(structuredInput.getBlsToExecutionChange());

    try {
      BeaconState postState =
          structuredInput
              .getState()
              .updated(
                  state ->
                      spec.getBlockProcessor(state.getSlot())
                          .processBlsToExecutionChanges(state, blsToExecutionChanges));
      Bytes output = postState.sszSerialize();
      return Optional.of(output.toArrayUnsafe());
    } catch (BlockProcessingException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  private <T extends SszData> T deserialize(byte[] data, SszSchema<T> type) {
    // allow exception to propagate on failure - indicates a preprocessing or deserializing error
    T structuredInput = type.sszDeserialize(Bytes.wrap(data));
    if (structuredInput == null) {
      throw new RuntimeException(
          "Failed to deserialize input. Likely a preprocessing or deserialization bug.");
    }
    return structuredInput;
  }
}
