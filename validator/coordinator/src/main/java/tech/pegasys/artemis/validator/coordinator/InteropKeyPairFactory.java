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

import static java.util.Collections.emptyList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.util.mikuli.SecretKey;

public class InteropKeyPairFactory implements ValidatorKeyPairFactory {
  private static final ALogger STDOUT = new ALogger("stdout");

  private final ArtemisConfiguration config;

  public InteropKeyPairFactory(final ArtemisConfiguration config) {
    this.config = config;
  }

  @Override
  public List<BLSKeyPair> generateKeyPairs(final int startIndex, final int endIndex) {
    try {
      JSONParser parser = new JSONParser();
      Object obj = parser.parse(Files.newBufferedReader(Paths.get(config.getInteropInputFile())));
      JSONObject array = (JSONObject) obj;
      JSONArray privateKeyStrings = (JSONArray) array.get("privateKeys");
      return IntStream.rangeClosed(startIndex, endIndex)
          .mapToObj(
              i ->
                  new BLSKeyPair(
                      new KeyPair(
                          SecretKey.fromBytes(
                              Bytes.fromHexString(privateKeyStrings.get(i).toString())))))
          .collect(Collectors.toList());
    } catch (IOException | ParseException e) {
      STDOUT.log(Level.FATAL, e.toString());
      return emptyList();
    }
  }
}
