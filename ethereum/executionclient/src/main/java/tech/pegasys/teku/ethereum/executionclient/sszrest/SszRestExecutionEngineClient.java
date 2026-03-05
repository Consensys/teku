/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.ethereum.executionclient.sszrest;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobAndProofV1;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobAndProofV2;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobsBundleV1;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobsBundleV2;
import tech.pegasys.teku.ethereum.executionclient.schema.ClientVersionV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV2;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV3;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV4;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV2Response;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV3Response;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV4Response;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV5Response;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV6Response;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV2;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV3;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV4;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsSchema;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

/**
 * ExecutionEngineClient implementation that uses SSZ-REST as the primary transport and falls back
 * to a JSON-RPC (Web3j) delegate on network errors.
 *
 * <p>Only Engine API methods that have SSZ-REST counterparts are transported over SSZ-REST. Legacy
 * methods (V1/V2) and eth_* methods always go through JSON-RPC.
 */
public class SszRestExecutionEngineClient implements ExecutionEngineClient {

  private static final Logger LOG = LogManager.getLogger();

  private final SszRestClient sszRestClient;
  private final ExecutionEngineClient jsonRpcFallback;
  private final Spec spec;

  public SszRestExecutionEngineClient(
      final SszRestClient sszRestClient,
      final ExecutionEngineClient jsonRpcFallback,
      final Spec spec) {
    this.sszRestClient = sszRestClient;
    this.jsonRpcFallback = jsonRpcFallback;
    this.spec = spec;
  }

  // -- Legacy methods: always delegated to JSON-RPC --

  @Override
  public SafeFuture<PowBlock> getPowBlock(final Bytes32 blockHash) {
    return jsonRpcFallback.getPowBlock(blockHash);
  }

  @Override
  public SafeFuture<PowBlock> getPowChainHead() {
    return jsonRpcFallback.getPowChainHead();
  }

  @Override
  public SafeFuture<Response<ExecutionPayloadV1>> getPayloadV1(final Bytes8 payloadId) {
    return jsonRpcFallback.getPayloadV1(payloadId);
  }

  @Override
  public SafeFuture<Response<GetPayloadV2Response>> getPayloadV2(final Bytes8 payloadId) {
    return jsonRpcFallback.getPayloadV2(payloadId);
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV1(
      final ExecutionPayloadV1 executionPayload) {
    return jsonRpcFallback.newPayloadV1(executionPayload);
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV2(
      final ExecutionPayloadV2 executionPayload) {
    return jsonRpcFallback.newPayloadV2(executionPayload);
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV1(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV1> payloadAttributes) {
    return jsonRpcFallback.forkChoiceUpdatedV1(forkChoiceState, payloadAttributes);
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV2(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV2> payloadAttributes) {
    return jsonRpcFallback.forkChoiceUpdatedV2(forkChoiceState, payloadAttributes);
  }

  @Override
  public SafeFuture<Response<List<ClientVersionV1>>> getClientVersionV1(
      final ClientVersionV1 clientVersion) {
    return jsonRpcFallback.getClientVersionV1(clientVersion);
  }

  @Override
  public SafeFuture<Response<List<BlobAndProofV1>>> getBlobsV1(
      final List<VersionedHash> blobVersionedHashes) {
    return jsonRpcFallback.getBlobsV1(blobVersionedHashes);
  }

  @Override
  public SafeFuture<Response<List<BlobAndProofV2>>> getBlobsV2(
      final List<VersionedHash> blobVersionedHashes) {
    return jsonRpcFallback.getBlobsV2(blobVersionedHashes);
  }

  // -- SSZ-REST capable methods with JSON-RPC fallback --

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV3(
      final ExecutionPayloadV3 executionPayload,
      final List<VersionedHash> blobVersionedHashes,
      final Bytes32 parentBeaconBlockRoot) {
    return doNewPayloadViaSszRest(
            executionPayload,
            blobVersionedHashes,
            parentBeaconBlockRoot,
            null,
            3,
            SpecMilestone.DENEB)
        .exceptionallyCompose(
            e -> {
              if (SszRestException.isNetworkError(e)) {
                LOG.debug(
                    "SSZ-REST newPayloadV3 failed with network error, falling back to JSON-RPC",
                    e);
                return jsonRpcFallback.newPayloadV3(
                    executionPayload, blobVersionedHashes, parentBeaconBlockRoot);
              }
              return SafeFuture.failedFuture(e);
            });
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV4(
      final ExecutionPayloadV3 executionPayload,
      final List<VersionedHash> blobVersionedHashes,
      final Bytes32 parentBeaconBlockRoot,
      final List<Bytes> executionRequests) {
    return doNewPayloadViaSszRest(
            executionPayload,
            blobVersionedHashes,
            parentBeaconBlockRoot,
            executionRequests,
            4,
            SpecMilestone.ELECTRA)
        .exceptionallyCompose(
            e -> {
              if (SszRestException.isNetworkError(e)) {
                LOG.debug(
                    "SSZ-REST newPayloadV4 failed with network error, falling back to JSON-RPC",
                    e);
                return jsonRpcFallback.newPayloadV4(
                    executionPayload,
                    blobVersionedHashes,
                    parentBeaconBlockRoot,
                    executionRequests);
              }
              return SafeFuture.failedFuture(e);
            });
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayloadV5(
      final ExecutionPayloadV4 executionPayload,
      final List<VersionedHash> blobVersionedHashes,
      final Bytes32 parentBeaconBlockRoot,
      final List<Bytes> executionRequests) {
    return doNewPayloadViaSszRest(
            executionPayload,
            blobVersionedHashes,
            parentBeaconBlockRoot,
            executionRequests,
            5,
            SpecMilestone.FULU)
        .exceptionallyCompose(
            e -> {
              if (SszRestException.isNetworkError(e)) {
                LOG.debug(
                    "SSZ-REST newPayloadV5 failed with network error, falling back to JSON-RPC",
                    e);
                return jsonRpcFallback.newPayloadV5(
                    executionPayload,
                    blobVersionedHashes,
                    parentBeaconBlockRoot,
                    executionRequests);
              }
              return SafeFuture.failedFuture(e);
            });
  }

  @Override
  public SafeFuture<Response<GetPayloadV3Response>> getPayloadV3(final Bytes8 payloadId) {
    return sszRestClient
        .doRequest(
            "/engine/v3/get_payload", SszRestEncoding.encodeGetPayloadRequest(payloadId))
        .thenApply(
            data -> {
              final SszRestEncoding.GetPayloadResult result =
                  SszRestEncoding.decodeGetPayloadResponseV3(data);
              final SszRestEncoding.DecodedBlobsBundle bb =
                  SszRestEncoding.decodeBlobsBundle(result.blobsBundleSsz().toArrayUnsafe());
              final ExecutionPayloadSchema<?> payloadSchema = getPayloadSchema(SpecMilestone.DENEB);
              final ExecutionPayload ep =
                  payloadSchema.sszDeserialize(result.executionPayloadSsz());
              return new GetPayloadV3Response(
                  ExecutionPayloadV3.fromInternalExecutionPayload(ep),
                  result.blockValue(),
                  toBlobsBundleV1(bb),
                  result.shouldOverrideBuilder());
            })
        .thenApply(Response::fromPayloadReceivedAsSsz)
        .exceptionallyCompose(
            e -> {
              if (SszRestException.isNetworkError(e)) {
                LOG.debug(
                    "SSZ-REST getPayloadV3 failed with network error, falling back to JSON-RPC",
                    e);
                return jsonRpcFallback.getPayloadV3(payloadId);
              }
              return SafeFuture.failedFuture(e);
            });
  }

  @Override
  public SafeFuture<Response<GetPayloadV4Response>> getPayloadV4(final Bytes8 payloadId) {
    return sszRestClient
        .doRequest(
            "/engine/v4/get_payload", SszRestEncoding.encodeGetPayloadRequest(payloadId))
        .thenApply(
            data -> {
              final SszRestEncoding.GetPayloadResult result =
                  SszRestEncoding.decodeGetPayloadResponse(data);
              final SszRestEncoding.DecodedBlobsBundle bb =
                  SszRestEncoding.decodeBlobsBundle(result.blobsBundleSsz().toArrayUnsafe());
              final ExecutionPayloadSchema<?> payloadSchema =
                  getPayloadSchema(SpecMilestone.ELECTRA);
              final ExecutionPayload ep =
                  payloadSchema.sszDeserialize(result.executionPayloadSsz());
              final List<Bytes> requestsList =
                  decodeExecutionRequestsToList(result.executionRequestsSsz());
              return new GetPayloadV4Response(
                  ExecutionPayloadV3.fromInternalExecutionPayload(ep),
                  result.blockValue(),
                  toBlobsBundleV1(bb),
                  result.shouldOverrideBuilder(),
                  requestsList);
            })
        .thenApply(Response::fromPayloadReceivedAsSsz)
        .exceptionallyCompose(
            e -> {
              if (SszRestException.isNetworkError(e)) {
                LOG.debug(
                    "SSZ-REST getPayloadV4 failed with network error, falling back to JSON-RPC",
                    e);
                return jsonRpcFallback.getPayloadV4(payloadId);
              }
              return SafeFuture.failedFuture(e);
            });
  }

  @Override
  public SafeFuture<Response<GetPayloadV5Response>> getPayloadV5(final Bytes8 payloadId) {
    return sszRestClient
        .doRequest(
            "/engine/v5/get_payload", SszRestEncoding.encodeGetPayloadRequest(payloadId))
        .thenApply(
            data -> {
              final SszRestEncoding.GetPayloadResult result =
                  SszRestEncoding.decodeGetPayloadResponse(data);
              final SszRestEncoding.DecodedBlobsBundle bb =
                  SszRestEncoding.decodeBlobsBundle(result.blobsBundleSsz().toArrayUnsafe());
              final ExecutionPayloadSchema<?> payloadSchema =
                  getPayloadSchema(SpecMilestone.FULU);
              final ExecutionPayload ep =
                  payloadSchema.sszDeserialize(result.executionPayloadSsz());
              final List<Bytes> requestsList =
                  decodeExecutionRequestsToList(result.executionRequestsSsz());
              return new GetPayloadV5Response(
                  ExecutionPayloadV3.fromInternalExecutionPayload(ep),
                  result.blockValue(),
                  toBlobsBundleV2(bb),
                  result.shouldOverrideBuilder(),
                  requestsList);
            })
        .thenApply(Response::fromPayloadReceivedAsSsz)
        .exceptionallyCompose(
            e -> {
              if (SszRestException.isNetworkError(e)) {
                LOG.debug(
                    "SSZ-REST getPayloadV5 failed with network error, falling back to JSON-RPC",
                    e);
                return jsonRpcFallback.getPayloadV5(payloadId);
              }
              return SafeFuture.failedFuture(e);
            });
  }

  @Override
  public SafeFuture<Response<GetPayloadV6Response>> getPayloadV6(final Bytes8 payloadId) {
    return sszRestClient
        .doRequest(
            "/engine/v6/get_payload", SszRestEncoding.encodeGetPayloadRequest(payloadId))
        .thenApply(
            data -> {
              final SszRestEncoding.GetPayloadResult result =
                  SszRestEncoding.decodeGetPayloadResponse(data);
              final SszRestEncoding.DecodedBlobsBundle bb =
                  SszRestEncoding.decodeBlobsBundle(result.blobsBundleSsz().toArrayUnsafe());
              final ExecutionPayloadSchema<?> payloadSchema =
                  getPayloadSchema(SpecMilestone.GLOAS);
              final ExecutionPayload ep =
                  payloadSchema.sszDeserialize(result.executionPayloadSsz());
              final List<Bytes> requestsList =
                  decodeExecutionRequestsToList(result.executionRequestsSsz());
              return new GetPayloadV6Response(
                  ExecutionPayloadV4.fromInternalExecutionPayload(ep, UInt64.ZERO),
                  result.blockValue(),
                  toBlobsBundleV2(bb),
                  result.shouldOverrideBuilder(),
                  requestsList);
            })
        .thenApply(Response::fromPayloadReceivedAsSsz)
        .exceptionallyCompose(
            e -> {
              if (SszRestException.isNetworkError(e)) {
                LOG.debug(
                    "SSZ-REST getPayloadV6 failed with network error, falling back to JSON-RPC",
                    e);
                return jsonRpcFallback.getPayloadV6(payloadId);
              }
              return SafeFuture.failedFuture(e);
            });
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV3(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV3> payloadAttributes) {
    return doForkChoiceUpdatedViaSszRest(forkChoiceState, payloadAttributes)
        .exceptionallyCompose(
            e -> {
              if (SszRestException.isNetworkError(e)) {
                LOG.debug(
                    "SSZ-REST forkChoiceUpdatedV3 failed with network error, falling back to JSON-RPC",
                    e);
                return jsonRpcFallback.forkChoiceUpdatedV3(forkChoiceState, payloadAttributes);
              }
              return SafeFuture.failedFuture(e);
            });
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV4(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV4> payloadAttributes) {
    // PayloadAttributesV4 extends V3, so the same encoding works
    return doForkChoiceUpdatedViaSszRest(
            forkChoiceState,
            payloadAttributes.map(
                v4 ->
                    new PayloadAttributesV3(
                        v4.timestamp,
                        v4.prevRandao,
                        v4.suggestedFeeRecipient,
                        v4.withdrawals,
                        v4.parentBeaconBlockRoot)))
        .exceptionallyCompose(
            e -> {
              if (SszRestException.isNetworkError(e)) {
                LOG.debug(
                    "SSZ-REST forkChoiceUpdatedV4 failed with network error, falling back to JSON-RPC",
                    e);
                return jsonRpcFallback.forkChoiceUpdatedV4(forkChoiceState, payloadAttributes);
              }
              return SafeFuture.failedFuture(e);
            });
  }

  @Override
  public SafeFuture<Response<List<String>>> exchangeCapabilities(final List<String> capabilities) {
    return sszRestClient
        .doRequest(
            "/engine/v1/exchange_capabilities",
            SszRestEncoding.encodeExchangeCapabilities(capabilities))
        .thenApply(SszRestEncoding::decodeExchangeCapabilities)
        .thenApply(Response::fromPayloadReceivedAsSsz)
        .exceptionallyCompose(
            e -> {
              if (SszRestException.isNetworkError(e)) {
                LOG.debug(
                    "SSZ-REST exchangeCapabilities failed with network error, falling back to JSON-RPC",
                    e);
                return jsonRpcFallback.exchangeCapabilities(capabilities);
              }
              return SafeFuture.failedFuture(e);
            });
  }

  // -- Private helpers --

  private ExecutionPayloadSchema<?> getPayloadSchema(final SpecMilestone milestone) {
    final SchemaDefinitions schemaDefs = spec.forMilestone(milestone).getSchemaDefinitions();
    return SchemaDefinitionsBellatrix.required(schemaDefs).getExecutionPayloadSchema();
  }

  private ExecutionRequestsSchema getExecutionRequestsSchema() {
    final SchemaDefinitions schemaDefs =
        spec.forMilestone(SpecMilestone.ELECTRA).getSchemaDefinitions();
    return SchemaDefinitionsElectra.required(schemaDefs).getExecutionRequestsSchema();
  }

  /**
   * Decode SSZ-serialized ExecutionRequests container to the List<Bytes> format (EIP-7685 type
   * prefixed) expected by the JSON schema types.
   */
  private List<Bytes> decodeExecutionRequestsToList(final Bytes executionRequestsSsz) {
    if (executionRequestsSsz.isEmpty()) {
      return List.of();
    }
    final ExecutionRequestsSchema requestsSchema = getExecutionRequestsSchema();
    final ExecutionRequests requests = requestsSchema.sszDeserialize(executionRequestsSsz);
    return new ExecutionRequestsDataCodec(requestsSchema).encode(requests);
  }

  /**
   * Serialize the List<Bytes> execution requests (EIP-7685 format) to SSZ container bytes.
   */
  private byte[] encodeExecutionRequestsToSsz(final List<Bytes> executionRequests) {
    final ExecutionRequestsSchema requestsSchema = getExecutionRequestsSchema();
    final ExecutionRequests requests =
        new ExecutionRequestsDataCodec(requestsSchema).decode(executionRequests);
    return requests.sszSerialize().toArrayUnsafe();
  }

  private SafeFuture<Response<PayloadStatusV1>> doNewPayloadViaSszRest(
      final ExecutionPayloadV3 executionPayload,
      final List<VersionedHash> blobVersionedHashes,
      final Bytes32 parentBeaconBlockRoot,
      final List<Bytes> executionRequests,
      final int version,
      final SpecMilestone milestone) {
    final ExecutionPayloadSchema<?> payloadSchema = getPayloadSchema(milestone);
    final ExecutionPayload specPayload =
        executionPayload.asInternalExecutionPayload(payloadSchema);
    final byte[] executionPayloadSsz = specPayload.sszSerialize().toArrayUnsafe();

    final List<Bytes32> hashes =
        blobVersionedHashes.stream()
            .map(VersionedHash::get)
            .collect(Collectors.toList());

    final byte[] requestBody;
    if (executionRequests != null) {
      final byte[] requestsSsz = encodeExecutionRequestsToSsz(executionRequests);
      requestBody =
          SszRestEncoding.encodeNewPayloadV4Request(
              executionPayloadSsz, hashes, parentBeaconBlockRoot, requestsSsz);
    } else {
      requestBody =
          SszRestEncoding.encodeNewPayloadV3Request(
              executionPayloadSsz, hashes, parentBeaconBlockRoot);
    }

    final String path = "/engine/v" + version + "/new_payload";
    return sszRestClient
        .doRequest(path, requestBody)
        .thenApply(SszRestEncoding::decodePayloadStatus)
        .thenApply(this::toPayloadStatusV1)
        .thenApply(Response::fromPayloadReceivedAsSsz);
  }

  private SafeFuture<Response<ForkChoiceUpdatedResult>> doForkChoiceUpdatedViaSszRest(
      final ForkChoiceStateV1 forkChoiceState,
      final Optional<PayloadAttributesV3> payloadAttributes) {
    final byte[] attrSsz =
        payloadAttributes
            .map(
                attrs ->
                    SszRestEncoding.encodePayloadAttributesV3(
                        attrs.timestamp,
                        attrs.prevRandao,
                        attrs.suggestedFeeRecipient,
                        attrs.withdrawals,
                        attrs.parentBeaconBlockRoot))
            .orElse(null);

    final byte[] requestBody =
        SszRestEncoding.encodeForkchoiceUpdatedRequest(
            forkChoiceState.getHeadBlockHash(),
            forkChoiceState.getSafeBlockHash(),
            forkChoiceState.getFinalizedBlockHash(),
            attrSsz);

    return sszRestClient
        .doRequest("/engine/v3/forkchoice_updated", requestBody)
        .thenApply(SszRestEncoding::decodeForkchoiceUpdatedResponse)
        .thenApply(this::toForkChoiceUpdatedResult)
        .thenApply(Response::fromPayloadReceivedAsSsz);
  }

  private PayloadStatusV1 toPayloadStatusV1(
      final SszRestEncoding.PayloadStatusResult decoded) {
    return new PayloadStatusV1(
        decoded.status(), decoded.latestValidHash(), decoded.validationError());
  }

  private ForkChoiceUpdatedResult toForkChoiceUpdatedResult(
      final SszRestEncoding.ForkchoiceUpdatedResult decoded) {
    final PayloadStatusV1 payloadStatus =
        new PayloadStatusV1(
            decoded.payloadStatus().status(),
            decoded.payloadStatus().latestValidHash(),
            decoded.payloadStatus().validationError());
    return new ForkChoiceUpdatedResult(payloadStatus, decoded.payloadId());
  }

  private static BlobsBundleV1 toBlobsBundleV1(final SszRestEncoding.DecodedBlobsBundle bb) {
    return new BlobsBundleV1(
        bb.commitments().stream().map(Bytes48::wrap).collect(Collectors.toList()),
        bb.proofs().stream().map(Bytes48::wrap).collect(Collectors.toList()),
        bb.blobs());
  }

  private static BlobsBundleV2 toBlobsBundleV2(final SszRestEncoding.DecodedBlobsBundle bb) {
    return new BlobsBundleV2(
        bb.commitments().stream().map(Bytes48::wrap).collect(Collectors.toList()),
        bb.proofs().stream().map(Bytes48::wrap).collect(Collectors.toList()),
        bb.blobs());
  }
}
