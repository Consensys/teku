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

package tech.pegasys.teku.spec.datastructures.epbs.versions.gloas;

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_PENDING_WITHDRAWAL_SCHEMA;

import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class BuilderPendingPaymentSchema
    extends ContainerSchema2<BuilderPendingPayment, SszUInt64, BuilderPendingWithdrawal> {

  public BuilderPendingPaymentSchema(final SchemaRegistry schemaRegistry) {
    super(
        "BuilderPendingPayment",
        namedSchema("weight", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("withdrawal", schemaRegistry.get(BUILDER_PENDING_WITHDRAWAL_SCHEMA)));
  }

  public BuilderPendingPayment create(
      final UInt64 weight, final BuilderPendingWithdrawal withdrawal) {
    return new BuilderPendingPayment(this, weight, withdrawal);
  }

  @Override
  public BuilderPendingPayment createFromBackingNode(final TreeNode node) {
    return new BuilderPendingPayment(this, node);
  }
}
