package tech.pegasys.teku.statetransition.executionproofs;

import org.assertj.core.api.Assert;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.util.DataStructureUtil;

import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExecutionProofGeneratorImplTest {

    final Spec spec =
            TestSpecFactory.createMinimalElectra();
    private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    @Test
    void shouldGenerateDifferentProofsForDifferentSubnets() throws ExecutionException, InterruptedException {
        ExecutionProofGeneratorImpl generator = new ExecutionProofGeneratorImpl(spec.getGenesisSchemaDefinitions().toVersionElectra().get());
        SignedBlockContainer block = dataStructureUtil.randomSignedBlockContents();

        int subnetA = 1;
        int subnetB = 2;

        ExecutionProof proofA = generator.generateExecutionProof(block, subnetA).get();
        ExecutionProof proofB = generator.generateExecutionProof(block, subnetB).get();

        assertNotNull(proofA);
        assertNotNull(proofB);
        assertThat(proofA).isNotEqualTo(proofB);
    }



}