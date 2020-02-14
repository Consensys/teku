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

package tech.pegasys.artemis.datastructures.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.config.Constants;

@ExtendWith(BouncyCastleExtension.class)
class BeaconStateTest {

  /*
  private BeaconState newState(int numDeposits) {

    try {

      // Initialize state
      BeaconState state =
          initialize_beacon_state_from_eth1(
              Bytes32.ZERO, UnsignedLong.ZERO, randomDeposits(numDeposits, 100));

      return state;
    } catch (Exception e) {
      fail("get_genesis_beacon_state() failed");
      return null;
    }
  }

  @Test
  void activateValidator() {
    BeaconState state = newState(1);
    int validator_index = 0;
    UnsignedLong activation_epoch;

    BeaconStateUtil.activate_validator(state, validator_index, true);
    activation_epoch = state.getValidator_registry().get(validator_index).getActivation_epoch();
    assertThat(activation_epoch).isEqualTo(UnsignedLong.valueOf(GENESIS_EPOCH));

    BeaconStateUtil.activate_validator(state, validator_index, false);
    activation_epoch = state.getValidator_registry().get(validator_index).getActivation_epoch();
    assertThat(activation_epoch)
        .isEqualTo(UnsignedLong.valueOf(GENESIS_EPOCH + 1 + ACTIVATION_EXIT_DELAY));
  }

  @Test
  void initiateValidatorExitNotActive() {
    BeaconState state = newState(1);
    int validator_index = 0;

    assertThat(
            !state
                .getValidators()
                .get(validator_index)
                .getExit_epoch()
                .equals(FAR_FUTURE_EPOCH))
        .isEqualTo(false);
  }

  @Test
  void initiateValidatorExit() {
    BeaconState state = newState(1);
    int validator_index = 0;

    BeaconStateUtil.initiate_validator_exit(state, validator_index);
    assertThat(
            !state
                .getValidators()
                .get(validator_index)
                .getExit_epoch()
                .equals(FAR_FUTURE_EPOCH))
        .isEqualTo(true);
  }

  @Test
  void deepCopyBeaconState() {
    BeaconStateWithCache state = (BeaconStateWithCache) newState(1);
    BeaconState deepCopy = BeaconStateWithCache.deepCopy(state);

    // Test slot
    state.incrementSlot();
    assertThat(deepCopy.getSlot()).isNotEqualTo(state.getSlot());

    // Test fork
    state.setFork(new Fork(Bytes.random(4), Bytes.random(4), UnsignedLong.ONE));
    assertThat(deepCopy.getFork().getPrevious_version())
        .isNotEqualTo(state.getFork().getPrevious_version());

    // Test validator registry
    ArrayList<Validator> new_records = new ArrayList<>(Collections.nCopies(12, new Validator()));
    deepCopy.setValidators(new_records);
    assertThat(deepCopy.getValidators().get(0).getPubkey())
        .isNotEqualTo(state.getValidators().get(0).getPubkey());
  }

  private Bytes32 hashSrc() {
    Bytes bytes = Bytes.wrap(new byte[] {(byte) 1, (byte) 256, (byte) 65656});
    Security.addProvider(new BouncyCastleProvider());
    return Hash.keccak256(bytes);
  }

  @Test
  void getRandaoMixReturnsCorrectValue() {
    BeaconState state = newState(1);
    state.setSlot(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH * SLOTS_PER_EPOCH));
    assertThat(get_current_epoch(state).compareTo(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH)))
        .isEqualTo(0);
    List<Bytes32> latest_randao_mixes = state.getLatest_randao_mixes();
    latest_randao_mixes.set(0, Bytes32.fromHexString("0x42"));
    latest_randao_mixes.set(1, Bytes32.fromHexString("0x029a"));
    latest_randao_mixes.set(LATEST_RANDAO_MIXES_LENGTH - 1, Bytes32.fromHexString("0xdeadbeef"));

    assertThat(get_randao_mix(state, UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH)))
        .isEqualTo(Bytes32.fromHexString("0x42"));
    assertThat(get_randao_mix(state, UnsignedLong.valueOf(1)))
        .isEqualTo(Bytes32.fromHexString("0x029a"));
    assertThat(get_randao_mix(state, UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH - 1)))
        .isEqualTo(Bytes32.fromHexString("0xdeadbeef"));
  }

  @Test
  void generateSeedReturnsCorrectValue() {
    BeaconState state = newState(1);
    state.setSlot(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH * SLOTS_PER_EPOCH));
    assertThat(get_current_epoch(state).compareTo(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH)))
        .isEqualTo(0);
    List<Bytes32> latest_randao_mixes = state.getRandao_mixes();
    latest_randao_mixes.set(ACTIVATION_EXIT_DELAY + 1, Bytes32.fromHexString("0x029a"));

    UnsignedLong epoch = UnsignedLong.valueOf(ACTIVATION_EXIT_DELAY + MIN_SEED_LOOKAHEAD + 1);
    Bytes32 randao_mix =
        get_randao_mix(state, epoch.minus(UnsignedLong.valueOf(MIN_SEED_LOOKAHEAD)));
    assertThat(randao_mix).isEqualTo(Bytes32.fromHexString("0x029a"));
    try {
      Security.addProvider(new BouncyCastleProvider());
      assertThat(get_seed(state, epoch))
          .isEqualTo(
              Hash.sha2_256(
                  Bytes.wrap(
                      Bytes32.fromHexString("0x029a"),
                      get_active_index_root(state, epoch),
                      int_to_bytes32(epoch))));
    } catch (IllegalStateException e) {
      fail(e.toString());
    }
  }

  @Test
  void roundtripSSZ() {
    // NOTE: If the numDeposits below ever increases above one,
    // we'll need a better way of syncing deposit indexes with our test data.
    // The fix in place right now is a hack.
    BeaconState state = newState(1);
    BeaconState sszBeaconState = BeaconState.fromBytes(state.toBytes());
    assertEquals(state, sszBeaconState);
  }

  @Test
  @Disabled
  @SuppressWarnings("unchecked")
  void newDepositGeneration() {

    int numDeposits = 128;
    List<String> deposits = new ArrayList<>();
    List<String> publicKeys = new ArrayList<>();
    List<String> privateKeys = new ArrayList<>();

    JSONObject jsonObject = new JSONObject();

    for (int i = 0; i < numDeposits; i++) {
      // https://github.com/ethereum/eth2.0-specs/blob/0.4.0/specs/validator/0_beacon-chain-validator.md#submit-deposit
      BLSKeyPair keypair = BLSKeyPair.random(i);
      DepositData depositData =
          new DepositData(
              keypair.getPublicKey(),
              Bytes32.ZERO,
              UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE),
              BLSSignature.empty());
      BLSSignature proof_of_possession =
          BLSSignature.sign(
              keypair, depositData.signing_root("signature"), bls_domain(Constants.DOMAIN_DEPOSIT));
      depositData.setSignature(proof_of_possession);

      List<Bytes32> proof =
          new ArrayList<>(Collections.nCopies(DEPOSIT_CONTRACT_TREE_DEPTH, Bytes32.ZERO));
      Deposit deposit = new Deposit(proof, depositData);
      deposits.add(deposit.toBytes().toHexString());
      publicKeys.add(keypair.getPublicKey().toBytes().toHexString());
      privateKeys.add(keypair.getSecretKey().getSecretKey().toBytes().toHexString());
    }

    jsonObject.put("deposits", deposits);
    jsonObject.put("publicKeys", publicKeys);
    jsonObject.put("privateKeys", privateKeys);

    // Write JSON file
    try (Writer file =
        Files.newBufferedWriter(Paths.get("../../interopDepositsAndKeys.json"), UTF_8)) {

      file.write(jsonObject.toJSONString());
      file.flush();

    } catch (IOException e) {
      System.out.println(e.toString());
    }
  }

  @Test
  @Disabled
  @SuppressWarnings("unchecked")
  void newDepositPubkeysReading() {
    try {
      Path path = Paths.get("../../interopDepositsAndKeys.json");
      String read = Files.readAllLines(path).get(0);
      JSONParser parser = new JSONParser();
      Object obj = parser.parse(read);
      JSONObject array = (JSONObject) obj;
      JSONArray publicKeyStrings = (JSONArray) array.get("publicKeys");
      List<BLSPublicKey> readPubkeys = new ArrayList<>();
      publicKeyStrings.forEach(
          string ->
              readPubkeys.add(BLSPublicKey.fromBytes(Bytes.fromHexString(string.toString()))));
      System.out.println(readPubkeys);
      List<BLSPublicKey> originalPubkeys = new ArrayList<>();
      IntStream.range(0, 16)
          .forEach(
              i -> {
                BLSKeyPair keypair = BLSKeyPair.random(i);
                originalPubkeys.add(keypair.getPublicKey());
              });
      assertEquals(readPubkeys, originalPubkeys);
    } catch (IOException | ParseException e) {
      System.out.println(e.toString());
    }
  }

  @Test
  @Disabled
  @SuppressWarnings("unchecked")
  void newDepositPrivateKeysReading() {
    try {
      Path path = Paths.get("../../interopDepositsAndKeys.json");
      String read = Files.readAllLines(path).get(0);
      JSONParser parser = new JSONParser();
      Object obj = parser.parse(read);
      JSONObject array = (JSONObject) obj;
      JSONArray privateKeyStrings = (JSONArray) array.get("privateKeys");
      List<BLSSecretKey> readKeyPairs = new ArrayList<>();
      privateKeyStrings.forEach(
          string ->
              readKeyPairs.add(
                  new BLSKeyPair(
                          new KeyPair(SecretKey.fromBytes(Bytes.fromHexString(string.toString()))))
                      .getSecretKey()));
      System.out.println(readKeyPairs);
      IntStream.range(0, 16)
          .forEach(
              i -> {
                BLSKeyPair keypair = BLSKeyPair.random(i);
                assertEquals(
                    readKeyPairs.get(i).getSecretKey(), keypair.getSecretKey().getSecretKey());
              });
    } catch (IOException | ParseException e) {
      System.out.println(e.toString());
    }
  }

  @Test
  @Disabled
  @SuppressWarnings("unchecked")
  void newDepositReading() {
    List<Deposit> originalDeposits = newDeposits(16);
    try {
      Path path = Paths.get("../../interopDepositsAndKeys.json");
      String read = Files.readAllLines(path).get(0);
      JSONParser parser = new JSONParser();
      Object obj = parser.parse(read);
      JSONObject array = (JSONObject) obj;
      JSONArray privateKeyStrings = (JSONArray) array.get("deposits");
      List<Deposit> readDeposits = new ArrayList<>();
      privateKeyStrings.forEach(
          string -> readDeposits.add(Deposit.fromBytes(Bytes.fromHexString(string.toString()))));
      IntStream.range(0, 16)
          .forEach(
              i -> {
                assertEquals(originalDeposits.get(i), readDeposits.get(i));
              });
    } catch (IOException | ParseException e) {
      System.out.println(e.toString());
    }
  }
  */

  @Test
  void simple1() {
    BeaconStateRead s1 = new BeaconState();
    System.out.println(s1.hash_tree_root());
  }

  @Test
  void vectorLengthsTest() {
    List<Integer> vectorLengths =
        List.of(
            Constants.SLOTS_PER_HISTORICAL_ROOT,
            Constants.SLOTS_PER_HISTORICAL_ROOT,
            Constants.EPOCHS_PER_HISTORICAL_VECTOR,
            Constants.EPOCHS_PER_SLASHINGS_VECTOR);
    assertEquals(
        vectorLengths,
        SimpleOffsetSerializer.classReflectionInfo.get(BeaconState.class).getVectorLengths());
  }
}
