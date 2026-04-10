/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.validator.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

@TestSpecContext(milestone = SpecMilestone.GLOAS)
public class ProposerPreferencesPublisherTest {

  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ProposerConfigPropertiesProvider proposerConfigPropertiesProvider =
      mock(ProposerConfigPropertiesProvider.class);
  private final ForkProvider forkProvider = mock(ForkProvider.class);

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private ProposerPreferencesPublisher publisher;
  private Signer signer;
  private Validator validator;
  private BLSPublicKey publicKey;
  private Eth1Address feeRecipient;
  private UInt64 gasLimit;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();

    signer = mock(Signer.class);
    publicKey = dataStructureUtil.randomPublicKey();
    feeRecipient = dataStructureUtil.randomEth1Address();
    gasLimit = dataStructureUtil.randomUInt64();
    validator = new Validator(publicKey, signer, Optional::empty);

    final OwnedValidators ownedValidators = new OwnedValidators(Map.of(publicKey, validator));

    publisher =
        new ProposerPreferencesPublisher(
            validatorApiChannel,
            ownedValidators,
            proposerConfigPropertiesProvider,
            forkProvider,
            spec);

    when(proposerConfigPropertiesProvider.getFeeRecipient(publicKey))
        .thenReturn(Optional.of(feeRecipient));
    when(proposerConfigPropertiesProvider.getGasLimit(publicKey)).thenReturn(gasLimit);
    when(forkProvider.getForkInfo(any()))
        .thenReturn(SafeFuture.completedFuture(dataStructureUtil.randomForkInfo()));
    when(signer.signProposerPreferences(any(), any()))
        .thenReturn(SafeFuture.completedFuture(dataStructureUtil.randomSignature()));
    when(validatorApiChannel.sendSignedProposerPreferences(anyList()))
        .thenReturn(SafeFuture.COMPLETE);
    when(validatorApiChannel.getProposerDuties(any(), eq(true)))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
  }

  @TestTemplate
  void shouldPublishOnFirstCall() {
    final UInt64 anySlot = spec.computeStartSlotAtEpoch(UInt64.valueOf(5)).plus(1);
    final UInt64 nextEpoch = spec.computeEpochAtSlot(anySlot).plus(1);
    final UInt64 nextEpochSlot = spec.computeStartSlotAtEpoch(nextEpoch);

    when(validatorApiChannel.getProposerDuties(eq(nextEpoch), eq(true)))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    new ProposerDuties(
                        dataStructureUtil.randomBytes32(),
                        List.of(new ProposerDuty(publicKey, 42, nextEpochSlot)),
                        false))));

    publisher.onSlot(anySlot);

    verify(validatorApiChannel).sendSignedProposerPreferences(anyList());
  }

  @TestTemplate
  void shouldPublishAtThirdSlotOfEpoch() {
    // Consume first call
    publisher.onSlot(UInt64.ZERO);

    final UInt64 thirdSlotOfEpoch = spec.computeStartSlotAtEpoch(UInt64.valueOf(5)).plus(2);
    final UInt64 nextEpoch = spec.computeEpochAtSlot(thirdSlotOfEpoch).plus(1);
    final UInt64 nextEpochSlot = spec.computeStartSlotAtEpoch(nextEpoch);

    when(validatorApiChannel.getProposerDuties(eq(nextEpoch), eq(true)))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    new ProposerDuties(
                        dataStructureUtil.randomBytes32(),
                        List.of(new ProposerDuty(publicKey, 42, nextEpochSlot)),
                        false))));

    publisher.onSlot(thirdSlotOfEpoch);

    verify(validatorApiChannel).sendSignedProposerPreferences(anyList());
  }

  @TestTemplate
  void shouldNotPublishAtNonThirdSlotOfEpoch() {
    // Consume first call
    publisher.onSlot(UInt64.ZERO);

    final UInt64 fourthSlotOfEpoch = spec.computeStartSlotAtEpoch(UInt64.valueOf(5)).plus(3);

    publisher.onSlot(fourthSlotOfEpoch);

    verify(validatorApiChannel, never()).sendSignedProposerPreferences(anyList());
  }

  @TestTemplate
  void shouldNotPublishTwiceInStartupEpoch() {
    // First call publishes at slot 0 of epoch 5
    final UInt64 firstSlotOfEpoch = spec.computeStartSlotAtEpoch(UInt64.valueOf(5));
    final UInt64 nextEpoch = spec.computeEpochAtSlot(firstSlotOfEpoch).plus(1);
    final UInt64 nextEpochSlot = spec.computeStartSlotAtEpoch(nextEpoch);

    when(validatorApiChannel.getProposerDuties(eq(nextEpoch), eq(true)))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    new ProposerDuties(
                        dataStructureUtil.randomBytes32(),
                        List.of(new ProposerDuty(publicKey, 42, nextEpochSlot)),
                        false))));

    publisher.onSlot(firstSlotOfEpoch);
    verify(validatorApiChannel).sendSignedProposerPreferences(anyList());

    // 3rd slot of same epoch should NOT trigger a second publish
    final UInt64 thirdSlotOfSameEpoch = firstSlotOfEpoch.plus(2);
    publisher.onSlot(thirdSlotOfSameEpoch);
    // still only one invocation total
    verify(validatorApiChannel).sendSignedProposerPreferences(anyList());
  }

  @TestTemplate
  void shouldNotPublishWhenNoDutiesForOurValidators() {
    final UInt64 firstSlotOfEpoch = spec.computeStartSlotAtEpoch(UInt64.valueOf(5));
    final UInt64 nextEpoch = UInt64.valueOf(6);
    final UInt64 nextEpochSlot = spec.computeStartSlotAtEpoch(nextEpoch);

    final BLSPublicKey otherKey = dataStructureUtil.randomPublicKey();
    when(validatorApiChannel.getProposerDuties(eq(nextEpoch), eq(true)))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    new ProposerDuties(
                        dataStructureUtil.randomBytes32(),
                        List.of(new ProposerDuty(otherKey, 99, nextEpochSlot)),
                        false))));

    publisher.onSlot(firstSlotOfEpoch);

    verify(validatorApiChannel, never()).sendSignedProposerPreferences(anyList());
  }

  @TestTemplate
  void shouldNotPublishWhenProposerDutiesUnavailable() {
    final UInt64 firstSlotOfEpoch = spec.computeStartSlotAtEpoch(UInt64.valueOf(5));
    final UInt64 nextEpoch = UInt64.valueOf(6);

    when(validatorApiChannel.getProposerDuties(eq(nextEpoch), eq(true)))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    publisher.onSlot(firstSlotOfEpoch);

    verify(validatorApiChannel, never()).sendSignedProposerPreferences(anyList());
  }

  @TestTemplate
  void shouldNotPublishWhenFeeRecipientNotConfigured() {
    final UInt64 firstSlotOfEpoch = spec.computeStartSlotAtEpoch(UInt64.valueOf(5));
    final UInt64 nextEpoch = UInt64.valueOf(6);
    final UInt64 nextEpochSlot = spec.computeStartSlotAtEpoch(nextEpoch);

    when(proposerConfigPropertiesProvider.getFeeRecipient(publicKey)).thenReturn(Optional.empty());
    when(validatorApiChannel.getProposerDuties(eq(nextEpoch), eq(true)))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    new ProposerDuties(
                        dataStructureUtil.randomBytes32(),
                        List.of(new ProposerDuty(publicKey, 42, nextEpochSlot)),
                        false))));

    publisher.onSlot(firstSlotOfEpoch);

    verify(validatorApiChannel, never()).sendSignedProposerPreferences(anyList());
  }

  @TestTemplate
  void shouldHandleApiFailureGracefully() {
    final UInt64 firstSlotOfEpoch = spec.computeStartSlotAtEpoch(UInt64.valueOf(5));
    final UInt64 nextEpoch = UInt64.valueOf(6);

    when(validatorApiChannel.getProposerDuties(eq(nextEpoch), eq(true)))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("API error")));

    // Should not throw
    publisher.onSlot(firstSlotOfEpoch);
  }
}
