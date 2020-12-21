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

package tech.pegasys.teku.validator.client.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.signatures.SlashingProtector;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.Validator;

class ValidatorLoaderTest {

  private static final BLSPublicKey PUBLIC_KEY1 =
      BLSPublicKey.fromSSZBytes(
          Bytes.fromHexString(
              "0x9612d7a727c9d0a22e185a1c768478dfe919cada9266988cb32359c11f2b7b27f4ae4040902382ae2910c15e2b420d07"));
  private static final BLSPublicKey PUBLIC_KEY2 =
      BLSPublicKey.fromSSZBytes(
          Bytes.fromHexString(
              "0xb89bebc699769726a318c8e9971bd3171297c61aea4a6578a7a4f94b547dcba5bac16a89108b6b6a1fe3695d1a874a0b"));

  private static final URL SIGNER_URL;

  static {
    try {
      SIGNER_URL = new URL("http://localhost:9000");
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  private final SlashingProtector slashingProtector = mock(SlashingProtector.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final HttpClient httpClient = mock(HttpClient.class);
  private final MetricsSystem metricsSystem = new StubMetricsSystem();

  @SuppressWarnings("unchecked")
  private final HttpResponse<Void> upcheckResponse = mock(HttpResponse.class);

  private final ValidatorLoader validatorLoader =
      ValidatorLoader.create(slashingProtector, asyncRunner, metricsSystem);

  @BeforeEach
  void initUpcheckMockResponse() throws IOException, InterruptedException {
    when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
        .thenReturn(upcheckResponse);
  }

  @Test
  void initializeValidatorsWithExternalSignerAndSlashingProtection() throws Exception {
    final InteropConfig interopConfig = InteropConfig.builder().build();
    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorExternalSignerUrl(SIGNER_URL)
            .validatorExternalSignerPublicKeys(Collections.singletonList(PUBLIC_KEY1))
            .validatorExternalSignerSlashingProtectionEnabled(true)
            .build();

    final Map<BLSPublicKey, Validator> validators =
        validatorLoader.initializeValidators(config, interopConfig, () -> httpClient);

    assertThat(validators).hasSize(1);
    final Validator validator = validators.get(PUBLIC_KEY1);
    assertThat(validator).isNotNull();
    assertThat(validator.getPublicKey()).isEqualTo(PUBLIC_KEY1);
    assertThat(validator.getSigner().isLocal()).isFalse();

    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(10);
    final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
    when(slashingProtector.maySignBlock(
            PUBLIC_KEY1, forkInfo.getGenesisValidatorsRoot(), block.getSlot()))
        .thenReturn(SafeFuture.completedFuture(true));
    when(httpClient.sendAsync(any(), any())).thenReturn(new SafeFuture<>());
    final SafeFuture<BLSSignature> result = validator.getSigner().signBlock(block, forkInfo);
    assertThat(result).isNotDone();
    verify(slashingProtector)
        .maySignBlock(PUBLIC_KEY1, forkInfo.getGenesisValidatorsRoot(), block.getSlot());
  }

  @Test
  void initializeValidatorsWithExternalSignerAndNoSlashingProtection() throws Exception {
    final InteropConfig interopConfig = InteropConfig.builder().build();
    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorExternalSignerUrl(SIGNER_URL)
            .validatorExternalSignerPublicKeys(Collections.singletonList(PUBLIC_KEY1))
            .validatorExternalSignerSlashingProtectionEnabled(false)
            .build();

    final Map<BLSPublicKey, Validator> validators =
        validatorLoader.initializeValidators(config, interopConfig, () -> httpClient);

    assertThat(validators).hasSize(1);
    final Validator validator = validators.get(PUBLIC_KEY1);
    assertThat(validator).isNotNull();
    assertThat(validator.getPublicKey()).isEqualTo(PUBLIC_KEY1);
    assertThat(validator.getSigner().isLocal()).isFalse();

    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(10);
    final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
    when(slashingProtector.maySignBlock(
            PUBLIC_KEY1, forkInfo.getGenesisValidatorsRoot(), block.getSlot()))
        .thenReturn(SafeFuture.completedFuture(true));
    when(httpClient.sendAsync(any(), any())).thenReturn(new SafeFuture<>());
    final SafeFuture<BLSSignature> result = validator.getSigner().signBlock(block, forkInfo);
    assertThat(result).isNotDone();
    // Confirm request was sent without checking with the slashing protector
    verifyNoInteractions(slashingProtector);
    verify(httpClient).sendAsync(any(), any());
  }

  @Test
  void initializeValidatorsWithBothLocalAndExternalSigners(@TempDir Path tempDir) throws Exception {
    writeKeystore(tempDir);

    final InteropConfig interopConfig = InteropConfig.builder().build();
    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorExternalSignerUrl(SIGNER_URL)
            .validatorExternalSignerPublicKeys(Collections.singletonList(PUBLIC_KEY2))
            .validatorKeys(
                List.of(
                    tempDir.toAbsolutePath().toString()
                        + File.pathSeparator
                        + tempDir.toAbsolutePath().toString()))
            .build();

    final Map<BLSPublicKey, Validator> validators =
        validatorLoader.initializeValidators(config, interopConfig, () -> httpClient);

    assertThat(validators).hasSize(2);

    final Validator validator1 = validators.get(PUBLIC_KEY1);
    assertThat(validator1).isNotNull();
    assertThat(validator1.getPublicKey()).isEqualTo(PUBLIC_KEY1);
    assertThat(validator1.getSigner().isLocal()).isTrue();

    final Validator validator2 = validators.get(PUBLIC_KEY2);
    assertThat(validator2).isNotNull();
    assertThat(validator2.getPublicKey()).isEqualTo(PUBLIC_KEY2);
    assertThat(validator2.getSigner().isLocal()).isFalse();
  }

  @Test
  void initializeValidatorsWithDuplicateKeysInLocalAndExternalSignersTakesExternalAsPriority(
      @TempDir Path tempDir) throws Exception {
    writeKeystore(tempDir);

    final InteropConfig interopConfig = InteropConfig.builder().build();
    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorExternalSignerUrl(SIGNER_URL)
            .validatorExternalSignerPublicKeys(Collections.singletonList(PUBLIC_KEY1))
            .validatorKeys(
                List.of(
                    tempDir.toAbsolutePath().toString()
                        + File.pathSeparator
                        + tempDir.toAbsolutePath().toString()))
            .build();

    final Map<BLSPublicKey, Validator> validators =
        validatorLoader.initializeValidators(config, interopConfig, () -> httpClient);

    // Both local and external validators get loaded.
    assertThat(validators).hasSize(1);

    // Local validators are listed first
    final Validator validator = validators.get(PUBLIC_KEY1);
    assertThat(validator).isNotNull();
    assertThat(validator.getPublicKey()).isEqualTo(PUBLIC_KEY1);
    assertThat(validator.getSigner().isLocal()).isFalse();
  }

  private void writeKeystore(final Path tempDir) throws Exception {
    final URL resource = Resources.getResource("pbkdf2TestVector.json");
    Files.copy(Path.of(resource.toURI()), tempDir.resolve("key.json"));
    Files.writeString(tempDir.resolve("key.txt"), "testpassword");
  }

  @Test
  void initializeInteropValidatorsWhenInteropIsEnabled() {
    final int ownedValidatorCount = 10;
    final InteropConfig interopConfig =
        InteropConfig.builder()
            .interopEnabled(true)
            .interopOwnedValidatorCount(ownedValidatorCount)
            .build();
    final ValidatorConfig config = ValidatorConfig.builder().build();
    final Map<BLSPublicKey, Validator> validators =
        validatorLoader.initializeValidators(config, interopConfig, () -> httpClient);

    assertThat(validators).hasSize(ownedValidatorCount);
  }

  @Test
  void doNotInitializeInteropValidatorsWhenInteropIsDisabled() {
    final int ownedValidatorCount = 10;
    final InteropConfig interopConfig =
        InteropConfig.builder()
            .interopEnabled(false)
            .interopOwnedValidatorCount(ownedValidatorCount)
            .build();
    final ValidatorConfig config = ValidatorConfig.builder().build();
    final Map<BLSPublicKey, Validator> validators =
        validatorLoader.initializeValidators(config, interopConfig, () -> httpClient);

    assertThat(validators).isEmpty();
  }
}
