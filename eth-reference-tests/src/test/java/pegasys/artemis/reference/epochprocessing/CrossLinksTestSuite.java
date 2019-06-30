package pegasys.artemis.reference.epochprocessing;

import com.google.errorprone.annotations.MustBeClosed;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.BeaconStateTestHelper;
import tech.pegasys.artemis.datastructures.state.BeaconState;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.stream.Stream;

public class CrossLinksTestSuite extends BeaconStateTestHelper {
    private static String testFile = "**/tests/epoch_processing/crosslinks/crosslinks_mainnet.yaml";

    @ParameterizedTest(name = "crosslinks")
    @MethodSource("testCases")
    void epoch_processing(
            LinkedHashMap<String, Object> pre, LinkedHashMap<String, Object> post) {
        //State before processing
        BeaconState preState = convertMapToBeaconState(pre);
        //Expected state after processing
        BeaconState postState = convertMapToBeaconState(post);
        //process crosslinks
        //assertTrue(preState.equals(postState));
    }

    @MustBeClosed
    private static Stream<Arguments> testCases() throws IOException {
        return findTests(testFile, "test_cases");
    }
}
