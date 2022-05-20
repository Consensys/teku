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

package tech.pegasys.teku.validator.client;

import static tech.pegasys.teku.spec.datastructures.eth1.Eth1Address.ETH1ADDRESS_TYPE;

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
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringValueTypeDefinition;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;

public class RuntimeProposerConfig {
  private final Optional<Path> storagePath;
  private static final Logger LOG = LogManager.getLogger();

  public static final StringValueTypeDefinition<BLSPublicKey> PUBKEY_TYPE =
      DeserializableTypeDefinition.string(BLSPublicKey.class)
          .formatter(BLSPublicKey::toString)
          .parser(BLSPublicKey::fromHexString)
          .format("byte")
          .build();
  private static final DeserializableTypeDefinition<Config> CONFIG_TYPE =
      DeserializableTypeDefinition.object(Config.class)
          .initializer(Config::new)
          .name("RuntimeProposerConfig")
          .withField(
              "fee_recipient", ETH1ADDRESS_TYPE, Config::getFeeRecipient, Config::setFeeRecipient)
          .build();
  private static final DeserializableTypeDefinition<Map<BLSPublicKey, Config>> CONFIG_MAP_TYPE =
      DeserializableTypeDefinition.mapOf(PUBKEY_TYPE, CONFIG_TYPE, ConcurrentHashMap::new);

  private final Map<BLSPublicKey, Config> proposerConfigMap = new ConcurrentHashMap<>();

  public RuntimeProposerConfig(final Optional<Path> storagePath) {
    this.storagePath = storagePath;
    storagePath.ifPresent(this::read);
  }

  public Optional<Eth1Address> getEth1AddressForPubKey(final BLSPublicKey pubKey) {
    final Config config = proposerConfigMap.get(pubKey);
    if (config == null) {
      return Optional.empty();
    }
    return Optional.of(config.getFeeRecipient());
  }

  public synchronized void addOrUpdate(
      final BLSPublicKey publicKey, final Eth1Address eth1Address) {
    proposerConfigMap.put(publicKey, new Config(eth1Address));
    storagePath.ifPresent(this::save);
  }

  public void delete(final BLSPublicKey publicKey) {
    proposerConfigMap.remove(publicKey);
    storagePath.ifPresent(this::save);
  }

  private void save(final Path path) {
    try (OutputStream writer = new FileOutputStream(path.toFile(), false)) {
      JsonUtil.serializeToBytes(proposerConfigMap, CONFIG_MAP_TYPE, writer);
    } catch (IOException e) {
      LOG.error("Failed to store file: " + path.toAbsolutePath(), e);
    }
  }

  private synchronized void read(final Path path) {
    if (!path.toFile().exists()) {
      return;
    }
    if (!proposerConfigMap.isEmpty()) {
      proposerConfigMap.clear();
    }
    try (InputStream inputStream = new FileInputStream(path.toFile())) {
      proposerConfigMap.putAll(JsonUtil.parse(inputStream, CONFIG_MAP_TYPE));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to parse file: " + path.toAbsolutePath(), e);
    }
  }

  static class Config {
    private Eth1Address feeRecipient;

    public Config() {}

    public Config(final Eth1Address feeRecipient) {
      this.feeRecipient = feeRecipient;
    }

    public Eth1Address getFeeRecipient() {
      return feeRecipient;
    }

    public void setFeeRecipient(final Eth1Address feeRecipient) {
      this.feeRecipient = feeRecipient;
    }
  }
}
