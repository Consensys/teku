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

package tech.pegasys.teku.validator.client.duties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.failedFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlindedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlindedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlindedBlockContents;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContents;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlindedBlockContents;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContents;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.FileBackedGraffitiProvider;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.signer.BlockContainerSigner;
import tech.pegasys.teku.validator.client.signer.MilestoneBasedBlockContainerSigner;

class BlockProductionDutyTest {
  private static final String TYPE = "block";
  private static final UInt64 CAPELLA_SLOT = UInt64.valueOf(498294);
  private static final UInt64 DENEB_FORK_EPOCH = UInt64.valueOf(500000);
  private final Spec spec = TestSpecFactory.createMinimalWithDenebForkEpoch(DENEB_FORK_EPOCH);
  private final UInt64 denebSlot = DENEB_FORK_EPOCH.times(spec.getSlotsPerEpoch(ZERO)).plus(42);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final Signer signer = mock(Signer.class);
  private final Bytes32 graffiti = dataStructureUtil.randomBytes32();
  private final Validator validator =
      new Validator(
          dataStructureUtil.randomPublicKey(),
          signer,
          new FileBackedGraffitiProvider(Optional.of(graffiti), Optional.empty()));
  private final ForkInfo fork = dataStructureUtil.randomForkInfo();
  private final ValidatorLogger validatorLogger = mock(ValidatorLogger.class);
  private final BlockContainerSigner blockContainerSigner =
      new MilestoneBasedBlockContainerSigner(spec);

  private BlockProductionDuty duty;

  @BeforeEach
  public void setUp() {
    duty =
        new BlockProductionDuty(
            validator,
            CAPELLA_SLOT,
            forkProvider,
            validatorApiChannel,
            blockContainerSigner,
            false,
            spec);
    when(forkProvider.getForkInfo(any())).thenReturn(completedFuture(fork));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCreateAndPublishBlock(final boolean isBlindedBlocksEnabled) {
    duty =
        new BlockProductionDuty(
            validator,
            CAPELLA_SLOT,
            forkProvider,
            validatorApiChannel,
            blockContainerSigner,
            isBlindedBlocksEnabled,
            spec);
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BLSSignature blockSignature = dataStructureUtil.randomSignature();
    final BeaconBlock unsignedBlock;

    if (isBlindedBlocksEnabled) {
      unsignedBlock = dataStructureUtil.randomBlindedBeaconBlock(CAPELLA_SLOT);
    } else {
      unsignedBlock = dataStructureUtil.randomBeaconBlock(CAPELLA_SLOT);
    }

    when(signer.createRandaoReveal(spec.computeEpochAtSlot(CAPELLA_SLOT), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(validatorApiChannel.createUnsignedBlock(
            CAPELLA_SLOT, randaoReveal, Optional.of(graffiti), isBlindedBlocksEnabled))
        .thenReturn(completedFuture(Optional.of(unsignedBlock)));
    when(signer.signBlock(unsignedBlock, fork)).thenReturn(completedFuture(blockSignature));
    final SignedBeaconBlock signedBlock =
        dataStructureUtil.signedBlock(unsignedBlock, blockSignature);
    when(validatorApiChannel.sendSignedBlock(signedBlock))
        .thenReturn(completedFuture(SendSignedBlockResult.success(signedBlock.getRoot())));

    performAndReportDuty();

    verify(validatorApiChannel).sendSignedBlock(signedBlock);
    verify(validatorLogger)
        .dutyCompleted(
            eq(TYPE),
            eq(CAPELLA_SLOT),
            eq(1),
            eq(Set.of(unsignedBlock.hashTreeRoot())),
            ArgumentMatchers.argThat(Optional::isPresent));
    verifyNoMoreInteractions(validatorLogger);
  }

  @Test
  public void forDeneb_shouldCreateAndPublishBlockContents() {
    duty =
        new BlockProductionDuty(
            validator,
            denebSlot,
            forkProvider,
            validatorApiChannel,
            blockContainerSigner,
            false,
            spec);

    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BLSSignature blockSignature = dataStructureUtil.randomSignature();
    // can create BlockContents only post-Deneb
    final BlockContents unsignedBlockContents = dataStructureUtil.randomBlockContents(denebSlot);
    final BeaconBlock unsignedBlock = unsignedBlockContents.getBlock();
    final List<BlobSidecar> unsignedBlobSidecars =
        unsignedBlockContents.getBlobSidecars().orElseThrow();
    final Map<BlobSidecar, BLSSignature> blobSidecarsSignatures =
        unsignedBlobSidecars.stream()
            .collect(
                Collectors.toMap(Function.identity(), __ -> dataStructureUtil.randomSignature()));

    when(signer.createRandaoReveal(spec.computeEpochAtSlot(denebSlot), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(signer.signBlock(unsignedBlockContents.getBlock(), fork))
        .thenReturn(completedFuture(blockSignature));
    when(signer.signBlobSidecar(any(), eq(fork)))
        .thenAnswer(
            invocation ->
                completedFuture(
                    blobSidecarsSignatures.get((BlobSidecar) invocation.getArgument(0))));
    when(validatorApiChannel.createUnsignedBlock(
            denebSlot, randaoReveal, Optional.of(graffiti), false))
        .thenReturn(completedFuture(Optional.of(unsignedBlockContents)));
    when(validatorApiChannel.sendSignedBlock(any()))
        .thenReturn(completedFuture(SendSignedBlockResult.success(unsignedBlock.getRoot())));

    performAndReportDuty(denebSlot);

    final ArgumentCaptor<SignedBlockContents> signedBlockContentsArgumentCaptor =
        ArgumentCaptor.forClass(SignedBlockContents.class);

    verify(validatorApiChannel).sendSignedBlock(signedBlockContentsArgumentCaptor.capture());
    verify(validatorLogger)
        .dutyCompleted(
            eq(TYPE),
            eq(denebSlot),
            eq(1),
            eq(Set.of(unsignedBlock.hashTreeRoot())),
            ArgumentMatchers.argThat(Optional::isPresent));
    verifyNoMoreInteractions(validatorLogger);

    final SignedBlockContents signedBlockContents = signedBlockContentsArgumentCaptor.getValue();

    assertThat(signedBlockContents.isBlinded()).isFalse();

    final SignedBeaconBlock signedBlock = signedBlockContents.getSignedBlock();
    assertThat(signedBlock.getMessage()).isEqualTo(unsignedBlock);
    assertThat(signedBlock.getSignature()).isEqualTo(blockSignature);

    assertThat(signedBlockContents.getSignedBlobSidecars().isPresent()).isTrue();

    final List<SignedBlobSidecar> signedBlobSidecars =
        signedBlockContents.getSignedBlobSidecars().get();

    assertThat(signedBlobSidecars).isNotEmpty();

    IntStream.range(0, signedBlobSidecars.size())
        .forEach(
            index -> {
              final SignedBlobSidecar signedBlobSidecar = signedBlobSidecars.get(index);
              final BlobSidecar unsignedBlobSidecar = unsignedBlobSidecars.get(index);
              assertThat(signedBlobSidecar.getMessage()).isEqualTo(unsignedBlobSidecar);
              assertThat(signedBlobSidecar.getSignature())
                  .isEqualTo(blobSidecarsSignatures.get(unsignedBlobSidecar));
            });
  }

  @Test
  public void forDeneb_shouldCreateAndPublishBlindedBlockContents() {
    duty =
        new BlockProductionDuty(
            validator,
            denebSlot,
            forkProvider,
            validatorApiChannel,
            blockContainerSigner,
            true,
            spec);

    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BLSSignature blockSignature = dataStructureUtil.randomSignature();
    // can create BlindedBlockContents only post-Deneb fork
    final BlindedBlockContents unsignedBlindedBlockContents =
        dataStructureUtil.randomBlindedBlockContents(denebSlot);
    final BeaconBlock unsignedBlindedBlock = unsignedBlindedBlockContents.getBlock();
    final List<BlindedBlobSidecar> unsignedBlindedBlobSidecars =
        unsignedBlindedBlockContents.getBlindedBlobSidecars().orElseThrow();
    final Map<BlindedBlobSidecar, BLSSignature> blindedBlobSidecarsSignatures =
        unsignedBlindedBlobSidecars.stream()
            .collect(
                Collectors.toMap(Function.identity(), __ -> dataStructureUtil.randomSignature()));

    when(signer.createRandaoReveal(spec.computeEpochAtSlot(denebSlot), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(signer.signBlock(unsignedBlindedBlock.getBlock(), fork))
        .thenReturn(completedFuture(blockSignature));
    when(signer.signBlindedBlobSidecar(any(), eq(fork)))
        .thenAnswer(
            invocation ->
                completedFuture(
                    blindedBlobSidecarsSignatures.get(
                        (BlindedBlobSidecar) invocation.getArgument(0))));
    when(validatorApiChannel.createUnsignedBlock(
            denebSlot, randaoReveal, Optional.of(graffiti), true))
        .thenReturn(completedFuture(Optional.of(unsignedBlindedBlockContents)));
    when(validatorApiChannel.sendSignedBlock(any()))
        .thenReturn(completedFuture(SendSignedBlockResult.success(unsignedBlindedBlock.getRoot())));

    performAndReportDuty(denebSlot);

    final ArgumentCaptor<SignedBlindedBlockContents> signedBlindedBlockContentsArgumentCaptor =
        ArgumentCaptor.forClass(SignedBlindedBlockContents.class);

    verify(validatorApiChannel).sendSignedBlock(signedBlindedBlockContentsArgumentCaptor.capture());
    verify(validatorLogger)
        .dutyCompleted(
            eq(TYPE),
            eq(denebSlot),
            eq(1),
            eq(Set.of(unsignedBlindedBlock.hashTreeRoot())),
            ArgumentMatchers.argThat(Optional::isPresent));
    verifyNoMoreInteractions(validatorLogger);

    final SignedBlindedBlockContents signedBlindedBlockContents =
        signedBlindedBlockContentsArgumentCaptor.getValue();

    assertThat(signedBlindedBlockContents.isBlinded()).isTrue();

    final SignedBeaconBlock signedBlock = signedBlindedBlockContents.getSignedBlock();
    assertThat(signedBlock.getMessage()).isEqualTo(unsignedBlindedBlock);
    assertThat(signedBlock.isBlinded()).isTrue();
    assertThat(signedBlock.getSignature()).isEqualTo(blockSignature);

    assertThat(signedBlindedBlockContents.getSignedBlindedBlobSidecars().isPresent()).isTrue();

    final List<SignedBlindedBlobSidecar> signedBlindedBlobSidecars =
        signedBlindedBlockContents.getSignedBlindedBlobSidecars().get();

    assertThat(signedBlindedBlobSidecars).isNotEmpty();

    IntStream.range(0, signedBlindedBlobSidecars.size())
        .forEach(
            index -> {
              final SignedBlindedBlobSidecar signedBlindedBlobSidecar =
                  signedBlindedBlobSidecars.get(index);
              final BlindedBlobSidecar unsignedBlindedBlobSidecar =
                  unsignedBlindedBlobSidecars.get(index);
              assertThat(signedBlindedBlobSidecar.getBlindedBlobSidecar())
                  .isEqualTo(unsignedBlindedBlobSidecar);
              assertThat(signedBlindedBlobSidecar.getSignature())
                  .isEqualTo(blindedBlobSidecarsSignatures.get(unsignedBlindedBlobSidecar));
            });
  }

  @Test
  public void shouldFailWhenCreateRandaoFails() {
    final RuntimeException error = new RuntimeException("Sorry!");
    when(signer.createRandaoReveal(spec.computeEpochAtSlot(CAPELLA_SLOT), fork))
        .thenReturn(failedFuture(error));

    assertDutyFails(error);
  }

  @Test
  public void bellatrixBlockSummary() {
    final BeaconBlockBody block = mock(BeaconBlockBody.class);
    when(block.getOptionalExecutionPayloadSummary())
        .thenReturn(
            Optional.of(
                new PayloadSummary(
                    UInt64.valueOf(1024000),
                    UInt64.valueOf(102400),
                    dataStructureUtil.randomBytes32(),
                    dataStructureUtil.randomUInt64())));
    assertThat(BlockProductionDuty.getBlockSummary(block))
        .contains(
            "102400 (10%) gas, EL block:  499db7404cbff78670f0209f1932346fef68d985cb55a8d27472742bdf54d379 (4661716390776343276)");
  }

  @Test
  public void shouldFailWhenCreateUnsignedBlockFails() {
    final RuntimeException error = new RuntimeException("Sorry!");
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    when(signer.createRandaoReveal(spec.computeEpochAtSlot(CAPELLA_SLOT), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(validatorApiChannel.createUnsignedBlock(
            CAPELLA_SLOT, randaoReveal, Optional.of(graffiti), false))
        .thenReturn(failedFuture(error));

    assertDutyFails(error);
  }

  @Test
  public void shouldFailWhenCreateUnsignedBlockReturnsEmpty() {
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    when(signer.createRandaoReveal(spec.computeEpochAtSlot(CAPELLA_SLOT), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(validatorApiChannel.createUnsignedBlock(
            CAPELLA_SLOT, randaoReveal, Optional.of(graffiti), false))
        .thenReturn(completedFuture(Optional.empty()));

    performAndReportDuty();

    verify(validatorLogger)
        .dutyFailed(
            eq(TYPE),
            eq(CAPELLA_SLOT),
            eq(Set.of(validator.getPublicKey().toAbbreviatedString())),
            any(IllegalStateException.class));
    verifyNoMoreInteractions(validatorLogger);
  }

  @Test
  public void shouldFailWhenSigningBlockFails() {
    final RuntimeException error = new RuntimeException("Sorry!");
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BeaconBlock unsignedBlock = dataStructureUtil.randomBeaconBlock(CAPELLA_SLOT.longValue());
    when(signer.createRandaoReveal(spec.computeEpochAtSlot(CAPELLA_SLOT), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(validatorApiChannel.createUnsignedBlock(
            CAPELLA_SLOT, randaoReveal, Optional.of(graffiti), false))
        .thenReturn(completedFuture(Optional.of(unsignedBlock)));
    when(signer.signBlock(unsignedBlock, fork)).thenReturn(failedFuture(error));

    assertDutyFails(error);
  }

  public void assertDutyFails(final RuntimeException error) {
    performAndReportDuty();
    verify(validatorLogger)
        .dutyFailed(
            TYPE, CAPELLA_SLOT, Set.of(validator.getPublicKey().toAbbreviatedString()), error);
    verifyNoMoreInteractions(validatorLogger);
  }

  private void performAndReportDuty() {
    performAndReportDuty(CAPELLA_SLOT);
  }

  private void performAndReportDuty(final UInt64 slot) {
    final SafeFuture<DutyResult> result = duty.performDuty();
    assertThat(result).isCompleted();
    result.join().report(TYPE, slot, validatorLogger);
  }

  static class PayloadSummary implements ExecutionPayloadSummary {
    private final UInt64 gasLimit;
    private final UInt64 gasUsed;
    private final Bytes32 blockHash;
    private final UInt64 blockNumber;

    public PayloadSummary(
        final UInt64 gasLimit,
        final UInt64 gasUsed,
        final Bytes32 blockHash,
        final UInt64 blockNumber) {
      this.gasLimit = gasLimit;
      this.gasUsed = gasUsed;
      this.blockHash = blockHash;
      this.blockNumber = blockNumber;
    }

    @Override
    public UInt64 getGasLimit() {
      return gasLimit;
    }

    @Override
    public UInt64 getGasUsed() {
      return gasUsed;
    }

    @Override
    public UInt64 getBlockNumber() {
      return blockNumber;
    }

    @Override
    public Bytes32 getBlockHash() {
      return blockHash;
    }

    @Override
    public UInt256 getBaseFeePerGas() {
      return null;
    }

    @Override
    public UInt64 getTimestamp() {
      return null;
    }

    @Override
    public Bytes32 getPrevRandao() {
      return null;
    }

    @Override
    public Bytes32 getReceiptsRoot() {
      return null;
    }

    @Override
    public Bytes32 getStateRoot() {
      return null;
    }

    @Override
    public Bytes20 getFeeRecipient() {
      return null;
    }

    @Override
    public Bytes32 getParentHash() {
      return null;
    }

    @Override
    public Bytes getLogsBloom() {
      return null;
    }

    @Override
    public Bytes getExtraData() {
      return null;
    }

    @Override
    public Bytes32 getPayloadHash() {
      return null;
    }

    @Override
    public boolean isDefaultPayload() {
      return false;
    }

    @Override
    public Optional<Bytes32> getOptionalWithdrawalsRoot() {
      return Optional.empty();
    }
  }
}
