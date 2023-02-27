package org.mmu.tinkoffkinolab;

import java.util.Objects;

import io.swagger.client.ApiClient;
import io.swagger.client.api.FilmsApi;

public class FilmsApiHelper
{
    private static FilmsApi filmsApi;
    
    public static FilmsApi getFilmsApi()
    {
        if (filmsApi == null)
        {
            filmsApi = createFilmsApi();
        }
        return filmsApi;
    }
    
    private static FilmsApi createFilmsApi()
    {
        var api = new ApiClient();
        api.setApiKey(Constants.KINO_DEMO_API_KEY);
        filmsApi = new FilmsApi(api);
        return filmsApi;
    }
}
