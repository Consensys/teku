package tech.pegasys.teku.beaconrestapi.tekuv1.beacon;

import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.teku.GetEth1DataCacheResponse;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetEth1DataCache;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.util.DataStructureUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class GetEth1DataCacheIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

    private DataStructureUtil dataStructureUtil;
    private List<Eth1Data> eth1DataCacheBlocks;

    @BeforeEach
    public void setup() {
        startRestAPIAtGenesis(SpecMilestone.PHASE0);
        dataStructureUtil = new DataStructureUtil(spec);
    }

    @Test
    public void shouldReturnAllEth1BlocksFromCache() throws IOException {
        eth1DataCacheBlocks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            eth1DataCacheBlocks.add(dataStructureUtil.randomEth1Data());
        }
        when(eth1DataCache.getAllEth1Blocks()).thenReturn(eth1DataCacheBlocks);
        final Response response = get();
        assertThat(response.code()).isEqualTo(SC_OK);
        GetEth1DataCacheResponse getEth1DataResponse =
                jsonProvider.jsonToObject(response.body().string(), GetEth1DataCacheResponse.class);
        assertThat(getEth1DataResponse).isNotNull();
        assertThat(getEth1DataResponse.data.containsAll(eth1DataCacheBlocks)).isTrue();
    }

    private Response get() throws IOException {
        return getResponse(GetEth1DataCache.ROUTE);
    }

}
