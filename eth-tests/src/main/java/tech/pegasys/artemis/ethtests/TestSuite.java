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
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.io.InputStream;
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
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.params.provider.Arguments;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
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
          "referenceTest",
          "resources",
          "eth2.0-spec-tests",
          "tests");
  private static final String FILE = "file://";

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
    String constants = path.toString().contains("mainnet") ? "mainnet" : "minimal";
    Constants.setConstants(constants);

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
    return in;
  }

  @SuppressWarnings({"rawtypes"})
  public static Object getObjectFromYAMLInputStream(InputStream in, List<TestObject> testObjects) {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    Object object = null;
    try {
      if (testObjects != null
          && (testObjects.get(0).getClassName().equals(UnsignedLong.class)
              || testObjects.get(0).getClassName().equals(Boolean.class)
              || testObjects.get(0).getClassName().equals(String.class))) {
        object = ((String) mapper.readerFor(String.class).readValue(in));
      } else {
        object = ((Map) mapper.readerFor(Map.class).readValue(in));
      }

    } catch (IOException e) {
      LOG.log(Level.WARN, e.toString());
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
      LOG.log(Level.WARN, e.toString());
    }
    return null;
  }

  public static Object pathToObject(Path path, List<TestObject> testObjects) {
    return getObjectFromYAMLInputStream(getInputStreamFromPath(path), testObjects);
  }

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
              walkPath -> {
                return testSet.getFileNames().stream()
                    .flatMap(
                        fileName -> {
                          Object object =
                              pathToObject(
                                  Path.of(walkPath, fileName),
                                  testSet.getTestObjectByFileName(fileName));
                          return testSet.getTestObjectByFileName(fileName).stream()
                              .map(
                                  testObject -> {
                                    if (fileName.contains(".ssz")) {
                                      Bytes objectBytes =
                                          getSSZBytesFromPath(Path.of(walkPath, fileName));
                                      return SimpleOffsetSerializer.deserialize(
                                          objectBytes, testObject.getClassName());
                                    } else {
                                      return parseObjectFromFile(
                                          testObject.getClassName(), testObject.getPath(), object);
                                    }
                                  });
                        })
                    .collect(Collectors.toList());
              })
          .map(objects -> Arguments.of(objects.toArray()));
    } catch (IOException e) {
      LOG.log(Level.WARN, e.toString());
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
    testSet.add(new TestObject("roots.yaml", Bytes32.class, Paths.get("signing_root")));

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  @SuppressWarnings("rawtypes")
  public static Stream<Arguments> sszStaticSetupNoSigningRoot(
      Path path, Path configPath, Class className) throws Exception {
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

    return findTestsByPath(testSet);
  }

  @MustBeClosed
  @SuppressWarnings("rawtypes")
  public static Stream<Arguments> operationSuccessSetup(
      Path path, Path configPath, String operationName, Class operationClass) throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject(operationName, operationClass, null));
    testSet.add(new TestObject("pre.ssz", BeaconState.class, null));
    testSet.add(new TestObject("post.ssz", BeaconState.class, null));

    return findTestsByPath(testSet);
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
    testSet.add(new TestObject("pre.ssz", BeaconState.class, null));
    testSet.add(new TestObject("post.ssz", BeaconState.class, null));
    for (int i = 0; i < block_count; i++) {
      testSet.add(new TestObject("blocks_" + i + ".ssz", BeaconBlock.class, null));
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
    testSet.add(new TestObject("pre.ssz", BeaconState.class, null));
    for (int i = 0; i < block_count; i++) {
      testSet.add(new TestObject("blocks_" + i + ".ssz", BeaconBlock.class, null));
    }

    return convertArgumentToList(
        BeaconBlock.class, block_count, findTestsByPath(testSet).collect(Collectors.toList()))
        .stream();
  }

  @MustBeClosed
  public static Stream<Arguments> sanitySlotSetup(Path path, Path configPath) throws Exception {
    loadConfigFromPath(configPath);

    TestSet testSet = new TestSet(path);
    testSet.add(new TestObject("pre.ssz", BeaconState.class, null));
    testSet.add(new TestObject("post.ssz", BeaconState.class, null));
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
            BeaconState.class,
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
    testSet.add(new TestObject("state.ssz", BeaconState.class, null));
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
    testSet.add(new TestObject("genesis.yaml", BeaconState.class, null));
    testSet.add(new TestObject("is_valid.yaml", Boolean.class, null));

    return findTestsByPath(testSet);
  }
}
