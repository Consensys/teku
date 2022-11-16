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

package tech.pegasys.teku.spec.logic.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.Merkleizable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.attestation.AttestationWorthinessChecker;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.attestation.AttestationWorthinessCheckerAltair;
import tech.pegasys.teku.spec.logic.versions.altair.util.AttestationUtilAltair;
import tech.pegasys.teku.spec.logic.versions.phase0.util.AttestationUtilPhase0;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = {SpecMilestone.PHASE0, SpecMilestone.ALTAIR})
class AttestationUtilTest {

  private final MiscHelpers miscHelpers = mock(MiscHelpers.class);
  private final BeaconStateAccessors beaconStateAccessors = mock(BeaconStateAccessors.class);
  private final AsyncBLSSignatureVerifier asyncBLSSignatureVerifier =
      mock(AsyncBLSSignatureVerifier.class);

  private Spec spec;
  private DataStructureUtil dataStructureUtil;

  private AttestationUtil attestationUtil;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    final SpecVersion specVersion = spec.forMilestone(specContext.getSpecMilestone());
    final IntList beaconCommittee = createBeaconCommittee(specVersion);
    when(beaconStateAccessors.getBeaconCommittee(any(), any(), any())).thenReturn(beaconCommittee);
    when(beaconStateAccessors.getValidatorPubKey(any(), any()))
        .thenReturn(Optional.of(dataStructureUtil.randomPublicKey()));
    when(beaconStateAccessors.getDomain(any(), any(), any(), any()))
        .thenReturn(dataStructureUtil.randomBytes32());
    when(miscHelpers.computeSigningRoot(any(Merkleizable.class), any(Bytes32.class)))
        .thenReturn(dataStructureUtil.randomBytes(10));
    when(asyncBLSSignatureVerifier.verify(anyList(), any(Bytes.class), any(BLSSignature.class)))
        .thenReturn(SafeFuture.completedFuture(true));
    attestationUtil = spec.getGenesisSpec().getAttestationUtil();

    switch (specContext.getSpecMilestone()) {
      case PHASE0:
        attestationUtil =
            new AttestationUtilPhase0(
                spec.getGenesisSpecConfig(),
                specVersion.getSchemaDefinitions(),
                beaconStateAccessors,
                miscHelpers);
        break;
      case ALTAIR:
        attestationUtil =
            new AttestationUtilAltair(
                spec.getGenesisSpecConfig(),
                specVersion.getSchemaDefinitions(),
                beaconStateAccessors,
                miscHelpers);
        break;
      default:
        throw new UnsupportedOperationException("unsupported milestone");
    }
  }

  @TestTemplate
  void shouldCreateAttestationWorthinessChecker(final SpecContext specContext) {
    when(miscHelpers.computeStartSlotAtEpoch(any())).thenReturn(UInt64.ONE);

    switch (specContext.getSpecMilestone()) {
      case PHASE0:
        assertThat(
                attestationUtil.createAttestationWorthinessChecker(
                    dataStructureUtil.randomBeaconState(UInt64.ONE)))
            .isEqualTo(AttestationWorthinessChecker.NOOP);
        break;
      case ALTAIR:
        assertThat(
                attestationUtil.createAttestationWorthinessChecker(
                    dataStructureUtil.randomBeaconState(UInt64.ONE)))
            .isInstanceOf(AttestationWorthinessCheckerAltair.class);
        break;
      default:
        throw new UnsupportedOperationException("unsupported milestone");
    }
  }

  @TestTemplate
  void noValidationIsDoneIfAttestationIsAlreadyValidAndIndexedAttestationIsPresent(
      final SpecContext specContext) {
    specContext.assumeIsOneOf(SpecMilestone.PHASE0);
    final ValidateableAttestation validateableAttestation =
        ValidateableAttestation.from(spec, dataStructureUtil.randomAttestation());
    validateableAttestation.setValidIndexedAttestation();
    final IndexedAttestation indexedAttestation = dataStructureUtil.randomIndexedAttestation();
    validateableAttestation.setIndexedAttestation(indexedAttestation);

    final SafeFuture<AttestationProcessingResult> result =
        executeValidation(validateableAttestation);

    assertThat(result).isCompletedWithValue(AttestationProcessingResult.SUCCESSFUL);

    assertThat(validateableAttestation.isValidIndexedAttestation()).isTrue();
    assertThat(validateableAttestation.getIndexedAttestation()).hasValue(indexedAttestation);

    verifyNoInteractions(beaconStateAccessors, miscHelpers, asyncBLSSignatureVerifier);
  }

  @TestTemplate
  void createsAndValidatesIndexedAttestation(final SpecContext specContext) {
    specContext.assumeIsOneOf(SpecMilestone.PHASE0);
    final Attestation attestation = dataStructureUtil.randomAttestation();
    final ValidateableAttestation validateableAttestation =
        ValidateableAttestation.from(spec, attestation);

    final SafeFuture<AttestationProcessingResult> result =
        executeValidation(validateableAttestation);

    assertThat(result).isCompletedWithValue(AttestationProcessingResult.SUCCESSFUL);

    assertThat(validateableAttestation.isValidIndexedAttestation()).isTrue();
    assertThat(validateableAttestation.getIndexedAttestation()).isPresent();
    assertThat(validateableAttestation.getCommitteeShufflingSeed()).isPresent();

    verify(asyncBLSSignatureVerifier).verify(anyList(), any(Bytes.class), any(BLSSignature.class));
  }

  @TestTemplate
  void createsButDoesNotValidateIndexedAttestationBecauseItHasAlreadyBeenValidated(
      final SpecContext specContext) {
    specContext.assumeIsOneOf(SpecMilestone.PHASE0);
    final Attestation attestation = dataStructureUtil.randomAttestation();
    // reorged block does not require indexed attestation validation, however it requires the
    // creation of it
    final ValidateableAttestation validateableAttestation =
        ValidateableAttestation.fromReorgedBlock(spec, attestation);

    final SafeFuture<AttestationProcessingResult> result =
        executeValidation(validateableAttestation);

    assertThat(result).isCompletedWithValue(AttestationProcessingResult.SUCCESSFUL);

    assertThat(validateableAttestation.isValidIndexedAttestation()).isTrue();
    assertThat(validateableAttestation.getIndexedAttestation()).isPresent();
    assertThat(validateableAttestation.getCommitteeShufflingSeed()).isPresent();

    verifyNoInteractions(miscHelpers, asyncBLSSignatureVerifier);
  }

  private SafeFuture<AttestationProcessingResult> executeValidation(
      final ValidateableAttestation validateableAttestation) {
    return attestationUtil.isValidIndexedAttestationAsync(
        dataStructureUtil.randomFork(),
        dataStructureUtil.randomBeaconState(),
        validateableAttestation,
        asyncBLSSignatureVerifier);
  }

  private IntList createBeaconCommittee(final SpecVersion specVersion) {
    final int[] committee =
        IntStream.rangeClosed(0, specVersion.getConfig().getMaxValidatorsPerCommittee() - 1)
            .toArray();
    return IntList.of(committee);
  }
}
