/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.validator.client.duties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeSignature;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;

class SyncCommitteeProductionDutyTest {

  private static final String SIGNATURE_TYPE = "sync committee signature";
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ValidatorLogger validatorLogger = mock(ValidatorLogger.class);
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();

  private final Validator validator = createValidator();

  @BeforeEach
  void setUp() {
    when(forkProvider.getForkInfo()).thenReturn(SafeFuture.completedFuture(forkInfo));
  }

  @Test
  void shouldReturnNoOpWhenNoValidatorsAssigned() {
    final SyncCommitteeProductionDuties duties = dutyBuilder().build();
    assertThat(duties.produceSignatures(UInt64.ONE)).isCompletedWithValue(DutyResult.NO_OP);
  }

  @Test
  void shouldFailToProduceSignaturesWhenNodeHasNoBlock() {
    final SyncCommitteeProductionDuties duties =
        dutyBuilder().committeeAssignment(validator, 55, 1).build();
    final UInt64 slot = UInt64.valueOf(25);
    when(validatorApiChannel.getBlockRootInEffectAtSlot(slot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    produceSignaturesAndReport(duties, slot);

    verify(validatorLogger).dutySkippedWhileSyncing(SIGNATURE_TYPE, slot, 1);
  }

  @Test
  void shouldProduceSignature() {
    final UInt64 slot = UInt64.valueOf(25);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final int validatorIndex = 23;
    final BLSSignature signature = dataStructureUtil.randomSignature();
    final SyncCommitteeProductionDuties duties =
        dutyBuilder().committeeAssignment(validator, validatorIndex, 1).build();
    when(validatorApiChannel.getBlockRootInEffectAtSlot(slot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockRoot)));

    when(validator.getSigner().signSyncCommitteeSignature(slot, blockRoot, forkInfo))
        .thenReturn(SafeFuture.completedFuture(signature));

    produceSignaturesAndReport(duties, slot);

    verify(validatorApiChannel)
        .sendSyncCommitteeSignatures(
            List.of(createSignature(slot, blockRoot, validatorIndex, signature)));
    verify(validatorLogger).dutyCompleted(SIGNATURE_TYPE, slot, 1, Set.of(blockRoot));
  }

  @Test
  void shouldProduceOneSignatureForEachValidator() {
    final UInt64 slot = UInt64.valueOf(48);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final int validatorIndex1 = 11;
    final int validatorIndex2 = 22;
    final Validator validator2 = createValidator();
    final BLSSignature signature1 = dataStructureUtil.randomSignature();
    final BLSSignature signature2 = dataStructureUtil.randomSignature();
    final SyncCommitteeProductionDuties duties =
        dutyBuilder()
            .committeeAssignment(validator, validatorIndex1, 1)
            .committeeAssignment(validator, validatorIndex1, 2)
            .committeeAssignment(validator, validatorIndex1, 3)
            .committeeAssignment(validator2, validatorIndex2, 1)
            .committeeAssignment(validator2, validatorIndex2, 5)
            .build();
    when(validatorApiChannel.getBlockRootInEffectAtSlot(slot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockRoot)));

    when(validator.getSigner().signSyncCommitteeSignature(slot, blockRoot, forkInfo))
        .thenReturn(SafeFuture.completedFuture(signature1));
    when(validator2.getSigner().signSyncCommitteeSignature(slot, blockRoot, forkInfo))
        .thenReturn(SafeFuture.completedFuture(signature2));

    produceSignaturesAndReport(duties, slot);

    assertSentSignatures(
        createSignature(slot, blockRoot, validatorIndex1, signature1),
        createSignature(slot, blockRoot, validatorIndex2, signature2));

    verify(validatorLogger).dutyCompleted(SIGNATURE_TYPE, slot, 2, Set.of(blockRoot));
  }

  @SuppressWarnings("unchecked")
  private void assertSentSignatures(final SyncCommitteeSignature... signatures) {
    ArgumentCaptor<Collection<SyncCommitteeSignature>> argumentCaptor =
        ArgumentCaptor.forClass(Collection.class);
    verify(validatorApiChannel).sendSyncCommitteeSignatures(argumentCaptor.capture());

    assertThat(argumentCaptor.getValue()).containsExactlyInAnyOrder(signatures);
  }

  private SyncCommitteeSignature createSignature(
      final UInt64 slot,
      final Bytes32 blockRoot,
      final int validatorIndex,
      final BLSSignature signature) {
    return SchemaDefinitionsAltair.required(spec.getGenesisSchemaDefinitions())
        .getSyncCommitteeSignatureSchema()
        .create(slot, blockRoot, UInt64.valueOf(validatorIndex), signature);
  }

  private void produceSignaturesAndReport(
      final SyncCommitteeProductionDuties duties, final UInt64 slot) {
    final SafeFuture<DutyResult> result = duties.produceSignatures(slot);
    assertThat(result).isCompleted();
    result.join().report(SIGNATURE_TYPE, slot, validatorLogger);
  }

  private SyncCommitteeProductionDuties.Builder dutyBuilder() {
    return SyncCommitteeProductionDuties.builder()
        .validatorApiChannel(validatorApiChannel)
        .spec(spec)
        .forkProvider(forkProvider);
  }

  private Validator createValidator() {
    return new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty);
  }
}
