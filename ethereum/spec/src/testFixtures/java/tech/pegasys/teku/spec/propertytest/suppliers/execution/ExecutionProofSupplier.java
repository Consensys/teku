package tech.pegasys.teku.spec.propertytest.suppliers.execution;

import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.propertytest.suppliers.DataStructureUtilSupplier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class ExecutionProofSupplier extends DataStructureUtilSupplier<ExecutionProof> {
    public ExecutionProofSupplier() {
        super(DataStructureUtil::randomExecutionProof, SpecMilestone.ELECTRA);
    }

}
