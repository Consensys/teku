/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.util;

import static com.google.common.base.Preconditions.checkState;
import static ethereum.ckzg4844.CKZG4844JNI.BYTES_PER_CELL;
import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.ethereum.pow.api.DepositConstants.DEPOSIT_CONTRACT_TREE_DEPTH;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.kzg.KZG.CELLS_PER_EXT_BLOB;
import static tech.pegasys.teku.spec.constants.NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.SIGNED_VALIDATOR_REGISTRATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.VALIDATOR_REGISTRATION_SCHEMA;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import it.unimi.dsi.fastutil.ints.IntList;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.ssz.Merkleizable;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveVector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64Vector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64VectorSchema;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCell;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobKzgCommitmentsSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.Cell;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.CellSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumn;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodySchemaCapella;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodySchemaDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodySchemaGloas;
import tech.pegasys.teku.spec.datastructures.builder.BlobsBundleSchema;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBidBuilder;
import tech.pegasys.teku.spec.datastructures.builder.ExecutionPayloadAndBlobsBundle;
import tech.pegasys.teku.spec.datastructures.builder.ExecutionPayloadAndBlobsBundleSchema;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.builder.ValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BuilderPendingWithdrawal;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.IndexedPayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.IndexedPayloadAttestationSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadBuilder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProofSchema;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.TransactionSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsBundleDeneb;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsBuilderElectra;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.fulu.BlobsBundleFulu;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.interop.MockStartDepositGenerator;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrapSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientHeaderSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdateResponse;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdateResponseSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdateSchema;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EnrForkId;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.operations.DepositMessage;
import tech.pegasys.teku.spec.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.SingleAttestation;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncAggregatorSelectionData;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation.PendingAttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee.SyncCommitteeSchema;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0;
import tech.pegasys.teku.spec.datastructures.state.versions.capella.HistoricalSummary;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.util.DepositGenerator;
import tech.pegasys.teku.spec.datastructures.validator.BeaconPreparableProposer;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public final class DataStructureUtil {

  private static final int MAX_EP_RANDOM_TRANSACTIONS = 10;
  private static final int MAX_EP_RANDOM_TRANSACTIONS_SIZE = 32;

  private static final int MAX_EP_RANDOM_WITHDRAWALS = 4;
  private static final int MAX_EP_RANDOM_DEPOSIT_REQUESTS = 4;
  private static final int MAX_EP_RANDOM_WITHDRAWAL_REQUESTS = 2;
  private static final int MAX_EP_RANDOM_CONSOLIDATION_REQUESTS = 1;

  private final Spec spec;

  private int seed;
  private Supplier<BLSPublicKey> pubKeyGenerator = () -> BLSTestUtil.randomPublicKey(nextSeed());
  private Function<Integer, BLSSignature> signatureGenerator = BLSTestUtil::randomSignature;

  public DataStructureUtil(final Spec spec) {
    this(92892824, spec);
  }

  public DataStructureUtil(final int seed, final Spec spec) {
    this.seed = seed;
    this.spec = spec;
  }

  public DataStructureUtil withPubKeyGenerator(final Supplier<BLSPublicKey> pubKeyGenerator) {
    this.pubKeyGenerator = pubKeyGenerator;
    return this;
  }

  public DataStructureUtil withSignatureGenerator(
      final Function<Integer, BLSSignature> signatureGenerator) {
    this.signatureGenerator = signatureGenerator;
    return this;
  }

  private int nextSeed() {
    return seed++;
  }

  public long randomLong() {
    return new Random(nextSeed()).nextLong();
  }

  public long randomPositiveLong(final long bound) {
    return new Random(nextSeed()).longs(0, bound).findFirst().orElse(0L);
  }

  public int randomPositiveInt() {
    return randomInt(Integer.MAX_VALUE);
  }

  public int randomPositiveInt(final int bound) {
    return new Random(nextSeed()).ints(0, bound).findFirst().orElse(0);
  }

  public byte randomByte() {
    final byte[] bytes = new byte[1];
    new Random(nextSeed()).nextBytes(bytes);
    return bytes[0];
  }

  private boolean randomBoolean() {
    return new Random(nextSeed()).nextBoolean();
  }

  public UInt64 randomUInt64() {
    return UInt64.fromLongBits(randomLong());
  }

  public UInt64 randomUInt64(final long bound) {
    return UInt64.fromLongBits(randomPositiveLong(bound));
  }

  public UInt256 randomUInt256() {
    return UInt256.fromBytes(randomBytes(32));
  }

  public Eth1Address randomEth1Address() {
    return Eth1Address.fromHexString(randomBytes32().slice(0, 20).toHexString());
  }

  public Bytes4 randomBytes4() {
    return new Bytes4(randomBytes(4));
  }

  public Bytes20 randomBytes20() {
    return new Bytes20(randomBytes32().slice(0, 20));
  }

  public Bytes randomBytes256() {
    return randomBytes(256);
  }

  public Bytes32 randomBytes32() {
    final Random random = new Random(nextSeed());
    return Bytes32.random(random);
  }

  public Bytes48 randomBytes48() {
    final Random random = new Random(nextSeed());
    return Bytes48.random(random);
  }

  public Bytes8 randomBytes8() {
    return new Bytes8(randomBytes32().slice(0, 8));
  }

  public Bytes randomBytes(final int length) {
    final Random random = new Random(nextSeed());
    final byte[] result = new byte[length];
    random.nextBytes(result);
    return Bytes.wrap(result);
  }

  public String randomString(final int length) {
    return new String(randomBytes(length).toArrayUnsafe(), StandardCharsets.UTF_8);
  }

  public BLSSignature randomSignature() {
    return signatureGenerator.apply(nextSeed());
  }

  public SszSignature randomSszSignature() {
    return new SszSignature(randomSignature());
  }

  public <T extends SszData> SszList<T> randomSszList(
      final SszListSchema<T, ?> schema, final Supplier<T> valueGenerator, final long numItems) {
    return randomSszList(schema, numItems, valueGenerator);
  }

  public <T extends SszData> SszList<T> randomFullSszList(
      final SszListSchema<T, ?> schema, final Supplier<T> valueGenerator) {
    return randomSszList(schema, schema.getMaxLength(), valueGenerator);
  }

  public <T extends SszData> SszList<T> randomSszList(
      final SszListSchema<T, ?> schema, final long numItems, final Supplier<T> valueGenerator) {
    return Stream.generate(valueGenerator)
        .limit(Math.min(numItems, schema.getMaxLength()))
        .collect(schema.collector());
  }

  public <ElementT, SszElementT extends SszPrimitive<ElementT>>
      SszPrimitiveList<ElementT, SszElementT> randomSszPrimitiveList(
          final SszPrimitiveListSchema<ElementT, SszElementT, ?> schema,
          final long numItems,
          final Supplier<ElementT> valueGenerator) {
    return Stream.generate(valueGenerator)
        .limit(Math.min(numItems, schema.getMaxLength()))
        .collect(schema.collectorUnboxed());
  }

  public SszUInt64List randomSszUInt64List(final SszUInt64ListSchema<?> schema) {
    return randomSszUInt64List(
        schema, randomInt(Ints.saturatedCast(schema.getMaxLength() + 1)), this::randomUInt64);
  }

  public SszUInt64List randomSszUInt64List(
      final SszUInt64ListSchema<?> schema, final long numItems) {
    return randomSszUInt64List(schema, numItems, this::randomUInt64);
  }

  public SszUInt64List randomSszUInt64List(
      final SszUInt64ListSchema<?> schema,
      final long numItems,
      final Supplier<UInt64> valueGenerator) {
    return Stream.generate(valueGenerator).limit(numItems).collect(schema.collectorUnboxed());
  }

  public SszUInt64Vector randomSszUInt64Vector(
      final SszUInt64VectorSchema<?> schema, final long numItems) {
    return randomSszUInt64Vector(schema, numItems, this::randomUInt64);
  }

  public SszUInt64Vector randomSszUInt64Vector(
      final SszUInt64VectorSchema<?> schema,
      final long numItems,
      final Supplier<UInt64> valueGenerator) {
    return Stream.generate(valueGenerator).limit(numItems).collect(schema.collectorUnboxed());
  }

  public SszBytes32Vector randomSszBytes32Vector(
      final SszBytes32VectorSchema<?> schema, final Supplier<Bytes32> valueGenerator) {
    final int numItems = schema.getLength() / 10;
    final Bytes32 defaultElement = schema.getPrimitiveElementSchema().getDefault().get();
    return Stream.concat(
            Stream.generate(valueGenerator).limit(numItems),
            Stream.generate(() -> defaultElement).limit(schema.getLength() - numItems))
        .collect(schema.collectorUnboxed());
  }

  public <ElementT, SszElementT extends SszPrimitive<ElementT>>
      SszPrimitiveVector<ElementT, SszElementT> randomSszPrimitiveVector(
          final SszPrimitiveVectorSchema<ElementT, SszElementT, ?> schema,
          final Supplier<ElementT> valueGenerator) {
    final int numItems = schema.getLength() / 10;
    final ElementT defaultElement = schema.getPrimitiveElementSchema().getDefault().get();
    return Stream.concat(
            Stream.generate(valueGenerator).limit(numItems),
            Stream.generate(() -> defaultElement).limit(schema.getLength() - numItems))
        .collect(schema.collectorUnboxed());
  }

  public <SszElementT extends SszData, VectorT extends SszVector<SszElementT>>
      VectorT randomSszVector(
          final SszVectorSchema<SszElementT, VectorT> schema,
          final Supplier<SszElementT> valueGenerator) {
    final int numItems = schema.getLength() / 10;
    final SszElementT defaultElement = schema.getElementSchema().getDefault();
    return Stream.concat(
            Stream.generate(valueGenerator).limit(numItems),
            Stream.generate(() -> defaultElement).limit(schema.getLength() - numItems))
        .collect(schema.collector());
  }

  public SszByte randomSszByte() {
    return SszByte.of(randomByte());
  }

  public SszBitlist randomBitlist() {
    return randomBitlist(randomSlot());
  }

  public SszBitlist randomBitlist(final UInt64 slot) {
    return randomBitlist(getMaxValidatorsPerCommittee(slot));
  }

  public SszBitlist randomBitlist(final int n) {
    Random random = new Random(nextSeed());
    int[] bits = IntStream.range(0, n).sequential().filter(__ -> random.nextBoolean()).toArray();
    return SszBitlistSchema.create(n).ofBits(n, bits);
  }

  public SszBitvector randomCommitteeBitvector() {
    return randomSszBitvector(getMaxCommitteesPerSlot());
  }

  public SszBitvector randomSszBitvector(final int n) {
    Random random = new Random(nextSeed());
    int[] bits = IntStream.range(0, n).sequential().filter(__ -> random.nextBoolean()).toArray();
    return SszBitvectorSchema.create(n).ofBits(bits);
  }

  public BLSKeyPair randomKeyPair() {
    return BLSTestUtil.randomKeyPair(nextSeed());
  }

  public BLSPublicKey randomPublicKey() {
    return pubKeyGenerator.get();
  }

  public SszPublicKey randomSszPublicKey() {
    return new SszPublicKey(randomPublicKey());
  }

  public KZGCommitment randomKZGCommitment() {
    return KZGCommitment.fromBytesCompressed(randomBytes48());
  }

  public List<KZGCommitment> randomKZGCommitments(final int size) {
    return IntStream.range(0, size).mapToObj(__ -> randomKZGCommitment()).collect(toList());
  }

  public SszKZGCommitment randomSszKZGCommitment() {
    return new SszKZGCommitment(randomKZGCommitment());
  }

  public List<KZGProof> randomKZGProofs(final int size) {
    return IntStream.range(0, size).mapToObj(__ -> randomKZGProof()).collect(toList());
  }

  public KZGProof randomKZGProof() {
    return KZGProof.fromBytesCompressed(randomBytes48());
  }

  public SszKZGProof randomSszKZGProof() {
    return new SszKZGProof(randomKZGProof());
  }

  public Bytes48 randomPublicKeyBytes() {
    return pubKeyGenerator.get().toBytesCompressed();
  }

  public Eth1Data randomEth1Data() {
    return new Eth1Data(randomBytes32(), randomUInt64(), randomBytes32());
  }

  /**
   * A random UInt64 that is within a reasonable bound for an epoch number. The maximum value
   * returned won't be reached for another 12,000 years or so.
   */
  public UInt64 randomEpoch() {
    return UInt64.valueOf(randomInt(1_000_000_000));
  }

  /**
   * A random UInt64 that is within a reasonable bound for a slot number. The maximum value returned
   * won't be reached for another 12,000 years or so.
   */
  public UInt64 randomSlot() {
    return UInt64.valueOf(randomPositiveLong(32_000_000_000L));
  }

  /**
   * A random UInt64 that is within a reasonable bound for a validator index. Even tough
   * VALIDATOR_REGISTRY_LIMIT allows for more validators, Ethereum would run out of Ether before we
   * reached that many validators.
   */
  public UInt64 randomValidatorIndex() {
    return UInt64.valueOf(randomInt(3_000_000));
  }

  /** builders need to be staked in ePBS, so can use {@link #randomValidatorIndex()} */
  public UInt64 randomBuilderIndex() {
    return randomValidatorIndex();
  }

  public SlotAndBlockRoot randomSlotAndBlockRoot() {
    return randomSlotAndBlockRoot(randomUInt64());
  }

  public SlotAndBlockRoot randomSlotAndBlockRoot(final UInt64 slot) {
    return new SlotAndBlockRoot(slot, randomBytes32());
  }

  public Checkpoint randomCheckpoint(final long epoch) {
    return randomCheckpoint(UInt64.valueOf(epoch));
  }

  public Checkpoint randomCheckpoint(final UInt64 epoch) {
    return new Checkpoint(epoch, randomBytes32());
  }

  public Checkpoint randomCheckpoint() {
    return new Checkpoint(randomEpoch(), randomBytes32());
  }

  public SyncAggregate randomSyncAggregateIfRequiredBySchema(
      final BeaconBlockBodySchema<?> schema) {
    return schema.toVersionAltair().map(__ -> randomSyncAggregate()).orElse(null);
  }

  public SyncAggregate emptySyncAggregateIfRequiredByState(final BeaconState state) {
    return state.toVersionAltair().map(__ -> emptySyncAggregate()).orElse(null);
  }

  public SyncAggregate randomSyncAggregate() {
    return randomSyncAggregate(randomInt(4), randomInt(4));
  }

  public SyncAggregate emptySyncAggregate() {
    final SpecVersion specVersionAltair =
        Optional.ofNullable(spec.forMilestone(SpecMilestone.ALTAIR)).orElseThrow();

    return getSyncAggregateSchema(specVersionAltair).createEmpty();
  }

  public SyncAggregate randomSyncAggregate(final int... participantIndices) {
    final SpecVersion specVersionAltair =
        Optional.ofNullable(spec.forMilestone(SpecMilestone.ALTAIR)).orElseThrow();

    return getSyncAggregateSchema(specVersionAltair)
        .create(IntList.of(participantIndices), randomSignature());
  }

  private SyncAggregateSchema getSyncAggregateSchema(final SpecVersion specVersionAltair) {
    return SchemaDefinitionsAltair.required(specVersionAltair.getSchemaDefinitions())
        .getBeaconBlockBodySchema()
        .toVersionAltair()
        .orElseThrow()
        .getSyncAggregateSchema();
  }

  public SyncAggregatorSelectionData randomSyncAggregatorSelectionData() {
    final SpecVersion specVersionAltair =
        Optional.ofNullable(spec.forMilestone(SpecMilestone.ALTAIR)).orElseThrow();
    return specVersionAltair
        .getSchemaDefinitions()
        .toVersionAltair()
        .orElseThrow()
        .getSyncAggregatorSelectionDataSchema()
        .create(randomUInt64(), randomUInt64());
  }

  public BlobKzgCommitmentsSchema getBlobKzgCommitmentsSchema() {
    return SchemaDefinitionsDeneb.required(
            spec.forMilestone(SpecMilestone.DENEB).getSchemaDefinitions())
        .getBlobKzgCommitmentsSchema();
  }

  public SyncCommittee randomSyncCommittee() {
    final SyncCommitteeSchema syncCommitteeSchema =
        ((BeaconStateSchemaAltair)
                spec.forMilestone(SpecMilestone.ALTAIR)
                    .getSchemaDefinitions()
                    .getBeaconStateSchema())
            .getCurrentSyncCommitteeSchema();
    return syncCommitteeSchema.create(
        randomSszVector(
            syncCommitteeSchema.getPubkeysSchema(), () -> new SszPublicKey(randomPublicKey())),
        new SszPublicKey(randomPublicKey()));
  }

  public SyncCommittee randomSyncCommittee(final SszList<Validator> validators) {
    final SyncCommitteeSchema syncCommitteeSchema =
        ((BeaconStateSchemaAltair)
                spec.forMilestone(SpecMilestone.ALTAIR)
                    .getSchemaDefinitions()
                    .getBeaconStateSchema())
            .getCurrentSyncCommitteeSchema();
    return syncCommitteeSchema.create(
        randomSszVector(
            syncCommitteeSchema.getPubkeysSchema(),
            () -> new SszPublicKey(randomValidatorKey(validators))),
        new SszPublicKey(randomPublicKey()));
  }

  public ExecutionPayloadHeader randomExecutionPayloadHeader(
      final SpecVersion specVersion, final Bytes32 withdrawalsRoot) {
    final SpecConfigBellatrix specConfigBellatrix =
        SpecConfigBellatrix.required(specVersion.getConfig());
    return SchemaDefinitionsBellatrix.required(specVersion.getSchemaDefinitions())
        .getExecutionPayloadHeaderSchema()
        .createExecutionPayloadHeader(
            builder ->
                builder
                    .parentHash(randomBytes32())
                    .feeRecipient(randomBytes20())
                    .stateRoot(randomBytes32())
                    .receiptsRoot(randomBytes32())
                    .logsBloom(randomBytes(specConfigBellatrix.getBytesPerLogsBloom()))
                    .prevRandao(randomBytes32())
                    .blockNumber(randomUInt64())
                    .gasLimit(randomUInt64())
                    .gasUsed(randomUInt64())
                    .timestamp(randomUInt64())
                    .extraData(randomBytes(specConfigBellatrix.getMaxExtraDataBytes()))
                    .baseFeePerGas(randomUInt256())
                    .blockHash(randomBytes32())
                    .transactionsRoot(randomBytes32())
                    .withdrawalsRoot(() -> withdrawalsRoot)
                    .blobGasUsed(this::randomUInt64)
                    .excessBlobGas(this::randomUInt64));
  }

  public ExecutionPayloadHeader randomExecutionPayloadHeader(final SpecVersion specVersion) {
    return randomExecutionPayloadHeader(specVersion, randomBytes32());
  }

  public ExecutionPayloadHeader randomExecutionPayloadHeader() {
    final SpecVersion specVersion = spec.getGenesisSpec();
    return randomExecutionPayloadHeader(specVersion);
  }

  public BuilderBid randomBuilderBid() {
    return randomBuilderBid(__ -> {});
  }

  public BuilderBid randomBuilderBid(final Bytes32 withdrawalsRoot) {
    return randomBuilderBid(
        builder ->
            builder.header(randomExecutionPayloadHeader(spec.getGenesisSpec(), withdrawalsRoot)));
  }

  public BuilderBid randomBuilderBid(final Consumer<BuilderBidBuilder> builderModifier) {
    final SchemaDefinitionsBellatrix schemaDefinitions =
        getBellatrixSchemaDefinitions(randomSlot());
    return schemaDefinitions
        .getBuilderBidSchema()
        .createBuilderBid(
            builder -> {
              builder.header(randomExecutionPayloadHeader());
              schemaDefinitions
                  .toVersionDeneb()
                  .ifPresent(__ -> builder.blobKzgCommitments(randomBlobKzgCommitments()));
              schemaDefinitions
                  .toVersionElectra()
                  .ifPresent(__ -> builder.executionRequests(randomExecutionRequests()));
              // 1 ETH is 10^18 wei, Uint256 max is more than 10^77, so just to avoid
              // overflows in
              // computation
              builder.value(randomUInt256().divide(1000));
              builder.publicKey(randomPublicKey());
              builderModifier.accept(builder);
            });
  }

  public SignedBuilderBid randomSignedBuilderBid(final BuilderBid builderBid) {
    return getBellatrixSchemaDefinitions(randomSlot())
        .getSignedBuilderBidSchema()
        .create(builderBid, randomSignature());
  }

  public SignedBuilderBid randomSignedBuilderBid() {
    return getBellatrixSchemaDefinitions(randomSlot())
        .getSignedBuilderBidSchema()
        .create(randomBuilderBid(), randomSignature());
  }

  public SignedBuilderBid randomSignedBuilderBid(final Bytes32 withdrawalsRoot) {
    return getBellatrixSchemaDefinitions(randomSlot())
        .getSignedBuilderBidSchema()
        .create(randomBuilderBid(withdrawalsRoot), randomSignature());
  }

  public ExecutionPayload randomExecutionPayload() {
    return randomExecutionPayload(randomSlot());
  }

  public ExecutionPayload randomExecutionPayload(final UInt64 slot) {
    return randomExecutionPayload(slot, __ -> {});
  }

  public ExecutionPayload randomExecutionPayload(
      final UInt64 slot, final Consumer<ExecutionPayloadBuilder> builderModifier) {
    final SpecConfigBellatrix specConfigBellatrix =
        SpecConfigBellatrix.required(spec.atSlot(slot).getConfig());
    return getBellatrixSchemaDefinitions(slot)
        .getExecutionPayloadSchema()
        .createExecutionPayload(
            builder -> {
              final ExecutionPayloadBuilder executionPayloadBuilder =
                  builder
                      .parentHash(randomBytes32())
                      .feeRecipient(randomBytes20())
                      .stateRoot(randomBytes32())
                      .receiptsRoot(randomBytes32())
                      .logsBloom(randomBytes(specConfigBellatrix.getBytesPerLogsBloom()))
                      .prevRandao(randomBytes32())
                      .blockNumber(randomUInt64())
                      .gasLimit(randomUInt64())
                      .gasUsed(randomUInt64())
                      .timestamp(randomUInt64())
                      .extraData(randomBytes(specConfigBellatrix.getMaxExtraDataBytes()))
                      .baseFeePerGas(randomUInt256())
                      .blockHash(randomBytes32())
                      .transactions(randomExecutionPayloadTransactions())
                      .withdrawals(this::randomExecutionPayloadWithdrawals)
                      .blobGasUsed(this::randomUInt64)
                      .excessBlobGas(this::randomUInt64);
              builderModifier.accept(executionPayloadBuilder);
            });
  }

  public Transaction randomExecutionPayloadTransaction() {
    final TransactionSchema schema =
        getBellatrixSchemaDefinitions(randomSlot())
            .getExecutionPayloadSchema()
            .getTransactionSchema();
    return schema.fromBytes(Bytes.wrap(randomBytes(randomInt(MAX_EP_RANDOM_TRANSACTIONS_SIZE))));
  }

  public List<Bytes> randomExecutionPayloadTransactions() {
    return IntStream.rangeClosed(0, randomInt(MAX_EP_RANDOM_TRANSACTIONS))
        .mapToObj(__ -> randomBytes(randomInt(MAX_EP_RANDOM_TRANSACTIONS_SIZE)))
        .collect(toList());
  }

  public List<Withdrawal> randomExecutionPayloadWithdrawals() {
    return IntStream.rangeClosed(0, randomInt(MAX_EP_RANDOM_WITHDRAWALS))
        .mapToObj(__ -> randomWithdrawal())
        .collect(toList());
  }

  public List<DepositRequest> randomDepositRequests() {
    return IntStream.rangeClosed(0, randomInt(MAX_EP_RANDOM_DEPOSIT_REQUESTS))
        .mapToObj(__ -> randomDepositRequest())
        .collect(toList());
  }

  public List<WithdrawalRequest> randomWithdrawalRequests() {
    return IntStream.rangeClosed(0, randomInt(MAX_EP_RANDOM_WITHDRAWAL_REQUESTS))
        .mapToObj(__ -> randomWithdrawalRequest())
        .collect(toList());
  }

  public List<ConsolidationRequest> randomConsolidationRequests() {
    return IntStream.rangeClosed(0, randomInt(MAX_EP_RANDOM_CONSOLIDATION_REQUESTS))
        .mapToObj(__ -> randomConsolidationRequest())
        .collect(toList());
  }

  public ExecutionPayloadAndBlobsBundle randomExecutionPayloadAndBlobsBundle() {
    final SchemaDefinitionsDeneb schemaDefinitionsDeneb = getDenebSchemaDefinitions(randomSlot());
    final ExecutionPayload executionPayload = randomExecutionPayload();
    final tech.pegasys.teku.spec.datastructures.builder.BlobsBundle blobsBundle =
        randomBuilderBlobsBundle();

    final ExecutionPayloadAndBlobsBundleSchema schema =
        schemaDefinitionsDeneb.getExecutionPayloadAndBlobsBundleSchema();

    return new ExecutionPayloadAndBlobsBundle(schema, executionPayload, blobsBundle);
  }

  private BLSPublicKey randomValidatorKey(final SszList<Validator> validators) {
    final Random random = new Random(nextSeed());
    int rand = random.nextInt();
    while (rand < 0) {
      // Can't just use -1 * or Math.abs because -1 * Integer.MIN_VALUE == Integer.MIN_VALUE
      // And nextInt(validators.size()) is really not very random because of the way we use the seed
      rand = random.nextInt();
    }
    final int index = rand % validators.size();
    return validators.get(index).getPublicKey();
  }

  public SyncCommitteeMessage randomSyncCommitteeMessage() {
    return randomSyncCommitteeMessage(randomUInt64());
  }

  public SyncCommitteeMessage randomSyncCommitteeMessage(final long slot) {
    return randomSyncCommitteeMessage(UInt64.valueOf(slot));
  }

  public SyncCommitteeMessage randomSyncCommitteeMessage(final UInt64 slot) {
    return randomSyncCommitteeMessage(slot, randomBytes32());
  }

  public SyncCommitteeMessage randomSyncCommitteeMessage(
      final UInt64 slot, final Bytes32 beaconBlockRoot) {
    return getAltairSchemaDefinitions(ZERO)
        .getSyncCommitteeMessageSchema()
        .create(slot, beaconBlockRoot, randomUInt64(), randomSignature());
  }

  public AttestationData randomAttestationData() {
    return randomAttestationData(randomUInt64());
  }

  public AttestationData randomAttestationData(final UInt64 slot) {
    final UInt64 index =
        spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.ELECTRA)
            ? ZERO
            : randomUInt64();
    return new AttestationData(
        slot, index, randomBytes32(), randomCheckpoint(), randomCheckpoint());
  }

  public AttestationData randomAttestationData(final UInt64 slot, final UInt64 committeeIndex) {
    return new AttestationData(
        slot, committeeIndex, randomBytes32(), randomCheckpoint(), randomCheckpoint());
  }

  public AttestationData randomAttestationData(final UInt64 slot, final Bytes32 blockRoot) {
    return new AttestationData(
        slot,
        randomUInt64(),
        blockRoot,
        // Make checkpoint epochs realistic
        randomCheckpoint(spec.computeEpochAtSlot(slot).minusMinZero(1)),
        randomCheckpoint(spec.computeEpochAtSlot(slot)));
  }

  public Attestation randomAttestation() {
    return spec.getGenesisSchemaDefinitions()
        .getAttestationSchema()
        .create(
            randomBitlist(),
            randomAttestationData(),
            randomSignature(),
            this::randomCommitteeBitvector);
  }

  public SingleAttestation randomSingleAttestation() {
    return randomSingleAttestation(randomUInt64());
  }

  public SingleAttestation randomSingleAttestation(final UInt64 slot) {
    return spec.getGenesisSchemaDefinitions()
        .toVersionElectra()
        .orElseThrow()
        .getSingleAttestationSchema()
        .create(
            randomUInt64(Integer.MAX_VALUE),
            randomUInt64(Integer.MAX_VALUE),
            randomAttestationData(slot),
            randomSignature());
  }

  public SingleAttestation randomSingleAttestation(
      final UInt64 validatorIndex, final UInt64 committeeIndex) {
    return spec.getGenesisSchemaDefinitions()
        .toVersionElectra()
        .orElseThrow()
        .getSingleAttestationSchema()
        .create(committeeIndex, validatorIndex, randomAttestationData(), randomSignature());
  }

  public Attestation randomAttestation(final long slot) {
    return randomAttestation(UInt64.valueOf(slot));
  }

  public Attestation randomAttestation(final UInt64 slot) {
    return spec.atSlot(slot)
        .getSchemaDefinitions()
        .getAttestationSchema()
        .create(
            randomBitlist(slot),
            randomAttestationData(slot),
            randomSignature(),
            this::randomCommitteeBitvector);
  }

  public Attestation randomAttestation(final AttestationData attestationData) {
    return spec.getGenesisSchemaDefinitions()
        .getAttestationSchema()
        .create(
            randomBitlist(), attestationData, randomSignature(), this::randomCommitteeBitvector);
  }

  public AggregateAndProof randomAggregateAndProof() {
    return randomAggregateAndProof(randomUInt64());
  }

  public AggregateAndProof randomAggregateAndProof(final UInt64 slot) {
    return spec.getGenesisSchemaDefinitions()
        .getAggregateAndProofSchema()
        .create(randomUInt64(), randomAttestation(slot), randomSignature());
  }

  public SignedAggregateAndProof randomSignedAggregateAndProof() {
    return randomSignedAggregateAndProof(randomUInt64());
  }

  public SignedAggregateAndProof randomSignedAggregateAndProof(final UInt64 slot) {
    return spec.getGenesisSchemaDefinitions()
        .getSignedAggregateAndProofSchema()
        .create(randomAggregateAndProof(slot), randomSignature());
  }

  public VoteTracker randomVoteTracker() {
    return new VoteTracker(randomBytes32(), randomBytes32(), randomUInt64());
  }

  public PendingAttestation randomPendingAttestation() {
    final BeaconStateSchema<?, ?> beaconStateSchema = getBeaconStateSchema();
    checkState(
        beaconStateSchema instanceof BeaconStateSchemaPhase0,
        "Pending attestations only exist in phase0");
    final PendingAttestationSchema pendingAttestationSchema =
        ((BeaconStateSchemaPhase0) beaconStateSchema).getPendingAttestationSchema();
    return randomPendingAttestation(pendingAttestationSchema);
  }

  public PendingAttestation randomPendingAttestation(
      final PendingAttestationSchema pendingAttestationSchema) {
    return pendingAttestationSchema.create(
        randomBitlist(), randomAttestationData(), randomUInt64(), randomUInt64());
  }

  public AttesterSlashing randomAttesterSlashingAtSlot(final UInt64 slot) {
    final UInt64[] attestingIndices = {randomUInt64(), randomUInt64(), randomUInt64()};
    return spec.atSlot(slot)
        .getSchemaDefinitions()
        .getAttesterSlashingSchema()
        .create(
            randomIndexedAttestation(randomAttestationData(slot), attestingIndices),
            randomIndexedAttestation(randomAttestationData(slot), attestingIndices));
  }

  public AttesterSlashing randomAttesterSlashing() {
    return randomAttesterSlashing(
        randomSlot().longValue(), randomUInt64(), randomUInt64(), randomUInt64());
  }

  public AttesterSlashing randomAttesterSlashing(final UInt64... attestingIndices) {
    return randomAttesterSlashing(randomSlot().longValue(), attestingIndices);
  }

  public AttesterSlashing randomAttesterSlashing(
      final long slot, final UInt64... attestingIndices) {
    IndexedAttestation attestation1 = randomIndexedAttestation(attestingIndices);
    IndexedAttestation attestation2 = randomIndexedAttestation(attestingIndices);
    return spec.atSlot(UInt64.valueOf(slot))
        .getSchemaDefinitions()
        .getAttesterSlashingSchema()
        .create(attestation1, attestation2);
  }

  public List<SignedBeaconBlock> randomSignedBeaconBlockSequence(
      final SignedBeaconBlock parent, final int count) {
    return randomSignedBeaconBlockSequence(parent, count, false);
  }

  public List<SignedBeaconBlock> randomSignedBeaconBlockSequence(
      final SignedBeaconBlock parent, final int count, final boolean full) {
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    SignedBeaconBlock parentBlock = parent;
    for (int i = 0; i < count; i++) {
      final long nextSlot = parentBlock.getSlot().plus(UInt64.ONE).longValue();
      final Bytes32 parentRoot = parentBlock.getRoot();
      final SignedBeaconBlock block = randomSignedBeaconBlock(nextSlot, parentRoot, full);
      blocks.add(block);
      parentBlock = block;
    }
    return blocks;
  }

  public List<SignedBlockAndState> randomSignedBlockAndStateSequence(
      final SignedBeaconBlock parent, final int count, final boolean full) {
    final List<SignedBlockAndState> blocks = new ArrayList<>();
    SignedBeaconBlock parentBlock = parent;
    for (int i = 0; i < count; i++) {
      final long nextSlot = parentBlock.getSlot().plus(UInt64.ONE).longValue();
      final Bytes32 parentRoot = parentBlock.getRoot();
      final BeaconState state = randomBeaconState(UInt64.valueOf(nextSlot));
      final Bytes32 stateRoot = state.hashTreeRoot();
      final SignedBeaconBlock block =
          signedBlock(randomBeaconBlock(nextSlot, parentRoot, stateRoot, full));
      final BeaconState updatedState =
          state.updated(
              w -> w.setLatestBlockHeader(BeaconBlockHeader.fromBlock(block.getMessage())));
      blocks.add(new SignedBlockAndState(block, updatedState));
      parentBlock = block;
    }
    return blocks;
  }

  public SignedBeaconBlock randomSignedBlindedBeaconBlock() {
    final BeaconBlock beaconBlock = randomBlindedBeaconBlock(randomUInt64());
    return signedBlock(beaconBlock);
  }

  public SignedBeaconBlock randomSignedBlindedBeaconBlock(final long slotNum) {
    final BeaconBlock beaconBlock = randomBlindedBeaconBlock(UInt64.valueOf(slotNum));
    return signedBlock(beaconBlock);
  }

  public SignedBeaconBlock randomSignedBlindedBeaconBlock(final UInt64 slotNum) {
    final BeaconBlock beaconBlock = randomBlindedBeaconBlock(slotNum);
    return signedBlock(beaconBlock);
  }

  public SignedBeaconBlock randomSignedBlindedBeaconBlockWithCommitments(
      final SszList<SszKZGCommitment> commitments) {
    final UInt64 proposerIndex = randomUInt64();
    final UInt64 slot = randomUInt64();
    final Bytes32 stateRoot = randomBytes32();
    final Bytes32 parentRoot = randomBytes32();

    final BeaconBlockBody body = randomBlindedBeaconBlockBodyWithCommitments(slot, commitments);

    final BeaconBlock beaconBlock =
        new BeaconBlock(
            spec.atSlot(slot).getSchemaDefinitions().getBlindedBeaconBlockSchema(),
            slot,
            proposerIndex,
            parentRoot,
            stateRoot,
            body);

    return signedBlock(beaconBlock);
  }

  public SignedBeaconBlock randomSignedBeaconBlock() {
    return randomSignedBeaconBlock(randomUInt64());
  }

  public SignedBeaconBlock randomSignedBeaconBlock(final long slotNum) {
    return randomSignedBeaconBlock(UInt64.valueOf(slotNum));
  }

  public SignedBeaconBlock randomSignedBeaconBlock(final UInt64 slotNum) {
    final BeaconBlock beaconBlock = randomBeaconBlock(slotNum);
    return signedBlock(beaconBlock);
  }

  public SignedBeaconBlock randomSignedBeaconBlock(final long slotNum, final Bytes32 parentRoot) {
    return randomSignedBeaconBlock(slotNum, parentRoot, false);
  }

  public SignedBeaconBlock randomSignedBeaconBlock(
      final long slotNum, final Bytes32 parentRoot, final boolean full) {
    final BeaconBlock beaconBlock = randomBeaconBlock(slotNum, parentRoot, full);
    return signedBlock(beaconBlock);
  }

  public SignedBeaconBlock randomSignedBeaconBlockWithEmptyCommitments() {
    final UInt64 proposerIndex = randomUInt64();
    final UInt64 slot = randomUInt64();
    final Bytes32 stateRoot = randomBytes32();
    final Bytes32 parentRoot = randomBytes32();

    final BeaconBlockBody body = randomBeaconBlockBodyWithEmptyCommitments();

    final BeaconBlock beaconBlock =
        new BeaconBlock(
            spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockSchema(),
            slot,
            proposerIndex,
            parentRoot,
            stateRoot,
            body);
    return signedBlock(beaconBlock);
  }

  public SignedBeaconBlock randomSignedBeaconBlockWithCommitments(final int count) {
    return randomSignedBeaconBlockWithCommitments(randomBlobKzgCommitments(count));
  }

  public SignedBeaconBlock randomSignedBeaconBlockWithCommitments(
      final UInt64 slot, final int count) {
    return randomSignedBeaconBlockWithCommitments(slot, randomBlobKzgCommitments(count));
  }

  public SignedBeaconBlock randomSignedBeaconBlockWithCommitments(
      final SszList<SszKZGCommitment> commitments) {
    return randomSignedBeaconBlockWithCommitments(randomUInt64(), commitments);
  }

  public SignedBeaconBlock randomSignedBeaconBlockWithCommitments(
      final UInt64 slot, final SszList<SszKZGCommitment> commitments) {
    final UInt64 proposerIndex = randomUInt64();
    final Bytes32 stateRoot = randomBytes32();
    final Bytes32 parentRoot = randomBytes32();

    final BeaconBlockBody body = randomBeaconBlockBodyWithCommitments(commitments);

    final BeaconBlock beaconBlock =
        new BeaconBlock(
            spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockSchema(),
            slot,
            proposerIndex,
            parentRoot,
            stateRoot,
            body);
    return signedBlock(beaconBlock);
  }

  public SignedBeaconBlock signedBlock(final BeaconBlock block) {
    return signedBlock(block, randomSignature());
  }

  public SignedBeaconBlock signedBlock(final BeaconBlock block, final BLSSignature signature) {
    return SignedBeaconBlock.create(spec, block, signature);
  }

  public SignedBeaconBlock randomSignedBeaconBlock(final long slotNum, final BeaconState state) {
    return randomSignedBeaconBlock(UInt64.valueOf(slotNum), state);
  }

  public SignedBeaconBlock randomSignedBeaconBlock(final UInt64 slotNum, final BeaconState state) {
    final BeaconBlockBody body = randomBeaconBlockBody();
    final Bytes32 stateRoot = state.hashTreeRoot();

    final BeaconBlockSchema blockSchema =
        spec.atSlot(slotNum).getSchemaDefinitions().getBeaconBlockSchema();
    final BeaconBlock block =
        new BeaconBlock(blockSchema, slotNum, randomUInt64(), randomBytes32(), stateRoot, body);
    return signedBlock(block);
  }

  public BeaconBlockBuilder blockBuilder(final long slot) {
    final SpecVersion specVersion = spec.atSlot(UInt64.valueOf(slot));
    return new BeaconBlockBuilder(specVersion, this);
  }

  public BeaconBlock randomBeaconBlock() {
    return randomBeaconBlock(randomUInt64());
  }

  public BeaconBlock randomBeaconBlock(final long slotNum) {
    return randomBeaconBlock(UInt64.valueOf(slotNum));
  }

  public BeaconBlock randomBeaconBlock(final UInt64 slotNum) {
    return randomBeaconBlock(slotNum, randomBeaconBlockBody(slotNum));
  }

  public BeaconBlock randomBeaconBlock(final UInt64 slotNum, final BeaconBlockBody body) {
    final UInt64 proposerIndex = randomUInt64();
    final Bytes32 previousRoot = randomBytes32();
    final Bytes32 stateRoot = randomBytes32();

    return new BeaconBlock(
        spec.atSlot(slotNum).getSchemaDefinitions().getBeaconBlockSchema(),
        slotNum,
        proposerIndex,
        previousRoot,
        stateRoot,
        body);
  }

  public BeaconBlock randomBlindedBeaconBlock() {
    return randomBlindedBeaconBlock(randomUInt64());
  }

  public BeaconBlock randomBlindedBeaconBlock(final long slotNum) {
    return randomBlindedBeaconBlock(UInt64.valueOf(slotNum));
  }

  public BlockContainerAndMetaData randomBlockContainerAndMetaData(final long slotNum) {
    return randomBlockContainerAndMetaData(UInt64.valueOf(slotNum));
  }

  public BlockContainerAndMetaData randomBlockContainerAndMetaData(final UInt64 slotNum) {
    return new BlockContainerAndMetaData(
        randomBeaconBlock(slotNum),
        spec.atSlot(slotNum).getMilestone(),
        randomUInt256(),
        randomUInt256());
  }

  public BlockContainerAndMetaData randomBlindedBlockContainerAndMetaData(final UInt64 slotNum) {
    return new BlockContainerAndMetaData(
        randomBlindedBeaconBlock(slotNum),
        spec.atSlot(slotNum).getMilestone(),
        randomUInt256(),
        randomUInt256());
  }

  public BlockContainerAndMetaData randomBlockContainerAndMetaData(
      final BlockContainer blockContainer, final UInt64 slotNum) {
    return new BlockContainerAndMetaData(
        blockContainer, spec.atSlot(slotNum).getMilestone(), randomUInt256(), randomUInt256());
  }

  public BeaconBlock randomBlindedBeaconBlock(final UInt64 slot) {
    final UInt64 proposerIndex = randomUInt64();
    final Bytes32 previousRoot = randomBytes32();
    final Bytes32 stateRoot = randomBytes32();
    final BeaconBlockBody body = randomBlindedBeaconBlockBody(slot);

    return new BeaconBlock(
        spec.atSlot(slot).getSchemaDefinitions().getBlindedBeaconBlockSchema(),
        slot,
        proposerIndex,
        previousRoot,
        stateRoot,
        body);
  }

  public SignedBlockAndState randomSignedBlockAndState(final long slot) {
    return randomSignedBlockAndState(UInt64.valueOf(slot));
  }

  public SignedBlockAndState randomSignedBlockAndState(final UInt64 slot) {
    return randomSignedBlockAndState(slot, randomBytes32());
  }

  public SignedBlockAndState randomSignedBlockAndState(
      final UInt64 slot, final Bytes32 parentRoot) {
    final BeaconBlockAndState blockAndState =
        randomBlockAndState(slot, randomBeaconState(slot), parentRoot);

    return toSigned(blockAndState);
  }

  public SignedBlockAndState randomSignedBlockAndState(final BeaconState state) {
    final BeaconBlockAndState blockAndState = randomBlockAndState(state);

    return toSigned(blockAndState);
  }

  public SignedBlockAndState randomSignedBlockAndStateWithValidatorLogic(final int validatorCount) {
    final BeaconBlockAndState blockAndState = randomBlockAndStateWithValidatorLogic(validatorCount);
    return toSigned(blockAndState);
  }

  public SignedBlockAndState toSigned(final BeaconBlockAndState blockAndState) {
    final SignedBeaconBlock signedBlock = signedBlock(blockAndState.getBlock());
    return new SignedBlockAndState(signedBlock, blockAndState.getState());
  }

  public BeaconBlockAndState randomBlockAndState(final long slot) {
    return randomBlockAndState(UInt64.valueOf(slot));
  }

  public BeaconBlockAndState randomBlockAndState(final UInt64 slot) {
    final BeaconState state = randomBeaconState(slot);
    return randomBlockAndState(state);
  }

  public BeaconBlockAndState randomBlockAndState(final BeaconState state) {
    return randomBlockAndState(state.getSlot(), state, randomBytes32());
  }

  private BeaconBlockAndState randomBlockAndState(
      final UInt64 slot, final BeaconState state, final Bytes32 parentRoot) {
    final BeaconBlockBody body = randomBeaconBlockBody(slot);
    final UInt64 proposerIndex = UInt64.valueOf(randomPositiveInt());
    final BeaconBlockHeader latestHeader =
        new BeaconBlockHeader(slot, proposerIndex, parentRoot, Bytes32.ZERO, body.hashTreeRoot());

    final BeaconState matchingState = state.updated(s -> s.setLatestBlockHeader(latestHeader));
    final BeaconBlock block =
        new BeaconBlock(
            spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockSchema(),
            slot,
            proposerIndex,
            parentRoot,
            matchingState.hashTreeRoot(),
            body);

    return new BeaconBlockAndState(block, matchingState);
  }

  public BeaconBlockAndState randomBlockAndStateWithValidatorLogic(final int validatorCount) {
    final BeaconState state = randomBeaconState(validatorCount);
    return randomBlockAndStateWithValidatorLogic(state.getSlot(), state, randomBytes32());
  }

  private BeaconBlockAndState randomBlockAndStateWithValidatorLogic(
      final UInt64 slot, final BeaconState state, final Bytes32 parentRoot) {
    final BeaconBlockBody body = randomBeaconBlockBody(slot, state.getValidators().size());
    final UInt64 proposerIndex = randomUInt64(state.getValidators().size());
    final BeaconBlockHeader latestHeader =
        new BeaconBlockHeader(slot, proposerIndex, parentRoot, Bytes32.ZERO, body.hashTreeRoot());

    final BeaconState matchingState = state.updated(s -> s.setLatestBlockHeader(latestHeader));
    final BeaconBlock block =
        new BeaconBlock(
            spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockSchema(),
            slot,
            proposerIndex,
            parentRoot,
            matchingState.hashTreeRoot(),
            body);

    return new BeaconBlockAndState(block, matchingState);
  }

  public BeaconBlock randomBeaconBlock(
      final long slotNum, final Bytes32 parentRoot, final boolean isFull) {
    return randomBeaconBlock(slotNum, parentRoot, randomBytes32(), isFull);
  }

  public BeaconBlock randomBeaconBlock(
      final long slot, final Bytes32 parentRoot, final Bytes32 stateRoot, final boolean isFull) {
    return randomBeaconBlock(UInt64.valueOf(slot), parentRoot, stateRoot, isFull);
  }

  public BeaconBlock randomBeaconBlock(
      final UInt64 slot, final Bytes32 parentRoot, final Bytes32 stateRoot, final boolean isFull) {
    final UInt64 proposerIndex = randomUInt64();
    final BeaconBlockBody body =
        !isFull ? randomBeaconBlockBody(slot) : randomFullBeaconBlockBody(slot);

    return new BeaconBlock(
        spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockSchema(),
        slot,
        proposerIndex,
        parentRoot,
        stateRoot,
        body);
  }

  public BeaconBlock randomBeaconBlock(final long slotNum, final Bytes32 parentRoot) {
    return randomBeaconBlock(slotNum, parentRoot, false);
  }

  public SignedBeaconBlockHeader randomSignedBeaconBlockHeader() {
    return randomSignedBeaconBlockHeader(randomUInt64(), randomUInt64());
  }

  public SignedBeaconBlockHeader randomSignedBeaconBlockHeader(final UInt64 slot) {
    return randomSignedBeaconBlockHeader(slot, randomValidatorIndex());
  }

  public SignedBeaconBlockHeader randomSignedBeaconBlockHeader(
      final UInt64 slot, final UInt64 proposerIndex) {
    return new SignedBeaconBlockHeader(
        randomBeaconBlockHeader(slot, proposerIndex), randomSignature());
  }

  public BeaconBlockHeader randomBeaconBlockHeader() {
    return randomBeaconBlockHeader(randomUInt64(), randomUInt64());
  }

  public BeaconBlockHeader randomBeaconBlockHeader(final UInt64 slot, final UInt64 proposerIndex) {
    return new BeaconBlockHeader(
        slot, proposerIndex, randomBytes32(), randomBytes32(), randomBytes32());
  }

  public BeaconBlockBody randomBlindedBeaconBlockBody() {
    return randomBlindedBeaconBlockBody(randomUInt64(), __ -> {});
  }

  public BeaconBlockBody randomBlindedBeaconBlockBodyWithCommitments(
      final UInt64 slot, final SszList<SszKZGCommitment> commitments) {
    return randomBlindedBeaconBlockBody(
        slot,
        builder -> {
          if (builder.supportsKzgCommitments()) {
            builder.blobKzgCommitments(commitments);
          }
        });
  }

  public BeaconBlockBody randomBlindedBeaconBlockBody(final UInt64 slot) {
    return randomBlindedBeaconBlockBody(slot, __ -> {});
  }

  public BeaconBlockBody randomBlindedBeaconBlockBody(
      final UInt64 slot, final Consumer<BeaconBlockBodyBuilder> builderModifier) {
    final BeaconBlockBodySchema<?> schema =
        spec.atSlot(slot).getSchemaDefinitions().getBlindedBeaconBlockBodySchema();
    return schema
        .createBlockBody(
            builder -> {
              builder
                  .randaoReveal(randomSignature())
                  .eth1Data(randomEth1Data())
                  .graffiti(Bytes32.ZERO)
                  .proposerSlashings(
                      randomSszList(
                          schema.getProposerSlashingsSchema(), this::randomProposerSlashing, 1))
                  .attesterSlashings(
                      randomSszList(
                          schema.getAttesterSlashingsSchema(), this::randomAttesterSlashing, 1))
                  .attestations(
                      randomSszList(schema.getAttestationsSchema(), this::randomAttestation, 3))
                  .deposits(
                      randomSszList(schema.getDepositsSchema(), this::randomDepositWithoutIndex, 1))
                  .voluntaryExits(
                      randomSszList(
                          schema.getVoluntaryExitsSchema(), this::randomSignedVoluntaryExit, 1));
              if (builder.supportsSyncAggregate()) {
                builder.syncAggregate(randomSyncAggregateIfRequiredBySchema(schema));
              }
              if (builder.supportsExecutionPayload()) {
                builder.executionPayloadHeader(randomExecutionPayloadHeader(spec.atSlot(slot)));
              }
              if (builder.supportsBlsToExecutionChanges()) {
                builder.blsToExecutionChanges(randomSignedBlsToExecutionChangesList());
              }
              if (builder.supportsKzgCommitments()) {
                builder.blobKzgCommitments(randomBlobKzgCommitments());
              }
              if (builder.supportsExecutionRequests()) {
                builder.executionRequests(randomExecutionRequests());
              }
              if (builder.supportsSignedExecutionPayloadBid()) {
                builder.signedExecutionPayloadBid(randomSignedExecutionPayloadBid());
              }
              if (builder.supportsPayloadAttestations()) {
                builder.payloadAttestations(
                    randomSszList(
                        BeaconBlockBodySchemaGloas.required(schema).getPayloadAttestationsSchema(),
                        this::randomPayloadAttestation,
                        getMaxPayloadAttestations()));
              }
              builderModifier.accept(builder);
              return SafeFuture.COMPLETE;
            })
        .join();
  }

  public BeaconBlockBody randomBeaconBlockBody() {
    return randomBeaconBlockBody(randomUInt64(), __ -> {});
  }

  public BeaconBlockBody randomBeaconBlockBody(final UInt64 slot) {
    return randomBeaconBlockBody(slot, __ -> {});
  }

  public BeaconBlockBody randomBeaconBlockBody(
      final UInt64 proposalSlot, final int validatorCount) {
    Preconditions.checkArgument(
        proposalSlot.isGreaterThan(1), "Proposal slot must be greater than 1");
    final BeaconBlockBodySchema<?> schema =
        spec.atSlot(proposalSlot).getSchemaDefinitions().getBeaconBlockBodySchema();
    return randomBeaconBlockBody(
        proposalSlot,
        builder -> {
          builder
              .proposerSlashings(
                  randomSszList(
                      schema.getProposerSlashingsSchema(),
                      () ->
                          randomProposerSlashing(
                              randomUInt64(proposalSlot.decrement().longValue()),
                              randomUInt64(validatorCount - 1)),
                      1))
              .attesterSlashings(
                  randomSszList(
                      schema.getAttesterSlashingsSchema(),
                      () -> randomAttesterSlashing(randomUInt64(validatorCount - 1)),
                      1));
          if (builder.supportsExecutionPayload()) {
            builder.executionPayload(randomExecutionPayload(proposalSlot));
          }
        });
  }

  public BeaconBlockBody randomBeaconBlockBodyWithEmptyCommitments() {
    return randomBeaconBlockBody(builder -> builder.blobKzgCommitments(emptyBlobKzgCommitments()));
  }

  public BeaconBlockBody randomBeaconBlockBodyWithCommitments(final int count) {
    return randomBeaconBlockBodyWithCommitments(randomBlobKzgCommitments(count));
  }

  public BeaconBlockBody randomBeaconBlockBodyWithCommitments(
      final SszList<SszKZGCommitment> commitments) {
    return randomBeaconBlockBody(
        builder -> {
          if (builder.supportsKzgCommitments()) {
            builder.blobKzgCommitments(commitments);
          }
        });
  }

  public BeaconBlockBody randomBeaconBlockBody(
      final Consumer<BeaconBlockBodyBuilder> builderModifier) {
    return randomBeaconBlockBody(randomUInt64(), builderModifier);
  }

  public BeaconBlockBody randomBeaconBlockBody(
      final UInt64 slot, final Consumer<BeaconBlockBodyBuilder> builderModifier) {
    final BeaconBlockBodySchema<?> schema =
        spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockBodySchema();
    return schema
        .createBlockBody(
            builder -> {
              builder
                  .randaoReveal(randomSignature())
                  .eth1Data(randomEth1Data())
                  .graffiti(Bytes32.ZERO)
                  .proposerSlashings(
                      randomSszList(
                          schema.getProposerSlashingsSchema(), this::randomProposerSlashing, 1))
                  .attesterSlashings(
                      randomSszList(
                          schema.getAttesterSlashingsSchema(),
                          () -> randomAttesterSlashingAtSlot(slot),
                          1))
                  .attestations(
                      randomSszList(
                          schema.getAttestationsSchema(), () -> randomAttestation(slot), 3))
                  .deposits(
                      randomSszList(schema.getDepositsSchema(), this::randomDepositWithoutIndex, 1))
                  .voluntaryExits(
                      randomSszList(
                          schema.getVoluntaryExitsSchema(), this::randomSignedVoluntaryExit, 1));
              if (builder.supportsSyncAggregate()) {
                builder.syncAggregate(randomSyncAggregateIfRequiredBySchema(schema));
              }
              if (builder.supportsExecutionPayload()) {
                builder.executionPayload(randomExecutionPayload(slot));
              }
              if (builder.supportsBlsToExecutionChanges()) {
                builder.blsToExecutionChanges(randomSignedBlsToExecutionChangesList());
              }
              if (builder.supportsKzgCommitments()) {
                builder.blobKzgCommitments(randomBlobKzgCommitments());
              }
              if (builder.supportsExecutionRequests()) {
                builder.executionRequests(randomExecutionRequests());
              }
              if (builder.supportsSignedExecutionPayloadBid()) {
                builder.signedExecutionPayloadBid(randomSignedExecutionPayloadBid());
              }
              if (builder.supportsPayloadAttestations()) {
                builder.payloadAttestations(
                    randomSszList(
                        BeaconBlockBodySchemaGloas.required(schema).getPayloadAttestationsSchema(),
                        this::randomPayloadAttestation,
                        getMaxPayloadAttestations()));
              }
              builderModifier.accept(builder);
              return SafeFuture.COMPLETE;
            })
        .join();
  }

  public BeaconBlockBody randomFullBeaconBlockBody(final UInt64 slot) {
    return randomFullBeaconBlockBody(slot, __ -> {});
  }

  public BeaconBlockBody randomFullBeaconBlockBody(
      final UInt64 slot, final Consumer<BeaconBlockBodyBuilder> builderModifier) {
    final BeaconBlockBodySchema<?> schema =
        spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockBodySchema();
    return schema
        .createBlockBody(
            builder -> {
              builder
                  .randaoReveal(randomSignature())
                  .eth1Data(randomEth1Data())
                  .graffiti(Bytes32.ZERO)
                  .proposerSlashings(
                      randomFullSszList(
                          schema.getProposerSlashingsSchema(), this::randomProposerSlashing))
                  .attesterSlashings(
                      randomFullSszList(
                          schema.getAttesterSlashingsSchema(), () -> randomAttesterSlashing(slot)))
                  .attestations(
                      randomFullSszList(
                          schema.getAttestationsSchema(), () -> randomAttestation(slot)))
                  .deposits(
                      randomFullSszList(
                          schema.getDepositsSchema(), this::randomDepositWithoutIndex))
                  .voluntaryExits(
                      randomFullSszList(
                          schema.getVoluntaryExitsSchema(), this::randomSignedVoluntaryExit));
              if (builder.supportsSyncAggregate()) {
                builder.syncAggregate(randomSyncAggregateIfRequiredBySchema(schema));
              }
              if (builder.supportsExecutionPayload()) {
                builder.executionPayload(randomExecutionPayload());
              }
              if (builder.supportsBlsToExecutionChanges()) {
                builder.blsToExecutionChanges(
                    randomFullSszList(
                        BeaconBlockBodySchemaCapella.required(schema)
                            .getBlsToExecutionChangesSchema(),
                        this::randomSignedBlsToExecutionChange));
              }
              if (builder.supportsKzgCommitments()) {
                builder.blobKzgCommitments(
                    randomFullSszList(
                        BeaconBlockBodySchemaDeneb.required(schema).getBlobKzgCommitmentsSchema(),
                        this::randomSszKZGCommitment));
              }
              if (builder.supportsExecutionRequests()) {
                builder.executionRequests(randomExecutionRequests());
              }
              if (builder.supportsSignedExecutionPayloadBid()) {
                builder.signedExecutionPayloadBid(randomSignedExecutionPayloadBid());
              }
              if (builder.supportsPayloadAttestations()) {
                builder.payloadAttestations(
                    randomFullSszList(
                        BeaconBlockBodySchemaGloas.required(schema).getPayloadAttestationsSchema(),
                        this::randomPayloadAttestation));
              }
              builderModifier.accept(builder);
              return SafeFuture.COMPLETE;
            })
        .join();
  }

  public ProposerSlashing randomProposerSlashing() {
    return randomProposerSlashing(randomUInt64(), randomUInt64());
  }

  public ProposerSlashing randomProposerSlashing(final int validatorLimit) {
    return randomProposerSlashing(randomUInt64(), randomUInt64(validatorLimit));
  }

  public AttesterSlashing randomAttesterSlashing(final int validatorLimit) {
    return randomAttesterSlashing(randomUInt64(validatorLimit));
  }

  public ProposerSlashing randomProposerSlashing(final UInt64 slot, final UInt64 proposerIndex) {
    return new ProposerSlashing(
        randomSignedBeaconBlockHeader(slot, proposerIndex),
        randomSignedBeaconBlockHeader(slot, proposerIndex));
  }

  public IndexedAttestation randomIndexedAttestation() {
    return randomIndexedAttestation(randomUInt64(), randomUInt64(), randomUInt64());
  }

  public IndexedAttestation randomIndexedAttestation(final UInt64... attestingIndicesInput) {
    return randomIndexedAttestation(randomAttestationData(), attestingIndicesInput);
  }

  public IndexedAttestation randomIndexedAttestation(
      final AttestationData data, final UInt64... attestingIndicesInput) {
    final IndexedAttestationSchema indexedAttestationSchema =
        spec.atSlot(data.getSlot()).getSchemaDefinitions().getIndexedAttestationSchema();
    final SszUInt64List attestingIndices =
        indexedAttestationSchema.getAttestingIndicesSchema().of(attestingIndicesInput);
    return indexedAttestationSchema.create(attestingIndices, data, randomSignature());
  }

  public DepositMessage randomDepositMessage(
      final BLSKeyPair keyPair, final Optional<Bytes32> maybeWithdrawalCredentials) {
    final BLSPublicKey pubkey = keyPair.getPublicKey();
    final Bytes32 withdrawalCredentials = maybeWithdrawalCredentials.orElse(randomBytes32());
    return new DepositMessage(pubkey, withdrawalCredentials, getMaxEffectiveBalance());
  }

  public DepositMessage randomDepositMessage() {
    final BLSKeyPair keyPair = randomKeyPair();
    return randomDepositMessage(keyPair, Optional.empty());
  }

  public DepositData randomDepositData() {
    final BLSKeyPair keyPair = randomKeyPair();
    final DepositMessage depositMessage = randomDepositMessage(keyPair, Optional.empty());

    final Bytes32 domain = computeDepositDomain();
    final Bytes signingRoot = getSigningRoot(depositMessage, domain);

    final BLSSignature signature = BLS.sign(keyPair.getSecretKey(), signingRoot);

    return new DepositData(depositMessage, signature);
  }

  public DepositData randomDepositData(final Bytes32 withdrawalCredentials) {
    final BLSKeyPair keyPair = randomKeyPair();
    final DepositMessage depositMessage =
        randomDepositMessage(keyPair, Optional.of(withdrawalCredentials));

    final Bytes32 domain = computeDepositDomain();
    final Bytes signingRoot = getSigningRoot(depositMessage, domain);

    final BLSSignature signature = BLS.sign(keyPair.getSecretKey(), signingRoot);

    return new DepositData(depositMessage, signature);
  }

  public DepositWithIndex randomDepositWithIndex() {
    return randomDepositWithIndex(randomLong());
  }

  public DepositWithIndex randomDepositWithIndex(final long depositIndex) {
    final Bytes32 randomBytes32 = randomBytes32();
    final SszBytes32VectorSchema<?> proofSchema = Deposit.SSZ_SCHEMA.getProofSchema();
    return new DepositWithIndex(
        new Deposit(
            Stream.generate(() -> randomBytes32)
                .limit(proofSchema.getLength())
                .collect(proofSchema.collectorUnboxed()),
            randomDepositData()),
        UInt64.valueOf(depositIndex));
  }

  public DepositsFromBlockEvent randomDepositsFromBlockEvent(
      final long blockIndex,
      final long depositIndexStartInclusive,
      final long depositIndexEndExclusive) {
    return randomDepositsFromBlockEvent(
        UInt64.valueOf(blockIndex), depositIndexStartInclusive, depositIndexEndExclusive);
  }

  public DepositsFromBlockEvent randomDepositsFromBlockEvent(
      final UInt64 blockIndex,
      final long depositIndexStartInclusive,
      final long depositIndexEndExclusive) {
    final List<tech.pegasys.teku.ethereum.pow.api.Deposit> deposits = new ArrayList<>();
    for (long i = depositIndexStartInclusive; i < depositIndexEndExclusive; i++) {
      deposits.add(randomDepositEvent(UInt64.valueOf(i)));
    }
    return DepositsFromBlockEvent.create(
        blockIndex, randomBytes32(), randomUInt64(), deposits.stream());
  }

  public MinGenesisTimeBlockEvent randomMinGenesisTimeBlockEvent(final long blockIndex) {
    return new MinGenesisTimeBlockEvent(
        randomUInt64(), UInt64.valueOf(blockIndex), randomBytes32());
  }

  public Deposit randomDepositWithoutIndex() {
    final Bytes32 randomBytes32 = randomBytes32();
    final SszBytes32VectorSchema<?> proofSchema = Deposit.SSZ_SCHEMA.getProofSchema();
    return new Deposit(
        Stream.generate(() -> randomBytes32)
            .limit(proofSchema.getLength())
            .collect(proofSchema.collectorUnboxed()),
        randomDepositData());
  }

  public Deposit randomDeposit() {
    final Bytes32 randomBytes32 = randomBytes32();
    final SszBytes32VectorSchema<?> proofSchema = Deposit.SSZ_SCHEMA.getProofSchema();
    return new Deposit(
        Stream.generate(() -> randomBytes32)
            .limit(proofSchema.getLength())
            .collect(proofSchema.collectorUnboxed()),
        randomDepositData());
  }

  public tech.pegasys.teku.ethereum.pow.api.Deposit randomDepositEvent(final long index) {
    return randomDepositEvent(UInt64.valueOf(index));
  }

  public tech.pegasys.teku.ethereum.pow.api.Deposit randomDepositEvent(final UInt64 index) {
    return new tech.pegasys.teku.ethereum.pow.api.Deposit(
        BLSTestUtil.randomPublicKey(nextSeed()),
        randomBytes32(),
        randomSignature(),
        randomUInt64(),
        index);
  }

  public tech.pegasys.teku.ethereum.pow.api.Deposit randomDepositEvent() {
    return randomDepositEvent(randomUInt64());
  }

  public List<Deposit> randomDeposits(final int num) {
    return randomDepositsWithIndex(num).stream().map(DepositWithIndex::deposit).toList();
  }

  public List<DepositWithIndex> randomDepositsWithIndex(final int num) {
    return Stream.generate(this::randomDepositWithIndex).limit(num).collect(Collectors.toList());
  }

  public SszList<Deposit> randomSszDeposits(final int num) {
    final BeaconBlockBodySchema<?> schema =
        spec.getGenesisSpec().getSchemaDefinitions().getBeaconBlockBodySchema();
    return randomSszList(schema.getDepositsSchema(), this::randomDepositWithoutIndex, num);
  }

  public DepositTreeSnapshot randomDepositTreeSnapshot() {
    return randomDepositTreeSnapshot(randomLong(), randomUInt64());
  }

  public DepositTreeSnapshot randomDepositTreeSnapshot(
      final long depositsCount, final UInt64 blockHeight) {
    return new DepositTreeSnapshot(
        Stream.generate(this::randomBytes32).limit(DEPOSIT_CONTRACT_TREE_DEPTH).collect(toList()),
        Bytes32.random(),
        depositsCount,
        Bytes32.random(),
        blockHeight);
  }

  public SignedVoluntaryExit randomSignedVoluntaryExit() {
    return randomSignedVoluntaryExit(randomUInt64());
  }

  public SignedVoluntaryExit randomSignedVoluntaryExit(final UInt64 validatorIndex) {
    return new SignedVoluntaryExit(randomVoluntaryExit(validatorIndex), randomSignature());
  }

  public VoluntaryExit randomVoluntaryExit() {
    return randomVoluntaryExit(randomUInt64());
  }

  public VoluntaryExit randomVoluntaryExit(final UInt64 validatorIndex) {
    return new VoluntaryExit(randomUInt64(), validatorIndex);
  }

  public ValidatorBuilder validatorBuilder() {
    return new ValidatorBuilder(spec, this);
  }

  /*
   STOP! Before thinking about adding a new method to create a specific type of validator, consider using
   DataStructureUtil.validatorBuilder() to create a custom validator that suit your needs.
  */
  public Validator randomValidator() {
    return validatorBuilder().build();
  }

  public Fork randomFork() {
    return new Fork(randomBytes4(), randomBytes4(), randomUInt64());
  }

  public ForkInfo randomForkInfo() {
    return new ForkInfo(randomFork(), randomBytes32());
  }

  public EnrForkId randomEnrForkId() {
    return new EnrForkId(randomBytes4(), randomBytes4(), randomUInt64());
  }

  public ExecutionPayloadContext randomPayloadExecutionContext(final boolean optimisticHead) {
    return new ExecutionPayloadContext(
        randomBytes8(),
        randomForkChoiceState(optimisticHead),
        randomPayloadBuildingAttributes(false));
  }

  public ExecutionPayloadContext randomPayloadExecutionContext(
      final Bytes32 finalizedBlockHash,
      final boolean optimisticHead,
      final boolean withValidatorRegistration) {
    return randomPayloadExecutionContext(
        randomUInt64(), finalizedBlockHash, optimisticHead, withValidatorRegistration);
  }

  public ExecutionPayloadContext randomPayloadExecutionContext(
      final UInt64 slot,
      final Bytes32 finalizedBlockHash,
      final boolean optimisticHead,
      final boolean withValidatorRegistration) {
    return new ExecutionPayloadContext(
        randomBytes8(),
        randomForkChoiceState(slot, finalizedBlockHash, optimisticHead),
        randomPayloadBuildingAttributes(withValidatorRegistration));
  }

  public ExecutionPayloadContext randomPayloadExecutionContext(
      final boolean optimisticHead, final boolean withValidatorRegistration) {
    return new ExecutionPayloadContext(
        randomBytes8(),
        randomForkChoiceState(optimisticHead),
        randomPayloadBuildingAttributes(withValidatorRegistration));
  }

  public ExecutionPayloadContext randomPayloadExecutionContext(
      final UInt64 slot, final boolean optimisticHead) {
    return new ExecutionPayloadContext(
        randomBytes8(),
        randomForkChoiceState(slot, randomBytes32(), optimisticHead),
        randomPayloadBuildingAttributes(false));
  }

  public PayloadBuildingAttributes randomPayloadBuildingAttributes(
      final boolean withValidatorRegistration) {
    return new PayloadBuildingAttributes(
        randomUInt64(),
        randomUInt64(),
        randomUInt64(),
        randomBytes32(),
        randomEth1Address(),
        withValidatorRegistration
            ? Optional.of(randomSignedValidatorRegistration())
            : Optional.empty(),
        randomWithdrawalList(),
        randomBytes32());
  }

  public ClientVersion randomClientVersion() {
    return new ClientVersion(
        randomString(2),
        randomString(randomInt(1, 10)),
        randomString(randomInt(1, 10)),
        randomBytes4());
  }

  public BeaconPreparableProposer randomBeaconPreparableProposer() {
    return new BeaconPreparableProposer(randomUInt64(), randomEth1Address());
  }

  public List<BeaconPreparableProposer> randomBeaconPreparableProposers(final int size) {
    return IntStream.range(0, size)
        .mapToObj(__ -> randomBeaconPreparableProposer())
        .collect(toList());
  }

  public ValidatorRegistration randomValidatorRegistration() {
    return VALIDATOR_REGISTRATION_SCHEMA.create(
        randomBytes20(), randomUInt64(), randomUInt64(), randomPublicKey());
  }

  public ValidatorRegistration randomValidatorRegistration(final BLSPublicKey publicKey) {
    return VALIDATOR_REGISTRATION_SCHEMA.create(
        randomBytes20(), randomUInt64(), randomUInt64(), publicKey);
  }

  public SignedValidatorRegistration randomSignedValidatorRegistration() {
    return randomSignedValidatorRegistration(randomPublicKey());
  }

  public SignedValidatorRegistration randomSignedValidatorRegistration(
      final BLSPublicKey publicKey) {
    return SIGNED_VALIDATOR_REGISTRATION_SCHEMA.create(
        randomValidatorRegistration(publicKey), randomSignature());
  }

  public SszList<SignedValidatorRegistration> randomSignedValidatorRegistrations(final int size) {
    return SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.createFromElements(
        IntStream.range(0, size).mapToObj(__ -> randomSignedValidatorRegistration()).toList());
  }

  public ForkChoiceState randomForkChoiceState(final boolean optimisticHead) {
    return randomForkChoiceState(randomUInt64(), randomBytes32(), optimisticHead);
  }

  public ForkChoiceState randomForkChoiceState(
      final UInt64 headBlockSlot, final Bytes32 finalizedBlockHash, final boolean optimisticHead) {
    return new ForkChoiceState(
        randomBytes32(),
        headBlockSlot,
        randomUInt64(),
        randomBytes32(),
        randomBytes32(),
        finalizedBlockHash,
        optimisticHead);
  }

  public BeaconState genesisBeaconState(final int validatorCount) {
    final List<BLSKeyPair> keyPairs = new ArrayList<>();
    for (int i = 0; i < validatorCount; i++) {
      keyPairs.add(randomKeyPair());
    }
    final List<DepositData> initialDepositData =
        new MockStartDepositGenerator(spec, new DepositGenerator(spec, true))
            .createDeposits(keyPairs, spec.getGenesisSpecConfig().getMaxEffectiveBalance());

    final List<Deposit> deposits = initialDepositData.stream().map(Deposit::new).toList();
    final BeaconState initialState =
        spec.initializeBeaconStateFromEth1(Bytes32.ZERO, UInt64.ZERO, deposits, Optional.empty());
    return initialState.updated(
        state -> state.setGenesisTime(new SystemTimeProvider().getTimeInSeconds()));
  }

  public BeaconState randomBeaconState() {
    return randomBeaconState(100, 100);
  }

  public BeaconState randomBeaconState(final int validatorCount) {
    return randomBeaconState(validatorCount, 100);
  }

  public BeaconState randomBeaconState(final int validatorCount, final int numItemsInSSZLists) {
    return stateBuilder(spec.getGenesisSpec().getMilestone(), validatorCount, numItemsInSSZLists)
        .build();
  }

  public BeaconState randomBeaconState(
      final int validatorCount, final int numItemsInSSZLists, final UInt64 slot) {
    return stateBuilder(spec.getGenesisSpec().getMilestone(), validatorCount, numItemsInSSZLists)
        .slot(slot)
        .build();
  }

  public BeaconState randomBeaconStateWithActiveValidators(
      final int validatorCount, final UInt64 slot) {
    return randomBeaconState(validatorCount, 100, slot)
        .updated(
            state -> {
              final SszMutableList<Validator> validators = state.getValidators();
              for (int i = 0; i < validators.size(); i++) {
                validators.update(
                    i,
                    validator ->
                        validator
                            .withActivationEligibilityEpoch(ZERO)
                            .withActivationEpoch(ZERO)
                            .withExitEpoch(SpecConfig.FAR_FUTURE_EPOCH)
                            .withWithdrawableEpoch(SpecConfig.FAR_FUTURE_EPOCH));
              }
            });
  }

  public AbstractBeaconStateBuilder<
          ? extends BeaconState,
          ? extends MutableBeaconState,
          ? extends AbstractBeaconStateBuilder<?, ?, ?>>
      stateBuilder(
          final SpecMilestone milestone, final int validatorCount, final int numItemsInSszLists) {
    return switch (milestone) {
      case PHASE0 -> stateBuilderPhase0(validatorCount, numItemsInSszLists);
      case ALTAIR -> stateBuilderAltair(validatorCount, numItemsInSszLists);
      case BELLATRIX -> stateBuilderBellatrix(validatorCount, numItemsInSszLists);
      case CAPELLA -> stateBuilderCapella(validatorCount, numItemsInSszLists);
      case DENEB -> stateBuilderDeneb(validatorCount, numItemsInSszLists);
      case ELECTRA -> stateBuilderElectra(validatorCount, numItemsInSszLists);
      case FULU -> stateBuilderFulu(validatorCount, numItemsInSszLists);
      case GLOAS -> stateBuilderGloas(validatorCount, numItemsInSszLists);
    };
  }

  public BeaconStateBuilderPhase0 stateBuilderPhase0() {
    return BeaconStateBuilderPhase0.create(this, spec, 10, 10);
  }

  public BeaconStateBuilderPhase0 stateBuilderPhase0(
      final int validatorCount, final int numItemsInSSZLists) {
    return BeaconStateBuilderPhase0.create(this, spec, validatorCount, numItemsInSSZLists);
  }

  public BeaconStateBuilderAltair stateBuilderAltair() {
    return stateBuilderAltair(10, 10);
  }

  public BeaconStateBuilderAltair stateBuilderAltair(
      final int defaultValidatorCount, final int defaultItemsInSSZLists) {
    return BeaconStateBuilderAltair.create(
        this, spec, defaultValidatorCount, defaultItemsInSSZLists);
  }

  public BeaconStateBuilderBellatrix stateBuilderBellatrix(
      final int defaultValidatorCount, final int defaultItemsInSSZLists) {
    return BeaconStateBuilderBellatrix.create(
        this, spec, defaultValidatorCount, defaultItemsInSSZLists);
  }

  public BeaconStateBuilderCapella stateBuilderCapella() {
    return stateBuilderCapella(10, 10);
  }

  public BeaconStateBuilderCapella stateBuilderCapella(
      final int defaultValidatorCount, final int defaultItemsInSSZLists) {
    return BeaconStateBuilderCapella.create(
        this, spec, defaultValidatorCount, defaultItemsInSSZLists);
  }

  public BeaconStateBuilderDeneb stateBuilderDeneb(
      final int defaultValidatorCount, final int defaultItemsInSSZLists) {
    return BeaconStateBuilderDeneb.create(
        this, spec, defaultValidatorCount, defaultItemsInSSZLists);
  }

  public BeaconStateBuilderElectra stateBuilderElectra(
      final int defaultValidatorCount, final int defaultItemsInSSZLists) {
    return BeaconStateBuilderElectra.create(
        this, spec, defaultValidatorCount, defaultItemsInSSZLists);
  }

  public BeaconStateBuilderFulu stateBuilderFulu(
      final int defaultValidatorCount, final int defaultItemsInSSZLists) {
    return BeaconStateBuilderFulu.create(this, spec, defaultValidatorCount, defaultItemsInSSZLists);
  }

  public BeaconStateBuilderGloas stateBuilderGloas(
      final int defaultValidatorCount, final int defaultItemsInSSZLists) {
    return BeaconStateBuilderGloas.create(
        this, spec, defaultValidatorCount, defaultItemsInSSZLists);
  }

  public BeaconState randomBeaconState(final UInt64 slot) {
    if (slot.isZero()) {
      return genesisBeaconState(100);
    }
    return randomBeaconState().updated(state -> state.setSlot(slot));
  }

  public BeaconState randomBeaconStatePreMerge(final UInt64 slot) {
    return randomBeaconState()
        .updated(state -> state.setSlot(slot))
        .updated(
            state ->
                state
                    .toMutableVersionBellatrix()
                    .orElseThrow()
                    .setLatestExecutionPayloadHeader(
                        getBellatrixSchemaDefinitions(slot)
                            .getExecutionPayloadHeaderSchema()
                            .getDefault()));
  }

  public AnchorPoint randomAnchorPoint(final UInt64 epoch) {
    final SignedBlockAndState anchorBlockAndState =
        randomSignedBlockAndState(computeStartSlotAtEpoch(epoch));
    return AnchorPoint.fromInitialBlockAndState(spec, anchorBlockAndState);
  }

  public AnchorPoint randomAnchorPoint(final UInt64 epoch, final Fork currentFork) {
    final UInt64 slot = computeStartSlotAtEpoch(epoch);
    final BeaconBlockAndState blockAndState =
        randomBlockAndState(
            slot,
            stateBuilder(spec.atSlot(slot).getMilestone(), 10, 10)
                .slot(slot)
                .fork(currentFork)
                .build(),
            randomBytes32());
    return AnchorPoint.fromInitialBlockAndState(spec, toSigned(blockAndState));
  }

  public AnchorPoint createAnchorFromState(final BeaconState anchorState) {
    // Create corresponding block
    final SchemaDefinitions schemaDefinitions =
        spec.atSlot(anchorState.getSlot()).getSchemaDefinitions();
    final BeaconBlock anchorBlock =
        new BeaconBlock(
            schemaDefinitions.getBeaconBlockSchema(),
            anchorState.getSlot(),
            ZERO,
            anchorState.getLatestBlockHeader().getParentRoot(),
            anchorState.hashTreeRoot(),
            spec.getGenesisSpec().getSchemaDefinitions().getBeaconBlockBodySchema().createEmpty());
    final SignedBeaconBlock signedAnchorBlock =
        SignedBeaconBlock.create(spec, anchorBlock, BLSSignature.empty());

    final Bytes32 anchorRoot = anchorBlock.hashTreeRoot();
    final UInt64 anchorEpoch = spec.getCurrentEpoch(anchorState);
    final Checkpoint anchorCheckpoint = new Checkpoint(anchorEpoch, anchorRoot);
    final BeaconState updatedAnchorState =
        anchorState.updated(w -> w.setLatestBlockHeader(BeaconBlockHeader.fromBlock(anchorBlock)));

    return AnchorPoint.create(
        spec, anchorCheckpoint, updatedAnchorState, Optional.of(signedAnchorBlock));
  }

  public SignedContributionAndProof randomSignedContributionAndProof() {
    return randomSignedContributionAndProof(randomUInt64(), randomBytes32());
  }

  public SignedContributionAndProof randomSignedContributionAndProof(final long slot) {
    return randomSignedContributionAndProof(slot, randomBytes32());
  }

  public SignedContributionAndProof randomSignedContributionAndProof(
      final long slot, final Bytes32 beaconBlockRoot) {
    return randomSignedContributionAndProof(UInt64.valueOf(slot), beaconBlockRoot);
  }

  public SignedContributionAndProof randomSignedContributionAndProof(
      final UInt64 slot, final Bytes32 beaconBlockRoot) {
    final ContributionAndProof contributionAndProof =
        randomContributionAndProof(slot, beaconBlockRoot);
    return getAltairSchemaDefinitions(slot)
        .getSignedContributionAndProofSchema()
        .create(contributionAndProof, randomSignature());
  }

  public ContributionAndProof randomContributionAndProof() {
    return randomContributionAndProof(randomUInt64(), randomBytes32());
  }

  public ContributionAndProof randomContributionAndProof(
      final UInt64 slot, final Bytes32 beaconBlockRoot) {
    return getAltairSchemaDefinitions(slot)
        .getContributionAndProofSchema()
        .create(
            randomUInt64(),
            randomSyncCommitteeContribution(slot, beaconBlockRoot),
            randomSignature());
  }

  public SyncCommitteeContribution randomSyncCommitteeContribution() {
    return randomSyncCommitteeContribution(randomUInt64(), randomBytes32());
  }

  public SyncCommitteeContribution randomSyncCommitteeContribution(final UInt64 slot) {
    return randomSyncCommitteeContribution(slot, randomBytes32());
  }

  public SyncCommitteeContribution randomSyncCommitteeContribution(
      final UInt64 slot, final Bytes32 beaconBlockRoot) {
    final int subcommitteeSize =
        spec.atSlot(slot).getSyncCommitteeUtil().orElseThrow().getSubcommitteeSize();
    return getAltairSchemaDefinitions(slot)
        .getSyncCommitteeContributionSchema()
        .create(
            slot,
            beaconBlockRoot,
            UInt64.valueOf(randomInt(SYNC_COMMITTEE_SUBNET_COUNT)),
            randomSszBitvector(subcommitteeSize),
            randomSignature());
  }

  public LightClientBootstrap randomLightClientBoostrap(final UInt64 slot) {
    final LightClientBootstrapSchema bootstrapSchema =
        getAltairSchemaDefinitions(slot).getLightClientBootstrapSchema();
    final LightClientHeaderSchema headerSchema =
        getAltairSchemaDefinitions(slot).getLightClientHeaderSchema();

    return bootstrapSchema.create(
        headerSchema.create(randomBeaconBlockHeader()),
        randomSyncCommittee(),
        randomSszBytes32Vector(
            bootstrapSchema.getSyncCommitteeBranchSchema(), this::randomBytes32));
  }

  public LightClientUpdate randomLightClientUpdate(final UInt64 slot) {
    final LightClientUpdateSchema schema =
        getAltairSchemaDefinitions(slot).getLightClientUpdateSchema();
    final LightClientHeaderSchema headerSchema =
        getAltairSchemaDefinitions(slot).getLightClientHeaderSchema();

    return schema.create(
        headerSchema.create(randomBeaconBlockHeader()),
        randomSyncCommittee(),
        randomSszBytes32Vector(schema.getSyncCommitteeBranchSchema(), this::randomBytes32),
        headerSchema.create(randomBeaconBlockHeader()),
        randomSszBytes32Vector(schema.getFinalityBranchSchema(), this::randomBytes32),
        randomSyncAggregate(),
        SszUInt64.of(randomUInt64()));
  }

  public LightClientUpdateResponse randomLightClientUpdateResponse(final UInt64 slot) {
    final LightClientUpdateResponseSchema schema =
        getAltairSchemaDefinitions(slot).getLightClientUpdateResponseSchema();

    return schema.create(
        SszUInt64.of(randomUInt64()), SszBytes4.of(randomBytes4()), randomLightClientUpdate(slot));
  }

  public Withdrawal randomWithdrawal() {
    return getCapellaSchemaDefinitions(randomSlot())
        .getWithdrawalSchema()
        .create(randomUInt64(), randomValidatorIndex(), randomBytes20(), randomUInt64());
  }

  public Withdrawal randomWithdrawal(final UInt64 validatorIndex, final UInt64 amount) {
    return getCapellaSchemaDefinitions(randomSlot())
        .getWithdrawalSchema()
        .create(randomUInt64(), validatorIndex, randomBytes20(), amount);
  }

  public DepositRequest randomDepositRequestWithValidSignature(final UInt64 index) {
    final BLSKeyPair keyPair = randomKeyPair();
    final DepositMessage depositMessage =
        new DepositMessage(keyPair.getPublicKey(), randomBytes32(), randomUInt64());
    final Bytes32 domain = computeDepositDomain();
    final Bytes signingRoot = getSigningRoot(depositMessage, domain);
    final BLSSignature signature = BLS.sign(keyPair.getSecretKey(), signingRoot);
    return getElectraSchemaDefinitions(randomSlot())
        .getDepositRequestSchema()
        .create(
            depositMessage.getPubkey(),
            depositMessage.getWithdrawalCredentials(),
            depositMessage.getAmount(),
            signature,
            index);
  }

  public DepositRequest randomDepositRequest() {
    return getElectraSchemaDefinitions(randomSlot())
        .getDepositRequestSchema()
        .create(
            randomPublicKey(),
            randomEth1WithdrawalCredentials(),
            randomUInt64(),
            randomSignature(),
            randomUInt64());
  }

  public HistoricalSummary randomHistoricalSummary() {
    return getCapellaSchemaDefinitions(randomSlot())
        .getHistoricalSummarySchema()
        .create(SszBytes32.of(randomBytes32()), SszBytes32.of(randomBytes32()));
  }

  public Optional<List<Withdrawal>> randomWithdrawalList() {
    if (spec.getGenesisSpec().getMilestone().isGreaterThanOrEqualTo(SpecMilestone.CAPELLA)) {
      final List<Withdrawal> withdrawals = new ArrayList<>();
      final int max =
          SpecConfigCapella.required(spec.getGenesisSpecConfig()).getMaxWithdrawalsPerPayload();
      for (int i = 0; i < max; i++) {
        withdrawals.add(randomWithdrawal());
      }
      return Optional.of(withdrawals);
    } else {
      return Optional.empty();
    }
  }

  public SszList<SignedBlsToExecutionChange> emptySignedBlsToExecutionChangesList() {
    return getCapellaSchemaDefinitions(randomSlot())
        .getBeaconBlockBodySchema()
        .toVersionCapella()
        .orElseThrow()
        .getBlsToExecutionChangesSchema()
        .createFromElements(List.of());
  }

  public BlsToExecutionChange randomBlsToExecutionChange() {
    return getCapellaSchemaDefinitions(randomSlot())
        .getBlsToExecutionChangeSchema()
        .create(randomValidatorIndex(), randomPublicKey(), randomBytes20());
  }

  public SszList<SignedBlsToExecutionChange> randomSignedBlsToExecutionChangesList() {
    final UInt64 slot = randomSlot();
    final SszListSchema<SignedBlsToExecutionChange, ?> signedBlsToExecutionChangeSchema =
        getCapellaSchemaDefinitions(slot)
            .getBeaconBlockBodySchema()
            .toVersionCapella()
            .orElseThrow()
            .getBlsToExecutionChangesSchema();
    final int maxBlsToExecutionChanges =
        SpecConfigCapella.required(spec.atSlot(slot).getConfig()).getMaxBlsToExecutionChanges();

    return randomSszList(
        signedBlsToExecutionChangeSchema,
        maxBlsToExecutionChanges,
        this::randomSignedBlsToExecutionChange);
  }

  public SignedBlsToExecutionChange randomSignedBlsToExecutionChange() {
    return getCapellaSchemaDefinitions(randomSlot())
        .getSignedBlsToExecutionChangeSchema()
        .create(randomBlsToExecutionChange(), randomSignature());
  }

  public Bytes32 randomBlsWithdrawalCredentials() {
    return Bytes32.wrap(
        Bytes.concatenate(
            Bytes.fromHexString("0x000000000000000000000000"),
            randomEth1Address().getWrappedBytes()));
  }

  public Bytes32 randomEth1WithdrawalCredentials() {
    return Bytes32.wrap(
        Bytes.concatenate(
            Bytes.fromHexString("0x010000000000000000000000"),
            randomEth1Address().getWrappedBytes()));
  }

  public Bytes32 randomCompoundingWithdrawalCredentials() {
    return Bytes32.wrap(
        Bytes.concatenate(
            Bytes.fromHexString("0x020000000000000000000000"),
            randomEth1Address().getWrappedBytes()));
  }

  public List<VersionedHash> randomVersionedHashes(final int count) {
    return IntStream.range(0, count)
        .mapToObj(__ -> new VersionedHash(randomBytes32()))
        .collect(toList());
  }

  public Blob randomValidBlob() {
    final BlobSchema blobSchema = getDenebSchemaDefinitions(randomSlot()).getBlobSchema();
    List<Bytes> blobElements =
        Stream.generate(this::randomBlobElement).limit(blobSchema.getLength() / 32).toList();
    return blobSchema.create(Bytes.wrap(blobElements));
  }

  private Bytes randomBlobElement() {
    return Bytes.wrap(Bytes.of(0), randomBytes(31));
  }

  public Bytes randomBlobBytes() {
    return randomValidBlob().getBytes();
  }

  public List<BlobSidecar> randomBlobSidecarsForBlock(final SignedBeaconBlock block) {
    return randomBlobSidecarsForBlock(
        block, (blobSidecarIndex, blobSidecarBuilder) -> blobSidecarBuilder);
  }

  public List<BlobSidecar> randomBlobSidecarsForBlock(
      final SignedBeaconBlock block,
      final BiFunction<Integer, RandomBlobSidecarBuilder, RandomBlobSidecarBuilder>
          blobSidecarBuilderModifier) {
    final SszList<SszKZGCommitment> blobKzgCommitments =
        BeaconBlockBodyDeneb.required(block.getBeaconBlock().orElseThrow().getBody())
            .getBlobKzgCommitments();

    return IntStream.range(0, blobKzgCommitments.size())
        .mapToObj(
            index -> {
              final RandomBlobSidecarBuilder builder =
                  createRandomBlobSidecarBuilder()
                      .signedBeaconBlockHeader(block.asHeader())
                      .kzgCommitment(blobKzgCommitments.get(index).getBytes())
                      .index(UInt64.valueOf(index));

              return blobSidecarBuilderModifier.apply(index, builder).build();
            })
        .toList();
  }

  public BlobSidecar randomBlobSidecar() {
    return new RandomBlobSidecarBuilder().build();
  }

  public BlobSidecar randomBlobSidecarForBlock(
      final SignedBeaconBlock signedBeaconBlock, final long index) {
    return new RandomBlobSidecarBuilder()
        .signedBeaconBlockHeader(signedBeaconBlock.asHeader())
        .index(UInt64.valueOf(index))
        .build();
  }

  public BlobSidecar randomBlobSidecarWithValidInclusionProofForBlock(
      final SignedBeaconBlock signedBeaconBlock, final int index) {
    return new RandomBlobSidecarBuilder()
        .signedBeaconBlockHeader(signedBeaconBlock.asHeader())
        .index(UInt64.valueOf(index))
        .kzgCommitment(
            BeaconBlockBodyDeneb.required(signedBeaconBlock.getMessage().getBody())
                .getBlobKzgCommitments()
                .get(index)
                .getKZGCommitment()
                .getBytesCompressed())
        .kzgCommitmentInclusionProof(
            validKzgCommitmentInclusionProof(
                UInt64.valueOf(index), signedBeaconBlock.getBeaconBlock().orElseThrow().getBody()))
        .build();
  }

  public List<Blob> randomBlobs(final int count, final UInt64 slot) {
    final List<Blob> blobs = new ArrayList<>();
    final BlobSchema blobSchema = getDenebSchemaDefinitions(slot).getBlobSchema();
    for (int i = 0; i < count; i++) {
      blobs.add(new Blob(blobSchema, randomBytes(blobSchema.getLength())));
    }
    return blobs;
  }

  public List<BlobSidecar> randomBlobSidecars(final int count) {
    List<BlobSidecar> blobSidecars = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      blobSidecars.add(new RandomBlobSidecarBuilder().build());
    }
    return blobSidecars;
  }

  public BlobIdentifier randomBlobIdentifier() {
    return randomBlobIdentifier(randomBytes32());
  }

  public BlobIdentifier randomBlobIdentifier(final Bytes32 blockRoot) {
    final int maxBlobsPerBlock =
        spec.forMilestone(SpecMilestone.DENEB)
            .getConfig()
            .toVersionDeneb()
            .orElseThrow()
            .getMaxBlobsPerBlock();
    return new BlobIdentifier(blockRoot, randomUInt64(maxBlobsPerBlock));
  }

  public List<BlobIdentifier> randomBlobIdentifiers(final int count) {
    return IntStream.range(0, count).mapToObj(__ -> randomBlobIdentifier()).collect(toList());
  }

  public tech.pegasys.teku.spec.datastructures.builder.BlobsBundle randomBuilderBlobsBundle() {
    return randomBuilderBlobsBundle(randomNumberOfBlobsPerBlock());
  }

  public tech.pegasys.teku.spec.datastructures.builder.BlobsBundle randomBuilderBlobsBundle(
      final int count) {
    return randomBuilderBlobsBundle(randomBlobKzgCommitments(count));
  }

  public tech.pegasys.teku.spec.datastructures.builder.BlobsBundle randomBuilderBlobsBundle(
      final SszList<SszKZGCommitment> commitments) {
    final UInt64 slot = randomSlot();
    final BlobsBundleSchema<?> schema = getDenebSchemaDefinitions(slot).getBlobsBundleSchema();
    return schema.create(
        commitments,
        randomSszKZGProofs(getNumberOfRequiredProofs(slot, commitments.size())),
        randomSszBlobs(commitments.size()));
  }

  public BlobsBundle randomBlobsBundle() {
    return randomBlobsBundle(Optional.empty(), randomSlot());
  }

  public BlobsBundle randomBlobsBundle(final int count) {
    return randomBlobsBundle(Optional.of(count), randomSlot());
  }

  private BlobsBundle randomBlobsBundle(final Optional<Integer> count, final UInt64 slot) {
    final List<KZGCommitment> commitments =
        count.map(this::randomBlobKzgCommitments).orElse(randomBlobKzgCommitments()).stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .toList();
    final List<KZGProof> proofs =
        randomKZGProofs(getNumberOfRequiredProofs(slot, commitments.size()));
    final List<Blob> blobs = randomBlobs(commitments.size(), slot);
    if (proofs.size() > commitments.size()) {
      // includes cell proofs
      return new BlobsBundleFulu(commitments, proofs, blobs);
    } else {
      return new BlobsBundleDeneb(commitments, proofs, blobs);
    }
  }

  public SszList<SszKZGProof> randomSszKZGProofs(final int count) {
    return randomSszList(
        getDenebSchemaDefinitions(ZERO).getBlobsBundleSchema().getProofsSchema(),
        this::randomSszKZGProof,
        count);
  }

  public SszList<Blob> randomSszBlobs(final int count) {
    return randomSszList(
        getDenebSchemaDefinitions(ZERO).getBlobsBundleSchema().getBlobsSchema(),
        this::randomValidBlob,
        count);
  }

  public SignedBlockContainer randomSignedBlockContents() {
    return randomSignedBlockContents(randomSlot());
  }

  public SignedBlockContainer randomSignedBlockContents(final UInt64 slot) {
    final SignedBeaconBlock signedBeaconBlock = randomSignedBeaconBlock(slot);
    final int numberOfBlobs =
        signedBeaconBlock
            .getMessage()
            .getBody()
            .getOptionalBlobKzgCommitments()
            .orElseThrow()
            .size();
    final List<Blob> blobs = randomBlobs(numberOfBlobs, slot);
    final List<KZGProof> kzgProofs =
        randomKZGProofs(getNumberOfRequiredProofs(slot, numberOfBlobs));
    return getDenebSchemaDefinitions(slot)
        .getSignedBlockContentsSchema()
        .create(signedBeaconBlock, kzgProofs, blobs);
  }

  public SignedBlockContainer randomSignedBlockContents(final BlobsBundle blobsBundle) {
    final UInt64 slot = randomUInt64();
    final BlobKzgCommitmentsSchema blobKzgCommitmentsSchema =
        SchemaDefinitionsDeneb.required(spec.atSlot(slot).getSchemaDefinitions())
            .getBlobKzgCommitmentsSchema();
    final SignedBeaconBlock signedBeaconBlock =
        randomSignedBeaconBlockWithCommitments(
            blobKzgCommitmentsSchema.createFromBlobsBundle(blobsBundle));
    return getDenebSchemaDefinitions(slot)
        .getSignedBlockContentsSchema()
        .create(signedBeaconBlock, blobsBundle.getProofs(), blobsBundle.getBlobs());
  }

  public BlockContainer randomBlockContents() {
    return randomBlockContents(randomSlot());
  }

  public BlockContainer randomBlockContents(final UInt64 slot) {
    final BeaconBlock beaconBlock = randomBeaconBlock(slot);
    final int numberOfBlobs =
        beaconBlock.getBody().getOptionalBlobKzgCommitments().orElseThrow().size();
    final List<Blob> blobs = randomBlobs(numberOfBlobs, slot);
    final List<KZGProof> kzgProofs = randomKZGProofs(numberOfBlobs);
    return getDenebSchemaDefinitions(slot)
        .getBlockContentsSchema()
        .create(beaconBlock, kzgProofs, blobs);
  }

  public RandomBlobSidecarBuilder createRandomBlobSidecarBuilder() {
    return new RandomBlobSidecarBuilder();
  }

  public SszList<ProposerSlashing> randomProposerSlashings(
      final int count, final int validatorIndexLimit) {
    return randomSszList(
        spec.getGenesisSchemaDefinitions().getBeaconBlockBodySchema().getProposerSlashingsSchema(),
        () -> randomProposerSlashing(validatorIndexLimit),
        count);
  }

  public SszList<AttesterSlashing> randomAttesterSlashings(
      final int count, final int validatorIndexLimit) {
    return randomSszList(
        spec.getGenesisSchemaDefinitions().getBeaconBlockBodySchema().getAttesterSlashingsSchema(),
        () -> randomAttesterSlashing(validatorIndexLimit),
        count);
  }

  public SszList<Attestation> randomAttestations(final int count, final UInt64 slot) {
    return randomSszList(
        spec.getGenesisSchemaDefinitions().getBeaconBlockBodySchema().getAttestationsSchema(),
        () -> randomAttestation(slot),
        count);
  }

  public class RandomBlobSidecarBuilder {

    private Optional<UInt64> index = Optional.empty();
    private Optional<Bytes> blob = Optional.empty();
    private Optional<Bytes48> kzgCommitment = Optional.empty();
    private Optional<Bytes48> kzgProof = Optional.empty();
    private Optional<SignedBeaconBlockHeader> signedBeaconBlockHeader = Optional.empty();
    private Optional<List<Bytes32>> kzgCommitmentInclusionProof = Optional.empty();

    public RandomBlobSidecarBuilder index(final UInt64 index) {
      this.index = Optional.of(index);
      return this;
    }

    public RandomBlobSidecarBuilder blob(final Bytes blob) {
      this.blob = Optional.of(blob);
      return this;
    }

    public RandomBlobSidecarBuilder kzgCommitment(final Bytes48 kzgCommitment) {
      this.kzgCommitment = Optional.of(kzgCommitment);
      return this;
    }

    public RandomBlobSidecarBuilder kzgProof(final Bytes48 kzgProof) {
      this.kzgProof = Optional.of(kzgProof);
      return this;
    }

    public RandomBlobSidecarBuilder signedBeaconBlockHeader(
        final SignedBeaconBlockHeader signedBeaconBlockHeader) {
      this.signedBeaconBlockHeader = Optional.of(signedBeaconBlockHeader);
      return this;
    }

    public RandomBlobSidecarBuilder kzgCommitmentInclusionProof(
        final List<Bytes32> kzgCommitmentInclusionProof) {
      this.kzgCommitmentInclusionProof = Optional.of(kzgCommitmentInclusionProof);
      return this;
    }

    public BlobSidecar build() {
      final BlobSidecarSchema blobSidecarSchema =
          getDenebSchemaDefinitions(
                  signedBeaconBlockHeader
                      .map(header -> header.getMessage().getSlot())
                      .orElse(randomSlot()))
              .getBlobSidecarSchema();

      return blobSidecarSchema.create(
          index.orElse(randomBlobSidecarIndex()),
          blob.orElse(randomBytes(blobSidecarSchema.getBlobSchema().getLength())),
          kzgCommitment.orElse(randomBytes48()),
          kzgProof.orElse(randomBytes48()),
          signedBeaconBlockHeader.orElse(randomSignedBeaconBlockHeader()),
          kzgCommitmentInclusionProof.orElse(randomKzgCommitmentInclusionProof()));
    }
  }

  public class RandomDataColumnSidecarBuilder {

    private Optional<UInt64> index = Optional.empty();
    private Optional<DataColumn> dataColumn = Optional.empty();
    private Optional<List<KZGCommitment>> kzgCommitments = Optional.empty();
    private Optional<List<KZGProof>> kzgProofs = Optional.empty();
    private Optional<SignedBeaconBlockHeader> signedBeaconBlockHeader = Optional.empty();
    private Optional<List<Bytes32>> kzgCommitmentsInclusionProof = Optional.empty();
    private Optional<Bytes32> beaconBlockRoot = Optional.empty();
    private Optional<UInt64> slot = Optional.empty();

    public RandomDataColumnSidecarBuilder index(final UInt64 index) {
      this.index = Optional.of(index);
      return this;
    }

    public RandomDataColumnSidecarBuilder dataColumn(final DataColumn dataColumn) {
      this.dataColumn = Optional.of(dataColumn);
      return this;
    }

    public RandomDataColumnSidecarBuilder kzgCommitments(final List<KZGCommitment> kzgCommitments) {
      this.kzgCommitments = Optional.of(kzgCommitments);
      return this;
    }

    public RandomDataColumnSidecarBuilder kzgProofs(final List<KZGProof> kzgProofs) {
      this.kzgProofs = Optional.of(kzgProofs);
      return this;
    }

    public RandomDataColumnSidecarBuilder signedBeaconBlockHeader(
        final SignedBeaconBlockHeader signedBeaconBlockHeader) {
      this.signedBeaconBlockHeader = Optional.of(signedBeaconBlockHeader);
      return this;
    }

    public RandomDataColumnSidecarBuilder kzgCommitmentsInclusionProof(
        final List<Bytes32> kzgCommitmentsInclusionProof) {
      this.kzgCommitmentsInclusionProof = Optional.of(kzgCommitmentsInclusionProof);
      return this;
    }

    public RandomDataColumnSidecarBuilder beaconBlockRoot(final Bytes32 beaconBlockRoot) {
      this.beaconBlockRoot = Optional.of(beaconBlockRoot);
      return this;
    }

    public RandomDataColumnSidecarBuilder slot(final UInt64 slot) {
      this.slot = Optional.of(slot);
      return this;
    }

    public DataColumnSidecar build() {
      final SignedBeaconBlockHeader signedBlockHeader =
          signedBeaconBlockHeader.orElseGet(
              () ->
                  slot.map(DataStructureUtil.this::randomSignedBeaconBlockHeader)
                      .orElseGet(DataStructureUtil.this::randomSignedBeaconBlockHeader));
      final DataColumnSidecarSchema<?> dataColumnSidecarSchema =
          getFuluSchemaDefinitions(signedBlockHeader.getMessage().getSlot())
              .getDataColumnSidecarSchema();
      final int numberOfProofs =
          kzgProofs
              .map(List::size)
              .or(() -> kzgCommitments.map(List::size))
              .orElseGet(DataStructureUtil.this::randomNumberOfBlobsPerBlock);
      final SszList<SszKZGCommitment> sszKzgCommitments =
          dataColumnSidecarSchema
              .getKzgCommitmentsSchema()
              .createFromElements(
                  kzgCommitments
                      .orElseGet(
                          () ->
                              IntStream.range(0, numberOfProofs)
                                  .mapToObj(__ -> randomKZGCommitment())
                                  .toList())
                      .stream()
                      .map(SszKZGCommitment::new)
                      .toList());

      final SszList<SszKZGProof> sszKzgProofs =
          dataColumnSidecarSchema
              .getKzgProofsSchema()
              .createFromElements(
                  kzgProofs
                      .orElseGet(
                          () ->
                              IntStream.range(0, numberOfProofs)
                                  .mapToObj(__ -> randomKZGProof())
                                  .toList())
                      .stream()
                      .map(SszKZGProof::new)
                      .toList());

      return dataColumnSidecarSchema.create(
          builder ->
              builder
                  .index(index.orElseGet(DataStructureUtil.this::randomDataColumnSidecarIndex))
                  .column(
                      dataColumn.orElseGet(
                          () ->
                              randomDataColumn(
                                  signedBlockHeader.getMessage().getSlot(), numberOfProofs)))
                  .kzgCommitments(sszKzgCommitments)
                  .kzgProofs(sszKzgProofs)
                  .signedBlockHeader(signedBlockHeader)
                  .kzgCommitmentsInclusionProof(
                      kzgCommitmentsInclusionProof.orElseGet(
                          () ->
                              IntStream.range(0, getKzgCommitmentsInclusionProofDepth())
                                  .mapToObj(__ -> randomBytes32())
                                  .toList()))
                  .beaconBlockRoot(
                      beaconBlockRoot.orElseGet(() -> signedBlockHeader.getMessage().getRoot()))
                  .slot(slot.orElseGet(() -> signedBlockHeader.getMessage().getSlot())));
    }
  }

  public DataColumn randomDataColumn(final UInt64 slot, final int blobs) {
    final DataColumnSchema dataColumnSchema = getFuluSchemaDefinitions(slot).getDataColumnSchema();
    List<Cell> list = IntStream.range(0, blobs).mapToObj(__ -> randomCell(slot)).toList();
    return dataColumnSchema.create(list);
  }

  public Cell randomCell(final UInt64 slot) {
    final CellSchema cellSchema = getFuluSchemaDefinitions(slot).getCellSchema();
    return cellSchema.create(randomBytes(cellSchema.getLength()));
  }

  public KZGCell randomKZGCell() {
    return new KZGCell(randomBytes(BYTES_PER_CELL));
  }

  public DataColumnSidecar randomDataColumnSidecar() {
    return new RandomDataColumnSidecarBuilder().build();
  }

  public DataColumnSidecar randomDataColumnSidecar(
      final SignedBeaconBlockHeader header, final UInt64 index) {
    return new RandomDataColumnSidecarBuilder()
        .signedBeaconBlockHeader(header)
        .index(index)
        .build();
  }

  public DataColumnSidecar randomDataColumnSidecar(
      final SignedBeaconBlockHeader header,
      final SszList<SszKZGCommitment> kzgCommitments,
      final UInt64 index) {
    return new RandomDataColumnSidecarBuilder()
        .signedBeaconBlockHeader(header)
        .kzgCommitments(kzgCommitments.stream().map(SszKZGCommitment::getKZGCommitment).toList())
        .index(index)
        .build();
  }

  public DataColumnSidecar randomDataColumnSidecar(
      final List<KZGCommitment> kzgCommitments, final DataColumn dataColumn) {
    return new RandomDataColumnSidecarBuilder()
        .kzgCommitments(kzgCommitments)
        .dataColumn(dataColumn)
        .build();
  }

  public DataColumnSidecar randomDataColumnSidecar(
      final List<KZGProof> kzgProofs,
      final List<KZGCommitment> kzgCommitments,
      final DataColumn dataColumn) {
    return new RandomDataColumnSidecarBuilder()
        .kzgProofs(kzgProofs)
        .kzgCommitments(kzgCommitments)
        .dataColumn(dataColumn)
        .build();
  }

  public DataColumnSidecar randomDataColumnSidecarWithInclusionProof(
      final SignedBeaconBlock signedBeaconBlock, final UInt64 index) {
    final MiscHelpersFulu miscHelpersFulu =
        MiscHelpersFulu.required(spec.getGenesisSpec().miscHelpers());
    final List<KZGCommitment> kzgCommitments =
        signedBeaconBlock
            .getMessage()
            .getBody()
            .getOptionalBlobKzgCommitments()
            .orElseThrow()
            .asList()
            .stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .toList();
    final List<Bytes32> inclusionProof =
        miscHelpersFulu.computeDataColumnKzgCommitmentsInclusionProof(
            signedBeaconBlock.getBeaconBlock().orElseThrow().getBody());
    return new RandomDataColumnSidecarBuilder()
        .signedBeaconBlockHeader(signedBeaconBlock.asHeader())
        .kzgCommitments(kzgCommitments)
        .kzgCommitmentsInclusionProof(inclusionProof)
        .index(index)
        .build();
  }

  public List<Bytes32> randomKzgCommitmentInclusionProof() {
    final int depth =
        SpecConfigDeneb.required(spec.forMilestone(SpecMilestone.DENEB).getConfig())
            .getKzgCommitmentInclusionProofDepth();
    return IntStream.range(0, depth).mapToObj(__ -> randomBytes32()).toList();
  }

  public List<Bytes32> validKzgCommitmentInclusionProof(
      final UInt64 blobIndex, final BeaconBlockBody beaconBlockBody) {
    return MiscHelpersDeneb.required(spec.forMilestone(SpecMilestone.DENEB).miscHelpers())
        .computeBlobKzgCommitmentInclusionProof(blobIndex, beaconBlockBody);
  }

  public SszList<SszKZGCommitment> randomBlobKzgCommitments() {
    // use MAX_BLOBS_PER_BLOCK as a limit
    return randomBlobKzgCommitments(randomNumberOfBlobsPerBlock());
  }

  public SszList<SszKZGCommitment> randomBlobKzgCommitments(final int count) {
    return randomSszList(getBlobKzgCommitmentsSchema(), count, this::randomSszKZGCommitment);
  }

  public SszList<SszKZGCommitment> emptyBlobKzgCommitments() {
    return getBlobKzgCommitmentsSchema().of();
  }

  public ExecutionRequests randomExecutionRequests() {
    return new ExecutionRequestsBuilderElectra(
            spec.forMilestone(SpecMilestone.ELECTRA).getSchemaDefinitions().getSchemaRegistry())
        .deposits(randomDepositRequests())
        .withdrawals(randomWithdrawalRequests())
        .consolidations(randomConsolidationRequests())
        .build();
  }

  public List<Bytes> randomEncodedExecutionRequests() {
    final ExecutionRequestsSchema executionRequestsSchema =
        SchemaDefinitionsElectra.required(
                spec.forMilestone(SpecMilestone.ELECTRA).getSchemaDefinitions())
            .getExecutionRequestsSchema();
    return new ExecutionRequestsDataCodec(executionRequestsSchema)
        .encode(randomExecutionRequests());
  }

  public WithdrawalRequest randomWithdrawalRequest() {
    return getElectraSchemaDefinitions(randomSlot())
        .getWithdrawalRequestSchema()
        .create(randomEth1Address(), randomPublicKey(), randomUInt64());
  }

  public WithdrawalRequest withdrawalRequest(
      final Bytes20 sourceAddress, final BLSPublicKey validatorPubKey, final UInt64 amount) {
    return getElectraSchemaDefinitions(randomSlot())
        .getWithdrawalRequestSchema()
        .create(sourceAddress, validatorPubKey, amount);
  }

  public WithdrawalRequest withdrawalRequest(final Validator validator) {
    final Bytes20 executionAddress = new Bytes20(validator.getWithdrawalCredentials().slice(12));
    return getElectraSchemaDefinitions(randomSlot())
        .getWithdrawalRequestSchema()
        .create(executionAddress, validator.getPublicKey(), randomUInt64());
  }

  public WithdrawalRequest withdrawalRequest(final Validator validator, final UInt64 amount) {
    final Bytes20 executionAddress = new Bytes20(validator.getWithdrawalCredentials().slice(12));
    return getElectraSchemaDefinitions(randomSlot())
        .getWithdrawalRequestSchema()
        .create(executionAddress, validator.getPublicKey(), amount);
  }

  public PendingDeposit randomPendingDeposit() {
    return getElectraSchemaDefinitions(randomSlot())
        .getPendingDepositSchema()
        .create(
            randomSszPublicKey(),
            SszBytes32.of(randomEth1WithdrawalCredentials()),
            SszUInt64.of(randomUInt64()),
            randomSszSignature(),
            SszUInt64.of(randomUInt64()));
  }

  public ConsolidationRequest randomConsolidationRequest() {
    return getElectraSchemaDefinitions(randomSlot())
        .getConsolidationRequestSchema()
        .create(randomEth1Address(), randomPublicKey(), randomPublicKey());
  }

  public PendingConsolidation randomPendingConsolidation() {
    return getElectraSchemaDefinitions(randomSlot())
        .getPendingConsolidationSchema()
        .create(SszUInt64.of(randomValidatorIndex()), SszUInt64.of(randomValidatorIndex()));
  }

  public PendingPartialWithdrawal randomPendingPartialWithdrawal() {
    return getElectraSchemaDefinitions(randomSlot())
        .getPendingPartialWithdrawalSchema()
        .create(
            SszUInt64.of(randomValidatorIndex()),
            SszUInt64.of(randomUInt64()),
            SszUInt64.of(randomUInt64()));
  }

  public PendingPartialWithdrawal randomPendingPartialWithdrawal(final long validatorIndex) {
    return getElectraSchemaDefinitions(randomSlot())
        .getPendingPartialWithdrawalSchema()
        .create(
            SszUInt64.of(UInt64.valueOf(validatorIndex)),
            SszUInt64.of(randomUInt64()),
            SszUInt64.of(randomUInt64()));
  }

  public UInt64 randomBlobSidecarIndex() {
    return randomUInt64(
        spec.forMilestone(spec.getForkSchedule().getHighestSupportedMilestone())
            .getConfig()
            .toVersionDeneb()
            .orElseThrow()
            .getMaxBlobsPerBlock());
  }

  private int randomNumberOfBlobsPerBlock() {
    // minimum 1 blob
    return randomInt(
        1,
        spec.forMilestone(spec.getForkSchedule().getHighestSupportedMilestone())
                .getConfig()
                .toVersionDeneb()
                .orElseThrow()
                .getMaxBlobsPerBlock()
            + 1);
  }

  public UInt64 randomDataColumnSidecarIndex() {
    return randomUInt64(
        spec.forMilestone(spec.getForkSchedule().getHighestSupportedMilestone())
            .getConfig()
            .toVersionFulu()
            .orElseThrow()
            .getNumberOfColumns());
  }

  public PayloadAttestationData randomPayloadAttestationData() {
    return getGloasSchemaDefinitions()
        .getPayloadAttestationDataSchema()
        .create(randomBytes32(), randomSlot(), randomBoolean(), randomBoolean());
  }

  public PayloadAttestationData randomPayloadAttestationData(final UInt64 slot) {
    return getGloasSchemaDefinitions()
        .getPayloadAttestationDataSchema()
        .create(randomBytes32(), slot, true, true);
  }

  public PayloadAttestation randomPayloadAttestation() {
    return getGloasSchemaDefinitions()
        .getPayloadAttestationSchema()
        .create(
            randomSszBitvector(getPtcSize()), randomPayloadAttestationData(), randomSignature());
  }

  public SszList<PayloadAttestation> emptyPayloadAttestations() {
    return BeaconBlockBodySchemaGloas.required(
            getGloasSchemaDefinitions().getBeaconBlockBodySchema())
        .getPayloadAttestationsSchema()
        .createFromElements(List.of());
  }

  public BuilderPendingWithdrawal randomBuilderPendingWithdrawal() {
    return getGloasSchemaDefinitions()
        .getBuilderPendingWithdrawalSchema()
        .create(randomEth1Address(), randomUInt64(), randomUInt64(), randomEpoch());
  }

  public BuilderPendingPayment randomBuilderPendingPayment() {
    return getGloasSchemaDefinitions()
        .getBuilderPendingPaymentSchema()
        .create(randomUInt64(), randomBuilderPendingWithdrawal());
  }

  public PayloadAttestationMessage randomPayloadAttestationMessage() {
    return getGloasSchemaDefinitions()
        .getPayloadAttestationMessageSchema()
        .create(randomValidatorIndex(), randomPayloadAttestationData(), randomSignature());
  }

  public IndexedPayloadAttestation randomIndexedPayloadAttestation() {
    final IndexedPayloadAttestationSchema indexedPayloadAttestationSchema =
        getGloasSchemaDefinitions().getIndexedPayloadAttestationSchema();
    return indexedPayloadAttestationSchema.create(
        randomSszUInt64List(indexedPayloadAttestationSchema.getAttestingIndicesSchema()),
        randomPayloadAttestationData(),
        randomSignature());
  }

  public ExecutionPayloadBid randomExecutionPayloadBid() {
    return randomExecutionPayloadBid(randomSlot(), randomBuilderIndex());
  }

  public ExecutionPayloadBid randomExecutionPayloadBid(
      final UInt64 slot, final UInt64 builderIndex) {
    return getGloasSchemaDefinitions()
        .getExecutionPayloadBidSchema()
        .create(
            randomBytes32(),
            randomBytes32(),
            randomBytes32(),
            randomBytes32(),
            randomEth1Address(),
            randomUInt64(),
            builderIndex,
            slot,
            randomUInt64(),
            randomUInt64(),
            randomBytes32());
  }

  public SignedExecutionPayloadBid randomSignedExecutionPayloadBid() {
    return getGloasSchemaDefinitions()
        .getSignedExecutionPayloadBidSchema()
        .create(randomExecutionPayloadBid(), randomSignature());
  }

  public ExecutionPayloadEnvelope randomExecutionPayloadEnvelope() {
    return randomExecutionPayloadEnvelope(randomSlot());
  }

  public ExecutionPayloadEnvelope randomExecutionPayloadEnvelope(final UInt64 slot) {
    return randomExecutionPayloadEnvelope(slot, randomBlobKzgCommitments());
  }

  public ExecutionPayloadEnvelope randomExecutionPayloadEnvelope(
      final UInt64 slot, final SszList<SszKZGCommitment> kzgCommitments) {
    return getGloasSchemaDefinitions()
        .getExecutionPayloadEnvelopeSchema()
        .create(
            randomExecutionPayload(),
            randomExecutionRequests(),
            randomBuilderIndex(),
            randomBytes32(),
            slot,
            kzgCommitments,
            randomBytes32());
  }

  public SignedExecutionPayloadEnvelope randomSignedExecutionPayloadEnvelope(final long slot) {
    return getGloasSchemaDefinitions()
        .getSignedExecutionPayloadEnvelopeSchema()
        .create(randomExecutionPayloadEnvelope(UInt64.valueOf(slot)), randomSignature());
  }

  public ExecutionProof randomExecutionProof() {
    final SchemaDefinitionsElectra schemaDefinitionsElectra =
        SchemaDefinitionsElectra.required(
            spec.forMilestone(SpecMilestone.ELECTRA).getSchemaDefinitions());
    final ExecutionProofSchema executionProofSchema =
        schemaDefinitionsElectra.getExecutionProofSchema();
    return executionProofSchema.create(
        SszBytes32.of(randomBytes32()),
        SszBytes32.of(randomBytes32()),
        SszUInt64.of(randomUInt64()),
        SszUInt64.of(randomUInt64()),
        executionProofSchema.getProofDataSchema().fromBytes(randomBytes(5)));
  }

  private int randomInt(final int origin, final int bound) {
    return new Random(nextSeed()).ints(origin, bound).findFirst().orElse(0);
  }

  private int randomInt(final int bound) {
    return new Random(nextSeed()).nextInt(bound);
  }

  private SchemaDefinitionsAltair getAltairSchemaDefinitions(final UInt64 slot) {
    return SchemaDefinitionsAltair.required(spec.atSlot(slot).getSchemaDefinitions());
  }

  private SchemaDefinitionsBellatrix getBellatrixSchemaDefinitions(final UInt64 slot) {
    return SchemaDefinitionsBellatrix.required(spec.atSlot(slot).getSchemaDefinitions());
  }

  private SchemaDefinitionsCapella getCapellaSchemaDefinitions(final UInt64 slot) {
    return SchemaDefinitionsCapella.required(spec.atSlot(slot).getSchemaDefinitions());
  }

  private SchemaDefinitionsDeneb getDenebSchemaDefinitions(final UInt64 slot) {
    return SchemaDefinitionsDeneb.required(spec.atSlot(slot).getSchemaDefinitions());
  }

  private SchemaDefinitionsElectra getElectraSchemaDefinitions(final UInt64 slot) {
    return SchemaDefinitionsElectra.required(spec.atSlot(slot).getSchemaDefinitions());
  }

  private SchemaDefinitionsFulu getFuluSchemaDefinitions(final UInt64 slot) {
    return SchemaDefinitionsFulu.required(spec.atSlot(slot).getSchemaDefinitions());
  }

  private SchemaDefinitionsGloas getGloasSchemaDefinitions() {
    return SchemaDefinitionsGloas.required(
        spec.forMilestone(SpecMilestone.GLOAS).getSchemaDefinitions());
  }

  int getEpochsPerEth1VotingPeriod() {
    return getConstant(SpecConfig::getEpochsPerEth1VotingPeriod);
  }

  int getSlotsPerEpoch() {
    return getConstant(SpecConfig::getSlotsPerEpoch);
  }

  int getPtcSize() {
    return getConstant(
        specConfig ->
            SpecConfigGloas.required(spec.forMilestone(SpecMilestone.GLOAS).getConfig())
                .getPtcSize());
  }

  int getKzgCommitmentsInclusionProofDepth() {
    return getConstant(
            specConfig ->
                SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig())
                    .getKzgCommitmentsInclusionProofDepth())
        .intValue();
  }

  int getJustificationBitsLength() {
    return getConstant(SpecConfig::getJustificationBitsLength);
  }

  private int getMaxValidatorsPerCommittee(final UInt64 slot) {
    if (spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.ELECTRA)) {
      return getConstant(SpecConfig::getMaxValidatorsPerCommittee)
          * getConstant(SpecConfig::getMaxCommitteesPerSlot);
    }
    return getConstant(SpecConfig::getMaxValidatorsPerCommittee);
  }

  private int getMaxCommitteesPerSlot() {
    return getConstant(SpecConfig::getMaxCommitteesPerSlot);
  }

  private UInt64 getMaxEffectiveBalance() {
    return getConstant(SpecConfig::getMaxEffectiveBalance);
  }

  private Integer getMaxPayloadAttestations() {
    return getConstant(
        specConfig ->
            SpecConfigGloas.required(spec.forMilestone(SpecMilestone.GLOAS).getConfig())
                .getMaxPayloadAttestations());
  }

  private int getNumberOfRequiredProofs(final UInt64 slot, final int numberOfCommitments) {
    if (spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
      return numberOfCommitments * CELLS_PER_EXT_BLOB;
    }
    return numberOfCommitments;
  }

  private Bytes32 computeDepositDomain() {
    final SpecVersion genesisSpec = spec.getGenesisSpec();
    final Bytes4 domain = Domain.DEPOSIT;
    return genesisSpec.miscHelpers().computeDomain(domain);
  }

  private Bytes getSigningRoot(final Merkleizable object, final Bytes32 domain) {
    return spec.getGenesisSpec().miscHelpers().computeSigningRoot(object, domain);
  }

  UInt64 computeStartSlotAtEpoch(final UInt64 epoch) {
    return spec.computeStartSlotAtEpoch(epoch);
  }

  public Spec getSpec() {
    return spec;
  }

  public BeaconStateSchema<?, ?> getBeaconStateSchema() {
    return spec.getGenesisSpec().getSchemaDefinitions().getBeaconStateSchema();
  }

  private <T> T getConstant(final Function<SpecConfig, T> getter) {
    return getter.apply(spec.getGenesisSpec().getConfig());
  }
}
