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

package pegasys.artemis.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kotlin.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.io.Resources;
import org.junit.jupiter.params.provider.Arguments;
import tech.pegasys.artemis.datastructures.operations.Attestation;

public abstract class TestSuite {

  @MustBeClosed
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Stream<Arguments> findTests(String glob, List<Pair<Class, List<String>>> objectPath)
      throws IOException {
    return Resources.find(glob)
        .flatMap(
            url -> {
              try (InputStream in = url.openConnection().getInputStream()) {
                return prepareTests(in, objectPath);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Stream<Arguments> prepareTests(
      InputStream in, List<Pair<Class, List<String>>> objectPath) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    Map allTests = mapper.readerFor(Map.class).readValue(in);
    List<Object> objects =
        objectPath.stream()
            .map(
                e -> {
                  Iterator<String> itr = e.getSecond().iterator();
                  Object testObject = Map.copyOf(allTests);
                  while (itr.hasNext()) {
                    String param = itr.next();
                    if (testObject.getClass().equals(ArrayList.class)) {
                      testObject = ((ArrayList) testObject).get(Integer.valueOf(param));
                    } else {
                      testObject = ((Map) testObject).get(param);
                    }
                  }
                  Class testClass = e.getFirst();
                  if (testClass.equals(Attestation.class))
                    return MapObjectUtil.getAttestation((Map) testObject);
                  else if (testClass.equals(Bytes32.class))
                    return Bytes32.fromHexString(testObject.toString());
                  else if (testClass.equals(Bytes.class))
                    return Bytes.fromHexString(testObject.toString());
                  return null;
                })
            .collect(Collectors.toList());

    return Arrays.stream(new Arguments[] {Arguments.of(objects.toArray())});
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Pair<Class, List<String>> getParams(Class classType, List<String> args) {
    return new Pair<Class, List<String>>(classType, args);
  }
}
