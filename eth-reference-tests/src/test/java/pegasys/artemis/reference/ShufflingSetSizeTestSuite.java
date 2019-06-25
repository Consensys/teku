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

/*
 * The "official" Shuffling reference test data is from https://github.com/ethereum/eth2.0-tests/
 */
//
// @ExtendWith(BouncyCastleExtension.class)
// class ShufflingSetSizeTestSuite {
//
//  private static String shufflingTestVectorsFile = "**/shuffling/test_vector_shuffling.yml";
//  private static String shufflingDifferentSetSizeTestFile = "**/shuffling/shuffling_set_size.yml";

/*
  @ParameterizedTest(name = "{index}. Shuffling validator set. Expecting: {2}")
  @MethodSource("readStandardShufflingTests")
  void testStandardShufflingVectors(
      LinkedHashMap<String, Object> input, String inputSeed, List<List<BigInteger>> output) {

    performShufflingTests(input, inputSeed, output);
  }

  @ParameterizedTest(name = "{index}. Shuffling validator sets with varied sizes. Expecting: {2}")
  @MethodSource("readShufflingWithDifferentSetSizeTests")
  void testShufflingWithDifferentSetSizes(
      LinkedHashMap<String, Object> input, String inputSeed, List<List<BigInteger>> output) {

    performShufflingTests(input, inputSeed, output);
  }

  @SuppressWarnings({"unchecked"})
  private void performShufflingTests(
      LinkedHashMap<String, Object> input, String inputSeed, List<List<BigInteger>> output) {
    UnsignedLong epoch = UnsignedLong.valueOf((BigInteger) input.get("epoch"));
    Bytes32 seed = Bytes32.fromHexString(inputSeed);

    List<LinkedHashMap<String, BigInteger>> validatorSet =
        (List<LinkedHashMap<String, BigInteger>>) input.get("validators");

    // TODO: While order is implicitly preserved here based on how it's read in,
    // we need to make sure we're explicitly adding validators to the list based on
    // their "original_index" field in the tests.
    List<Validator> validators =
        validatorSet.stream()
            .map(
                validatorMap ->
                    new Validator(
                        BLSPublicKey.empty(),
                        Bytes32.random(),
                        UnsignedLong.valueOf(validatorMap.get("activation_epoch")),
                        UnsignedLong.valueOf(validatorMap.get("exit_epoch")),
                        UnsignedLong.ZERO,
                        false,
                        false))
            .collect(Collectors.toList());

    List<List<Integer>> actual = CrosslinkCommitteeUtil.get_shuffled_index(seed, validators, epoch);

    // Just in case something goes haywire, use BigInteger#intValueExact to be able
    // to gracefully handle an integer overrun when reading in expected indices.
    try {
      // This is a nasty hack, but there's not much we can do about it. In order
      // for Jackson to read in the epochs from the YAML without blowing up about
      // them being larger than ints/longs, we have to read them in as BigIntegers.
      // Unfortunately, this goes for index values read in from the output as well.
      // This Stream converts our BigIntegers indices to ints for the equality
      // assertion.
      List<List<Integer>> expected =
          output.stream()
              .map(
                  committees ->
                      committees.stream()
                          .map(index -> index.intValueExact())
                          .collect(Collectors.toList()))
              .collect(Collectors.toList());
      assertEquals(expected, actual);
    } catch (ArithmeticException ae) {
      ae.printStackTrace();
      fail("An error occurred while parsing the test. 'original_index' didn't fit into an int.");
    }
  }

  @MustBeClosed
  private static Stream<Arguments> readStandardShufflingTests() throws IOException {
    return findTests(shufflingTestVectorsFile, "test_cases");
  }

  @MustBeClosed
  private static Stream<Arguments> readShufflingWithDifferentSetSizeTests() throws IOException {
    return findTests(shufflingDifferentSetSizeTestFile, "test_cases");
  }

  @MustBeClosed
  private static Stream<Arguments> findTests(String glob, String tcase) throws IOException {
    return Resources.find(glob)
        .flatMap(
            url -> {
              try (InputStream in = url.openConnection().getInputStream()) {
                return prepareTests(in, tcase);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Stream<Arguments> prepareTests(InputStream in, String tcase) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    Map allTests =
        mapper
            .readerFor(Map.class)
            .with(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .readValue(in);

    return ((List<Map>) allTests.get(tcase))
        .stream()
            .map(
                testCase ->
                    Arguments.of(
                        testCase.get("input"), testCase.get("seed"), testCase.get("output")));
  }
}
*/
