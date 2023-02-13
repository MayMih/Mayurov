package org.mmu.tinkoffkinolab;

import io.swagger.client.ApiClient;
import io.swagger.client.api.FilmsApi;

public class FilmsApiHelper
{
    public static FilmsApi filmsApi;
    
    public static void initFilmsApi()
    {
        var api = new ApiClient();
        api.setApiKey(Constants.KINO_DEMO_API_KEY);
        filmsApi = new FilmsApi(api);
    }
}
