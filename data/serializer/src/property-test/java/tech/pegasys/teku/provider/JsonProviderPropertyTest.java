/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.provider;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringEscapeUtils.unescapeJava;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Map;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.AttestationData;
import tech.pegasys.teku.api.schema.AttesterSlashing;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlockHeader;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.Checkpoint;
import tech.pegasys.teku.api.schema.Deposit;
import tech.pegasys.teku.api.schema.DepositData;
import tech.pegasys.teku.api.schema.Eth1Data;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.api.schema.IndexedAttestation;
import tech.pegasys.teku.api.schema.KZGCommitment;
import tech.pegasys.teku.api.schema.PendingAttestation;
import tech.pegasys.teku.api.schema.ProposerSlashing;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.Validator;
import tech.pegasys.teku.api.schema.VoluntaryExit;
import tech.pegasys.teku.api.schema.altair.BeaconStateAltair;
import tech.pegasys.teku.api.schema.altair.SignedBeaconBlockAltair;
import tech.pegasys.teku.api.schema.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.api.schema.bellatrix.SignedBeaconBlockBellatrix;
import tech.pegasys.teku.api.schema.capella.BeaconStateCapella;
import tech.pegasys.teku.api.schema.capella.SignedBeaconBlockCapella;
import tech.pegasys.teku.api.schema.deneb.BeaconStateDeneb;
import tech.pegasys.teku.api.schema.deneb.BlobSidecar;
import tech.pegasys.teku.api.schema.deneb.BlobsSidecar;
import tech.pegasys.teku.api.schema.deneb.SignedBeaconBlockAndBlobSidecar;
import tech.pegasys.teku.api.schema.deneb.SignedBeaconBlockAndBlobsSidecar;
import tech.pegasys.teku.api.schema.deneb.SignedBeaconBlockDeneb;
import tech.pegasys.teku.api.schema.phase0.BeaconStatePhase0;
import tech.pegasys.teku.api.schema.phase0.SignedBeaconBlockPhase0;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBeaconBlockAndBlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBeaconBlockAndBlobsSidecarSchema;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.propertytest.suppliers.SpecSupplier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class JsonProviderPropertyTest {
  private static final String Q = "\"";
  private final JsonProvider jsonProvider = new JsonProvider();

  private static final Map<SpecMilestone, Class<? extends SignedBeaconBlock>>
      SIGNED_BEACON_BLOCK_CLASS_MAP =
          Map.of(
              SpecMilestone.PHASE0,
              SignedBeaconBlockPhase0.class,
              SpecMilestone.ALTAIR,
              SignedBeaconBlockAltair.class,
              SpecMilestone.BELLATRIX,
              SignedBeaconBlockBellatrix.class,
              SpecMilestone.CAPELLA,
              SignedBeaconBlockCapella.class,
              SpecMilestone.DENEB,
              SignedBeaconBlockDeneb.class);

  private static final Map<SpecMilestone, Class<? extends BeaconState>> BEACON_STATE_CLASS_MAP =
      Map.of(
          SpecMilestone.PHASE0,
          BeaconStatePhase0.class,
          SpecMilestone.ALTAIR,
          BeaconStateAltair.class,
          SpecMilestone.BELLATRIX,
          BeaconStateBellatrix.class,
          SpecMilestone.CAPELLA,
          BeaconStateCapella.class,
          SpecMilestone.DENEB,
          BeaconStateDeneb.class);

  @Property
  void roundTripBytes32(@ForAll @Size(32) final byte[] value) throws JsonProcessingException {
    Bytes32 data = Bytes32.wrap(value);
    String serialized = jsonProvider.objectToJSON(data);
    assertEquals(Q + data.toHexString().toLowerCase() + Q, serialized);
    Bytes32 deserialize = jsonProvider.jsonToObject(serialized, Bytes32.class);
    assertEquals(data, deserialize);
  }

  @Property
  void roundTripUInt256(@ForAll @Size(32) final byte[] value) throws JsonProcessingException {
    final Bytes bytes = Bytes.wrap(value);
    final UInt256 original = UInt256.fromBytes(bytes);
    final String serialized = jsonProvider.objectToJSON(original);
    assertEquals(serialized, Q + original.toDecimalString() + Q);
    final UInt256 deserialized = jsonProvider.jsonToObject(serialized, UInt256.class);
    assertEquals(deserialized, original);
  }

  @Property
  void roundTripUInt64(@ForAll final long value) throws JsonProcessingException {
    final UInt64 original = UInt64.fromLongBits(value);
    final String serialized = jsonProvider.objectToJSON(original);
    assertEquals(serialized, Q + original.toString() + Q);
    final UInt64 deserialized = jsonProvider.jsonToObject(serialized, UInt64.class);
    assertEquals(deserialized, original);
  }

  @Property
  void serializeString(@ForAll final String original) throws JsonProcessingException {
    final String serialized = jsonProvider.objectToJSON(original);
    assertThat(unescapeJava(serialized).getBytes(UTF_8))
        .isEqualTo((Q + original + Q).getBytes(UTF_8));
  }

  @Property
  void roundTripByteArray(@ForAll final byte[] original) throws JsonProcessingException {
    final String serialized = jsonProvider.objectToJSON(original);
    assertEquals(serialized, byteArrayToUnsignedStringWithQuotesAndNoSpaces(original));
    final byte[] deserialized = jsonProvider.jsonToObject(serialized, byte[].class);
    assertThat(deserialized).isEqualTo(original);
  }

  static String byteArrayToUnsignedStringWithQuotesAndNoSpaces(final byte[] bytes) {
    return Arrays.toString(
            Arrays.asList(ArrayUtils.toObject(bytes)).stream()
                .map(Byte::toUnsignedInt)
                .map(s -> Q + s + Q)
                .toArray())
        .replace(" ", "");
  }

  @Property
  void roundTripBlsPubKey(@ForAll final int seed, @ForAll(supplier = SpecSupplier.class) Spec spec)
      throws JsonProcessingException {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final BLSPubKey original = new BLSPubKey(dataStructureUtil.randomPublicKey());
    final String serialized = jsonProvider.objectToJSON(original);
    final BLSPubKey deserialized = jsonProvider.jsonToObject(serialized, BLSPubKey.class);
    assertThat(deserialized).isEqualTo(original);
  }

  @Property
  void roundTripBlsSignature(
      @ForAll final int seed, @ForAll(supplier = SpecSupplier.class) Spec spec)
      throws JsonProcessingException {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final BLSSignature original = new BLSSignature(dataStructureUtil.randomSignature());
    final String serialized = jsonProvider.objectToJSON(original);
    final BLSSignature deserialized = jsonProvider.jsonToObject(serialized, BLSSignature.class);
    assertThat(deserialized).isEqualTo(original);
  }

  @Property
  public void roundTripBitVector(
      @ForAll final int seed,
      @ForAll(supplier = SpecSupplier.class) Spec spec,
      @ForAll @IntRange(min = 1, max = 1000) final int size)
      throws JsonProcessingException {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final SszBitvector original = dataStructureUtil.randomSszBitvector(size);
    final String serialized = jsonProvider.objectToJSON(original);
    final SszBitvector deserialized =
        JsonUtil.parse(serialized, original.getSchema().getJsonTypeDefinition());
    assertThat(deserialized).isEqualTo(original);
  }

  @Property
  public void roundTripFork(
      @ForAll final int seed, @ForAll(supplier = SpecSupplier.class) Spec spec)
      throws JsonProcessingException {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final Fork original = new Fork(dataStructureUtil.randomFork());
    final String serialized = jsonProvider.objectToJSON(original);
    final Object deserialized = jsonProvider.jsonToObject(serialized, Fork.class);
    assertThat(deserialized).isEqualToComparingFieldByField(original);
  }

  @Property
  public void roundTripCheckpoint(
      @ForAll final int seed,
      @ForAll(supplier = SpecSupplier.class) Spec spec,
      @ForAll final long epoch)
      throws JsonProcessingException {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final Checkpoint original =
        new Checkpoint(dataStructureUtil.randomCheckpoint(UInt64.fromLongBits(epoch)));
    final String serialized = jsonProvider.objectToJSON(original);
    final Object deserialized = jsonProvider.jsonToObject(serialized, Checkpoint.class);
    assertThat(deserialized).isEqualToComparingFieldByField(original);
  }

  @Property
  public void roundTripValidator(
      @ForAll final int seed, @ForAll(supplier = SpecSupplier.class) Spec spec)
      throws JsonProcessingException {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final Validator original = new Validator(dataStructureUtil.randomValidator());
    final String serialized = jsonProvider.objectToJSON(original);
    final Object deserialized = jsonProvider.jsonToObject(serialized, Validator.class);
    assertThat(deserialized).isEqualToComparingFieldByField(original);
  }

  @Property
  public void roundTripAttestationData(
      @ForAll final int seed, @ForAll(supplier = SpecSupplier.class) Spec spec)
      throws JsonProcessingException {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final AttestationData original = new AttestationData(dataStructureUtil.randomAttestationData());
    final String serialized = jsonProvider.objectToJSON(original);
    final Object deserialized = jsonProvider.jsonToObject(serialized, AttestationData.class);
    assertThat(deserialized).isEqualToComparingFieldByField(original);
  }

  @Property
  public void roundTripIndexedAttestation(
      @ForAll final int seed, @ForAll(supplier = SpecSupplier.class) Spec spec)
      throws JsonProcessingException {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final IndexedAttestation original =
        new IndexedAttestation(dataStructureUtil.randomIndexedAttestation());
    final String serialized = jsonProvider.objectToJSON(original);
    final Object deserialized = jsonProvider.jsonToObject(serialized, IndexedAttestation.class);
    assertThat(deserialized).isEqualToComparingFieldByField(original);
  }

  @Property
  public void roundTripPendingAttestation(@ForAll final int seed, @ForAll final Eth2Network network)
      throws JsonProcessingException {
    final SpecMilestone specMilestone = SpecMilestone.PHASE0;
    final Spec spec = TestSpecFactory.create(specMilestone, network);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final PendingAttestation original =
        new PendingAttestation(dataStructureUtil.randomPendingAttestation());
    final String serialized = jsonProvider.objectToJSON(original);
    final Object deserialized = jsonProvider.jsonToObject(serialized, PendingAttestation.class);
    assertThat(deserialized).isEqualToComparingFieldByField(original);
  }

  @Property
  public void roundTripEth1Data(
      @ForAll final int seed, @ForAll(supplier = SpecSupplier.class) Spec spec)
      throws JsonProcessingException {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final Eth1Data original = new Eth1Data(dataStructureUtil.randomEth1Data());
    final String serialized = jsonProvider.objectToJSON(original);
    final Object deserialized = jsonProvider.jsonToObject(serialized, Eth1Data.class);
    assertThat(deserialized).isEqualToComparingFieldByField(original);
  }

  @Property
  public void roundTripDepositData(
      @ForAll final int seed, @ForAll(supplier = SpecSupplier.class) Spec spec)
      throws JsonProcessingException {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final DepositData original = new DepositData(dataStructureUtil.randomDepositData());
    final String serialized = jsonProvider.objectToJSON(original);
    final Object deserialized = jsonProvider.jsonToObject(serialized, DepositData.class);
    assertThat(deserialized).isEqualToComparingFieldByField(original);
  }

  @Property
  public void roundTripBeaconBlockHeader(
      @ForAll final int seed, @ForAll(supplier = SpecSupplier.class) Spec spec)
      throws JsonProcessingException {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final BeaconBlockHeader original =
        new BeaconBlockHeader(dataStructureUtil.randomBeaconBlockHeader());
    final String serialized = jsonProvider.objectToJSON(original);
    final Object deserialized = jsonProvider.jsonToObject(serialized, BeaconBlockHeader.class);
    assertThat(deserialized).isEqualToComparingFieldByField(original);
  }

  @Property
  public void roundTripBeaconProposerSlashing(
      @ForAll final int seed, @ForAll(supplier = SpecSupplier.class) Spec spec)
      throws JsonProcessingException {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final ProposerSlashing original =
        new ProposerSlashing(dataStructureUtil.randomProposerSlashing());
    final String serialized = jsonProvider.objectToJSON(original);
    final Object deserialized = jsonProvider.jsonToObject(serialized, ProposerSlashing.class);
    assertThat(deserialized).isEqualToComparingFieldByField(original);
  }

  @Property
  public void roundTripBeaconAttesterSlashing(
      @ForAll final int seed, @ForAll(supplier = SpecSupplier.class) Spec spec)
      throws JsonProcessingException {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final AttesterSlashing original =
        new AttesterSlashing(dataStructureUtil.randomAttesterSlashing());
    final String serialized = jsonProvider.objectToJSON(original);
    final Object deserialized = jsonProvider.jsonToObject(serialized, AttesterSlashing.class);
    assertThat(deserialized).isEqualToComparingFieldByField(original);
  }

  @Property
  public void roundTripBeaconAttestation(
      @ForAll final int seed, @ForAll(supplier = SpecSupplier.class) Spec spec)
      throws JsonProcessingException {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final Attestation original = new Attestation(dataStructureUtil.randomAttestation());
    final String serialized = jsonProvider.objectToJSON(original);
    final Object deserialized = jsonProvider.jsonToObject(serialized, Attestation.class);
    assertThat(deserialized).isEqualToComparingFieldByField(original);
  }

  @Property
  public void roundTripDeposit(
      @ForAll final int seed, @ForAll(supplier = SpecSupplier.class) Spec spec)
      throws JsonProcessingException {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final Deposit original = new Deposit(dataStructureUtil.randomDeposit());
    final String serialized = jsonProvider.objectToJSON(original);
    final Object deserialized = jsonProvider.jsonToObject(serialized, Deposit.class);
    assertThat(deserialized).isEqualToComparingFieldByField(original);
  }

  @Property
  public void roundTripVoluntaryExit(
      @ForAll final int seed, @ForAll(supplier = SpecSupplier.class) Spec spec)
      throws JsonProcessingException {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final VoluntaryExit original = new VoluntaryExit(dataStructureUtil.randomVoluntaryExit());
    final String serialized = jsonProvider.objectToJSON(original);
    final Object deserialized = jsonProvider.jsonToObject(serialized, VoluntaryExit.class);
    assertThat(deserialized).isEqualToComparingFieldByField(original);
  }

  @Property(tries = 100)
  public void roundTripSignedBeaconBlock(
      @ForAll final int seed,
      @ForAll(supplier = SpecSupplier.class) Spec spec,
      @ForAll final long slot,
      @ForAll @Size(32) final byte[] parentRoot,
      @ForAll @Size(32) final byte[] stateRoot,
      @ForAll final boolean isFull)
      throws Exception {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final Class<? extends SignedBeaconBlock> clazz =
        SIGNED_BEACON_BLOCK_CLASS_MAP.get(spec.getForkSchedule().getHighestSupportedMilestone());
    final Constructor<?> constructor =
        clazz.getConstructor(tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock.class);
    final Object original =
        constructor.newInstance(
            dataStructureUtil.signedBlock(
                dataStructureUtil.randomBeaconBlock(
                    UInt64.fromLongBits(slot),
                    Bytes32.wrap(parentRoot),
                    Bytes32.wrap(stateRoot),
                    isFull)));
    final String serialized = jsonProvider.objectToJSON(original);
    final Object deserialized = jsonProvider.jsonToObject(serialized, clazz);
    assertThat(deserialized).isEqualToComparingFieldByField(original);
  }

  @Property(tries = 100)
  public void roundTripBeaconState(
      @ForAll final int seed,
      @ForAll(supplier = SpecSupplier.class) Spec spec,
      @ForAll @IntRange(max = 1000) final int validatorCount,
      @ForAll @IntRange(max = 1000) final int numItemsInSSZLists)
      throws Exception {
    final SpecMilestone specMilestone = spec.getForkSchedule().getHighestSupportedMilestone();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final Class<? extends BeaconState> clazz = BEACON_STATE_CLASS_MAP.get(specMilestone);
    final Constructor<? extends BeaconState> constructor =
        clazz.getConstructor(
            tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState.class);
    final BeaconState original =
        constructor.newInstance(
            dataStructureUtil.randomBeaconState(validatorCount, numItemsInSSZLists));
    final DeserializableTypeDefinition<? extends SszData> stateTypeDefinition =
        spec.forMilestone(specMilestone)
            .getSchemaDefinitions()
            .getBeaconStateSchema()
            .getJsonTypeDefinition();

    final String serialized = jsonProvider.objectToJSON(original);
    final SszData deserialized = JsonUtil.parse(serialized, stateTypeDefinition);
    assertThat(deserialized.hashTreeRoot())
        .isEqualTo(original.asInternalBeaconState(spec).hashTreeRoot());
  }

  @Property
  void roundTripKZGCommitment(@ForAll final int seed) throws JsonProcessingException {
    final SpecMilestone specMilestone = SpecMilestone.DENEB;
    final Spec spec = TestSpecFactory.create(specMilestone, Eth2Network.MINIMAL);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final KZGCommitment original = new KZGCommitment(dataStructureUtil.randomKZGCommitment());
    final String serialized = jsonProvider.objectToJSON(original);
    final KZGCommitment deserialized = jsonProvider.jsonToObject(serialized, KZGCommitment.class);
    assertThat(deserialized).isEqualTo(original);
  }

  @Property
  void roundTripBlobsSidecar(@ForAll final int seed) throws JsonProcessingException {
    final SpecMilestone specMilestone = SpecMilestone.DENEB;
    final Spec spec = TestSpecFactory.create(specMilestone, Eth2Network.MINIMAL);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final BlobsSidecar original = new BlobsSidecar(dataStructureUtil.randomBlobsSidecar());
    final String serialized = jsonProvider.objectToJSON(original);
    final BlobsSidecar deserialized = jsonProvider.jsonToObject(serialized, BlobsSidecar.class);
    assertThat(deserialized).usingRecursiveComparison().isEqualTo(original);
  }

  @Property
  void roundTripBlobSidecar(@ForAll final int seed) throws JsonProcessingException {
    final SpecMilestone specMilestone = SpecMilestone.DENEB;
    final Spec spec = TestSpecFactory.create(specMilestone, Eth2Network.MINIMAL);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final BlobSidecar original = new BlobSidecar(dataStructureUtil.randomBlobSidecar());
    final String serialized = jsonProvider.objectToJSON(original);
    final BlobSidecar deserialized = jsonProvider.jsonToObject(serialized, BlobSidecar.class);
    assertThat(deserialized).usingRecursiveComparison().isEqualTo(original);
  }

  @Property
  void roundTripSignedBeaconBlockAndBlobsSidecar(@ForAll final int seed)
      throws JsonProcessingException {
    final SpecMilestone specMilestone = SpecMilestone.DENEB;
    final Spec spec = TestSpecFactory.create(specMilestone, Eth2Network.MINIMAL);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final SignedBeaconBlockAndBlobsSidecar original =
        new SignedBeaconBlockAndBlobsSidecar(
            dataStructureUtil.randomSignedBeaconBlockAndBlobsSidecar());
    final String serialized = jsonProvider.objectToJSON(original);
    final SignedBeaconBlockAndBlobsSidecar deserialized =
        jsonProvider.jsonToObject(serialized, SignedBeaconBlockAndBlobsSidecar.class);
    final SignedBeaconBlockAndBlobsSidecarSchema signedBeaconBlockAndBlobsSidecarSchema =
        spec.getGenesisSchemaDefinitions()
            .toVersionDeneb()
            .orElseThrow()
            .getSignedBeaconBlockAndBlobsSidecarSchema();
    assertThat(
            deserialized.asInternalSignedBeaconBlockAndBlobsSidecar(
                signedBeaconBlockAndBlobsSidecarSchema, spec))
        .isEqualTo(
            original.asInternalSignedBeaconBlockAndBlobsSidecar(
                signedBeaconBlockAndBlobsSidecarSchema, spec));
  }

  @Property
  void roundTripSignedBeaconBlockAndBlobSidecar(@ForAll final int seed)
      throws JsonProcessingException {
    final SpecMilestone specMilestone = SpecMilestone.DENEB;
    final Spec spec = TestSpecFactory.create(specMilestone, Eth2Network.MINIMAL);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final SignedBeaconBlockAndBlobSidecar original =
        new SignedBeaconBlockAndBlobSidecar(
            dataStructureUtil.randomSignedBeaconBlockAndBlobSidecar());
    final String serialized = jsonProvider.objectToJSON(original);
    final SignedBeaconBlockAndBlobSidecar deserialized =
        jsonProvider.jsonToObject(serialized, SignedBeaconBlockAndBlobSidecar.class);
    final SignedBeaconBlockAndBlobSidecarSchema signedBeaconBlockAndBlobSidecarSchema =
        spec.getGenesisSchemaDefinitions()
            .toVersionDeneb()
            .orElseThrow()
            .getSignedBeaconBlockAndBlobSidecarSchema();
    assertThat(
            deserialized.asInternalSignedBeaconBlockAndBlobSidecar(
                signedBeaconBlockAndBlobSidecarSchema, spec))
        .isEqualTo(
            original.asInternalSignedBeaconBlockAndBlobSidecar(
                signedBeaconBlockAndBlobSidecarSchema, spec));
  }
}
