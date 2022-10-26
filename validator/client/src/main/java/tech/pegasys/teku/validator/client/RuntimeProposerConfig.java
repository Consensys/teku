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

package tech.pegasys.teku.validator.client;

import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH1ADDRESS_TYPE;

import com.google.common.base.Preconditions;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringValueTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.ProposerConfig.BuilderConfig;

public class RuntimeProposerConfig {
  private final Optional<Path> storagePath;
  private static final Logger LOG = LogManager.getLogger();

  private static final StringValueTypeDefinition<BLSPublicKey> PUBKEY_TYPE =
      DeserializableTypeDefinition.string(BLSPublicKey.class)
          .formatter(BLSPublicKey::toString)
          .parser(BLSPublicKey::fromHexString)
          .format("byte")
          .build();
  private static final DeserializableTypeDefinition<RuntimeConfig> CONFIG_TYPE =
      DeserializableTypeDefinition.object(RuntimeConfig.class, RuntimeConfigBuilder.class)
          .initializer(RuntimeConfigBuilder::new)
          .finisher(RuntimeConfigBuilder::build)
          .name("RuntimeProposerConfig")
          .withOptionalField(
              "fee_recipient",
              ETH1ADDRESS_TYPE,
              RuntimeConfig::getFeeRecipient,
              RuntimeConfigBuilder::feeRecipient)
          .withOptionalField(
              "gas_limit",
              CoreTypes.UINT64_TYPE,
              RuntimeConfig::getBuilderGasLimit,
              RuntimeConfigBuilder::gasLimit)
          .build();
  private static final DeserializableTypeDefinition<Map<BLSPublicKey, RuntimeConfig>>
      CONFIG_MAP_TYPE =
          DeserializableTypeDefinition.mapOf(PUBKEY_TYPE, CONFIG_TYPE, ConcurrentHashMap::new);

  private final Map<BLSPublicKey, RuntimeConfig> proposerConfigMap = new ConcurrentHashMap<>();

  public RuntimeProposerConfig(final Optional<Path> storagePath) {
    this.storagePath = storagePath;
    storagePath.ifPresent(
        path -> {
          if (path.toFile().exists()) {
            try (InputStream inputStream = new FileInputStream(path.toFile())) {
              proposerConfigMap.putAll(JsonUtil.parse(inputStream, CONFIG_MAP_TYPE));
            } catch (IOException e) {
              throw new IllegalStateException("Failed to parse file: " + path.toAbsolutePath(), e);
            }
          }
        });
  }

  public Optional<Eth1Address> getEth1AddressForPubKey(final BLSPublicKey publicKey) {
    return getProposerConfig(publicKey).flatMap(RuntimeConfig::getFeeRecipient);
  }

  public Optional<UInt64> getGasLimitForPubKey(final BLSPublicKey publicKey) {
    return getProposerConfig(publicKey).flatMap(RuntimeConfig::getBuilderGasLimit);
  }

  synchronized void updateFeeRecipient(
      final BLSPublicKey publicKey, final Eth1Address eth1Address) {
    Preconditions.checkNotNull(eth1Address, "should delete rather than update to null");
    final Optional<RuntimeConfig> currentConfig = getProposerConfig(publicKey);
    if (currentConfig.isEmpty()) {
      proposerConfigMap.put(
          publicKey, new RuntimeConfigBuilder().feeRecipient(Optional.of(eth1Address)).build());
    } else {
      RuntimeConfigBuilder configBuilder = new RuntimeConfigBuilder(currentConfig.get());
      configBuilder.feeRecipient(Optional.of(eth1Address));
      updateEntry(publicKey, configBuilder.build());
    }
    storagePath.ifPresent(this::save);
  }

  synchronized void updateGasLimit(final BLSPublicKey publicKey, final UInt64 gasLimit) {
    Preconditions.checkNotNull(gasLimit, "should delete rather than update to null");
    final Optional<RuntimeConfig> currentConfig = getProposerConfig(publicKey);
    if (currentConfig.isEmpty()) {
      proposerConfigMap.put(
          publicKey, new RuntimeConfigBuilder().gasLimit(Optional.of(gasLimit)).build());
    } else {
      RuntimeConfigBuilder configBuilder = new RuntimeConfigBuilder(currentConfig.get());
      configBuilder.gasLimit(Optional.of(gasLimit));
      updateEntry(publicKey, configBuilder.build());
    }
    storagePath.ifPresent(this::save);
  }

  private synchronized void updateEntry(final BLSPublicKey publicKey, final RuntimeConfig config) {
    if (config.isEmpty()) {
      proposerConfigMap.remove(publicKey);
    } else {
      proposerConfigMap.put(publicKey, config);
    }
  }

  synchronized void deleteFeeRecipient(final BLSPublicKey publicKey) {
    final Optional<RuntimeConfig> currentConfig = getProposerConfig(publicKey);
    if (currentConfig.isPresent()) {
      RuntimeConfigBuilder builder = new RuntimeConfigBuilder(currentConfig.get());
      builder.feeRecipient(Optional.empty());
      updateEntry(publicKey, builder.build());
      storagePath.ifPresent(this::save);
    }
  }

  synchronized void deleteGasLimit(final BLSPublicKey publicKey) {
    final Optional<RuntimeConfig> currentConfig = getProposerConfig(publicKey);
    if (currentConfig.isPresent()) {
      RuntimeConfigBuilder builder = new RuntimeConfigBuilder(currentConfig.get());
      builder.gasLimit(Optional.empty());
      updateEntry(publicKey, builder.build());
      storagePath.ifPresent(this::save);
    }
  }

  public Optional<RuntimeConfig> getProposerConfig(final BLSPublicKey publicKey) {
    return Optional.ofNullable(proposerConfigMap.get(publicKey));
  }

  private void save(final Path path) {
    try (OutputStream writer = new FileOutputStream(path.toFile(), false)) {
      JsonUtil.serializeToBytes(proposerConfigMap, CONFIG_MAP_TYPE, writer);
    } catch (IOException e) {
      LOG.error("Failed to store file: " + path.toAbsolutePath(), e);
    }
  }

  static class RuntimeConfig extends ProposerConfig.Config {

    public RuntimeConfig(final Eth1Address feeRecipient, final BuilderConfig builder) {
      super(feeRecipient, builder);
    }

    public boolean isEmpty() {
      return getFeeRecipient().isEmpty() && getBuilderGasLimit().isEmpty();
    }
  }

  static class RuntimeConfigBuilder {
    private Optional<Eth1Address> feeRecipient = Optional.empty();
    private Optional<UInt64> gasLimit = Optional.empty();

    public RuntimeConfigBuilder() {}

    public RuntimeConfigBuilder(final RuntimeConfig currentConfig) {
      feeRecipient = currentConfig.getFeeRecipient();
      gasLimit = currentConfig.getBuilderGasLimit();
    }

    public RuntimeConfigBuilder feeRecipient(final Optional<Eth1Address> feeRecipient) {
      this.feeRecipient = feeRecipient;
      return this;
    }

    public RuntimeConfigBuilder gasLimit(final Optional<UInt64> gasLimit) {
      this.gasLimit = gasLimit;
      return this;
    }

    public RuntimeConfig build() {
      return new RuntimeConfig(
          feeRecipient.orElse(null),
          gasLimit.map(gl -> new ProposerConfig.BuilderConfig(null, gl, null)).orElse(null));
    }
  }
}
