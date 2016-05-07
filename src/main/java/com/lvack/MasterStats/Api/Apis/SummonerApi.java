package com.lvack.MasterStats.Api.Apis;

import com.lvack.MasterStats.Api.ResponseClasses.SummonerDto;
import com.lvack.MasterStats.Api.RiotApi;
import com.lvack.MasterStats.Api.RiotApiResponse;
import com.lvack.MasterStats.Api.RiotApiUtils;
import com.google.gson.reflect.TypeToken;

import javax.ws.rs.client.WebTarget;
import java.util.HashMap;
import java.util.Map;

/**
 * SummonerClass for RiotApiChallengeChampionMastery
 *
 * @author Leon Vack - TWENTY |20
 */

public class SummonerApi extends RiotSubApi {
    private static final String BASE_PATH = "/api/lol/{region}/v1.4/summoner/";

    public SummonerApi(RiotApi riotApi) {
        super(BASE_PATH, riotApi);
    }

    public RiotApiResponse<Map<String, SummonerDto>> getSummonersByNames(String... names) {
        WebTarget target = getFunctionTarget("by-name/{names}")
                .resolveTemplate("names", RiotApiUtils.arrayToCommaSeparatedList(names));
        return processApiResponse(new RiotApiResponse<>(riotApi.prepareRequest(target),
                new TypeToken<HashMap<String, SummonerDto>>() {}.getType()));
    }

    public RiotApiResponse<Map<String, SummonerDto>> getSummonersByIds(Long... ids) {
        WebTarget target = getFunctionTarget("{ids}")
                .resolveTemplate("ids", RiotApiUtils.arrayToCommaSeparatedList(ids));

        return processApiResponse(new RiotApiResponse<>(riotApi.prepareRequest(target),
                new TypeToken<HashMap<String, SummonerDto>>() {}.getType()));
    }
}
