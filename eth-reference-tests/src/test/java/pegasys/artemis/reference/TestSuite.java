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
import kotlin.Pair;
import org.apache.tuweni.io.Resources;
import org.junit.jupiter.params.provider.Arguments;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    List<Map> allTests = (ArrayList)((Map)mapper.readerFor(Map.class).readValue(in)).get("test_cases");
    return allTests.stream().filter(map -> {
        return objectPath.stream().allMatch(pair -> {
            Iterator<String> itr = pair.getSecond().iterator();
            Object testObject = Map.copyOf(map);
            while (itr.hasNext()) {
                String param = itr.next();
                testObject =  ((Map)testObject).get(param);
                if(testObject == null) return false;
            }
            return testObject != null;
        });
    }).map(map -> {
        return objectPath.stream().map(pair -> {
            Iterator<String> itr = pair.getSecond().iterator();
            Object testObject = Map.copyOf(map);
            while (itr.hasNext()) {
                String param = itr.next();
                testObject =  ((Map)testObject).get(param);
            }
            Class classType = pair.getFirst();
            return MapObjectUtil.convertMapToTypedObject(classType, testObject);
        }).collect(Collectors.toList());
    }).map(objects -> Arguments.of(objects.toArray()));
  }



  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Pair<Class, List<String>> getParams(Class classType, List<String> args) {
    return new Pair<Class, List<String>>(classType, args);
  }
}
