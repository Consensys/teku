/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.datastructures.execution.versions.electra;

import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.TransactionSchema;

public class InclusionListSchema
    extends ContainerSchema2<InclusionList, SszList<SszByteVector>, SszList<Transaction>> {

  public InclusionListSchema(final SpecConfigElectra specConfig) {
    super(
        "InclusionList",
        namedSchema(
            "summary",
            SszListSchema.create(
                SszByteVectorSchema.create(Bytes20.SIZE),
                specConfig.getMaxTransactionPerInclusionList(),
                SszSchemaHints.none())),
        namedSchema(
            "transactions",
            SszListSchema.create(
                new TransactionSchema(specConfig),
                specConfig.getMaxTransactionPerInclusionList())));
  }

  @Override
  public InclusionList createFromBackingNode(final TreeNode node) {
    return new InclusionList(this, node);
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<SszByteVector, ? extends SszList<SszByteVector>> getSummarySchema() {
    return (SszListSchema<SszByteVector, ? extends SszList<SszByteVector>>) getFieldSchema0();
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<Transaction, ? extends SszList<Transaction>> getTransactionsSchema() {
    return (SszListSchema<Transaction, ? extends SszList<Transaction>>) getFieldSchema1();
  }

  public TransactionSchema getTransactionSchema() {
    return (TransactionSchema) getTransactionsSchema().getElementSchema();
  }
}
