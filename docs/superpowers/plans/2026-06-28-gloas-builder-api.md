# Gloas Builder API (builder-specs PR #138) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the three new Gloas (ePBS) Builder API endpoints from [ethereum/builder-specs#138](https://github.com/ethereum/builder-specs/pull/138) in Teku's builder REST client so Teku (as proposer) can request execution payload bids, submit builder preferences, and submit signed beacon blocks to a builder/relay.

**Architecture:** Teku is a *client* of the Builder API. The Gloas ePBS builder flow is a **different protocol** from mev-boost and is therefore implemented in a **brand-new Gradle module `builder:client`** (top-level `builder/` directory, mirroring the `validator:*` layout) with its **own interface** — the existing mev-boost `BuilderClient` interface and `RestBuilderClient` in `ethereum/executionclient` are **left completely untouched and are NOT extended or reused**. The new module defines its own `GloasBuilderApiClient` interface, `GloasBuilderApiMethod` route enum, and `RestGloasBuilderApiClient` implementation. It depends on `:ethereum:executionclient` only for the *generic, protocol-agnostic HTTP transport* (`RestClient`/`OkHttpRestClient`/`BuilderApiResponse`/`Response`) — not for any mev-boost-specific logic. All SSZ container schemas the endpoints need already exist (created in prior Gloas work) — this plan creates **no** new spec data structures.

**Tech Stack:** Java 21, Gradle, OkHttp3, Teku SSZ/JSON serialization framework (`DeserializableTypeDefinition`, `SszSchema`), JUnit 5 + `MockWebServer` for integration tests, Mockito.

> **Design decision (per request):** Do not reuse `BuilderClient`. The new `GloasBuilderApiClient` lives in a new `builder:client` module. The only shared code is the generic REST transport in `ethereum/executionclient/.../rest/` (`RestClient`, `OkHttpRestClient`, `BuilderApiResponse`, `Response`, `ResponseSchemaAndDeserializableTypeDefinition`). If full isolation is preferred over that single dependency, those transport classes could later be promoted to a shared `infrastructure`/`ethereum` module — flagged but out of scope here.

## Global Constraints

- License header: every new/modified Java file must carry the existing Consensys Apache-2.0 header (copy verbatim from any sibling file, year `2026`).
- Spotless/import-order is enforced — run `./gradlew :ethereum:executionclient:spotlessApply` before each commit.
- Builder API base path prefix is `eth/vN/builder/...` with **no leading slash** (matches existing `BuilderApiMethod` entries).
- Endpoint path params in routes use the `:name` placeholder syntax resolved by `BuilderApiMethod.resolvePath(Map)`.
- The consensus-version HTTP header constant is `RestApiConstants.HEADER_CONSENSUS_VERSION` (`Eth-Consensus-Version`); its value is the milestone name lowercased: `milestone.name().toLowerCase(Locale.ROOT)`.
- Milestone for a slot is obtained via `spec.atSlot(slot)` → `SpecVersion.getMilestone()`.
- These endpoints are **Gloas-only**. Callers must only invoke them when `spec.atSlot(slot).getMilestone() == SpecMilestone.GLOAS` (or later). The client methods themselves resolve schemas via `SchemaDefinitionsGloas`; calling them for a pre-Gloas slot must throw a clear `IllegalArgumentException`.
- **New module:** `builder:client` (directory `builder/client`), registered in `settings.gradle` as `include 'builder:client'`. Root package `tech.pegasys.teku.builder.client`. Java source under `builder/client/src/main/java/...`, integration tests under `builder/client/src/integration-test/java/...` (the `integration-test` source set, `testFixtures`, Spotless, and Java toolchain are all configured globally in the root `build.gradle` `subprojects` block — no per-module plugin wiring needed, exactly like `ethereum:executionclient`).
- **Naming (new module):** interface `GloasBuilderApiClient`; route enum `GloasBuilderApiMethod`; REST impl `RestGloasBuilderApiClient`; options record/class `GloasBuilderApiClientOptions`; integration test `RestGloasBuilderApiClientTest`.

---

## Background: What Already Exists (do NOT recreate)

Confirmed present in the codebase — these are the building blocks the new endpoints consume:

| Schema container | Schema class | Access point |
|---|---|---|
| `SignedRequestAuth` (`message: RequestAuth{data: ByteList[4096], slot: Uint64}`, `signature: BLSSignature`) | `SignedRequestAuthSchema` | `tech.pegasys.teku.spec.schemas.ApiSchemas.SIGNED_REQUEST_AUTH_SCHEMA` |
| `BuilderPreferencesRequest` (`preferences: BuilderPreferences{maxExecutionPayment: Uint64}`, `auth: SignedRequestAuth`) | `BuilderPreferencesRequestSchema` | `tech.pegasys.teku.spec.schemas.ApiSchemas.BUILDER_PREFERENCES_REQUEST_SCHEMA` |
| `SignedExecutionPayloadBid` (`message: ExecutionPayloadBid`, `signature: BLSSignature`) | `SignedExecutionPayloadBidSchema` | `SchemaDefinitionsGloas.getSignedExecutionPayloadBidSchema()` |
| `SignedBeaconBlock` (Gloas variant, with embedded execution payload) | (milestone schema) | `schemaDefinitions.getSignedBeaconBlockSchema()` |

Package locations:
- `ethereum/spec/.../spec/datastructures/builder/versions/gloas/` — `RequestAuth`, `SignedRequestAuth`, `BuilderPreferences`, `BuilderPreferencesRequest` (+ schemas).
- `ethereum/spec/.../spec/datastructures/epbs/versions/gloas/` — `ExecutionPayloadBid`, `SignedExecutionPayloadBid` (+ schemas).
- `ethereum/spec/.../spec/schemas/ApiSchemas.java` and `SchemaDefinitionsGloas.java`.

The existing mev-boost Builder API surface — **reference only, DO NOT modify or extend** (it stays as-is for the legacy flow):
- `ethereum/executionclient/.../BuilderApiMethod.java` — mev-boost route enum (pattern to copy into the new `GloasBuilderApiMethod`).
- `ethereum/executionclient/.../BuilderClient.java` — mev-boost interface (`status`, `registerValidators`, `getHeader`, `getPayload`, `getPayloadV2`). **Not touched.**
- `ethereum/executionclient/.../rest/RestBuilderClient.java` — mev-boost REST impl; shows the per-milestone cached `ResponseSchemaAndDeserializableTypeDefinition` + `response.unwrapVersioned(...)` pattern to replicate in `RestGloasBuilderApiClient`.
- `ethereum/executionclient/src/integration-test/.../rest/RestBuilderClientTest.java` — `MockWebServer` + `@TestSpecContext` test harness to model the new test on.

The **generic transport** that the new module reuses (unchanged, except the one additive overload in Task 2):
- `ethereum/executionclient/.../rest/RestClient.java` + `rest/OkHttpRestClient.java` — `postAsync`/`getAsync` overloads, `ResponseSchemaAndDeserializableTypeDefinition`.
- `ethereum/executionclient/.../schema/BuilderApiResponse.java` + `schema/Response.java`.

## New Endpoints (from PR #138)

| Operation | Method | Path | Request body | Success response |
|---|---|---|---|---|
| `getExecutionPayloadBid` | POST | `eth/v1/builder/execution_payload_bid/:slot/:parent_hash/:parent_root/:proposer_pubkey` | **optional** `SignedRequestAuth` (JSON or SSZ) | `200` `SignedExecutionPayloadBid` (wrapped `{version,data}`); `204` no bid |
| `submitBuilderPreferences` | POST | `eth/v1/builder/builder_preferences/:validator_pubkey` | `BuilderPreferencesRequest` | `202` (no body) |
| `submitSignedBeaconBlock` | POST | `eth/v1/builder/beacon_blocks` | `SignedBeaconBlock` (Gloas) | `202` (no body) |

All three require the `Eth-Consensus-Version` header set to `gloas`.

---

## File Structure

**New module `builder:client` (top-level `builder/` dir):**
- Create: `builder/client/build.gradle` — module dependencies.
- Create: `builder/client/src/main/java/tech/pegasys/teku/builder/client/GloasBuilderApiMethod.java` — route enum (3 routes).
- Create: `builder/client/src/main/java/tech/pegasys/teku/builder/client/GloasBuilderApiClient.java` — brand-new interface (3 methods).
- Create: `builder/client/src/main/java/tech/pegasys/teku/builder/client/GloasBuilderApiClientOptions.java` — timeouts (record, with a `DEFAULT`).
- Create: `builder/client/src/main/java/tech/pegasys/teku/builder/client/rest/RestGloasBuilderApiClient.java` — REST implementation.
- Create: `builder/client/src/integration-test/java/tech/pegasys/teku/builder/client/rest/RestGloasBuilderApiClientTest.java` — `MockWebServer` integration tests.

**Modified (root config):**
- `settings.gradle` — add `include 'builder:client'`.

**Modified (generic transport, additive only — in existing module):**
- `ethereum/executionclient/.../rest/RestClient.java` — 1 new `postAsync` overload (optional body).
- `ethereum/executionclient/.../rest/OkHttpRestClient.java` — implement the new overload.

**Untouched:** `BuilderClient`, `RestBuilderClient`, `BuilderApiMethod`, `ThrottlingBuilderClient`, `MetricRecordingBuilderClient` — the mev-boost flow is unchanged.

No new spec data structures are required — all SSZ schemas already exist.

> **Throttling/metrics decorators:** the legacy flow wraps `BuilderClient` in `Throttling*`/`MetricRecording*`. Those are deferred for `GloasBuilderApiClient` until there is a consumer (see Out of Scope) — adding them now would be speculative (YAGNI). The interface is designed so equivalent decorators can be added later without changes to callers.

---

## Task 1: Create the `builder:client` Gradle module + skeleton

Bootstraps the new module with the route enum, the (empty) interface, an options class, and a compiling REST-client skeleton. Endpoint methods are added in Tasks 3–5.

**Files:**
- Create: `builder/client/build.gradle`
- Modify: `settings.gradle` (add `include 'builder:client'`)
- Create: `builder/client/src/main/java/tech/pegasys/teku/builder/client/GloasBuilderApiMethod.java`
- Create: `builder/client/src/main/java/tech/pegasys/teku/builder/client/GloasBuilderApiClient.java`
- Create: `builder/client/src/main/java/tech/pegasys/teku/builder/client/GloasBuilderApiClientOptions.java`
- Create: `builder/client/src/main/java/tech/pegasys/teku/builder/client/rest/RestGloasBuilderApiClient.java`

**Interfaces:**
- Produces: module `:builder:client`; enum `GloasBuilderApiMethod` with `GET_EXECUTION_PAYLOAD_BID`, `SUBMIT_BUILDER_PREFERENCES`, `SUBMIT_SIGNED_BEACON_BLOCK`; empty interface `GloasBuilderApiClient`; class `RestGloasBuilderApiClient implements GloasBuilderApiClient`; `GloasBuilderApiClientOptions` with a `DEFAULT`.
- Consumes: `:ethereum:executionclient` generic transport (`RestClient`, `Response`, `BuilderApiResponse`), `:ethereum:spec`.

- [ ] **Step 1: Register the module in `settings.gradle`**

Add alongside the other includes (e.g. after `include 'beacon:validator'`):

```groovy
include 'builder:client'
```

- [ ] **Step 2: Create `builder/client/build.gradle`**

Mirror `ethereum/executionclient/build.gradle` (no plugins block — conventions are applied globally by the root `build.gradle`):

```groovy
dependencies {
	implementation project(':infrastructure:async')
	implementation project(':infrastructure:http')
	implementation project(':infrastructure:json')
	implementation project(':infrastructure:metrics')
	implementation project(':infrastructure:time')
	implementation project(':infrastructure:unsigned')
	implementation project(':infrastructure:version')
	implementation project(':ethereum:spec')
	implementation project(':ethereum:json-types')
	implementation project(':ethereum:executionclient')

	implementation 'com.squareup.okhttp3:okhttp'
	implementation 'com.fasterxml.jackson.core:jackson-databind'
	implementation 'io.consensys.tuweni:tuweni-bytes'

	testImplementation testFixtures(project(':infrastructure:async'))
	testImplementation testFixtures(project(':infrastructure:time'))
	testImplementation testFixtures(project(':ethereum:spec'))

	integrationTestImplementation testFixtures(project(':infrastructure:json'))
	integrationTestImplementation testFixtures(project(':infrastructure:time'))
	integrationTestImplementation testFixtures(project(':ethereum:spec'))
	integrationTestImplementation 'com.squareup.okhttp3:mockwebserver'
}
```

- [ ] **Step 3: Create `GloasBuilderApiMethod`**

```java
package tech.pegasys.teku.builder.client;

import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Map;

public enum GloasBuilderApiMethod {
  GET_EXECUTION_PAYLOAD_BID(
      "eth/v1/builder/execution_payload_bid/:slot/:parent_hash/:parent_root/:proposer_pubkey"),
  SUBMIT_BUILDER_PREFERENCES("eth/v1/builder/builder_preferences/:validator_pubkey"),
  SUBMIT_SIGNED_BEACON_BLOCK("eth/v1/builder/beacon_blocks");

  private final String path;

  GloasBuilderApiMethod(final String path) {
    this.path = path;
  }

  public String getPath() {
    return path;
  }

  public String resolvePath(final Map<String, String> urlParams) {
    String result = path;
    for (final Map.Entry<String, String> param : urlParams.entrySet()) {
      result = result.replace(":" + param.getKey(), encode(param.getValue(), UTF_8));
    }
    return result;
  }
}
```

(Copy the Apache-2.0 license header to the top of this and every new file.)

- [ ] **Step 4: Create the empty `GloasBuilderApiClient` interface**

```java
package tech.pegasys.teku.builder.client;

/**
 * Client for the Gloas (ePBS) Builder API (ethereum/builder-specs). This is a distinct protocol
 * from mev-boost; see {@code tech.pegasys.teku.ethereum.executionclient.BuilderClient} for the
 * legacy flow. Methods are added per endpoint in subsequent tasks.
 */
public interface GloasBuilderApiClient {}
```

- [ ] **Step 5: Create `GloasBuilderApiClientOptions`**

```java
package tech.pegasys.teku.builder.client;

import java.time.Duration;

public record GloasBuilderApiClientOptions(
    Duration executionPayloadBidTimeout,
    Duration builderPreferencesTimeout,
    Duration signedBeaconBlockTimeout) {

  public static final GloasBuilderApiClientOptions DEFAULT =
      new GloasBuilderApiClientOptions(
          Duration.ofSeconds(1), Duration.ofSeconds(8), Duration.ofSeconds(8));
}
```

- [ ] **Step 6: Create the `RestGloasBuilderApiClient` skeleton**

```java
package tech.pegasys.teku.builder.client.rest;

import tech.pegasys.teku.builder.client.GloasBuilderApiClient;
import tech.pegasys.teku.builder.client.GloasBuilderApiClientOptions;
import tech.pegasys.teku.ethereum.executionclient.rest.RestClient;
import tech.pegasys.teku.spec.Spec;

public class RestGloasBuilderApiClient implements GloasBuilderApiClient {

  private final GloasBuilderApiClientOptions options;
  private final RestClient restClient;
  private final Spec spec;
  private final boolean setUserAgentHeader;

  public RestGloasBuilderApiClient(
      final GloasBuilderApiClientOptions options,
      final RestClient restClient,
      final Spec spec,
      final boolean setUserAgentHeader) {
    this.options = options;
    this.restClient = restClient;
    this.spec = spec;
    this.setUserAgentHeader = setUserAgentHeader;
  }
}
```

> The unused fields are populated by the endpoint tasks; if the build fails on unused-field/private-field checks, add `@SuppressWarnings` only as a last resort — Tasks 3–5 use all of them, so prefer completing them before running a strict check.

- [ ] **Step 7: Compile to verify**

Run: `./gradlew :builder:client:compileJava`
Expected: BUILD SUCCESSFUL (confirms the module is registered and wired).

- [ ] **Step 8: Commit**

```bash
git add settings.gradle builder/client
git commit -m "feat: scaffold builder:client module for Gloas builder API"
```

---

## Task 2: Add an optional-body `postAsync` overload to the REST transport

The `getExecutionPayloadBid` endpoint takes an **optional** `SignedRequestAuth` body. The existing `postAsync` overloads all require a non-null `TReq`. This task adds one overload that accepts `Optional<TReq>` and posts an empty body when absent.

**Files:**
- Modify: `ethereum/executionclient/src/main/java/tech/pegasys/teku/ethereum/executionclient/rest/RestClient.java:48-55`
- Modify: `ethereum/executionclient/src/main/java/tech/pegasys/teku/ethereum/executionclient/rest/OkHttpRestClient.java:99-114`

**Interfaces:**
- Produces:
  ```java
  <TResp extends SszData, TReq extends SszData>
      SafeFuture<Response<BuilderApiResponse<TResp>>> postAsync(
          String apiPath,
          Map<String, String> headers,
          Optional<TReq> requestBodyObject,
          boolean postAsSsz,
          ResponseSchemaAndDeserializableTypeDefinition<TResp> responseSchema,
          Duration timeout);
  ```
- Consumes: existing `createOctetStreamRequestBody`, `createJsonRequestBody`, `createPostRequest`, `makeAsyncRequest` in `OkHttpRestClient`.

- [ ] **Step 1: Declare the overload in `RestClient`**

Add `import java.util.Optional;` (if missing) and declare, directly after the existing `postAsync` that returns `Response<BuilderApiResponse<TResp>>` (around line 55):

```java
  <TResp extends SszData, TReq extends SszData>
      SafeFuture<Response<BuilderApiResponse<TResp>>> postAsync(
          String apiPath,
          Map<String, String> headers,
          Optional<TReq> requestBodyObject,
          boolean postAsSsz,
          ResponseSchemaAndDeserializableTypeDefinition<TResp> responseSchema,
          Duration timeout);
```

- [ ] **Step 2: Implement it in `OkHttpRestClient`**

Add after the existing matching `postAsync` (around line 114). `RequestBody.create(new byte[0], null)` produces a valid empty POST body:

```java
  @Override
  public <TResp extends SszData, TReq extends SszData>
      SafeFuture<Response<BuilderApiResponse<TResp>>> postAsync(
          final String apiPath,
          final Map<String, String> headers,
          final Optional<TReq> requestBodyObject,
          final boolean postAsSsz,
          final ResponseSchemaAndDeserializableTypeDefinition<TResp> responseSchema,
          final Duration timeout) {
    final RequestBody requestBody =
        requestBodyObject
            .map(
                body ->
                    postAsSsz
                        ? createOctetStreamRequestBody(body)
                        : createJsonRequestBody(body))
            .orElseGet(() -> RequestBody.create(new byte[0], null));
    final Request request = createPostRequest(apiPath, requestBody, headers);
    return makeAsyncRequest(request, Optional.of(responseSchema), timeout);
  }
```

- [ ] **Step 3: Compile to verify**

Run: `./gradlew :ethereum:executionclient:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add ethereum/executionclient/src/main/java/tech/pegasys/teku/ethereum/executionclient/rest/RestClient.java \
        ethereum/executionclient/src/main/java/tech/pegasys/teku/ethereum/executionclient/rest/OkHttpRestClient.java
git commit -m "feat: add optional-body postAsync overload to builder RestClient"
```

---

## Task 3: Implement `getExecutionPayloadBid`

POST with optional `SignedRequestAuth` body, returns a versioned `SignedExecutionPayloadBid`, `204` ⇒ empty `Optional`. This task also establishes the integration-test harness (`RestGloasBuilderApiClientTest`) and the shared header helpers in `RestGloasBuilderApiClient`.

**Files:**
- Modify: `builder/client/src/main/java/.../GloasBuilderApiClient.java` (add method)
- Modify: `builder/client/src/main/java/.../rest/RestGloasBuilderApiClient.java` (implement + header helpers)
- Create: `builder/client/src/integration-test/java/.../rest/RestGloasBuilderApiClientTest.java`

**Interfaces:**
- Consumes: `GloasBuilderApiMethod.GET_EXECUTION_PAYLOAD_BID` (Task 1); `RestClient.postAsync(..., Optional<TReq>, ...)` (Task 2); `SchemaDefinitionsGloas.getSignedExecutionPayloadBidSchema()`; `BuilderApiResponse.createTypeDefinition(...)`.
- Produces (on `GloasBuilderApiClient`):
  ```java
  SafeFuture<Response<Optional<SignedExecutionPayloadBid>>> getExecutionPayloadBid(
      UInt64 slot,
      Bytes32 parentHash,
      Bytes32 parentRoot,
      BLSPublicKey proposerPubkey,
      Optional<SignedRequestAuth> auth);
  ```

- [ ] **Step 1: Add the method to `GloasBuilderApiClient`**

Add imports and the method:

```java
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.SignedRequestAuth;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
```

```java
  SafeFuture<Response<Optional<SignedExecutionPayloadBid>>> getExecutionPayloadBid(
      UInt64 slot,
      Bytes32 parentHash,
      Bytes32 parentRoot,
      BLSPublicKey proposerPubkey,
      Optional<SignedRequestAuth> auth);
```

- [ ] **Step 2: Write the failing integration test (and the harness)**

Create `RestGloasBuilderApiClientTest.java`. Model the harness on `RestBuilderClientTest` but pin it to Gloas only (no `@TestSpecContext` milestone matrix needed — these endpoints are Gloas-only):

```java
package tech.pegasys.teku.builder.client.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.builder.client.GloasBuilderApiClientOptions;
import tech.pegasys.teku.ethereum.executionclient.rest.OkHttpRestClient;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class RestGloasBuilderApiClientTest {

  private static final Duration WAIT = Duration.ofSeconds(10);
  private static final UInt64 SLOT = UInt64.ONE;

  private final OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
  private final MockWebServer mockWebServer = new MockWebServer();

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private RestGloasBuilderApiClient client;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer.start();
    final String endpoint = "http://localhost:" + mockWebServer.getPort();
    final OkHttpRestClient restClient = new OkHttpRestClient(okHttpClient, endpoint);
    client =
        new RestGloasBuilderApiClient(
            GloasBuilderApiClientOptions.DEFAULT, restClient, spec, true);
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  void getExecutionPayloadBid_returnsBid() throws Exception {
    final SignedExecutionPayloadBid bid = dataStructureUtil.randomSignedExecutionPayloadBid();
    final String responseJson =
        "{\"version\":\"gloas\",\"data\":"
            + tech.pegasys.teku.infrastructure.json.JsonUtil.serialize(
                bid, bid.getSchema().getJsonTypeDefinition())
            + "}";
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setHeader("Eth-Consensus-Version", "gloas")
            .setBody(responseJson));

    final BLSPublicKey proposer = dataStructureUtil.randomPublicKey();
    final Bytes32 parentHash = dataStructureUtil.randomBytes32();
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();

    final SafeFuture<Response<Optional<SignedExecutionPayloadBid>>> future =
        client.getExecutionPayloadBid(SLOT, parentHash, parentRoot, proposer, Optional.empty());
    final Response<Optional<SignedExecutionPayloadBid>> response =
        future.get(WAIT.toSeconds(), TimeUnit.SECONDS);

    assertThat(response.isSuccess()).isTrue();
    assertThat(response.payload()).isPresent();

    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getPath())
        .isEqualTo(
            "/eth/v1/builder/execution_payload_bid/"
                + SLOT
                + "/"
                + parentHash.toHexString()
                + "/"
                + parentRoot.toHexString()
                + "/"
                + proposer.toBytesCompressed().toHexString());
    assertThat(recordedRequest.getHeader("Eth-Consensus-Version")).isEqualTo("gloas");
  }

  @Test
  void getExecutionPayloadBid_noBidReturnsEmpty() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(204));

    final SafeFuture<Response<Optional<SignedExecutionPayloadBid>>> future =
        client.getExecutionPayloadBid(
            SLOT,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomPublicKey(),
            Optional.empty());
    final Response<Optional<SignedExecutionPayloadBid>> response =
        future.get(WAIT.toSeconds(), TimeUnit.SECONDS);

    assertThat(response.isSuccess()).isTrue();
    assertThat(response.payload()).isEmpty();
  }
}
```

> Verify `DataStructureUtil.randomSignedExecutionPayloadBid()` and `TestSpecFactory.createMinimalGloas()` exist (prior Gloas work — grep both). If a helper has a different name, use the existing one. `JsonUtil.serialize(obj, typeDef)` is the standard serialize call — confirm the exact signature in `infrastructure/json`.

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :builder:client:integrationTest --tests "*RestGloasBuilderApiClientTest*"`
Expected: FAIL — `getExecutionPayloadBid` not yet implemented (compile error).

- [ ] **Step 4: Implement in `RestGloasBuilderApiClient`**

Add the header helpers/constants and the method. Full set of additions:

```java
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.builder.client.GloasBuilderApiMethod;
import tech.pegasys.teku.ethereum.executionclient.rest.RestClient.ResponseSchemaAndDeserializableTypeDefinition;
import tech.pegasys.teku.ethereum.executionclient.schema.BuilderApiResponse;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.SignedRequestAuth;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBidSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
```

Fields + helpers inside the class:

```java
  private static final Map.Entry<String, String> USER_AGENT_HEADER =
      Map.entry(
          "User-Agent",
          VersionProvider.CLIENT_IDENTITY + "/" + VersionProvider.IMPLEMENTATION_VERSION);

  private static final Map.Entry<String, String> ACCEPT_HEADER =
      Map.entry("Accept", "application/octet-stream;q=1.0,application/json;q=0.9");

  private static final AtomicBoolean LAST_RECEIVED_RESPONSE_WAS_IN_SSZ = new AtomicBoolean(false);

  private final Map<
          SpecMilestone,
          ResponseSchemaAndDeserializableTypeDefinition<SignedExecutionPayloadBid>>
      cachedExecutionPayloadBidResponseType = new ConcurrentHashMap<>();

  @SafeVarargs
  private Map<String, String> buildHeadersWith(final Map.Entry<String, String>... headers) {
    final ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    if (setUserAgentHeader) {
      builder.put(USER_AGENT_HEADER);
    }
    Arrays.stream(headers).forEach(builder::put);
    return builder.build();
  }

  private SchemaDefinitionsGloas getSchemaDefinitionsGloas(final SpecVersion specVersion) {
    return specVersion
        .getSchemaDefinitions()
        .toVersionGloas()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    specVersion.getMilestone()
                        + " is not Gloas. The Gloas builder API requires milestones >= Gloas."));
  }
```

The method:

```java
  @Override
  public SafeFuture<Response<Optional<SignedExecutionPayloadBid>>> getExecutionPayloadBid(
      final UInt64 slot,
      final Bytes32 parentHash,
      final Bytes32 parentRoot,
      final BLSPublicKey proposerPubkey,
      final Optional<SignedRequestAuth> auth) {

    final Map<String, String> urlParams = new HashMap<>();
    urlParams.put("slot", slot.toString());
    urlParams.put("parent_hash", parentHash.toHexString());
    urlParams.put("parent_root", parentRoot.toHexString());
    urlParams.put("proposer_pubkey", proposerPubkey.toBytesCompressed().toHexString());

    final SpecVersion specVersion = spec.atSlot(slot);
    final SpecMilestone milestone = specVersion.getMilestone();

    final ResponseSchemaAndDeserializableTypeDefinition<SignedExecutionPayloadBid>
        responseTypeDefinition =
            cachedExecutionPayloadBidResponseType.computeIfAbsent(
                milestone,
                __ -> {
                  final SignedExecutionPayloadBidSchema schema =
                      getSchemaDefinitionsGloas(specVersion).getSignedExecutionPayloadBidSchema();
                  return new ResponseSchemaAndDeserializableTypeDefinition<>(
                      schema,
                      BuilderApiResponse.createTypeDefinition(schema.getJsonTypeDefinition()));
                });

    return restClient
        .postAsync(
            GloasBuilderApiMethod.GET_EXECUTION_PAYLOAD_BID.resolvePath(urlParams),
            buildHeadersWith(
                ACCEPT_HEADER,
                Map.entry(HEADER_CONSENSUS_VERSION, milestone.name().toLowerCase(Locale.ROOT))),
            auth,
            LAST_RECEIVED_RESPONSE_WAS_IN_SSZ.get(),
            responseTypeDefinition,
            options.executionPayloadBidTimeout())
        .thenApply(
            response ->
                response.unwrapVersioned(
                    this::extractSignedExecutionPayloadBid,
                    milestone,
                    BuilderApiResponse::version,
                    true))
        .thenApply(Response::convertToOptional)
        .thenPeek(
            response -> LAST_RECEIVED_RESPONSE_WAS_IN_SSZ.set(response.receivedAsSsz()));
  }

  private <T extends SignedExecutionPayloadBid> SignedExecutionPayloadBid
      extractSignedExecutionPayloadBid(final BuilderApiResponse<T> builderApiResponse) {
    return builderApiResponse.data();
  }
```

> Verify `SchemaDefinitions.toVersionGloas()` exists; if absent, use `SchemaDefinitionsGloas.required(specVersion.getSchemaDefinitions())`. Confirm `Response.unwrapVersioned`/`convertToOptional`/`receivedAsSsz` signatures by referencing `RestBuilderClient.getHeader` (they are reused verbatim).

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :builder:client:integrationTest --tests "*RestGloasBuilderApiClientTest*"`
Expected: PASS.

- [ ] **Step 6: Spotless + commit**

```bash
./gradlew :builder:client:spotlessApply
git add builder/client
git commit -m "feat: implement getExecutionPayloadBid in GloasBuilderApiClient"
```

---

## Task 4: Implement `submitBuilderPreferences`

POST `BuilderPreferencesRequest` for a validator pubkey; `202` ⇒ success, no body.

**Files:**
- Modify: `builder/client/src/main/java/.../GloasBuilderApiClient.java`
- Modify: `builder/client/src/main/java/.../rest/RestGloasBuilderApiClient.java`
- Modify: `builder/client/src/integration-test/java/.../rest/RestGloasBuilderApiClientTest.java`

**Interfaces:**
- Consumes: `GloasBuilderApiMethod.SUBMIT_BUILDER_PREFERENCES`; the existing `RestClient.postAsync(apiPath, headers, requestBodyObject, postAsSsz, timeout)` returning `Response<Void>`.
- Produces (on `GloasBuilderApiClient`):
  ```java
  SafeFuture<Response<Void>> submitBuilderPreferences(
      UInt64 slot, BLSPublicKey validatorPubkey, BuilderPreferencesRequest preferences);
  ```
  (`slot` resolves the milestone for the `Eth-Consensus-Version` header.)

- [ ] **Step 1: Add the method to `GloasBuilderApiClient`**

```java
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderPreferencesRequest;
```

```java
  SafeFuture<Response<Void>> submitBuilderPreferences(
      UInt64 slot, BLSPublicKey validatorPubkey, BuilderPreferencesRequest preferences);
```

- [ ] **Step 2: Write the failing integration test**

Add to `RestGloasBuilderApiClientTest`:

```java
  @Test
  void submitBuilderPreferences_accepted() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(202));

    final BLSPublicKey validatorPubkey = dataStructureUtil.randomPublicKey();
    final var preferences = dataStructureUtil.randomBuilderPreferencesRequest();

    final SafeFuture<Response<Void>> future =
        client.submitBuilderPreferences(SLOT, validatorPubkey, preferences);
    final Response<Void> response = future.get(WAIT.toSeconds(), TimeUnit.SECONDS);

    assertThat(response.isSuccess()).isTrue();

    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getPath())
        .isEqualTo(
            "/eth/v1/builder/builder_preferences/"
                + validatorPubkey.toBytesCompressed().toHexString());
    assertThat(recordedRequest.getHeader("Eth-Consensus-Version")).isEqualTo("gloas");
  }
```

> Confirm `DataStructureUtil.randomBuilderPreferencesRequest()` exists (grep `randomBuilderPreferences`). If absent, build the object from `ApiSchemas.BUILDER_PREFERENCES_REQUEST_SCHEMA` directly in the test.

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :builder:client:integrationTest --tests "*RestGloasBuilderApiClientTest*"`
Expected: FAIL — method not implemented.

- [ ] **Step 4: Implement in `RestGloasBuilderApiClient`**

```java
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderPreferencesRequest;
```

```java
  @Override
  public SafeFuture<Response<Void>> submitBuilderPreferences(
      final UInt64 slot,
      final BLSPublicKey validatorPubkey,
      final BuilderPreferencesRequest preferences) {
    final SpecMilestone milestone = spec.atSlot(slot).getMilestone();
    final Map<String, String> urlParams = new HashMap<>();
    urlParams.put("validator_pubkey", validatorPubkey.toBytesCompressed().toHexString());

    return restClient.postAsync(
        GloasBuilderApiMethod.SUBMIT_BUILDER_PREFERENCES.resolvePath(urlParams),
        buildHeadersWith(
            Map.entry(HEADER_CONSENSUS_VERSION, milestone.name().toLowerCase(Locale.ROOT))),
        preferences,
        false,
        options.builderPreferencesTimeout());
  }
```

> `postAsSsz=false` (JSON) keeps the first implementation simple; SSZ negotiation can be added later mirroring `registerValidators`.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :builder:client:integrationTest --tests "*RestGloasBuilderApiClientTest*"`
Expected: PASS.

- [ ] **Step 6: Spotless + commit**

```bash
./gradlew :builder:client:spotlessApply
git add builder/client
git commit -m "feat: implement submitBuilderPreferences in GloasBuilderApiClient"
```

---

## Task 5: Implement `submitSignedBeaconBlock`

POST a Gloas `SignedBeaconBlock` (with embedded execution payload); `202` ⇒ success, no body.

**Files:**
- Modify: `builder/client/src/main/java/.../GloasBuilderApiClient.java`
- Modify: `builder/client/src/main/java/.../rest/RestGloasBuilderApiClient.java`
- Modify: `builder/client/src/integration-test/java/.../rest/RestGloasBuilderApiClientTest.java`

**Interfaces:**
- Consumes: `GloasBuilderApiMethod.SUBMIT_SIGNED_BEACON_BLOCK`; existing `RestClient.postAsync(apiPath, headers, body, postAsSsz, timeout)` returning `Response<Void>`.
- Produces (on `GloasBuilderApiClient`):
  ```java
  SafeFuture<Response<Void>> submitSignedBeaconBlock(SignedBeaconBlock signedBeaconBlock);
  ```

- [ ] **Step 1: Add the method to `GloasBuilderApiClient`**

```java
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
```

```java
  SafeFuture<Response<Void>> submitSignedBeaconBlock(SignedBeaconBlock signedBeaconBlock);
```

- [ ] **Step 2: Write the failing integration test**

Add to `RestGloasBuilderApiClientTest`:

```java
  @Test
  void submitSignedBeaconBlock_accepted() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(202));

    final tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock signedBeaconBlock =
        dataStructureUtil.randomSignedBeaconBlock();

    final SafeFuture<Response<Void>> future =
        client.submitSignedBeaconBlock(signedBeaconBlock);
    final Response<Void> response = future.get(WAIT.toSeconds(), TimeUnit.SECONDS);

    assertThat(response.isSuccess()).isTrue();

    final RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getPath()).isEqualTo("/eth/v1/builder/beacon_blocks");
    assertThat(recordedRequest.getHeader("Eth-Consensus-Version")).isEqualTo("gloas");
  }
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :builder:client:integrationTest --tests "*RestGloasBuilderApiClientTest*"`
Expected: FAIL — method not implemented.

- [ ] **Step 4: Implement in `RestGloasBuilderApiClient`**

```java
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
```

```java
  @Override
  public SafeFuture<Response<Void>> submitSignedBeaconBlock(
      final SignedBeaconBlock signedBeaconBlock) {
    final SpecMilestone milestone = spec.atSlot(signedBeaconBlock.getSlot()).getMilestone();
    return restClient.postAsync(
        GloasBuilderApiMethod.SUBMIT_SIGNED_BEACON_BLOCK.getPath(),
        buildHeadersWith(
            Map.entry(HEADER_CONSENSUS_VERSION, milestone.name().toLowerCase(Locale.ROOT))),
        signedBeaconBlock,
        LAST_RECEIVED_RESPONSE_WAS_IN_SSZ.get(),
        options.signedBeaconBlockTimeout());
  }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :builder:client:integrationTest --tests "*RestGloasBuilderApiClientTest*"`
Expected: PASS.

- [ ] **Step 6: Spotless + commit**

```bash
./gradlew :builder:client:spotlessApply
git add builder/client
git commit -m "feat: implement submitSignedBeaconBlock in GloasBuilderApiClient"
```

---

## Task 6: Full module verification

**Files:** none (verification only).

- [ ] **Step 1: Compile the new module + the transport module**

Run: `./gradlew :builder:client:compileJava :builder:client:compileIntegrationTestJava :ethereum:executionclient:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run the new module's test suites**

Run: `./gradlew :builder:client:test :builder:client:integrationTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Confirm the legacy mev-boost module still builds and passes (untouched, but the transport overload from Task 2 lives there)**

Run: `./gradlew :ethereum:executionclient:test :ethereum:executionclient:integrationTest`
Expected: BUILD SUCCESSFUL — no regression in `RestBuilderClientTest`.

- [ ] **Step 4: Spotless across both modules**

Run: `./gradlew :builder:client:spotlessCheck :ethereum:executionclient:spotlessCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Full build sanity (catches settings.gradle / dependency wiring issues)**

Run: `./gradlew :builder:client:build -x test -x integrationTest`
Expected: BUILD SUCCESSFUL.

---

## Out of Scope (follow-up plans)

builder-specs PR #138 defines only the HTTP API contract. The following are required to make the endpoints *useful* but are separate, larger efforts (much of the ePBS state-transition/gossip side already exists from prior Gloas work) and should each get their own plan:

0. **Throttling / metrics decorators** — add `ThrottlingGloasBuilderApiClient` and `MetricRecordingGloasBuilderApiClient` (mirroring the mev-boost decorators) once there is a consumer. Deferred here as YAGNI — no caller exists yet.
1. **Block production wiring** — call `getExecutionPayloadBid` from the Gloas block-production flow (`ExecutionBuilderModule` / `ExecutionLayerManagerImpl`), and decide how `GloasBuilderApiClient` is constructed/injected (a Gloas analogue of the factory that builds `RestBuilderClient` today); validate the bid (signature, collateral coverage, fee recipient) per `specs/gloas/validator.md`.
2. **Builder preferences publishing** — produce a signed `BuilderPreferencesRequest` (build `RequestAuth`, sign with the validator key using `DOMAIN_REQUEST_AUTH`) and call `submitBuilderPreferences` in the epoch prior to proposal (likely from `ValidatorRegistrator`/`ProposerPreferencesPublisher`).
3. **Signed beacon block submission** — call `submitSignedBeaconBlock` to commit to a received bid as part of the Gloas proposer duty.
4. **`Date-Milliseconds` / `X-Timeout-Ms` request headers** — optional anti-DDoS/timeout headers on `getExecutionPayloadBid`; add to `RestBuilderClientOptions` + the request builder when the proposer flow is wired.
5. **SSZ request negotiation for `submitBuilderPreferences`** — mirror the JSON-fallback-with-backoff used by `registerValidators`.

---

## Self-Review

**Spec coverage (PR #138 endpoints):**
- New `builder:client` module + brand-new `GloasBuilderApiClient` interface (mev-boost `BuilderClient` untouched) → Task 1 ✓
- `getExecutionPayloadBid` → Task 3 ✓ (incl. optional auth body via Task 2, 204 handling, path params, consensus-version header)
- `submitBuilderPreferences` → Task 4 ✓
- `submitSignedBeaconBlock` → Task 5 ✓
- New types (`RequestAuthV1`, `SignedRequestAuthV1`, `BuilderPreferencesV1`, `BuilderPreferencesRequestV1`) → already in codebase (Background) ✓
- `ConsensusVersion` enum `gloas` → `SpecMilestone.GLOAS` already exists ✓

**Type consistency:** `GloasBuilderApiClient` signatures defined in Tasks 3/4/5 are reused verbatim in `RestGloasBuilderApiClient`. The mev-boost `BuilderClient` and its decorators are not modified. Schema accessors verified: `ApiSchemas.SIGNED_REQUEST_AUTH_SCHEMA`, `ApiSchemas.BUILDER_PREFERENCES_REQUEST_SCHEMA`, `SchemaDefinitionsGloas.getSignedExecutionPayloadBidSchema()`. Generic transport reused: `RestClient`, `OkHttpRestClient`, `BuilderApiResponse`, `Response` (in `:ethereum:executionclient`, added as a dependency in Task 1's `build.gradle`).

**Known verification points flagged inline** (do not skip during execution): exact name of the `SchemaDefinitions → SchemaDefinitionsGloas` down-cast (`toVersionGloas()` vs `SchemaDefinitionsGloas.required(...)`); existence of `DataStructureUtil.randomSignedExecutionPayloadBid()` / `randomBuilderPreferencesRequest()` and `TestSpecFactory.createMinimalGloas()`; exact `JsonUtil.serialize(...)` signature; that `RestClient`/`OkHttpRestClient`/`BuilderApiResponse`/`Response` are `public` and importable from `:builder:client` (they are — package `tech.pegasys.teku.ethereum.executionclient.rest`/`.schema`); the precise 200-response wrapper shape for `getExecutionPayloadBid` (assumed `BuilderApiResponse` `{version,data}`, matching `getHeader`); the exact `build.gradle` dependency coordinates (mirror `ethereum/executionclient/build.gradle`).
