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
import static tech.pegasys.teku.validator.client.restapi.ValidatorTypes.PUBKEY_TYPE;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;

public class RuntimeProposerConfig {
  private final Optional<Path> storagePath;
  private static final Logger LOG = LogManager.getLogger();
  private static final DeserializableTypeDefinition<Map.Entry<BLSPublicKey, Eth1Address>> CONFIG_TYPE =
      DeserializableTypeDefinition.<Map.Entry<BLSPublicKey, Eth1Address>, EntryBuilder>object()
          .initializer(EntryBuilder::new)
          .finisher(a->a)
          .name("RuntimeProposerConfig")
          .withField("public_key", PUBKEY_TYPE, Map.Entry::getKey, EntryBuilder::setKey)
          .withField(
              "fee_recipient", ETH1ADDRESS_TYPE, Map.Entry::getValue, EntryBuilder::setValue)
          .build();
  private static final DeserializableTypeDefinition<Collection<Map.Entry<BLSPublicKey, Eth1Address>>> CONFIG_LIST =
      DeserializableTypeDefinition.collectionOf(CONFIG_TYPE, list -> list);
//  private static final DeserializableTypeDefinition<List<Config>> CONFIG_LIST =
//      DeserializableTypeDefinition.listOf(CONFIG_TYPE);
  private final Map<BLSPublicKey, Eth1Address> proposerConfigMap = new ConcurrentHashMap<>();

  public RuntimeProposerConfig(final Optional<Path> storagePath) {
    this.storagePath = storagePath;
    storagePath.ifPresent(this::read);
  }

  public Optional<Eth1Address> getEth1AddressForPubKey(final BLSPublicKey pubKey) {
    return Optional.ofNullable(proposerConfigMap.get(pubKey));
  }

  public synchronized void addOrUpdate(
      final BLSPublicKey publicKey, final Eth1Address eth1Address) {
    proposerConfigMap.put(publicKey, eth1Address);
    storagePath.ifPresent(this::save);
  }

  public void delete(final BLSPublicKey publicKey) {
    proposerConfigMap.remove(publicKey);
    storagePath.ifPresent(this::save);
  }

  private void save(final Path path) {
    try (OutputStream writer = new FileOutputStream(path.toFile(), false)) {
//      final List<Config> config =
//          proposerConfigMap.entrySet().stream()
//              .map((entry) -> new Config(entry.getKey(), entry.getValue()))
//              .collect(Collectors.toList());
      JsonUtil.serializeToBytes(proposerConfigMap.entrySet(), CONFIG_LIST, writer);

    } catch (IOException e) {
      LOG.error("Failed to store runtime key-manager/api_proposer_config file.", e);
    }
  }

  private synchronized void read(final Path path) {
    if (!path.toFile().exists()) {
      return;
    }
    try (InputStream inputStream = new FileInputStream(path.toFile())) {
      final Collection<Map.Entry<BLSPublicKey, Eth1Address>> config = JsonUtil.parse(inputStream, CONFIG_LIST);
      config.forEach(item -> proposerConfigMap.put(item.getKey(), item.getValue()));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to parse key-manager/api_proposer_config file", e);
    }
  }

  static class EntryBuilder implements Map.Entry<BLSPublicKey, Eth1Address> {
    private BLSPublicKey key;
    private Eth1Address value;


    @Override
    public BLSPublicKey getKey() {
      return key;
    }

    @Override
    public Eth1Address getValue() {
      return value;
    }

    @Override
    public Eth1Address setValue(final Eth1Address value) {
      final Eth1Address old = this.value;
      this.value = value;
      return old;
    }

    public void setKey(final BLSPublicKey key) {
      this.key = key;
    }
  }
//  static class Config {
//    private BLSPublicKey publicKey;
//    private Eth1Address feeRecipient;
//
//    public Config() {}
//
//    public Config(final BLSPublicKey publicKey, final Eth1Address feeRecipient) {
//      this.publicKey = publicKey;
//      this.feeRecipient = feeRecipient;
//    }
//
//    public BLSPublicKey getPublicKey() {
//      return publicKey;
//    }
//
//    public void setPublicKey(final BLSPublicKey publicKey) {
//      this.publicKey = publicKey;
//    }
//
//    public Eth1Address getFeeRecipient() {
//      return feeRecipient;
//    }
//
//    public void setFeeRecipient(final Eth1Address feeRecipient) {
//      this.feeRecipient = feeRecipient;
//    }
//  }
}
