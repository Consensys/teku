# Light Client Finality & Optimistic Updates — Phase 1 Remainder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the spec-layer light-client work: add `LightClientFinalityUpdate` and `LightClientOptimisticUpdate` types + fork-aware schemas, the two missing state proof helpers, `LightClientUtil` generators, fixtures, ssz_static reference coverage, and tests — plus fix the Electra next-sync-committee gindex.

**Architecture:** Mirror the *existing* `LightClientUpdate` machinery exactly (registry-driven fork variants, `SchemaDefinitionsAltair` getters, `DataStructureUtil` generators, `SszTestExecutor` wiring). Update/Bootstrap/Header schemas and their Electra/Gloas variants already exist on this branch — do NOT recreate them.

**Tech Stack:** Java 25, Gradle, Teku SSZ containers (`Container3`/`Container5`, `ContainerSchema3`/`ContainerSchema5`), `SchemaRegistry`, JUnit5 `@TestSpecContext`/`@TestTemplate`.

## Global Constraints

- Google Java style; run `./gradlew spotlessApply` before every commit. Compiles under `-Werror` — no wildcard imports, explicit imports only.
- No `.join()`/`.get()` on futures (N/A here, but no blocking).
- Module: all main code in `ethereum/spec`, fixtures in `ethereum/spec` testFixtures, ref-test wiring in `eth-reference-tests`.
- Copyright header (2026, Consensys) on every new file — copy verbatim from any sibling file.
- Spec source of truth: `specrefs/functions.yml` `create_light_client_update`/`create_light_client_finality_update`/`create_light_client_optimistic_update` and `altair/light-client/full-node.md`.
- Verified gindices (vendored `single_merkle_proof` vectors): Electra current=86, **next=87**, finalized=169; Gloas next=2946, finalized=735.
- The `createLightClientUpdate` util OMITS the spec's defensive `assert`s (participation minimum + `hash_tree_root` equalities) — construction only, matching the existing `getLightClientBootstrap`. Mark with a `ponytail:` comment.

---

### Task 1: Fix Electra next-sync-committee gindex (86 → 87)

**Files:**
- Modify: `ethereum/spec/src/main/java/tech/pegasys/teku/spec/constants/LightClientConstants.java:25`
- Test (existing, must stay green): `ethereum/spec/src/test/java/tech/pegasys/teku/spec/datastructures/lightclient/LightClientUpdateSchemaTest.java`

**Interfaces:**
- Produces: `NEXT_SYNC_COMMITTEE_GINDEX_ELECTRA = 87` (consumed by existing `LightClientUpdateSchemaElectra`).

- [ ] **Step 1: Change the constant**

```java
public static final int NEXT_SYNC_COMMITTEE_GINDEX_ELECTRA = 87;
```

- [ ] **Step 2: Run the existing schema test — branch length is unchanged (floorLog2(87)=floorLog2(86)=6), so it must still pass**

Run: `./gradlew :ethereum:spec:test --tests "*LightClientUpdateSchemaTest"`
Expected: PASS (Electra next_sync branch length still 6).

- [ ] **Step 3: Commit**

```bash
git add ethereum/spec/src/main/java/tech/pegasys/teku/spec/constants/LightClientConstants.java
git commit -m "fix: correct Electra next_sync_committee gindex to 87"
```

---

### Task 2: State proof helpers `createNextSyncCommitteeProof` / `createFinalityBranchProof`

**Files:**
- Modify: `ethereum/spec/src/main/java/tech/pegasys/teku/spec/datastructures/state/beaconstate/versions/altair/BeaconStateAltair.java`
- Test: `ethereum/spec/src/test/java/tech/pegasys/teku/spec/datastructures/state/beaconstate/versions/altair/BeaconStateAltairProofTest.java` (new)

**Interfaces:**
- Produces:
  - `SszBytes32Vector createNextSyncCommitteeProof()`
  - `SszBytes32Vector createFinalityBranchProof()`
  (both consumed by `LightClientUtil.createLightClientUpdate` in Task 5)

- [ ] **Step 1: Write the failing test**

```java
package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

// Altair + Electra only: Gloas/HEZE proof depth needs progressive state merkleization (#10931).
@TestSpecContext(
    allMilestones = true,
    ignoredMilestones = {SpecMilestone.PHASE0, SpecMilestone.GLOAS, SpecMilestone.HEZE})
public class BeaconStateAltairProofTest {
  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  void setup(final SpecContext specContext) {
    dataStructureUtil = specContext.getDataStructureUtil();
  }

  @TestTemplate
  void nextSyncCommitteeProof_isNonEmpty() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    assertThat(BeaconStateAltair.required(state).createNextSyncCommitteeProof().size())
        .isGreaterThan(0);
  }

  @TestTemplate
  void finalityBranchProof_isNonEmpty() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    assertThat(BeaconStateAltair.required(state).createFinalityBranchProof().size())
        .isGreaterThan(0);
  }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :ethereum:spec:test --tests "*BeaconStateAltairProofTest"`
Expected: FAIL — `createNextSyncCommitteeProof` / `createFinalityBranchProof` not defined.

- [ ] **Step 3: Add the helpers** (mirror the existing `createCurrentSyncCommitteeProof` at `BeaconStateAltair.java:63`)

Add imports at top of `BeaconStateAltair.java`:
```java
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
```

Add these methods next to `createCurrentSyncCommitteeProof`:
```java
  default SszBytes32Vector createNextSyncCommitteeProof() {
    final List<Bytes32> proof =
        MerkleUtil.constructMerkleProof(
            getBackingNode(),
            getSchema()
                .getChildGeneralizedIndex(
                    getSchema().getFieldIndex(BeaconStateFields.NEXT_SYNC_COMMITTEE)));

    return SszBytes32VectorSchema.create(proof.size())
        .createFromElements(proof.stream().map(SszBytes32::of).toList());
  }

  default SszBytes32Vector createFinalityBranchProof() {
    // finalized_checkpoint.root is two levels deep: state -> finalized_checkpoint -> root (field 1)
    final long finalizedCheckpointGIndex =
        getSchema()
            .getChildGeneralizedIndex(
                getSchema().getFieldIndex(BeaconStateFields.FINALIZED_CHECKPOINT));
    final long rootGIndexWithinCheckpoint = Checkpoint.SSZ_SCHEMA.getChildGeneralizedIndex(1);
    final long finalityLeafGIndex =
        GIndexUtil.gIdxCompose(finalizedCheckpointGIndex, rootGIndexWithinCheckpoint);

    final List<Bytes32> proof =
        MerkleUtil.constructMerkleProof(getBackingNode(), finalityLeafGIndex);

    return SszBytes32VectorSchema.create(proof.size())
        .createFromElements(proof.stream().map(SszBytes32::of).toList());
  }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :ethereum:spec:test --tests "*BeaconStateAltairProofTest"`
Expected: PASS.

- [ ] **Step 5: spotless + commit**

```bash
./gradlew spotlessApply
git add ethereum/spec/src/main/java/tech/pegasys/teku/spec/datastructures/state/beaconstate/versions/altair/BeaconStateAltair.java \
        ethereum/spec/src/test/java/tech/pegasys/teku/spec/datastructures/state/beaconstate/versions/altair/BeaconStateAltairProofTest.java
git commit -m "feat: add next_sync_committee and finality branch proof helpers"
```

---

### Task 3: `LightClientOptimisticUpdate` type + schema + registry + getter

**Files:**
- Create: `.../datastructures/lightclient/LightClientOptimisticUpdate.java`
- Create: `.../datastructures/lightclient/LightClientOptimisticUpdateSchema.java`
- Modify: `.../schemas/registry/SchemaTypes.java` (add id)
- Modify: `.../schemas/registry/SchemaRegistryBuilder.java` (add provider + import + register)
- Modify: `.../schemas/SchemaDefinitionsAltair.java` (field + registry get + getter)

**Interfaces:**
- Consumes: `LIGHT_CLIENT_HEADER_SCHEMA` (registry), `SyncAggregateSchema`.
- Produces: `LightClientOptimisticUpdateSchema.create(LightClientHeader, SyncAggregate, SszUInt64)`; `SchemaDefinitionsAltair.getLightClientOptimisticUpdateSchema()`; `SchemaTypes.LIGHT_CLIENT_OPTIMISTIC_UPDATE_SCHEMA`.

Structure per spec: `{attested_header, sync_aggregate, signature_slot}` — no branch, so a **single** schema across all forks (header is fork-varying via the registry).

- [ ] **Step 1: Create the container** `LightClientOptimisticUpdate.java`

```java
package tech.pegasys.teku.spec.datastructures.lightclient;

import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;

public class LightClientOptimisticUpdate
    extends Container3<LightClientOptimisticUpdate, LightClientHeader, SyncAggregate, SszUInt64> {

  public LightClientOptimisticUpdate(
      final LightClientOptimisticUpdateSchema schema,
      final LightClientHeader attestedHeader,
      final SyncAggregate syncAggregate,
      final SszUInt64 signatureSlot) {
    super(schema, attestedHeader, syncAggregate, signatureSlot);
  }

  protected LightClientOptimisticUpdate(
      final LightClientOptimisticUpdateSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public LightClientHeader getAttestedHeader() {
    return getField0();
  }
}
```

- [ ] **Step 2: Create the schema** `LightClientOptimisticUpdateSchema.java` (model on `LightClientUpdateSchema`'s header wiring)

```java
package tech.pegasys.teku.spec.datastructures.lightclient;

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.LIGHT_CLIENT_HEADER_SCHEMA;

import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class LightClientOptimisticUpdateSchema
    extends ContainerSchema3<
        LightClientOptimisticUpdate, LightClientHeader, SyncAggregate, SszUInt64> {

  public LightClientOptimisticUpdateSchema(
      final SpecConfigAltair specConfigAltair, final SchemaRegistry registry) {
    super(
        "LightClientOptimisticUpdate",
        namedSchema(
            "attested_header",
            SszSchema.as(LightClientHeader.class, registry.get(LIGHT_CLIENT_HEADER_SCHEMA))),
        namedSchema(
            "sync_aggregate", SyncAggregateSchema.create(specConfigAltair.getSyncCommitteeSize())),
        namedSchema("signature_slot", SszPrimitiveSchemas.UINT64_SCHEMA));
  }

  public LightClientOptimisticUpdate create(
      final LightClientHeader attestedHeader,
      final SyncAggregate syncAggregate,
      final SszUInt64 signatureSlot) {
    return new LightClientOptimisticUpdate(this, attestedHeader, syncAggregate, signatureSlot);
  }

  @Override
  public LightClientOptimisticUpdate createFromBackingNode(final TreeNode node) {
    return new LightClientOptimisticUpdate(this, node);
  }
}
```

- [ ] **Step 3: Add the SchemaTypes id** — in `SchemaTypes.java` next to `LIGHT_CLIENT_UPDATE_SCHEMA` (line 156). Also add the import for the new schema class.

```java
  public static final SchemaId<LightClientOptimisticUpdateSchema>
      LIGHT_CLIENT_OPTIMISTIC_UPDATE_SCHEMA = create("LIGHT_CLIENT_OPTIMISTIC_UPDATE_SCHEMA");
```

- [ ] **Step 4: Register the provider** — in `SchemaRegistryBuilder.java`. Add explicit import for `LightClientOptimisticUpdateSchema` and the static import `LIGHT_CLIENT_OPTIMISTIC_UPDATE_SCHEMA`. Add `.addProvider(createLightClientOptimisticUpdateSchemaProvider())` next to line 264, and the method:

```java
  private static SchemaProvider<?> createLightClientOptimisticUpdateSchemaProvider() {
    return providerBuilder(LIGHT_CLIENT_OPTIMISTIC_UPDATE_SCHEMA)
        .withCreator(
            ALTAIR,
            (registry, specConfig, schemaName) ->
                new LightClientOptimisticUpdateSchema(
                    SpecConfigAltair.required(specConfig), registry))
        .build();
  }
```

- [ ] **Step 5: Expose the getter** — in `SchemaDefinitionsAltair.java`: add field `private final LightClientOptimisticUpdateSchema lightClientOptimisticUpdateSchema;`, assign in ctor `this.lightClientOptimisticUpdateSchema = schemaRegistry.get(LIGHT_CLIENT_OPTIMISTIC_UPDATE_SCHEMA);`, add getter + import + static import.

```java
  public LightClientOptimisticUpdateSchema getLightClientOptimisticUpdateSchema() {
    return lightClientOptimisticUpdateSchema;
  }
```

- [ ] **Step 6: Compile**

Run: `./gradlew :ethereum:spec:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: spotless + commit**

```bash
./gradlew spotlessApply
git add ethereum/spec/src/main/java/tech/pegasys/teku/spec/datastructures/lightclient/LightClientOptimisticUpdate*.java \
        ethereum/spec/src/main/java/tech/pegasys/teku/spec/schemas/
git commit -m "feat: add LightClientOptimisticUpdate type and schema"
```

---

### Task 4: `LightClientFinalityUpdate` type + schema + Electra/Gloas variants + registry + getter

**Files:**
- Create: `.../lightclient/LightClientFinalityUpdate.java`
- Create: `.../lightclient/LightClientFinalityUpdateSchema.java`
- Create: `.../lightclient/versions/electra/LightClientFinalityUpdateSchemaElectra.java`
- Create: `.../lightclient/versions/gloas/LightClientFinalityUpdateSchemaGloas.java`
- Modify: `SchemaTypes.java`, `SchemaRegistryBuilder.java`, `SchemaDefinitionsAltair.java`

**Interfaces:**
- Produces: `LightClientFinalityUpdateSchema.create(attestedHeader, finalizedHeader, finalityBranch, syncAggregate, signatureSlot)`, `getFinalityBranchSchema()`, `SchemaDefinitionsAltair.getLightClientFinalityUpdateSchema()`, `SchemaTypes.LIGHT_CLIENT_FINALITY_UPDATE_SCHEMA`.

Structure per spec: `{attested_header, finalized_header, finality_branch, sync_aggregate, signature_slot}` (Container5). Gindex-parameterized like `LightClientUpdateSchema`; variants supply the finalized-root gindex per fork.

- [ ] **Step 1: Create the container** `LightClientFinalityUpdate.java`

```java
package tech.pegasys.teku.spec.datastructures.lightclient;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;

public class LightClientFinalityUpdate
    extends Container5<
        LightClientFinalityUpdate,
        LightClientHeader,
        LightClientHeader,
        SszBytes32Vector,
        SyncAggregate,
        SszUInt64> {

  public LightClientFinalityUpdate(
      final LightClientFinalityUpdateSchema schema,
      final LightClientHeader attestedHeader,
      final LightClientHeader finalizedHeader,
      final SszBytes32Vector finalityBranch,
      final SyncAggregate syncAggregate,
      final SszUInt64 signatureSlot) {
    super(schema, attestedHeader, finalizedHeader, finalityBranch, syncAggregate, signatureSlot);
  }

  protected LightClientFinalityUpdate(
      final LightClientFinalityUpdateSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public LightClientHeader getAttestedHeader() {
    return getField0();
  }
}
```

- [ ] **Step 2: Create the base schema** `LightClientFinalityUpdateSchema.java` (gindex-parameterized, mirror `LightClientUpdateSchema`)

```java
package tech.pegasys.teku.spec.datastructures.lightclient;

import static tech.pegasys.teku.spec.constants.LightClientConstants.FINALIZED_ROOT_GINDEX;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.LIGHT_CLIENT_HEADER_SCHEMA;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateSchema;
import tech.pegasys.teku.spec.logic.common.helpers.MathHelpers;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class LightClientFinalityUpdateSchema
    extends ContainerSchema5<
        LightClientFinalityUpdate,
        LightClientHeader,
        LightClientHeader,
        SszBytes32Vector,
        SyncAggregate,
        SszUInt64> {

  public LightClientFinalityUpdateSchema(
      final SpecConfigAltair specConfigAltair,
      final SchemaRegistry registry,
      final int finalizedBranchGindex) {
    super(
        "LightClientFinalityUpdate",
        namedSchema(
            "attested_header",
            SszSchema.as(LightClientHeader.class, registry.get(LIGHT_CLIENT_HEADER_SCHEMA))),
        namedSchema(
            "finalized_header",
            SszSchema.as(LightClientHeader.class, registry.get(LIGHT_CLIENT_HEADER_SCHEMA))),
        namedSchema(
            "finality_branch",
            SszBytes32VectorSchema.create(MathHelpers.floorLog2(finalizedBranchGindex))),
        namedSchema(
            "sync_aggregate", SyncAggregateSchema.create(specConfigAltair.getSyncCommitteeSize())),
        namedSchema("signature_slot", SszPrimitiveSchemas.UINT64_SCHEMA));
  }

  public LightClientFinalityUpdateSchema(
      final SpecConfigAltair specConfigAltair, final SchemaRegistry registry) {
    this(specConfigAltair, registry, FINALIZED_ROOT_GINDEX);
  }

  public LightClientFinalityUpdate create(
      final LightClientHeader attestedHeader,
      final LightClientHeader finalizedHeader,
      final SszBytes32Vector finalityBranch,
      final SyncAggregate syncAggregate,
      final SszUInt64 signatureSlot) {
    return new LightClientFinalityUpdate(
        this, attestedHeader, finalizedHeader, finalityBranch, syncAggregate, signatureSlot);
  }

  @Override
  public LightClientFinalityUpdate createFromBackingNode(final TreeNode node) {
    return new LightClientFinalityUpdate(this, node);
  }

  @SuppressWarnings("unchecked")
  public SszBytes32VectorSchema<SszBytes32Vector> getFinalityBranchSchema() {
    return (SszBytes32VectorSchema<SszBytes32Vector>) getChildSchema(2);
  }
}
```

- [ ] **Step 3: Create the Electra variant** `versions/electra/LightClientFinalityUpdateSchemaElectra.java`

```java
package tech.pegasys.teku.spec.datastructures.lightclient.versions.electra;

import static tech.pegasys.teku.spec.constants.LightClientConstants.FINALIZED_ROOT_GINDEX_ELECTRA;

import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdateSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class LightClientFinalityUpdateSchemaElectra extends LightClientFinalityUpdateSchema {
  public LightClientFinalityUpdateSchemaElectra(
      final SpecConfigElectra specConfigElectra, final SchemaRegistry registry) {
    super(specConfigElectra, registry, FINALIZED_ROOT_GINDEX_ELECTRA);
  }
}
```

- [ ] **Step 4: Create the Gloas variant** `versions/gloas/LightClientFinalityUpdateSchemaGloas.java`

```java
package tech.pegasys.teku.spec.datastructures.lightclient.versions.gloas;

import static tech.pegasys.teku.spec.constants.LightClientConstants.FINALIZED_ROOT_GINDEX_GLOAS;

import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdateSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class LightClientFinalityUpdateSchemaGloas extends LightClientFinalityUpdateSchema {
  public LightClientFinalityUpdateSchemaGloas(
      final SpecConfigGloas specConfigGloas, final SchemaRegistry registry) {
    super(specConfigGloas, registry, FINALIZED_ROOT_GINDEX_GLOAS);
  }
}
```

- [ ] **Step 5: Add SchemaTypes id** in `SchemaTypes.java`:

```java
  public static final SchemaId<LightClientFinalityUpdateSchema>
      LIGHT_CLIENT_FINALITY_UPDATE_SCHEMA = create("LIGHT_CLIENT_FINALITY_UPDATE_SCHEMA");
```

- [ ] **Step 6: Register the provider** in `SchemaRegistryBuilder.java` (imports for base + Electra + Gloas variants, static import of the id, `.addProvider(createLightClientFinalityUpdateSchemaProvider())`):

```java
  private static SchemaProvider<?> createLightClientFinalityUpdateSchemaProvider() {
    return providerBuilder(LIGHT_CLIENT_FINALITY_UPDATE_SCHEMA)
        .withCreator(
            ALTAIR,
            (registry, specConfig, schemaName) ->
                new LightClientFinalityUpdateSchema(SpecConfigAltair.required(specConfig), registry))
        .withCreator(
            ELECTRA,
            (registry, specConfig, schemaName) ->
                new LightClientFinalityUpdateSchemaElectra(
                    SpecConfigElectra.required(specConfig), registry))
        .withCreator(
            GLOAS,
            (registry, specConfig, schemaName) ->
                new LightClientFinalityUpdateSchemaGloas(
                    SpecConfigGloas.required(specConfig), registry))
        .build();
  }
```

- [ ] **Step 7: Expose getter** in `SchemaDefinitionsAltair.java` (field + `schemaRegistry.get(LIGHT_CLIENT_FINALITY_UPDATE_SCHEMA)` + getter + imports).

- [ ] **Step 8: Compile**

Run: `./gradlew :ethereum:spec:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: spotless + commit**

```bash
./gradlew spotlessApply
git add ethereum/spec/src/main/java/tech/pegasys/teku/spec/datastructures/lightclient/ \
        ethereum/spec/src/main/java/tech/pegasys/teku/spec/schemas/
git commit -m "feat: add LightClientFinalityUpdate type and fork-aware schemas"
```

---

### Task 5: `LightClientUtil` generators

**Files:**
- Modify: `.../logic/common/util/LightClientUtil.java`
- Test: extend `.../logic/common/util/LightClientUtilTest.java`

**Interfaces:**
- Consumes: `BeaconStateAltair.createNextSyncCommitteeProof/createFinalityBranchProof` (Task 2), `getLightClientFinalityUpdateSchema`/`getLightClientOptimisticUpdateSchema` (Tasks 3–4), `SyncCommitteeUtil.computeSyncCommitteePeriod`, `BeaconBlockHeader.fromBlock`.
- Produces:
  - `LightClientUpdate createLightClientUpdate(BeaconState state, SignedBeaconBlock block, BeaconState attestedState, SignedBeaconBlock attestedBlock, Optional<SignedBeaconBlock> finalizedBlock)`
  - `LightClientFinalityUpdate createLightClientFinalityUpdate(LightClientUpdate update)`
  - `LightClientOptimisticUpdate createLightClientOptimisticUpdate(LightClientUpdate update)`

- [ ] **Step 1: Write the failing test** (append to `LightClientUtilTest`, which already ignores GLOAS/HEZE)

```java
  @TestTemplate
  public void createUpdate_thenDeriveFinalityAndOptimistic() {
    final BeaconState attestedState = dataStructureUtil.randomBeaconState();
    final SignedBeaconBlock attestedBlock = dataStructureUtil.randomSignedBeaconBlock(attestedState);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    final SignedBeaconBlock finalizedBlock = dataStructureUtil.randomSignedBeaconBlock();

    final LightClientUpdate update =
        lightClientUtil.createLightClientUpdate(
            dataStructureUtil.randomBeaconState(),
            block,
            attestedState,
            attestedBlock,
            java.util.Optional.of(finalizedBlock));

    assertThat(update.getSignatureSlot().get()).isEqualTo(block.getSlot());

    final LightClientFinalityUpdate finalityUpdate =
        lightClientUtil.createLightClientFinalityUpdate(update);
    assertThat(finalityUpdate.getAttestedHeader()).isEqualTo(update.getAttestedHeader());

    final LightClientOptimisticUpdate optimisticUpdate =
        lightClientUtil.createLightClientOptimisticUpdate(update);
    assertThat(optimisticUpdate.getAttestedHeader()).isEqualTo(update.getAttestedHeader());
  }
```

Add getters used above to the containers if missing: `LightClientUpdate.getAttestedHeader()` (`getField0()`), `getSignatureSlot()` (`getField6()`). Add them in this step.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :ethereum:spec:test --tests "*LightClientUtilTest"`
Expected: FAIL — methods not defined.

- [ ] **Step 3: Implement the generators** in `LightClientUtil.java`

```java
  // ponytail: omits the spec's create_light_client_update asserts (participation minimum +
  // hash_tree_root equalities) — construction only, matching getLightClientBootstrap. Add the
  // validating asserts if this is ever used to serve real updates from a store.
  public LightClientUpdate createLightClientUpdate(
      final BeaconState state,
      final SignedBeaconBlock block,
      final BeaconState attestedState,
      final SignedBeaconBlock attestedBlock,
      final Optional<SignedBeaconBlock> finalizedBlock) {
    final LightClientUpdateSchema schema = schemaDefinitionsAltair.getLightClientUpdateSchema();
    final LightClientHeaderSchema<?> headerSchema =
        schemaDefinitionsAltair.getLightClientHeaderSchema();

    final LightClientHeader attestedHeader =
        headerSchema.create(BeaconBlockHeader.fromBlock(attestedBlock.getMessage()));

    final UInt64 signaturePeriod =
        syncCommitteeUtil.computeSyncCommitteePeriod(
            beaconStateAccessors.getCurrentEpoch(state).equals(UInt64.ZERO)
                ? beaconStateAccessors.getCurrentEpoch(state)
                : beaconStateAccessors.getCurrentEpoch(state)); // slot->epoch below; see note
    // Sync-committee periods from block/attested slots:
    final UInt64 updateSignaturePeriod =
        syncCommitteeUtil.computeSyncCommitteePeriod(
            beaconStateAccessors.getCurrentEpoch(attestedState)); // placeholder, replaced Step 3b

    // next_sync_committee only when signed by the current sync committee period
    final SyncCommittee nextSyncCommittee;
    final SszBytes32Vector nextSyncCommitteeBranch;
    final boolean samePeriod =
        periodAtSlot(attestedBlock.getSlot()).equals(periodAtSlot(block.getSlot()));
    if (samePeriod) {
      nextSyncCommittee = BeaconStateAltair.required(attestedState).getNextSyncCommittee();
      nextSyncCommitteeBranch =
          BeaconStateAltair.required(attestedState).createNextSyncCommitteeProof();
    } else {
      nextSyncCommittee = schema.getNextSyncCommitteeSchemaDefault();
      nextSyncCommitteeBranch = schema.getSyncCommitteeBranchSchema().getDefault();
    }

    final LightClientHeader finalizedHeader;
    final SszBytes32Vector finalityBranch;
    if (finalizedBlock.isPresent()) {
      finalizedHeader = headerSchema.create(BeaconBlockHeader.fromBlock(finalizedBlock.get().getMessage()));
      finalityBranch = BeaconStateAltair.required(attestedState).createFinalityBranchProof();
    } else {
      finalizedHeader = headerSchema.create(BeaconBlockHeader.fromState(attestedState).zero());
      finalityBranch = schema.getFinalityBranchSchema().getDefault();
    }

    return schema.create(
        attestedHeader,
        nextSyncCommittee,
        nextSyncCommitteeBranch,
        finalizedHeader,
        finalityBranch,
        block.getMessage().getBody().getOptionalSyncAggregate().orElseThrow(),
        SszUInt64.of(block.getSlot()));
  }

  public LightClientFinalityUpdate createLightClientFinalityUpdate(final LightClientUpdate update) {
    return schemaDefinitionsAltair
        .getLightClientFinalityUpdateSchema()
        .create(
            update.getAttestedHeader(),
            update.getFinalizedHeader(),
            update.getFinalityBranch(),
            update.getSyncAggregate(),
            update.getSignatureSlotSsz());
  }

  public LightClientOptimisticUpdate createLightClientOptimisticUpdate(
      final LightClientUpdate update) {
    return schemaDefinitionsAltair
        .getLightClientOptimisticUpdateSchema()
        .create(update.getAttestedHeader(), update.getSyncAggregate(), update.getSignatureSlotSsz());
  }

  private UInt64 periodAtSlot(final UInt64 slot) {
    return syncCommitteeUtil.computeSyncCommitteePeriod(
        schemaDefinitionsAltair == null ? slot : miscHelpers.computeEpochAtSlot(slot));
  }
```

> **Implementer note (resolve during coding):** the exact accessor names — `computeSyncCommitteePeriod` input (epoch), `MiscHelpers.computeEpochAtSlot`, `SyncAggregate` getter on the block body, and the container default helpers (`getDefault()` on the branch vector schema, `getNextSyncCommittee()` default) — must be confirmed against the actual APIs. Wire `MiscHelpers`/period helpers through the constructor if not already available. Drop the two dead placeholder `*Period` locals above (kept here only to show the period comparison intent); the real logic is `periodAtSlot(attestedBlock.slot) == periodAtSlot(block.slot)`. Add `getFinalizedHeader()`/`getFinalityBranch()`/`getSyncAggregate()`/`getSignatureSlotSsz()` getters to `LightClientUpdate`.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :ethereum:spec:test --tests "*LightClientUtilTest"`
Expected: PASS at Altair..Fulu (GLOAS/HEZE ignored).

- [ ] **Step 5: spotless + commit**

```bash
./gradlew spotlessApply
git add ethereum/spec/src/main/java/tech/pegasys/teku/spec/datastructures/lightclient/LightClientUpdate.java \
        ethereum/spec/src/main/java/tech/pegasys/teku/spec/logic/common/util/LightClientUtil.java \
        ethereum/spec/src/test/java/tech/pegasys/teku/spec/logic/common/util/LightClientUtilTest.java
git commit -m "feat: add createLightClientUpdate/FinalityUpdate/OptimisticUpdate to LightClientUtil"
```

---

### Task 6: Fixtures + schema tests for the two new types

**Files:**
- Modify: `ethereum/spec/src/testFixtures/java/tech/pegasys/teku/spec/util/DataStructureUtil.java`
- Create: `.../datastructures/lightclient/LightClientFinalityUpdateSchemaTest.java`
- Create: `.../datastructures/lightclient/LightClientOptimisticUpdateSchemaTest.java`

**Interfaces:**
- Produces: `randomLightClientFinalityUpdate(UInt64 slot)`, `randomLightClientOptimisticUpdate(UInt64 slot)`.

- [ ] **Step 1: Add fixtures** (mirror `randomLightClientUpdate` at `DataStructureUtil.java:2417`)

```java
  public LightClientFinalityUpdate randomLightClientFinalityUpdate(final UInt64 slot) {
    final LightClientFinalityUpdateSchema schema =
        getAltairSchemaDefinitions(slot).getLightClientFinalityUpdateSchema();
    final LightClientHeaderSchema<?> headerSchema =
        getAltairSchemaDefinitions(slot).getLightClientHeaderSchema();
    return schema.create(
        randomLightClientHeader(slot),
        randomLightClientHeader(slot),
        randomSszBytes32Vector(schema.getFinalityBranchSchema(), this::randomBytes32),
        randomSyncAggregate(),
        SszUInt64.of(randomUInt64()));
  }

  public LightClientOptimisticUpdate randomLightClientOptimisticUpdate(final UInt64 slot) {
    final LightClientOptimisticUpdateSchema schema =
        getAltairSchemaDefinitions(slot).getLightClientOptimisticUpdateSchema();
    return schema.create(
        randomLightClientHeader(slot), randomSyncAggregate(), SszUInt64.of(randomUInt64()));
  }
```

(Add imports for the two schema/type classes. Note `headerSchema` local in the finality fixture is unused if you build headers via `randomLightClientHeader` — drop it.)

- [ ] **Step 2: Write finality schema test** (mirror `LightClientUpdateSchemaTest` finality-branch section: Altair 6 / Electra 7 / Gloas 9, plus round-trip). Full file modeled exactly on `LightClientUpdateSchemaTest`, using `getLightClientFinalityUpdateSchema()` and `randomLightClientFinalityUpdate`.

- [ ] **Step 3: Write optimistic schema test** (round-trip only across all milestones ≥ Altair; no branch to assert). Modeled on `LightClientHeaderSchemaTest`'s `shouldRoundTripViaSsz`.

- [ ] **Step 4: Run**

Run: `./gradlew :ethereum:spec:test --tests "*LightClientFinalityUpdateSchemaTest" --tests "*LightClientOptimisticUpdateSchemaTest"`
Expected: PASS.

- [ ] **Step 5: spotless + commit**

```bash
./gradlew spotlessApply
git add ethereum/spec/src/testFixtures/ ethereum/spec/src/test/java/tech/pegasys/teku/spec/datastructures/lightclient/
git commit -m "test: add fixtures and schema tests for finality/optimistic updates"
```

---

### Task 7: Enable ssz_static reference tests

**Files:**
- Modify: `eth-reference-tests/src/referenceTest/java/tech/pegasys/teku/reference/.../ssz_static/SszTestExecutor.java:149,155`

**Interfaces:**
- Consumes: `getLightClientFinalityUpdateSchema()`, `getLightClientOptimisticUpdateSchema()`.

- [ ] **Step 1: Replace the two IGNORE entries** (lines 149 and 155) with real executors, mirroring the `ssz_static/LightClientUpdate` entry at line 159:

```java
          .put(
              "ssz_static/LightClientFinalityUpdate",
              new SszTestExecutor<>(
                  schemas ->
                      SchemaDefinitionsAltair.required(schemas)
                          .getLightClientFinalityUpdateSchema()))
          .put(
              "ssz_static/LightClientOptimisticUpdate",
              new SszTestExecutor<>(
                  schemas ->
                      SchemaDefinitionsAltair.required(schemas)
                          .getLightClientOptimisticUpdateSchema()))
```

Keep `ssz_static/LightClientStore` and `ssz_static/LightClientSnapshot` as `IGNORE_TESTS` (client-side, out of scope).

- [ ] **Step 2: Run the ssz_static reference tests for these types** (Altair, Electra, Gloas)

Run:
```bash
ENV_TEST_TYPE=ssz_static/LightClientFinalityUpdate ENV_SPEC=minimal ENV_MILESTONE=electra ./gradlew --no-daemon :eth-reference-tests:referenceTest --tests tech.pegasys.teku.reference.ManualReferenceTestRunner -x generateReferenceTestClasses
```
Expected: PASS (repeat for `gloas`, and for `LightClientOptimisticUpdate`).

- [ ] **Step 3: Commit**

```bash
git add eth-reference-tests/
git commit -m "test: enable ssz_static ref tests for finality/optimistic updates"
```

---

### Task 8: Full module verification

- [ ] **Step 1:** `./gradlew spotlessCheck :ethereum:spec:test`
- [ ] **Step 2:** `ethspecify check --path=specrefs` (finality/optimistic functions now have implementation sources — remove any stale exception entries for them).
- [ ] **Step 3: Commit** any specref updates: `git commit -m "chore: update specrefs for light-client update generation"`

---

## Self-Review Notes

- **Spec coverage:** Every Phase 1 bullet maps to a task except the already-done items (update-schema gindex refactor, Electra update variant, registry migration) which are complete on this branch — no task needed.
- **Scope deltas flagged to user:** (a) `169` reused not added; (b) `NEXT_SYNC_COMMITTEE_GINDEX_ELECTRA` is a fix 86→87 (Task 1); (c) finality update gets a **Gloas** variant too (required for ssz_static at Gloas), not Electra-only; (d) the util omits spec asserts, marked with `ponytail:`.
- **Known soft spots for the implementer (Task 5):** period-comparison helper wiring (`MiscHelpers.computeEpochAtSlot` + `computeSyncCommitteePeriod`), container default helpers, and `SyncAggregate` block accessor need confirmation against live APIs — the Task 5 note lists them explicitly.
- **Gloas/HEZE** remain ignored in `LightClientUtilTest` and `BeaconStateAltairProofTest` (proof depth needs progressive state merkleization, upstream #10931).