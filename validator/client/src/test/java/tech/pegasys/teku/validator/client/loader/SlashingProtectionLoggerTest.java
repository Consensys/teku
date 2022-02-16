/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.validator.client.loader;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.LocalSigner;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.core.signatures.SlashingProtectedSigner;
import tech.pegasys.teku.core.signatures.SlashingProtector;
import tech.pegasys.teku.data.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.GraffitiProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.signer.ExternalSigner;

public class SlashingProtectionLoggerTest {
  private final Duration TIMEOUT = Duration.ofMillis(500);
  private final SlashingProtector slashingProtector = mock(SlashingProtector.class);
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BLSKeyPair keyPair = dataStructureUtil.randomKeyPair();
  private final BLSPublicKey pubKey = keyPair.getPublicKey();
  private StubAsyncRunner asyncRunner;
  private ValidatorLogger validatorLogger;
  private SlashingProtectionLogger slashingProtectionLogger;
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  @BeforeEach
  public void beforeEach() throws Exception {
    when(slashingProtector.getSigningRecord(any())).thenReturn(Optional.empty());
    this.asyncRunner = new StubAsyncRunner();
    this.validatorLogger = mock(ValidatorLogger.class);
    this.slashingProtectionLogger =
        new SlashingProtectionLogger(slashingProtector, spec, asyncRunner, validatorLogger);
    this.slashingProtectionLogger.onSlot(UInt64.ONE);
  }

  @Test
  public void shouldLogNothingOnEmptyList() {
    slashingProtectionLogger.protectionSummary(new ArrayList<>());
    asyncRunner.executeQueuedActions();
    verify(validatorLogger, never()).activatedSlashingProtection(any());
    verify(validatorLogger, never()).noLocalSlashingProtection(any());
    verify(validatorLogger, never()).outdatedSlashingProtection(any(), any());
  }

  @Test
  public void shouldLogNotProtectedValidator() {
    Validator unProtectedValidator = createUnProtectedValidator(pubKey);
    List<Validator> unProtectedValidators = new ArrayList<>();
    unProtectedValidators.add(unProtectedValidator);
    slashingProtectionLogger.protectionSummary(unProtectedValidators);
    asyncRunner.executeQueuedActions();
    verify(validatorLogger, never()).activatedSlashingProtection(any());
    Set<String> unProtectedValidatorKeys = new HashSet<>();
    unProtectedValidatorKeys.add(unProtectedValidator.getPublicKey().toAbbreviatedString());
    verify(validatorLogger, times(1)).noLocalSlashingProtection(unProtectedValidatorKeys);
    verify(validatorLogger, never()).outdatedSlashingProtection(any(), any());
  }

  @Test
  public void shouldLogProtectedValidator() {
    Validator validator = createProtectedValidator(keyPair);
    List<Validator> protectedValidators = new ArrayList<>();
    protectedValidators.add(validator);
    slashingProtectionLogger.protectionSummary(protectedValidators);
    asyncRunner.executeQueuedActions();
    Set<String> protectedValidatorKeys = new HashSet<>();
    protectedValidatorKeys.add(validator.getPublicKey().toAbbreviatedString());
    verify(validatorLogger, times(1)).activatedSlashingProtection(protectedValidatorKeys);
    verify(validatorLogger, never()).noLocalSlashingProtection(any());
    verify(validatorLogger, never()).outdatedSlashingProtection(any(), any());
  }

  @Test
  public void shouldLogObsolescenceOfProtectedValidator() {
    Validator validator = createProtectedValidator(keyPair);
    List<Validator> protectedValidators = new ArrayList<>();
    protectedValidators.add(validator);
    slashingProtectionLogger.onSlot(spec.computeStartSlotAtEpoch(UInt64.valueOf(1000)));
    slashingProtectionLogger.protectionSummary(protectedValidators);
    asyncRunner.executeQueuedActions();
    Set<String> protectedValidatorKeys = new HashSet<>();
    protectedValidatorKeys.add(validator.getPublicKey().toAbbreviatedString());
    verify(validatorLogger, times(1)).activatedSlashingProtection(protectedValidatorKeys);
    verify(validatorLogger, never()).noLocalSlashingProtection(any());
    verify(validatorLogger, times(1)).outdatedSlashingProtection(eq(protectedValidatorKeys), any());
  }

  @Test
  public void shouldObsoleteProtectedValidatorBothBySlotAndAttestation() throws Exception {
    UInt64 recentSlot = spec.computeStartSlotAtEpoch(UInt64.valueOf(900));
    BLSKeyPair keyPair1 = dataStructureUtil.randomKeyPair();
    Validator validatorNoAttestationRecord = createProtectedValidator(keyPair1);
    when(slashingProtector.getSigningRecord(validatorNoAttestationRecord.getPublicKey()))
        .thenReturn(Optional.of(new ValidatorSigningRecord(Bytes32.ZERO, recentSlot, null, null)));
    BLSKeyPair keyPair2 = dataStructureUtil.randomKeyPair();
    Validator validatorNoBlockProposerRecord = createProtectedValidator(keyPair2);
    when(slashingProtector.getSigningRecord(validatorNoBlockProposerRecord.getPublicKey()))
        .thenReturn(
            Optional.of(
                new ValidatorSigningRecord(
                    Bytes32.ZERO, UInt64.ZERO, UInt64.valueOf(900), UInt64.valueOf(950))));
    BLSKeyPair keyPair3 = dataStructureUtil.randomKeyPair();
    Validator validatorGoodRecords = createProtectedValidator(keyPair3);
    when(slashingProtector.getSigningRecord(validatorGoodRecords.getPublicKey()))
        .thenReturn(
            Optional.of(
                new ValidatorSigningRecord(
                    Bytes32.ZERO, recentSlot, UInt64.valueOf(900), UInt64.valueOf(950))));

    List<Validator> validators = new ArrayList<>();
    validators.add(validatorNoAttestationRecord);
    validators.add(validatorNoBlockProposerRecord);
    validators.add(validatorGoodRecords);
    slashingProtectionLogger.onSlot(spec.computeStartSlotAtEpoch(UInt64.valueOf(1000)));
    slashingProtectionLogger.protectionSummary(validators);
    asyncRunner.executeQueuedActions();
    Set<String> protectedValidatorKeys =
        validators.stream()
            .map(validator -> validator.getPublicKey().toAbbreviatedString())
            .collect(Collectors.toSet());
    Set<String> outdatedValidatorKeys = new HashSet<>();
    outdatedValidatorKeys.add(validatorNoAttestationRecord.getPublicKey().toAbbreviatedString());
    outdatedValidatorKeys.add(validatorNoBlockProposerRecord.getPublicKey().toAbbreviatedString());
    verify(validatorLogger, times(1)).activatedSlashingProtection(protectedValidatorKeys);
    verify(validatorLogger, never()).noLocalSlashingProtection(any());
    verify(validatorLogger, times(1)).outdatedSlashingProtection(eq(outdatedValidatorKeys), any());
  }

  private Validator createProtectedValidator(BLSKeyPair blsKeyPair) {
    LocalSigner localSigner = new LocalSigner(spec, blsKeyPair, asyncRunner);
    Signer signer =
        new SlashingProtectedSigner(blsKeyPair.getPublicKey(), slashingProtector, localSigner);
    return new Validator(blsKeyPair.getPublicKey(), signer, mock(GraffitiProvider.class));
  }

  private Validator createUnProtectedValidator(BLSPublicKey publicKey) {
    ExternalSigner externalSigner;
    try {
      externalSigner =
          new ExternalSigner(
              spec,
              mock(HttpClient.class),
              new URL("http://127.0.0.1/"),
              publicKey,
              TIMEOUT,
              mock(ThrottlingTaskQueue.class),
              metricsSystem);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
    return new Validator(publicKey, externalSigner, mock(GraffitiProvider.class));
  }
}
