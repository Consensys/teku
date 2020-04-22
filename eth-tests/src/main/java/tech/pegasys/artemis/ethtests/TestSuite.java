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

package tech.pegasys.artemis.ethtests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Streams;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.params.provider.Arguments;
import tech.pegasys.artemis.bls.BLSPublicKey;
import tech.pegasys.artemis.bls.BLSSecretKey;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;
import tech.pegasys.artemis.datastructures.state.BeaconStateImpl;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.config.Constants;

public abstract class TestSuite {
  protected static Path configPath = null;
  private static final Logger LOG = LogManager.getLogger();
  private static final Path pathToTests =
      Paths.get(
          System.getProperty("user.dir"),
          "src",
          "referenceTest",
          "resources",
          "eth2.0-spec-tests",
          "tests");

  @SuppressWarnings({"rawtypes"})
  public static void loadConfigFromPath(Path path) throws Exception {
    path = Path.of(pathToTests.toString(), path.toString());
    String result = "";
    while (result.isEmpty() && path.getNameCount() > 0) {
      try (Stream<Path> walk = Files.walk(path)) {
        result =
            walk.map(Path::toString)
                .filter(currentPath -> currentPath.contains("config.yaml"))
                .collect(Collectors.joining());
      } catch (IOException e) {
        LOG.warn("Failed to read config from " + path, e);
      }
      if (result.isEmpty()) path = path.getParent();
    }
    if (result.isEmpty())
      throw new Exception(
          "TestSuite.loadConfigFromPath(): Configuration files was not found in the hierarchy of the provided path");
    String constants = path.toString().contains("mainnet") ? "mainnet" : "minimal";
    Constants.setConstants(constants);

    // TODO fix this massacre of a technical debt
    // Checks if constants were changed from minimal to mainnet or vice-versa, and updates
    // reflection information
    if (Constants.SLOTS_PER_HISTORICAL_ROOT
        != SimpleOffsetSerializer.classReflectionInfo
            .get(BeaconStateImpl.class)
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
    InputStream in = null;
    try {
      URL url = path.toUri().toURL();
      in = url.openConnection().getInputStream();
    } catch (IOException e) {
      LOG.warn("Failed to load " + path, e);
    }
    return in;
  }

  @SuppressWarnings({"rawtypes"})
  public static Object getObjectFromYAMLInputStream(Path path, List<TestObject> testObjects) {
    final InputStream in = getInputStreamFromPath(path);
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    Object object = null;
    try {
      if (testObjects != null
          && (testObjects.get(0).getClassName().equals(UnsignedLong.class)
              || testObjects.get(0).getClassName().equals(Boolean.class)
              || testObjects.get(0).getClassName().equals(String.class))) {
        object = mapper.readerFor(String.class).readValue(in);
      } else {
        object = mapper.readerFor(Map.class).readValue(in);
      }

    } catch (IOException e) {
      LOG.warn("Failed to parse YAML from file " + path.toAbsolutePath(), e);
    }
    return object;
  }

  @SuppressWarnings({"rawtypes"})
  public static Bytes getSSZBytesFromPath(Path path) {
    InputStream in = getInputStreamFromPath(path);
    try {
      byte[] targetArray = new byte[in.available()];
      in.read(targetArray);
      return Bytes.wrap(targetArray);
    } catch (IOException e) {
      LOG.warn("Failed to load SSZ from " + path, e);
    }
    return null;
  }

  public static Object pathToObject(Path path, List<TestObject> testObjects) {
    return getObjectFromYAMLInputStream(path, testObjects);
  }

  @SuppressWarnings("unchecked")
  public static Stream<Arguments> findTestsByPath(TestSet testSet) {
    Path path = Path.of(pathToTests.toString(), testSet.getPath().toString());
    try (Stream<Path> walk = Files.walk(path)) {
      List<String> result = walk.map(x -> x.toString()).collect(Collectors.toList());
      result =
          result.stream()
              .filter(walkPath -> isFilePathConfiguredForTest(testSet, walkPath))
              .collect(Collectors.toList());

      return result.stream()
          .map(
              walkPath ->
                  Streams.concat(
                          testSet.getFileNames().stream()
                              .flatMap(
                                  fileName ->
                                      testSet.getTestObjectByFileName(fileName).stream()
                                          .map(
                                              testObject -> {
                                                if (fileName.contains(".ssz")) {
                                                  Bytes objectBytes =
                                                      getSSZBytesFromPath(
                                                          Path.of(walkPath, fileName));
                                                  return SimpleOffsetSerializer.deserialize(
                                                      objectBytes, testObject.getClassName());
                                                } else {
                                                  return parseObjectFromFile(
                                                      testObject.getClassName(),
                                                      testObject.getPath(),
                                                      pathToObject(
                                                          Path.of(walkPath, fileName),
                                                          testSet.getTestObjectByFileName(
                                                              fileName)));
                                                }
                                              })),
                          Stream.of(Path.of(walkPath).getFileName().toString()))
                      .collect(Collectors.toList()))
          .map(objects -> Arguments.of(objects.toArray()));
    } catch (IOException e) {
      LOG.warn("Failed to load tests from " + path, e);
    }
    return null;
  }

  public static boolean isFilePathConfiguredForTest(TestSet testSet, String walkPath) {
    boolean isIncludedPath =
        testSet.getFileNames().stream()
            .allMatch(fileName -> Files.exists(Path.of(walkPath, fileName)));
    boolean isSuccessTest =
        testSet.getFileNames().stream()
                .filter(
                    fileName ->
                        fileName.contains("pre.yaml")
                            || fileName.contains("pre.ssz")
                            || fileName.contains("post.yaml")
                            || fileName.contains("post.ssz"))
                .collect(Collectors.toList())
                .size()
            > 1;
    if (isSuccessTest) return isIncludedPath;
    boolean isNotExcludedPath =
        !(Files.exists(Path.of(walkPath, "post.ssz"))
            || Files.exists(Path.of(walkPath, "post.yaml")));
    boolean isMetaPath =
        testSet.getFileNames().size() == 1 && testSet.getFileNames().get(0).equals("meta.yaml");
    return isIncludedPath && (isNotExcludedPath || isMetaPath);
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

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Object parseObjectFromFile(Class className, Path path, Object object) {
    // We found what we're looking for; recursion is finished
    if (path == null) return MapObjectUtil.convertMapToTypedObject(className, object);
    if (object.getClass() == LinkedHashMap.class) {
      // We just recurse down the path for HashMaps
      Path head = path.getName(0);
      Path tail = path.getNameCount() > 1 ? path.subpath(1, path.getNameCount()) : null;
      return parseObjectFromFile(className, tail, ((Map) object).get(head.toString()));
    } else if (object.getClass() == ArrayList.class) {
      // If we've found an ArrayList, we need to construct a list of the objects we're looking for
      return ((ArrayList) object)
          .stream().map(x -> parseObjectFromFile(className, path, x)).collect(Collectors.toList());
    } else {
      throw new RuntimeException(
          "Unknown class encountered in parseObjectFromFile: " + object.getClass());
    }
  }

  @MustBeClosed
  public static Stream<Arguments> aggregateSetup(Path path) {
    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("data.yaml", BLSSignature[].class, Paths.get("input")));
    testSet.add(new TestObject("data.yaml", BLSSignature.class, Paths.get("output")));
    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> aggregateVerifySetup(Path path) {
    TestSet testSet = new TestSet(path);
    // We are expecting an array of PublicKeys, but we must just specify just PublicKey type here
    // due to the test file format.
    testSet.add(
        new TestObject("data.yaml", BLSPublicKey.class, Paths.get("input", "pairs", "pubkey")));
    // As above
    testSet.add(new TestObject("data.yaml", Bytes.class, Paths.get("input", "pairs", "message")));
    testSet.add(new TestObject("data.yaml", BLSSignature.class, Paths.get("input", "signature")));
    testSet.add(new TestObject("data.yaml", Boolean.class, Paths.get("output")));
    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> fastAggregateVerifySetup(Path path) {
    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("data.yaml", BLSPublicKey[].class, Paths.get("input", "pubkeys")));
    testSet.add(new TestObject("data.yaml", Bytes.class, Paths.get("input", "message")));
    testSet.add(new TestObject("data.yaml", BLSSignature.class, Paths.get("input", "signature")));
    testSet.add(new TestObject("data.yaml", Boolean.class, Paths.get("output")));
    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> signSetup(Path path) {
    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("data.yaml", Bytes.class, Paths.get("input", "message")));
    testSet.add(new TestObject("data.yaml", BLSSecretKey.class, Paths.get("input", "privkey")));
    testSet.add(new TestObject("data.yaml", BLSSignature.class, Paths.get("output")));
    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> verifySetup(Path path) {
    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("data.yaml", BLSPublicKey.class, Paths.get("input", "pubkey")));
    testSet.add(new TestObject("data.yaml", Bytes.class, Paths.get("input", "message")));
    testSet.add(new TestObject("data.yaml", BLSSignature.class, Paths.get("input", "signature")));
    testSet.add(new TestObject("data.yaml", Boolean.class, Paths.get("output")));
    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> epochProcessingSetup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);
    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("pre.ssz", BeaconStateImpl.class, null));
    testSet.add(new TestObject("post.ssz", BeaconStateImpl.class, null));

    return findTestsByPath(testSet);
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
  @SuppressWarnings("rawtypes")
  public static Stream<Arguments> sszStaticSetup(Path path, Path configPath, Class className)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("serialized.ssz", className, null));
    testSet.add(new TestObject("roots.yaml", Bytes32.class, Paths.get("root")));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> genericBlockHeaderSetup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("block.yaml", SignedBeaconBlock.class, null));
    testSet.add(new TestObject("pre.yaml", BeaconStateImpl.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> blockHeaderSuccessSetup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("block.yaml", SignedBeaconBlock.class, null));
    testSet.add(new TestObject("pre.yaml", BeaconStateImpl.class, null));
    testSet.add(new TestObject("post.yaml", BeaconStateImpl.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> invalidSignatureBlockHeaderSetup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("block.yaml", SignedBeaconBlock.class, null));
    testSet.add(new TestObject("meta.yaml", Integer.class, Paths.get("bls_setting")));
    testSet.add(new TestObject("pre.yaml", BeaconStateImpl.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> operationDepositType1Setup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("deposit.yaml", Deposit.class, null));
    testSet.add(new TestObject("meta.yaml", Integer.class, Paths.get("bls_setting")));
    testSet.add(new TestObject("pre.yaml", BeaconStateImpl.class, null));
    testSet.add(new TestObject("post.yaml", BeaconStateImpl.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> operationDepositType2Setup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("deposit.yaml", Deposit.class, null));
    testSet.add(new TestObject("pre.yaml", BeaconStateImpl.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> operationDepositType3Setup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("deposit.yaml", Deposit.class, null));
    testSet.add(new TestObject("pre.yaml", BeaconStateImpl.class, null));
    testSet.add(new TestObject("post.yaml", BeaconStateImpl.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  @SuppressWarnings("rawtypes")
  public static Stream<Arguments> operationSetup(
      Path path, Path configPath, String operationName, Class operationClass) throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject(operationName, operationClass, null));
    testSet.add(new TestObject("pre.ssz", BeaconStateImpl.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  @SuppressWarnings("rawtypes")
  public static Stream<Arguments> operationSuccessSetup(
      Path path, Path configPath, String operationName, Class operationClass) throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject(operationName, operationClass, null));
    testSet.add(new TestObject("pre.ssz", BeaconStateImpl.class, null));
    testSet.add(new TestObject("post.ssz", BeaconStateImpl.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> operationVoluntaryExitType1Setup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("voluntary_exit.yaml", VoluntaryExit.class, null));
    testSet.add(new TestObject("meta.yaml", Integer.class, Paths.get("bls_setting")));
    testSet.add(new TestObject("pre.yaml", BeaconStateImpl.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> operationVoluntaryExitType2Setup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("voluntary_exit.yaml", VoluntaryExit.class, null));
    testSet.add(new TestObject("pre.yaml", BeaconStateImpl.class, null));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  public static Stream<Arguments> operationVoluntaryExitType3Setup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("voluntary_exit.yaml", VoluntaryExit.class, null));
    testSet.add(new TestObject("pre.yaml", BeaconStateImpl.class, null));
    testSet.add(new TestObject("post.yaml", BeaconStateImpl.class, null));

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
    testSet.add(new TestObject("pre.ssz", BeaconStateImpl.class, null));
    testSet.add(new TestObject("post.ssz", BeaconStateImpl.class, null));
    for (int i = 0; i < block_count; i++) {
      testSet.add(new TestObject("blocks_" + i + ".ssz", SignedBeaconBlock.class, null));
    }

    return convertArgumentToList(
        SignedBeaconBlock.class, block_count, findTestsByPath(testSet).collect(Collectors.toList()))
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
    testSet.add(new TestObject("pre.ssz", BeaconStateImpl.class, null));
    for (int i = 0; i < block_count; i++) {
      testSet.add(new TestObject("blocks_" + i + ".ssz", SignedBeaconBlock.class, null));
    }

    return convertArgumentToList(
        SignedBeaconBlock.class, block_count, findTestsByPath(testSet).collect(Collectors.toList()))
        .stream();
  }

  @MustBeClosed
  public static Stream<Arguments> sanitySlotSetup(Path path, Path configPath) throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("pre.ssz", BeaconStateImpl.class, null));
    testSet.add(new TestObject("post.ssz", BeaconStateImpl.class, null));
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

  public static Stream<Arguments> genesisInitializationCheck(
      Path path, Path configPath, int numValidators, long genesisTime) throws Exception {

    loadConfigFromPath(configPath);
    TestSet testSet = new TestSet(path);
    testSet.add(
        new TestObject(
            "quickstart_genesis_" + numValidators + "_" + genesisTime + ".ssz",
            BeaconStateImpl.class,
            null));

    return findTestsByPath(testSet);
  }

  public static Stream<Arguments> genesisInitializationSetup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet metaDataSet = new TestSet(path);
    metaDataSet.add(new TestObject("meta.yaml", Integer.class, Paths.get("deposits_count")));
    Integer deposits_count = loadMetaData(metaDataSet);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("state.ssz", BeaconStateImpl.class, null));
    testSet.add(new TestObject("eth1_timestamp.yaml", UnsignedLong.class, null));
    testSet.add(new TestObject("eth1_block_hash.ssz", Bytes32.class, null));
    for (int i = 0; i < deposits_count; i++) {
      testSet.add(new TestObject("deposits_" + i + ".ssz", Deposit.class, null));
    }

    List<Arguments> arguments =
        convertArgumentToList(
            Deposit.class, deposits_count, findTestsByPath(testSet).collect(Collectors.toList()));
    return arguments.stream();
  }

  @MustBeClosed
  public static Stream<Arguments> genesisValiditySetup(Path path, Path configPath)
      throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("genesis.yaml", BeaconStateImpl.class, null));
    testSet.add(new TestObject("is_valid.yaml", Boolean.class, null));

    return findTestsByPath(testSet);
  }
}
