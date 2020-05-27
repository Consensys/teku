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

package tech.pegasys.teku.validator.client.loader;

import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.util.config.TekuConfiguration;

public class YamlValidatorKeyProvider implements ValidatorKeyProvider {

  private static final Logger LOG = LogManager.getLogger();
  private static final int KEY_LENGTH = 48;

  @SuppressWarnings("unchecked")
  @Override
  public List<BLSKeyPair> loadValidatorKeys(final TekuConfiguration config) {
    final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    final Path keyFile = Path.of(config.getValidatorsKeyFile());
    LOG.log(Level.DEBUG, "Loading validator keys from " + keyFile.toAbsolutePath().toString());
    try (final InputStream in = Files.newInputStream(keyFile)) {
      final List<Map<String, String>> values = mapper.readValue(in, new TypeReference<>() {});
      return values.stream()
          .map(
              value -> {
                final String privKey = value.get("privkey");
                if (privKey == null) {
                  throw new IllegalArgumentException(
                      "Invalid private key supplied.  Please check your validator keys configuration file");
                }
                return new BLSKeyPair(
                    BLSSecretKey.fromBytes(padLeft(Bytes.fromHexString(privKey))));
              })
          .collect(toList());
    } catch (final JsonMappingException e) {
      throw new RuntimeException("Error while reading validator keys file values", e);
    } catch (final IOException e) {
      throw new RuntimeException("Failed to load validator key file", e);
    }
  }

  private Bytes padLeft(Bytes input) {
    return Bytes.concatenate(Bytes.wrap(new byte[KEY_LENGTH - input.size()]), input);
  }
}
