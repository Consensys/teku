package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.coordinator.Eth1DataCache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetEth1DataCacheTest extends AbstractMigratedBeaconHandlerTest {

    private DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    private final Eth1DataCache eth1DataCache = mock(Eth1DataCache.class);

    private final List<Eth1Data> eth1DataCacheBlocks = Arrays.asList(
            dataStructureUtil.randomEth1Data(),
            dataStructureUtil.randomEth1Data(),
            dataStructureUtil.randomEth1Data()
    );

    @BeforeEach
    void setup() {
        setHandler(new GetEth1DataCache(eth1DataCache));
    }

    @Test
    public void shouldReturnListOfCachedEth1Blocks() throws JsonProcessingException {
        when(eth1DataCache.getAllEth1Blocks()).thenReturn(eth1DataCacheBlocks);
        handler.handleRequest(request);
        assertThat(request.getResponseCode()).isEqualTo(SC_OK);
        assertThat(((List<Eth1Data>) request.getResponseBody()).containsAll(eth1DataCache.getAllEth1Blocks())).isTrue();
    }

    @Test
    public void shouldReturnNotFoundWhenCacheIsEmpty() throws JsonProcessingException {
        when(eth1DataCache.getAllEth1Blocks()).thenReturn(new ArrayList<>());
        handler.handleRequest(request);
        assertThat(request.getResponseBody()).isEqualTo(new HttpErrorResponse(HttpStatusCodes.SC_NOT_FOUND, "Eth1 blocks cache is empty"));

    }
}
