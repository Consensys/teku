package pegasys.artemis.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.MustBeClosed;
import org.apache.tuweni.io.Resources;
import org.junit.jupiter.params.provider.Arguments;
import tech.pegasys.artemis.datastructures.state.BeaconState;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class BeaconStateTestHelper {

    public static BeaconState convertMapToBeaconState(LinkedHashMap<String, String> map){
        //TODO Convert Map to BeaconState object
        Object slotValue = map.get("slot");
        UnsignedLong slot = UnsignedLong.valueOf(slotValue.toString());
        Object genesisTimeValue = map.get("genesis_time");
        UnsignedLong genesis_time = UnsignedLong.valueOf(genesisTimeValue.toString());

        BeaconState state = new BeaconState();
        state.setSlot(slot);
        return state;
    }

    @MustBeClosed
    public static Stream<Arguments> findTests(String glob, String tcase) throws IOException {
        return Resources.find(glob)
                .flatMap(
                        url -> {
                            try (InputStream in = url.openConnection().getInputStream()) {
                                return prepareTests(in, tcase);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Stream<Arguments> prepareTests(InputStream in, String tcase) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Map allTests = mapper.readerFor(Map.class).readValue(in);

        return ((List<Map>) allTests.get(tcase))
                .stream().map(testCase -> Arguments.of(testCase.get("pre"), testCase.get("post")));
    }

}
