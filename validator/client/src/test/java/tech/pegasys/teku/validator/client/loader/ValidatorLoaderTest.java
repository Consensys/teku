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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.signatures.DeletableSigner;
import tech.pegasys.teku.core.signatures.SlashingProtector;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.service.serviceutils.layout.SeparateServiceDataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.ValidatorClientService;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeletionStatus;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ImportStatus;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;

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

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final InteropConfig disabledInteropConfig =
      InteropConfig.builder().specProvider(spec).build();

  private final SlashingProtector slashingProtector = mock(SlashingProtector.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final HttpClient httpClient = mock(HttpClient.class);
  private final MetricsSystem metricsSystem = new StubMetricsSystem();
  private final PublicKeyLoader publicKeyLoader = new PublicKeyLoader();

  @SuppressWarnings("unchecked")
  private final HttpResponse<Void> upcheckResponse = mock(HttpResponse.class);

  private final Supplier<HttpClient> httpClientFactory = () -> httpClient;

  @BeforeEach
  void initUpcheckMockResponse() throws IOException, InterruptedException {
    when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
        .thenReturn(upcheckResponse);
  }

  @Test
  void shouldLoadPublicKeysFromUrls() {
    final PublicKeyLoader publicKeyLoader = mock(PublicKeyLoader.class);
    final List<BLSPublicKey> expectedKeys = List.of(PUBLIC_KEY1, PUBLIC_KEY2);
    final String publicKeysUrl = "http://example.com";
    when(publicKeyLoader.getPublicKeys(List.of(publicKeysUrl))).thenReturn(expectedKeys);

    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorExternalSignerUrl(SIGNER_URL)
            .validatorExternalSignerPublicKeySources(Collections.singletonList(publicKeysUrl))
            .validatorExternalSignerSlashingProtectionEnabled(true)
            .build();

    final ValidatorLoader validatorLoader =
        ValidatorLoader.create(
            spec,
            config,
            disabledInteropConfig,
            httpClientFactory,
            slashingProtector,
            publicKeyLoader,
            asyncRunner,
            metricsSystem,
            Optional.empty());

    validatorLoader.loadValidators();
    final OwnedValidators validators = validatorLoader.getOwnedValidators();

    assertThat(validators.getValidatorCount()).isEqualTo(2);

    final Validator validator1 = validators.getValidator(PUBLIC_KEY1).orElseThrow();
    assertThat(validator1).isNotNull();
    assertThat(validator1.getPublicKey()).isEqualTo(PUBLIC_KEY1);
    assertThat(validator1.getSigner().isLocal()).isFalse();

    final Validator validator2 = validators.getValidator(PUBLIC_KEY2).orElseThrow();
    assertThat(validator2).isNotNull();
    assertThat(validator2.getPublicKey()).isEqualTo(PUBLIC_KEY2);
    assertThat(validator2.getSigner().isLocal()).isFalse();
  }

  @Test
  void initializeValidatorsWithExternalSignerAndSlashingProtection() {
    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorExternalSignerUrl(SIGNER_URL)
            .validatorExternalSignerPublicKeySources(
                Collections.singletonList(PUBLIC_KEY1.toString()))
            .validatorExternalSignerSlashingProtectionEnabled(true)
            .build();
    final ValidatorLoader validatorLoader =
        ValidatorLoader.create(
            spec,
            config,
            disabledInteropConfig,
            httpClientFactory,
            slashingProtector,
            publicKeyLoader,
            asyncRunner,
            metricsSystem,
            Optional.empty());

    validatorLoader.loadValidators();
    final OwnedValidators validators = validatorLoader.getOwnedValidators();

    assertThat(validators.getValidatorCount()).isEqualTo(1);
    final Validator validator = validators.getValidator(PUBLIC_KEY1).orElseThrow();
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
  void initializeValidatorsWithExternalSignerAndNoSlashingProtection() {
    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorExternalSignerUrl(SIGNER_URL)
            .validatorExternalSignerPublicKeySources(
                Collections.singletonList(PUBLIC_KEY1.toString()))
            .validatorExternalSignerSlashingProtectionEnabled(false)
            .build();
    final ValidatorLoader validatorLoader =
        ValidatorLoader.create(
            spec,
            config,
            disabledInteropConfig,
            httpClientFactory,
            slashingProtector,
            publicKeyLoader,
            asyncRunner,
            metricsSystem,
            Optional.empty());

    validatorLoader.loadValidators();
    final OwnedValidators validators = validatorLoader.getOwnedValidators();

    assertThat(validators.getValidatorCount()).isEqualTo(1);
    final Validator validator = validators.getValidator(PUBLIC_KEY1).orElseThrow();
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
    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorExternalSignerUrl(SIGNER_URL)
            .validatorExternalSignerPublicKeySources(
                Collections.singletonList(PUBLIC_KEY2.toString()))
            .validatorKeys(
                List.of(tempDir.toAbsolutePath() + File.pathSeparator + tempDir.toAbsolutePath()))
            .build();
    final ValidatorLoader validatorLoader =
        ValidatorLoader.create(
            spec,
            config,
            disabledInteropConfig,
            httpClientFactory,
            slashingProtector,
            publicKeyLoader,
            asyncRunner,
            metricsSystem,
            Optional.empty());

    validatorLoader.loadValidators();
    final OwnedValidators validators = validatorLoader.getOwnedValidators();

    assertThat(validators.getValidatorCount()).isEqualTo(2);

    final Validator validator1 = validators.getValidator(PUBLIC_KEY1).orElseThrow();
    assertThat(validator1).isNotNull();
    assertThat(validator1.getPublicKey()).isEqualTo(PUBLIC_KEY1);
    assertThat(validator1.getSigner().isLocal()).isTrue();

    final Validator validator2 = validators.getValidator(PUBLIC_KEY2).orElseThrow();
    assertThat(validator2).isNotNull();
    assertThat(validator2.getPublicKey()).isEqualTo(PUBLIC_KEY2);
    assertThat(validator2.getSigner().isLocal()).isFalse();
  }

  @Test
  void shouldInitializeLocalAndMutableValidators(
      @TempDir Path tempDir, @TempDir Path tempDirMutable) throws Exception {
    final BLSPublicKey mutableValidatorPubKey =
        BLSPublicKey.fromSSZBytes(
            Bytes.fromHexString(
                "0xa057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a"));

    final DataDirLayout dataDirLayout =
        new SeparateServiceDataDirLayout(tempDirMutable, Optional.empty(), Optional.empty());
    writeKeystore(tempDir);
    writeMutableKeystore(dataDirLayout);
    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorKeys(
                List.of(tempDir.toAbsolutePath() + File.pathSeparator + tempDir.toAbsolutePath()))
            .build();
    final ValidatorLoader validatorLoader =
        ValidatorLoader.create(
            spec,
            config,
            disabledInteropConfig,
            httpClientFactory,
            slashingProtector,
            publicKeyLoader,
            asyncRunner,
            metricsSystem,
            Optional.of(dataDirLayout));

    validatorLoader.loadValidators();
    final OwnedValidators validators = validatorLoader.getOwnedValidators();

    assertThat(validators.getValidatorCount()).isEqualTo(2);

    final Validator validator1 = validators.getValidator(PUBLIC_KEY1).orElseThrow();
    assertThat(validator1).isNotNull();
    assertThat(validator1.getPublicKey()).isEqualTo(PUBLIC_KEY1);
    assertThat(validator1.isReadOnly()).isTrue();

    final Validator validator2 = validators.getValidator(mutableValidatorPubKey).orElseThrow();
    assertThat(validator2).isNotNull();
    assertThat(validator2.getPublicKey()).isEqualTo(mutableValidatorPubKey);
    assertThat(validator2.isReadOnly()).isFalse();
  }

  @Test
  void shouldReturnErrorIfDeleteOnReadOnlySource(@TempDir Path tempDir) throws Exception {
    writeKeystore(tempDir);

    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorKeys(
                List.of(tempDir.toAbsolutePath() + File.pathSeparator + tempDir.toAbsolutePath()))
            .build();
    final ValidatorLoader validatorLoader =
        ValidatorLoader.create(
            spec,
            config,
            disabledInteropConfig,
            httpClientFactory,
            slashingProtector,
            publicKeyLoader,
            asyncRunner,
            metricsSystem,
            Optional.empty());

    final DeleteKeyResult result =
        validatorLoader.deleteMutableValidator(dataStructureUtil.randomPublicKey());
    assertThat(result.getStatus()).isEqualTo(DeletionStatus.ERROR);
    assertThat(result.getMessage().orElse("")).contains("Unable to delete validator");
  }

  @Test
  void shouldInitializeOnlyLocalValidatorsWhenRestDisabled(
      @TempDir Path tempDir, @TempDir Path tempDirMutable) throws Exception {
    final DataDirLayout dataDirLayout =
        new SeparateServiceDataDirLayout(tempDirMutable, Optional.empty(), Optional.empty());
    writeKeystore(tempDir);
    writeMutableKeystore(dataDirLayout);
    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorKeys(
                List.of(tempDir.toAbsolutePath() + File.pathSeparator + tempDir.toAbsolutePath()))
            .build();
    final ValidatorLoader validatorLoader =
        ValidatorLoader.create(
            spec,
            config,
            disabledInteropConfig,
            httpClientFactory,
            slashingProtector,
            publicKeyLoader,
            asyncRunner,
            metricsSystem,
            Optional.empty());

    validatorLoader.loadValidators();
    final OwnedValidators validators = validatorLoader.getOwnedValidators();

    assertThat(validators.getValidatorCount()).isEqualTo(1);

    final Validator validator1 = validators.getValidator(PUBLIC_KEY1).orElseThrow();
    assertThat(validator1).isNotNull();
    assertThat(validator1.getPublicKey()).isEqualTo(PUBLIC_KEY1);
    assertThat(validator1.isReadOnly()).isTrue();
  }

  @Test
  void shouldCallMutableValidatorSourceToDelete(@TempDir final Path tempDir) {
    final ValidatorSource validatorSource = mock(ValidatorSource.class);
    final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    final DataDirLayout dataDirLayout =
        new SeparateServiceDataDirLayout(tempDir, Optional.empty(), Optional.empty());
    ValidatorLoader loader =
        ValidatorLoader.create(
            List.of(validatorSource),
            Optional.of(validatorSource),
            null,
            Optional.of(dataDirLayout));

    when(validatorSource.deleteValidator(publicKey)).thenReturn(DeleteKeyResult.success());
    loader.deleteMutableValidator(publicKey);
    verify(validatorSource).deleteValidator(publicKey);
  }

  @Test
  void shouldNotInitializeMutableValidatorsWithoutDirectoryStructure(
      @TempDir Path tempDir, @TempDir Path tempDirMutable) throws Exception {
    final DataDirLayout dataDirLayout =
        new SeparateServiceDataDirLayout(tempDirMutable, Optional.empty(), Optional.empty());
    writeKeystore(tempDir);

    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorKeys(
                List.of(tempDir.toAbsolutePath() + File.pathSeparator + tempDir.toAbsolutePath()))
            .build();
    final ValidatorLoader validatorLoader =
        ValidatorLoader.create(
            spec,
            config,
            disabledInteropConfig,
            httpClientFactory,
            slashingProtector,
            publicKeyLoader,
            asyncRunner,
            metricsSystem,
            Optional.of(dataDirLayout));

    validatorLoader.loadValidators();
    final OwnedValidators validators = validatorLoader.getOwnedValidators();

    assertThat(validators.getValidatorCount()).isEqualTo(1);

    final Validator validator1 = validators.getValidator(PUBLIC_KEY1).orElseThrow();
    assertThat(validator1).isNotNull();
    assertThat(validator1.getPublicKey()).isEqualTo(PUBLIC_KEY1);
    assertThat(validator1.isReadOnly()).isTrue();
  }

  @Test
  void initializeValidatorsWithDuplicateKeysInLocalAndExternalSignersTakesExternalAsPriority(
      @TempDir Path tempDir) throws Exception {
    writeKeystore(tempDir);
    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorExternalSignerUrl(SIGNER_URL)
            .validatorExternalSignerPublicKeySources(
                Collections.singletonList(PUBLIC_KEY1.toString()))
            .validatorKeys(
                List.of(tempDir.toAbsolutePath() + File.pathSeparator + tempDir.toAbsolutePath()))
            .build();
    final ValidatorLoader validatorLoader =
        ValidatorLoader.create(
            spec,
            config,
            disabledInteropConfig,
            httpClientFactory,
            slashingProtector,
            publicKeyLoader,
            asyncRunner,
            metricsSystem,
            Optional.empty());

    validatorLoader.loadValidators();
    final OwnedValidators validators = validatorLoader.getOwnedValidators();

    // Both local and external validators get loaded.
    assertThat(validators.getValidatorCount()).isEqualTo(1);

    // Local validators are listed first
    final Validator validator = validators.getValidator(PUBLIC_KEY1).orElseThrow();
    assertThat(validator).isNotNull();
    assertThat(validator.getPublicKey()).isEqualTo(PUBLIC_KEY1);
    assertThat(validator.getSigner().isLocal()).isFalse();
  }

  @Test
  void shouldEnableSlashingProtectionForLocalValidators(@TempDir Path tempDir) throws Exception {
    writeKeystore(tempDir);

    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorKeys(
                List.of(tempDir.toAbsolutePath() + File.pathSeparator + tempDir.toAbsolutePath()))
            .build();
    final ValidatorLoader validatorLoader =
        ValidatorLoader.create(
            spec,
            config,
            disabledInteropConfig,
            httpClientFactory,
            slashingProtector,
            publicKeyLoader,
            asyncRunner,
            metricsSystem,
            Optional.empty());

    validatorLoader.loadValidators();
    final OwnedValidators validators = validatorLoader.getOwnedValidators();

    assertThat(validators.getValidatorCount()).isEqualTo(1);

    final Validator validator = validators.getValidator(PUBLIC_KEY1).orElseThrow();
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(1);
    final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
    when(slashingProtector.maySignBlock(any(), any(), any())).thenReturn(new SafeFuture<>());
    assertThat(validator.getSigner().signBlock(block, forkInfo)).isNotDone();
    verify(slashingProtector)
        .maySignBlock(
            validator.getPublicKey(), forkInfo.getGenesisValidatorsRoot(), block.getSlot());
  }

  @Test
  void shouldLoadAdditionalExternalValidatorsOnReload() {
    final PublicKeyLoader publicKeyLoader = mock(PublicKeyLoader.class);
    final List<BLSPublicKey> initialKeys = List.of(PUBLIC_KEY1);
    final String publicKeysUrl = "http://example.com";
    when(publicKeyLoader.getPublicKeys(List.of(publicKeysUrl))).thenReturn(initialKeys);

    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorExternalSignerUrl(SIGNER_URL)
            .validatorExternalSignerPublicKeySources(Collections.singletonList(publicKeysUrl))
            .validatorExternalSignerSlashingProtectionEnabled(true)
            .build();
    final ValidatorLoader validatorLoader =
        ValidatorLoader.create(
            spec,
            config,
            disabledInteropConfig,
            httpClientFactory,
            slashingProtector,
            publicKeyLoader,
            asyncRunner,
            metricsSystem,
            Optional.empty());

    validatorLoader.loadValidators();
    final OwnedValidators validators = validatorLoader.getOwnedValidators();
    assertThat(validators.getPublicKeys()).containsOnly(PUBLIC_KEY1);

    final List<BLSPublicKey> reconfiguredKeys = List.of(PUBLIC_KEY1, PUBLIC_KEY2);
    when(publicKeyLoader.getPublicKeys(List.of(publicKeysUrl))).thenReturn(reconfiguredKeys);

    validatorLoader.loadValidators();
    assertThat(validators.getPublicKeys()).containsExactlyInAnyOrder(PUBLIC_KEY1, PUBLIC_KEY2);
  }

  @Test
  void shouldNotRemoveExternalValidatorsOnReload() {
    final PublicKeyLoader publicKeyLoader = mock(PublicKeyLoader.class);
    final List<BLSPublicKey> initialKeys = List.of(PUBLIC_KEY1);
    final String publicKeysUrl = "http://example.com";
    when(publicKeyLoader.getPublicKeys(List.of(publicKeysUrl))).thenReturn(initialKeys);

    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorExternalSignerUrl(SIGNER_URL)
            .validatorExternalSignerPublicKeySources(Collections.singletonList(publicKeysUrl))
            .validatorExternalSignerSlashingProtectionEnabled(true)
            .build();
    final ValidatorLoader validatorLoader =
        ValidatorLoader.create(
            spec,
            config,
            disabledInteropConfig,
            httpClientFactory,
            slashingProtector,
            publicKeyLoader,
            asyncRunner,
            metricsSystem,
            Optional.empty());

    validatorLoader.loadValidators();
    final OwnedValidators validators = validatorLoader.getOwnedValidators();
    assertThat(validators.getPublicKeys()).containsOnly(PUBLIC_KEY1);

    final List<BLSPublicKey> reconfiguredKeys = List.of(PUBLIC_KEY2);
    when(publicKeyLoader.getPublicKeys(List.of(publicKeysUrl))).thenReturn(reconfiguredKeys);

    validatorLoader.loadValidators();
    assertThat(validators.getPublicKeys()).containsExactlyInAnyOrder(PUBLIC_KEY1, PUBLIC_KEY2);
  }

  @Test
  void shouldLoadAdditionalLocalValidatorsOnReload(final @TempDir Path tempDir) throws Exception {
    final ValidatorConfig config =
        ValidatorConfig.builder()
            .validatorKeys(
                List.of(tempDir.toAbsolutePath() + File.pathSeparator + tempDir.toAbsolutePath()))
            .build();

    final ValidatorLoader validatorLoader =
        ValidatorLoader.create(
            spec,
            config,
            disabledInteropConfig,
            httpClientFactory,
            slashingProtector,
            publicKeyLoader,
            asyncRunner,
            metricsSystem,
            Optional.empty());

    // No validators initially
    validatorLoader.loadValidators();
    final OwnedValidators validators = validatorLoader.getOwnedValidators();
    assertThat(validators.getPublicKeys()).isEmpty();

    // Then we add one and reload
    writeKeystore(tempDir);
    validatorLoader.loadValidators();

    assertThat(validators.getPublicKeys()).containsExactlyInAnyOrder(PUBLIC_KEY1);
  }

  @Test
  void initializeInteropValidatorsWhenInteropIsEnabled() {
    final int ownedValidatorCount = 10;
    final InteropConfig interopConfig =
        InteropConfig.builder()
            .specProvider(spec)
            .interopEnabled(true)
            .interopOwnedValidatorCount(ownedValidatorCount)
            .build();
    final ValidatorConfig config = ValidatorConfig.builder().build();
    final ValidatorLoader validatorLoader =
        ValidatorLoader.create(
            spec,
            config,
            interopConfig,
            httpClientFactory,
            slashingProtector,
            publicKeyLoader,
            asyncRunner,
            metricsSystem,
            Optional.empty());
    validatorLoader.loadValidators();
    final OwnedValidators validators = validatorLoader.getOwnedValidators();

    assertThat(validators.getValidatorCount()).isEqualTo(ownedValidatorCount);
  }

  @Test
  void shouldNotLoadMutableValidatorIfNotEnabled() {
    final ValidatorConfig config = ValidatorConfig.builder().build();
    final ValidatorLoader validatorLoader =
        ValidatorLoader.create(
            spec,
            config,
            disabledInteropConfig,
            httpClientFactory,
            slashingProtector,
            publicKeyLoader,
            asyncRunner,
            metricsSystem,
            Optional.empty());
    validatorLoader.loadValidators();
    final PostKeyResult result = validatorLoader.loadMutableValidator(null, "", Optional.empty());
    assertThat(result).isEqualTo(PostKeyResult.error("Not able to add validator"));
  }

  @Test
  void shouldLoadMutableValidatorIfEnabled(@TempDir final Path tempDir) throws Exception {
    final ValidatorConfig config = ValidatorConfig.builder().build();
    final ValidatorLoader validatorLoader =
        ValidatorLoader.create(
            spec,
            config,
            disabledInteropConfig,
            httpClientFactory,
            slashingProtector,
            publicKeyLoader,
            asyncRunner,
            metricsSystem,
            Optional.of(getDataDirLayout(tempDir)));
    validatorLoader.loadValidators();

    final String keystoreString =
        Resources.toString(Resources.getResource("pbkdf2TestVector.json"), StandardCharsets.UTF_8);
    PostKeyResult result =
        validatorLoader.loadMutableValidator(
            KeyStoreLoader.loadFromString(keystoreString), "testpassword", Optional.empty());
    assertThat(result.getImportStatus()).isEqualTo(ImportStatus.IMPORTED);

    final Optional<Validator> validator =
        validatorLoader.getOwnedValidators().getValidator(PUBLIC_KEY1);
    assertThat(validator).isPresent();
    assertThat(validator.orElseThrow().getSigner()).isInstanceOf(DeletableSigner.class);
  }

  @Test
  void doNotInitializeInteropValidatorsWhenInteropIsDisabled() {
    final int ownedValidatorCount = 10;
    final InteropConfig interopConfig =
        InteropConfig.builder()
            .specProvider(spec)
            .interopEnabled(false)
            .interopOwnedValidatorCount(ownedValidatorCount)
            .build();
    final ValidatorConfig config = ValidatorConfig.builder().build();
    final ValidatorLoader validatorLoader =
        ValidatorLoader.create(
            spec,
            config,
            interopConfig,
            httpClientFactory,
            slashingProtector,
            publicKeyLoader,
            asyncRunner,
            metricsSystem,
            Optional.empty());
    validatorLoader.loadValidators();
    final OwnedValidators validators = validatorLoader.getOwnedValidators();

    assertThat(validators.hasNoValidators()).isTrue();
  }

  private void writeKeystore(final Path tempDir) throws Exception {
    final URL resource = Resources.getResource("pbkdf2TestVector.json");
    Files.copy(Path.of(resource.toURI()), tempDir.resolve("key.json"));
    Files.writeString(tempDir.resolve("key.txt"), "testpassword");
  }

  private void writeMutableKeystore(final DataDirLayout tempDir) throws Exception {
    final URL resource = Resources.getResource("testKeystore.json");
    final Path keystore = ValidatorClientService.getAlterableKeystorePath(tempDir);
    final Path keystorePassword = ValidatorClientService.getAlterableKeystorePasswordPath(tempDir);
    Files.createDirectory(tempDir.getValidatorDataDirectory());
    Files.createDirectory(keystore);
    Files.createDirectory(keystorePassword);
    Files.copy(Path.of(resource.toURI()), keystore.resolve("key.json"));
    Files.writeString(keystorePassword.resolve("key.txt"), "testpassword");
  }

  private DataDirLayout getDataDirLayout(final Path tempDir) {
    return new DataDirLayout() {
      @Override
      public Path getBeaconDataDirectory() {
        return tempDir;
      }

      @Override
      public Path getValidatorDataDirectory() {
        return tempDir;
      }
    };
  }
}
