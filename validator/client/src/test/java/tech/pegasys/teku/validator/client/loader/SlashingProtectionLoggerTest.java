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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.LocalSigner;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.core.signatures.SlashingProtectedSigner;
import tech.pegasys.teku.core.signatures.SlashingProtector;
import tech.pegasys.teku.data.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.GraffitiProvider;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.signer.ExternalSigner;

public class SlashingProtectionLoggerTest {
  private final Duration TIMEOUT = Duration.ofMillis(500);
  private final SlashingProtector slashingProtector = mock(SlashingProtector.class);
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BLSKeyPair keyPair = dataStructureUtil.randomKeyPair();
  private final BLSPublicKey pubKey = keyPair.getPublicKey();
  private final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
  private final DefaultSlashingProtectionLogger slashingProtectionLogger =
      new DefaultSlashingProtectionLogger(slashingProtector, forkProvider);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final AsyncRunner asyncRunner = new StubAsyncRunner();

  @BeforeEach
  public void setup() {
    when(forkProvider.getForkInfo(any())).thenReturn(SafeFuture.of(() -> forkInfo));
    when(slashingProtector.loadSigningRecord(pubKey, forkInfo.getGenesisValidatorsRoot()))
        .thenReturn(
            SafeFuture.of(() -> new ValidatorSigningRecord(forkInfo.getGenesisValidatorsRoot())));
  }

  @Test
  public void shouldPresentNotProtectedValidator() throws Exception {
    ExternalSigner externalSigner =
        new ExternalSigner(
            spec,
            mock(HttpClient.class),
            new URL("http://127.0.0.1/"),
            pubKey,
            TIMEOUT,
            mock(ThrottlingTaskQueue.class),
            metricsSystem);
    Validator validator = new Validator(pubKey, externalSigner, mock(GraffitiProvider.class));
    ValidatorSigningRecord signingRecord =
        new ValidatorSigningRecord(forkInfo.getGenesisValidatorsRoot());
    assertThat(
            slashingProtectionLogger.presentValidatorSlashingProtection(
                validator, forkInfo, signingRecord))
        .isCompletedWithValue(
            String.format("%s: not protected", validator.getPublicKey().toAbbreviatedString()));
  }

  @Test
  public void shouldPresentProtectedValidator() {
    LocalSigner localSigner = new LocalSigner(spec, keyPair, asyncRunner);
    Signer signer = new SlashingProtectedSigner(pubKey, slashingProtector, localSigner);
    Validator validator = new Validator(pubKey, signer, mock(GraffitiProvider.class));
    ValidatorSigningRecord signingRecord =
        new ValidatorSigningRecord(forkInfo.getGenesisValidatorsRoot());
    assertThat(
            slashingProtectionLogger.presentValidatorSlashingProtection(
                validator, forkInfo, signingRecord))
        .isCompletedWithValue(
            String.format("%s: protected", validator.getPublicKey().toAbbreviatedString()));
  }

  @Test
  public void shouldPresentProtectedValidatorSlot() {
    final UInt64 slot = UInt64.valueOf(123459);
    when(slashingProtector.loadSigningRecord(pubKey, forkInfo.getGenesisValidatorsRoot()))
        .thenReturn(
            SafeFuture.of(
                () ->
                    new ValidatorSigningRecord(
                        forkInfo.getGenesisValidatorsRoot(), slot, null, null)));
    LocalSigner localSigner = new LocalSigner(spec, keyPair, asyncRunner);
    Signer signer = new SlashingProtectedSigner(pubKey, slashingProtector, localSigner);
    Validator validator = new Validator(pubKey, signer, mock(GraffitiProvider.class));
    ValidatorSigningRecord signingRecord =
        new ValidatorSigningRecord(forkInfo.getGenesisValidatorsRoot());
    assertThat(
            slashingProtectionLogger.presentValidatorSlashingProtection(
                validator, forkInfo, signingRecord))
        .isCompletedWithValue(
            String.format(
                "%s: protected, saved latest signature for slot #%s",
                validator.getPublicKey().toAbbreviatedString(), slot));
  }

  @Test
  public void shouldPresentProtectedValidatorAttestation() {
    final UInt64 epochSource = UInt64.valueOf(12);
    final UInt64 epochTarget = UInt64.valueOf(20);
    when(slashingProtector.loadSigningRecord(pubKey, forkInfo.getGenesisValidatorsRoot()))
        .thenReturn(
            SafeFuture.of(
                () ->
                    new ValidatorSigningRecord(
                        forkInfo.getGenesisValidatorsRoot(),
                        UInt64.ZERO,
                        epochSource,
                        epochTarget)));
    LocalSigner localSigner = new LocalSigner(spec, keyPair, asyncRunner);
    Signer signer = new SlashingProtectedSigner(pubKey, slashingProtector, localSigner);
    Validator validator = new Validator(pubKey, signer, mock(GraffitiProvider.class));
    ValidatorSigningRecord signingRecord =
        new ValidatorSigningRecord(forkInfo.getGenesisValidatorsRoot());
    assertThat(
            slashingProtectionLogger.presentValidatorSlashingProtection(
                validator, forkInfo, signingRecord))
        .isCompletedWithValue(
            String.format(
                "%s: protected, saved latest signature for slot #0, attestation (epochs %s -> %s)",
                validator.getPublicKey().toAbbreviatedString(), epochSource, epochTarget));
  }
}
