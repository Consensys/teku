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

package tech.pegasys.teku.reference.phase0.bls;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.bls.BLSSignature;

/** Hides all the messy type casting required to extract data from a BLS test data.yaml file. */
@SuppressWarnings("unchecked")
public class BlsTestData {

  private final Map<Object, Object> data;

  public BlsTestData(final Map<Object, Object> data) {

    this.data = data;
  }

  public static BlsTestData parse(final Path dataFile) throws IOException {
    try (final InputStream in = Files.newInputStream(dataFile)) {
      final Map<Object, Object> data =
          new ObjectMapper(new YAMLFactory()).readerFor(Map.class).readValue(in);
      return new BlsTestData(data);
    }
  }

  public BLSPublicKey getInputPublicKey() {
    return BLSPublicKey.fromBytes(getInputBytes("pubkey"));
  }

  private Bytes getInputBytes(final String name) {
    return Bytes.fromHexString((String) getInput().get(name));
  }

  private Map<String, Object> getInput() {
    return (Map<String, Object>) data.get("input");
  }

  public Bytes getInputMessage() {
    return getInputBytes("message");
  }

  public BLSSignature getInputSignature() {
    return BLSSignature.fromBytes(
        Bytes.fromHexStringLenient((String) getInput().get("signature"), 96));
  }

  public BLSSecretKey getInputPrivateKey() {
    return BLSSecretKey.fromBytes(getInputBytes("privkey"));
  }

  public boolean getOutputBoolean() {
    return (boolean) data.get("output");
  }

  public List<BLSSignature> getInputSignatures() {
    return ((List<String>) data.get("input"))
        .stream()
            .map(value -> Bytes.fromHexStringLenient(value, 96))
            .map(BLSSignature::fromBytes)
            .collect(Collectors.toList());
  }

  public BLSSignature getOutputSignature() {
    return BLSSignature.fromBytes(Bytes.fromHexString((String) data.get("output")));
  }

  public List<BLSPublicKey> getPairsPublicKeys() {
    return getValuesFromPairs("pubkey").map(BLSPublicKey::fromBytes).collect(Collectors.toList());
  }

  public List<Bytes> getPairsMessages() {
    return getValuesFromPairs("message").collect(Collectors.toList());
  }

  private Stream<Bytes> getValuesFromPairs(final String fieldName) {
    final List<Map<Object, Object>> pairs = (List<Map<Object, Object>>) getInput().get("pairs");
    return pairs.stream().map(pair -> (String) pair.get(fieldName)).map(Bytes::fromHexString);
  }

  public List<BLSPublicKey> getInputPublicKeys() {
    final List<String> values = (List<String>) getInput().get("pubkeys");
    return values.stream()
        .map(Bytes::fromHexString)
        .map(BLSPublicKey::fromBytes)
        .collect(Collectors.toList());
  }
}
