/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.validator.coordinator;

import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.logging.StatusLogger.STATUS_LOG;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.util.mikuli.SecretKey;
import tech.pegasys.teku.logging.StatusLogger.Color;

public class YamlValidatorKeyProvider implements ValidatorKeyProvider {
  private static final int KEY_LENGTH = 48;

  @SuppressWarnings("unchecked")
  @Override
  public List<BLSKeyPair> loadValidatorKeys(final ArtemisConfiguration config) {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    final Path keyFile = Path.of(config.getValidatorsKeyFile());
    STATUS_LOG.log(
        Level.DEBUG,
        "Loading validator keys from " + keyFile.toAbsolutePath().toString(),
        Color.GREEN);
    try (InputStream in = Files.newInputStream(keyFile)) {
      final List<Object> values = mapper.readerFor(Map.class).readValues(in).readAll();
      return values.stream()
          .map(
              value -> {
                Map<String, String> keys = (Map<String, String>) value;
                final String privKey = keys.get("privkey");
                return new BLSKeyPair(
                    new KeyPair(SecretKey.fromBytes(padLeft(Bytes.fromHexString(privKey)))));
              })
          .collect(toList());
    } catch (final IOException e) {
      throw new RuntimeException("Failed to load validator key file", e);
    }
  }

  private Bytes padLeft(Bytes input) {
    return Bytes.concatenate(Bytes.wrap(new byte[KEY_LENGTH - input.size()]), input);
  }
}
