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
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.MustBeClosed;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kotlin.Pair;
import org.apache.tuweni.io.Resources;
import org.junit.jupiter.params.provider.Arguments;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.*;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;

public abstract class TestSuite {
  private static final Path pathToTests =
      Paths.get(
          System.getProperty("user.dir").toString(),
          "src",
          "test",
          "resources",
          "eth2.0-spec-tests",
          "tests");
  private static final String FILE = "file://";

  @Deprecated
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

  public static void loadConfigFromPath(Path path) throws Exception {
    path = Path.of(pathToTests.toString(), path.toString());
    String result = "";
    while (result.isEmpty() && path.getNameCount() > 0) {
      try (Stream<Path> walk = Files.walk(path)) {
        result =
            walk.map(x -> x.toString())
                .filter(currentPath -> currentPath.contains("config.yaml"))
                .collect(Collectors.joining());
      } catch (IOException e) {
        e.printStackTrace();
      }
      if (result.isEmpty()) path = path.getParent();
    }
    if (result.isEmpty())
      throw new Exception(
          "TestSuite.loadConfigFromPath(): Configuration files was not found in the hierarchy of the provided path");
    Constants.init((Map) pathToObject(Paths.get(result)));
  }

  @MustBeClosed
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Stream<Arguments> findTestsByPath(List<Pair<Class, String>> objectPath)
      throws IOException {
    return findTestsByPath(Paths.get(""), objectPath);
  }

  public static InputStream getInputStreamFromPath(Path path) {
    URL url = null;
    InputStream in = null;
    try {
      url = new URL(FILE + path);
      in = url.openConnection().getInputStream();
    } catch (MalformedURLException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    ;
    return in;
  }


  public static Object getObjectFromYAMLInputStream(InputStream in) {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    Object object = null;
    try {
      object = ((Map) mapper.readerFor(Map.class).readValue(in));
    } catch (IOException e) {
      e.printStackTrace();
    }
    return object;
  }

  public static Object pathToObject(Path path) {
    return getObjectFromYAMLInputStream(getInputStreamFromPath(path));
  }

  public static class Context {
      public String path;
      public Object obj;
  }

  @MustBeClosed
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Stream<Arguments> findTestsByPath(Path path, List<Pair<Class, String>> objectPath)
      throws IOException {
    path = Path.of(pathToTests.toString(), path.toString());
    try (Stream<Path> walk = Files.walk(path)) {
      List<String> result = walk.map(x -> x.toString()).collect(Collectors.toList());
//      List<Context> contexts = new ArrayList<>();
      return result
          .parallelStream()
          .filter(
              walkPath ->
                  objectPath
                      .parallelStream()
                      .allMatch(pair -> Files.exists(Path.of(walkPath, pair.getSecond()))))
          .map(
              walkPath -> {
                return objectPath.stream()
                    .map(
                        pair -> {
                          Object object = pathToObject(Path.of(walkPath, pair.getSecond()));
                            Context c = new Context();
                            c.path = walkPath;
                            try {
                                if (pair.getFirst().equals(ReadLineType.class)) {
                                    BufferedReader inputStreamFromPath = new BufferedReader(new InputStreamReader(getInputStreamFromPath(Path.of(walkPath, pair.getSecond()))));
                                    String s = inputStreamFromPath.readLine();
                                    c.obj = s;
                                } else {
                                    c.obj = MapObjectUtil.convertMapToTypedObject(pair.getFirst(), object);
                                }
                            } catch (Exception e) {
                                System.out.println("here");
                            }
                          return c;
                        });
              })
          .map(objects -> Arguments.of(objects.toArray()));
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  @Deprecated
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Stream<Arguments> prepareTests(
      InputStream in, List<Pair<Class, List<String>>> objectPath) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    List<Map> allTests =
        (ArrayList) ((Map) mapper.readerFor(Map.class).readValue(in)).get("test_cases");
    return allTests.stream()
        .filter(
            map -> {
              return objectPath.stream()
                  .allMatch(
                      pair -> {
                        Iterator<String> itr = pair.getSecond().iterator();
                        Object testObject = Map.copyOf(map);
                        while (itr.hasNext()) {
                          String param = itr.next();
                          testObject = ((Map) testObject).get(param);
                          if (testObject == null) return false;
                        }
                        return testObject != null;
                      });
            })
        .map(
            map -> {
              return objectPath.stream()
                  .map(
                      pair -> {
                        Iterator<String> itr = pair.getSecond().iterator();
                        Object testObject = Map.copyOf(map);
                        while (itr.hasNext()) {
                          String param = itr.next();
                          testObject = ((Map) testObject).get(param);
                        }
                        Class classType = pair.getFirst();
                        return MapObjectUtil.convertMapToTypedObject(classType, testObject);
                      })
                  .collect(Collectors.toList());
            })
        .map(objects -> Arguments.of(objects.toArray()));
  }

  @Deprecated
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Pair<Class, List<String>> getParams(Class classType, List<String> args) {
    return new Pair<Class, List<String>>(classType, args);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Pair<Class, String> getParams(Class classType, String args) {
    return new Pair<Class, String>(classType, args);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  public static Stream<Arguments> epochProcessingSetup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    List<Pair<Class, String>> arguments = new ArrayList<Pair<Class, String>>();
    arguments.add(getParams(BeaconState.class, "pre.yaml"));
    arguments.add(getParams(BeaconState.class, "post.yaml"));
    return findTestsByPath(path, arguments);
  }

  private static class ReadLineType { }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @MustBeClosed
    public static Stream<Arguments> sanitySlotsProcessingSetup(Path path, Path configPath)
            throws Exception {
        loadConfigFromPath(configPath);

        List<Pair<Class, String>> arguments = new ArrayList<Pair<Class, String>>();
        arguments.add(getParams(BeaconStateWithCache.class, "pre.yaml"));
        arguments.add(getParams(BeaconStateWithCache.class, "post.yaml"));
        arguments.add(getParams(ReadLineType.class, "slots.yaml"));
        return findTestsByPath(path, arguments);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @MustBeClosed
    public static Stream<Arguments> sanityBlocksProcessingSetup(Path path, Path configPath)
            throws Exception {
        loadConfigFromPath(configPath);

        List<Pair<Class, String>> arguments = new ArrayList<Pair<Class, String>>();
        arguments.add(getParams(BeaconState.class, "pre.yaml"));
        arguments.add(getParams(BeaconState.class, "post.yaml"));
        arguments.add(getParams(ReadLineType.class, "meta.yaml"));
        return findTestsByPath(path, arguments);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @MustBeClosed
    public static Stream<Arguments> operationsProcessingSetup(Path path, Path configPath)
            throws Exception {
        loadConfigFromPath(configPath);

        List<Pair<Class, String>> arguments = new ArrayList<Pair<Class, String>>();
        arguments.add(getParams(BeaconState.class, "pre.yaml"));
        arguments.add(getParams(Attestation.class, "attestation.yaml"));
        return findTestsByPath(path, arguments);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @MustBeClosed
    public static Stream<Arguments> depositProcessingSetup(Path path, Path configPath)
            throws Exception {
        loadConfigFromPath(configPath);

        List<Pair<Class, String>> arguments = new ArrayList<Pair<Class, String>>();
        arguments.add(getParams(BeaconState.class, "pre.yaml"));
        arguments.add(getParams(Deposit.class, "deposit.yaml"));
        return findTestsByPath(path, arguments);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @MustBeClosed
    public static Stream<Arguments> proposerSlashingProcessingSetup(Path path, Path configPath)
            throws Exception {
        loadConfigFromPath(configPath);

        List<Pair<Class, String>> arguments = new ArrayList<Pair<Class, String>>();
        arguments.add(getParams(BeaconState.class, "pre.yaml"));
        arguments.add(getParams(ProposerSlashing.class, "proposer_slashing.yaml"));
        return findTestsByPath(path, arguments);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @MustBeClosed
    public static Stream<Arguments> voluntaryExitProcessingSetup(Path path, Path configPath)
            throws Exception {
        loadConfigFromPath(configPath);

        List<Pair<Class, String>> arguments = new ArrayList<Pair<Class, String>>();
        arguments.add(getParams(BeaconState.class, "pre.yaml"));
        arguments.add(getParams(VoluntaryExit.class, "voluntary_exit.yaml"));
        return findTestsByPath(path, arguments);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @MustBeClosed
    public static Stream<Arguments> attestorSlashingProcessingSetup(Path path, Path configPath)
            throws Exception {
        loadConfigFromPath(configPath);

        List<Pair<Class, String>> arguments = new ArrayList<Pair<Class, String>>();
        arguments.add(getParams(BeaconState.class, "pre.yaml"));
        arguments.add(getParams(AttesterSlashing.class, "attester_slashing.yaml"));
        return findTestsByPath(path, arguments);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @MustBeClosed
    public static Stream<Arguments> blockHeaderProcessingSetup(Path path, Path configPath)
            throws Exception {
        loadConfigFromPath(configPath);

        List<Pair<Class, String>> arguments = new ArrayList<Pair<Class, String>>();
        arguments.add(getParams(BeaconState.class, "pre.yaml"));
        arguments.add(getParams(BeaconBlock.class, "block.yaml"));
        return findTestsByPath(path, arguments);
    }
}
