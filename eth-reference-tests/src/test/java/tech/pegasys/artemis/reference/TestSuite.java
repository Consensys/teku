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

package tech.pegasys.artemis.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.io.Resources;
import org.junit.jupiter.params.provider.Arguments;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.mikuli.G2Point;
import tech.pegasys.artemis.util.mikuli.PublicKey;
import tech.pegasys.artemis.util.mikuli.SecretKey;
import tech.pegasys.artemis.util.mikuli.Signature;

public abstract class TestSuite {
  protected static Path configPath = null;
  private static final ALogger LOG = new ALogger(TestSuite.class.getName());
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
  @SuppressWarnings({"rawtypes"})
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

  public static Path getTestPath(Path path) {
    return Path.of(pathToTests.toString(), path.toString());
  }

  @SuppressWarnings({"rawtypes"})
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
        LOG.log(Level.WARN, e.toString());
      }
      if (result.isEmpty()) path = path.getParent();
    }
    if (result.isEmpty())
      throw new Exception(
          "TestSuite.loadConfigFromPath(): Configuration files was not found in the hierarchy of the provided path");
    Constants.init((Map) pathToObject(Paths.get(result)));

    // TODO fix this massacre of a technical debt
    // Checks if constants were changed from minimal to mainnet or vice-versa, and updates
    // reflection information
    if (Constants.SLOTS_PER_HISTORICAL_ROOT
        != SimpleOffsetSerializer.classReflectionInfo
            .get(BeaconState.class)
            .getVectorLengths()
            .get(0)) {
      SimpleOffsetSerializer.setConstants();
    }
  }

  public static Integer loadMetaData(TestSet testSet) {
    return (Integer)
        findTestsByPath(testSet).map(e -> e.get()).collect(Collectors.toList()).get(0)[0];
  }

  public static InputStream getInputStreamFromPath(Path path) {
    URL url = null;
    InputStream in = null;
    try {
      url = new URL(FILE + path);
      in = url.openConnection().getInputStream();
    } catch (MalformedURLException e) {
      LOG.log(Level.WARN, e.toString());
    } catch (IOException e) {
      LOG.log(Level.WARN, e.toString());
    }
    ;
    return in;
  }

  public static Bytes readInBinaryFromPath(Path path) {
    path = Path.of(pathToTests.toString(), path.toString());
    Bytes readBytes = null;
    try {
      InputStream inputStream = new FileInputStream(path.toFile());
      readBytes = Bytes.wrap(inputStream.readAllBytes());
    } catch (FileNotFoundException e) {
      LOG.log(Level.WARN, e.toString());
    } catch (IOException e) {
      LOG.log(Level.WARN, e.toString());
    }
    return readBytes;
  }

  @SuppressWarnings({"rawtypes"})
  public static Object getObjectFromYAMLInputStream(InputStream in) {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    Object object = null;
    try {
      object = ((Map) mapper.readerFor(Map.class).readValue(in));
    } catch (IOException e) {
      LOG.log(Level.WARN, e.toString());
    }
    return object;
  }

  @SuppressWarnings({"rawtypes"})
  public static Integer getIntegerFromYAMLInputStream(InputStream in) {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    try {
      return ((Integer) mapper.readerFor(Integer.class).readValue(in));
    } catch (IOException e) {
      LOG.log(Level.WARN, e.toString());
    }
    return null;
  }

  @SuppressWarnings({"rawtypes"})
  public static Bytes getSSZBytesFromPath(Path path) {
    InputStream in = getInputStreamFromPath(path);
    try {
      byte[] targetArray = new byte[in.available()];
      in.read(targetArray);
      return Bytes.wrap(targetArray);
    } catch (IOException e) {
      LOG.log(Level.WARN, e.toString());
    }
    return null;
  }

  public static Object pathToObject(Path path) {
    return getObjectFromYAMLInputStream(getInputStreamFromPath(path));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Object getPrimitiveFromPath(Path path, Class classInfo) {
    InputStream in = getInputStreamFromPath(path);
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    try {
      switch (classInfo.getSimpleName()) {
        case "String":
          return (mapper.readerFor(String.class).readValue(in));
        case "Integer":
          return (mapper.readerFor(Integer.class).readValue(in));
      }
    } catch (IOException e) {
    }
    return null;
  }

  public static Integer pathToString(Path path) {
    return getIntegerFromYAMLInputStream(getInputStreamFromPath(path));
  }

  public static Stream<Arguments> findTestsByPath(TestSet testSet) {
    Path path = Path.of(pathToTests.toString(), testSet.getPath().toString());
    try (Stream<Path> walk = Files.walk(path)) {
      List<String> result = walk.map(x -> x.toString()).collect(Collectors.toList());
      result =
          result.stream()
              .filter(
                  walkPath ->
                      testSet.getFileNames().stream()
                          .allMatch(fileName -> Files.exists(Path.of(walkPath, fileName))))
              .collect(Collectors.toList());

      Stream<Arguments> arguments =
          result.stream()
              .map(
                  walkPath -> {
                    return testSet.getFileNames().stream()
                        .flatMap(
                            fileName -> {
                              Object object = pathToObject(Path.of(walkPath, fileName));
                              return testSet.getTestObjectByFileName(fileName).stream()
                                  .map(
                                      testObject ->
                                          parseObjectFromFile(
                                              testObject.getClassName(),
                                              testObject.getPath(),
                                              object));
                            })
                        .collect(Collectors.toList());
                  })
              .map(objects -> Arguments.of(objects.toArray()));
      return arguments;
    } catch (IOException e) {
      LOG.log(Level.WARN, e.toString());
    }
    return null;
  }

  public static List<Arguments> findSSZTestsByPath(TestSet testSet) {
    Path path = Path.of(pathToTests.toString(), testSet.getPath().toString());
    List<Arguments> listOfarguments = new ArrayList<>();
    List<String> result = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(path)) {
      result = walk.map(x -> x.toString()).collect(Collectors.toList());
    } catch (IOException e) {
      LOG.log(Level.WARN, e.toString());
    }
    result =
        result.stream()
            .filter(
                walkPath ->
                    testSet.getFileNames().stream()
                        .allMatch(fileName -> Files.exists(Path.of(walkPath, fileName))))
            .collect(Collectors.toList());

    for (String testFolder : result) {
      List<Object> objectList = new ArrayList<>();
      for (String fileName : testSet.getFileNames()) {
        Path filePath = Path.of(testFolder, fileName);
        if (fileName.contains("ssz")) {
          Bytes objectBytes = getSSZBytesFromPath(filePath);
          TestObject testObject = testSet.getTestObjectByFileName1(fileName);
          objectList.add(
              SimpleOffsetSerializer.deserialize(objectBytes, testObject.getClassName()));
        } else {
          if (fileName.contains("timestamp")) {
            objectList.add(
                UnsignedLong.valueOf((Integer) getPrimitiveFromPath(filePath, Integer.class)));
          } else {
            Object object = pathToObject(filePath);
            TestObject testObject = testSet.getTestObjectByFileName1(fileName);
            objectList.add(
                parseObjectFromFile(testObject.getClassName(), testObject.getPath(), object));
          }
        }
      }
      listOfarguments.add(Arguments.of(objectList.toArray()));
    }
    return listOfarguments;
  }

  @MustBeClosed
  public static Stream<Arguments> findSSZTestsByPathWithTestType(TestSet testSet) {
    Path path = Path.of(pathToTests.toString(), testSet.getPath().toString());
    try (Stream<Path> walk = Files.walk(path)) {
      TestSet testSetPostRemoved = new TestSet(testSet);
      testSetPostRemoved.remove(testSet.size() - 1);
      List<String> result = walk.map(x -> x.toString()).collect(Collectors.toList());
      List<Pair<String, Boolean>> resultPair =
          result.stream().map(i -> new MutablePair<>(i, false)).collect(Collectors.toList());
      resultPair =
          resultPair.stream()
              .filter(
                  pair -> {
                    String walkPath = pair.getLeft();
                    Boolean isSuccesTest =
                        testSet.getFileNames().stream()
                            .allMatch(fileName -> Files.exists(Path.of(walkPath, fileName)));
                    pair.setValue(isSuccesTest);
                    return isSuccesTest
                        || testSetPostRemoved.getFileNames().stream()
                            .allMatch(fileName -> Files.exists(Path.of(walkPath, fileName)));
                  })
              .collect(Collectors.toList());

      List<Arguments> arguments = new ArrayList<>();
      for (Pair<String, Boolean> pair : resultPair) {
        String walkPath = pair.getLeft();
        Boolean isSuccessTest = pair.getRight();
        TestSet newTestSet;
        if (isSuccessTest) {
          newTestSet = new TestSet(testSet);
        } else {
          newTestSet = new TestSet(testSetPostRemoved);
        }
        List<Object> objects =
            newTestSet.getFileNames().stream()
                .flatMap(
                    fileName -> {
                      Bytes objectBytes = getSSZBytesFromPath(Path.of(walkPath, fileName));
                      return newTestSet.getTestObjectByFileName(fileName).stream()
                          .map(
                              testObject ->
                                  SimpleOffsetSerializer.deserialize(
                                      objectBytes, testObject.getClassName()));
                    })
                .collect(Collectors.toList());
        if (!isSuccessTest) {
          objects.add(new BeaconState());
        }
        objects.add(isSuccessTest);
        String filename = new File(walkPath).getName();
        objects.add(filename);
        arguments.add(Arguments.of(objects.toArray()));
      }

      Stream<Arguments> argumentsStream = arguments.stream();
      return argumentsStream;
    } catch (Exception e) {
      LOG.log(Level.WARN, e.toString());
    }
    return null;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static List<Arguments> convertArgumentToList(
      Class className, Integer count, List<Arguments> input) {
    List<Arguments> finalArguments = new ArrayList<>();
    for (Arguments arguments : input) {
      Object[] objectArguments = arguments.get();
      int counter = 0;
      Object[] outputObjects = new Object[objectArguments.length - count + 1];
      List argumentList = new ArrayList();
      for (Object object : objectArguments) {
        if (object.getClass().equals(className)) {
          argumentList.add(object);
        } else {
          outputObjects[counter] = object;
          counter++;
        }
      }
      if (argumentList.size() > 0) outputObjects[counter] = argumentList;
      finalArguments.add(Arguments.of(outputObjects));
    }
    return finalArguments;
  }

  @SuppressWarnings({"rawtypes"})
  private static Object parseObjectFromFile(Class className, Path path, Object object) {
    if (path != null) {
      Iterator<Path> itr = path.iterator();
      while (itr.hasNext()) {
        object = ((Map) object).get(itr.next().toString());
      }
    }
    return MapObjectUtil.convertMapToTypedObject(className, object);
  }

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
                        Iterator<String> itr = pair.getValue().iterator();
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
                        Iterator<String> itr = pair.getValue().iterator();
                        Object testObject = Map.copyOf(map);
                        while (itr.hasNext()) {
                          String param = itr.next();
                          testObject = ((Map) testObject).get(param);
                        }
                        Class classType = pair.getKey();
                        return MapObjectUtil.convertMapToTypedObject(classType, testObject);
                      })
                  .collect(Collectors.toList());
            })
        .map(objects -> Arguments.of(objects.toArray()));
  }

  @SuppressWarnings({"rawtypes"})
  public static Pair<Class, List<String>> getParams(Class classType, List<String> args) {
    return new ImmutablePair<Class, List<String>>(classType, args);
  }

  @SuppressWarnings({"rawtypes"})
  public static Pair<Class, String> getParams(Class classType, String args) {
    return new ImmutablePair<Class, String>(classType, args);
  }

  @MustBeClosed
  public static Stream<Arguments> aggregatePublicKeysSetup(Path path) {

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("data.yaml", PublicKey[].class, Paths.get("input")));
    testSet.add(new TestObject("data.yaml", PublicKey.class, Paths.get("output")));
    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> messageHashCompressedSetup(Path path) {

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("data.yaml", G2Point.class, Paths.get("input")));
    testSet.add(new TestObject("data.yaml", G2Point.class, Paths.get("output")));
    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> privateKeyPublicKeySetup(Path path) {

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("data.yaml", SecretKey.class, Paths.get("input")));
    testSet.add(new TestObject("data.yaml", PublicKey.class, Paths.get("output")));
    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> messageHashUncompressedSetup(Path path) {

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("data.yaml", G2Point.class, Paths.get("input")));
    testSet.add(new TestObject("data.yaml", G2Point.class, Paths.get("output")));
    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> signMessagesSetup(Path path) {

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("data.yaml", Bytes.class, Paths.get("input", "message")));
    testSet.add(new TestObject("data.yaml", Bytes.class, Paths.get("input", "domain")));
    testSet.add(new TestObject("data.yaml", SecretKey.class, Paths.get("input", "privkey")));
    testSet.add(new TestObject("data.yaml", Signature.class, Paths.get("output")));
    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> aggregateSignaturesSetup(Path path) {

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("data.yaml", Signature[].class, Paths.get("input")));
    testSet.add(new TestObject("data.yaml", Bytes.class, Paths.get("output")));
    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> epochProcessingSetup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);
    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("pre.ssz", BeaconState.class, null));
    testSet.add(new TestObject("post.ssz", BeaconState.class, null));

    return findSSZTestsByPath(testSet).stream();
  }

  @MustBeClosed
  @SuppressWarnings({"rawtypes"})
  public static Stream<Arguments> sszStaticMerkleizableSetup(
      Path path, Path configPath, Class className) throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("value.yaml", className, null));
    testSet.add(new TestObject("meta.yaml", Bytes32.class, Paths.get("root")));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  @SuppressWarnings({"rawtypes"})
  public static Stream<Arguments> sszStaticRootSigningRootSetup(
      Path path, Path configPath, Class className) throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("value.yaml", className, null));
    testSet.add(new TestObject("meta.yaml", Bytes32.class, Paths.get("root")));
    testSet.add(new TestObject("meta.yaml", Bytes32.class, Paths.get("signing_root")));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> sszStaticAttestationSetup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("attestation.yaml", Attestation.class, null));
    testSet.add(new TestObject("pre.yaml", BeaconState.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> attestationSlashingSetup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("attester_slashing.yaml", AttesterSlashing.class, null));
    testSet.add(new TestObject("pre.yaml", BeaconState.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> genericBlockHeaderSetup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("block.yaml", BeaconBlock.class, null));
    testSet.add(new TestObject("pre.yaml", BeaconState.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> blockHeaderSuccessSetup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("block.yaml", BeaconBlock.class, null));
    testSet.add(new TestObject("pre.yaml", BeaconState.class, null));
    testSet.add(new TestObject("post.yaml", BeaconState.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> invalidSignatureBlockHeaderSetup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("block.yaml", BeaconBlock.class, null));
    testSet.add(new TestObject("meta.yaml", Integer.class, Paths.get("bls_setting")));
    testSet.add(new TestObject("pre.yaml", BeaconState.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> operationDepositType1Setup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("deposit.yaml", Deposit.class, null));
    testSet.add(new TestObject("meta.yaml", Integer.class, Paths.get("bls_setting")));
    testSet.add(new TestObject("pre.yaml", BeaconState.class, null));
    testSet.add(new TestObject("post.yaml", BeaconState.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> operationDepositType2Setup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("deposit.yaml", Deposit.class, null));
    testSet.add(new TestObject("pre.yaml", BeaconState.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> operationDepositType3Setup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("deposit.yaml", Deposit.class, null));
    testSet.add(new TestObject("pre.yaml", BeaconState.class, null));
    testSet.add(new TestObject("post.yaml", BeaconState.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  @SuppressWarnings("rawtypes")
  public static Stream<Arguments> operationSetup(
      Path path, Path configPath, String operationName, Class operationClass) throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject(operationName, operationClass, null));
    testSet.add(new TestObject("pre.ssz", BeaconState.class, null));
    testSet.add(new TestObject("post.ssz", BeaconState.class, null));

    return findSSZTestsByPathWithTestType(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> operationVoluntaryExitType1Setup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("voluntary_exit.yaml", VoluntaryExit.class, null));
    testSet.add(new TestObject("meta.yaml", Integer.class, Paths.get("bls_setting")));
    testSet.add(new TestObject("pre.yaml", BeaconState.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> operationVoluntaryExitType2Setup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("voluntary_exit.yaml", VoluntaryExit.class, null));
    testSet.add(new TestObject("pre.yaml", BeaconState.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> operationVoluntaryExitType3Setup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("voluntary_exit.yaml", VoluntaryExit.class, null));
    testSet.add(new TestObject("pre.yaml", BeaconState.class, null));
    testSet.add(new TestObject("post.yaml", BeaconState.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> sanityMultiBlockSetup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet metaDataSet = new TestSet(path);
    metaDataSet.add(new TestObject("meta.yaml", Integer.class, Paths.get("blocks_count")));
    Integer block_count = loadMetaData(metaDataSet);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("pre.yaml", BeaconState.class, null));
    testSet.add(new TestObject("post.yaml", BeaconState.class, null));
    for (int i = 0; i < block_count; i++) {
      testSet.add(new TestObject("blocks_" + i + ".yaml", BeaconBlock.class, null));
    }

    return convertArgumentToList(
        BeaconBlock.class, block_count, findTestsByPath(testSet).collect(Collectors.toList()))
        .stream();
  }

  @MustBeClosed
  public static Stream<Arguments> sanityMultiBlockSetupInvalid(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet metaDataSet = new TestSet(path);
    metaDataSet.add(new TestObject("meta.yaml", Integer.class, Paths.get("blocks_count")));
    Integer block_count = loadMetaData(metaDataSet);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("pre.yaml", BeaconState.class, null));
    for (int i = 0; i < block_count; i++) {
      testSet.add(new TestObject("blocks_" + i + ".yaml", BeaconBlock.class, null));
    }

    return convertArgumentToList(
        BeaconBlock.class, block_count, findTestsByPath(testSet).collect(Collectors.toList()))
        .stream();
  }

  @MustBeClosed
  public static Stream<Arguments> sanitySlotSetup(Path path, Path configPath) throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("pre.yaml", BeaconState.class, null));
    testSet.add(new TestObject("post.yaml", BeaconState.class, null));
    testSet.add(new TestObject("slots.yaml", UnsignedLong.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> shufflingShuffleSetup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("mapping.yaml", Bytes32.class, Paths.get("seed")));
    testSet.add(new TestObject("mapping.yaml", Integer.class, Paths.get("count")));
    testSet.add(new TestObject("mapping.yaml", Integer[].class, Paths.get("mapping")));

    return findTestsByPath(testSet);
  }

  public static Stream<Arguments> genesisInitializationSetup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet metaDataSet = new TestSet(path);
    metaDataSet.add(new TestObject("meta.yaml", Integer.class, Paths.get("deposits_count")));
    Integer deposits_count = loadMetaData(metaDataSet);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("state.ssz", BeaconState.class, null));
    testSet.add(new TestObject("eth1_timestamp.yaml", UnsignedLong.class, null));
    testSet.add(new TestObject("eth1_block_hash.ssz", Bytes32.class, null));
    for (int i = 0; i < deposits_count; i++) {
      testSet.add(new TestObject("deposits_" + i + ".ssz", Deposit.class, null));
    }

    List<Arguments> arguments =
        convertArgumentToList(Deposit.class, deposits_count, findSSZTestsByPath(testSet));
    return arguments.stream();
  }

  @MustBeClosed
  public static Stream<Arguments> genesisValiditySetup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("genesis.yaml", BeaconState.class, null));
    testSet.add(new TestObject("is_valid.yaml", Boolean.class, null));

    return findTestsByPath(testSet);
  }
}
