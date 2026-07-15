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

package tech.pegasys.teku.spec.datastructures.lightclient.versions.capella;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientHeaderSchema;
import tech.pegasys.teku.spec.logic.common.helpers.MathHelpers;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

import static tech.pegasys.teku.spec.constants.LightClientConstants.EXECUTION_PAYLOAD_GINDEX;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_HEADER_SCHEMA;

public class LightClientHeaderSchemaCapella extends ContainerSchema3<LightClientHeaderCapella, BeaconBlockHeader, ExecutionPayloadHeader, SszBytes32Vector> implements LightClientHeaderSchema<LightClientHeaderCapella> {
    public LightClientHeaderSchemaCapella(final SchemaRegistry registry) {
        super("LightClientHeader",
                namedSchema("beacon", BeaconBlockHeader.SSZ_SCHEMA),
                namedSchema("execution",
                        SszSchema.as(ExecutionPayloadHeader.class, registry.get(EXECUTION_PAYLOAD_HEADER_SCHEMA))),
                namedSchema("execution_branch", SszBytes32VectorSchema.create(MathHelpers.floorLog2(EXECUTION_PAYLOAD_GINDEX))));
    }

    public LightClientHeaderCapella create(final BeaconBlockHeader header, final ExecutionPayloadHeader executionHeader, final SszBytes32Vector branch) {
        return new LightClientHeaderCapella(this, header, executionHeader, branch);
    }

    @Override
    public LightClientHeaderCapella createFromBackingNode(final TreeNode node) {
        return new LightClientHeaderCapella(this, node);
    }

    @Override
    public LightClientHeaderSchemaCapella toVersionCapellaRequired() {
        return this;
    }
}