package tech.pegasys.teku.spec.datastructures.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.propertytest.suppliers.execution.ExecutionProofSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.execution.versions.capella.WithdrawalSupplier;

import static tech.pegasys.teku.spec.propertytest.util.PropertyTestHelper.assertDeserializeMutatedThrowsExpected;
import static tech.pegasys.teku.spec.propertytest.util.PropertyTestHelper.assertRoundTrip;

public class ExecutionProofPropertyTest {

    @Property
    void roundTrip(@ForAll(supplier = ExecutionProofSupplier.class) final ExecutionProof executionProof)
            throws JsonProcessingException {
        assertRoundTrip(executionProof);
    }

    @Property
    void deserializeMutated(
            @ForAll(supplier = ExecutionProofSupplier.class) final ExecutionProof executionProof,
            @ForAll final int seed) {
        assertDeserializeMutatedThrowsExpected(executionProof, seed);
    }
}
