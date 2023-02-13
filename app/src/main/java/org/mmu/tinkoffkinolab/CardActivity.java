package org.mmu.tinkoffkinolab;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.RoundedCorner;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.squareup.picasso.Picasso;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import io.swagger.client.ApiException;
import io.swagger.client.api.FilmsApi;

public class CardActivity extends AppCompatActivity
{
    private static final Map<String, String> _cardData = new HashMap<>();
    private ImageView imgPoster;
    private String filmId;
    
    private WebDataDownloadTask downloadTask;
    private TextView txtHeader;
    private TextView txtContent;
    private View androidContentView;
    
    
    
    //region 'Типы'
    
    private class WebDataDownloadTask extends AsyncTask<String, Void, Void>
    {
        private final FilmsApi filmsApi;
        private Map.Entry<Exception, String> error;
    
        public Map.Entry<Exception, String> getError()
        {
            return error;
        }
        
        public WebDataDownloadTask(FilmsApi engine)
        {
            filmsApi = engine;
        }
        
        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
            var progBar = findViewById(R.id.progress_bar);
            progBar.setVisibility(View.VISIBLE);
            Log.d(Constants.LOG_TAG, "Начало загрузки веб-ресурса...");
        }
        
        @Override
        protected Void doInBackground(String... request)
        {
            try
            {
                var response = filmsApi.apiV22FilmsIdGet(Integer.parseInt(filmId));
                _cardData.put(Constants.ADAPTER_TITLE, response.getNameRu());
                var res = response.getDescription() + "\n\n Жанры: " + response.getGenres().stream().map(
                        genre -> genre.getGenre() + ", ").collect(Collectors.joining()) +
                        "\n\n Страны: " + response.getCountries().stream().map(country ->
                                country.getCountry() + ", ").collect(Collectors.joining());
                _cardData.put(Constants.ADAPTER_CONTENT, res);
                _cardData.put(Constants.ADAPTER_POSTER_PREVIEW_URL, response.getPosterUrl());
            }
            catch (RuntimeException ex)
            {
                var mes = Objects.requireNonNullElse(ex.getMessage(), "");
                error = new AbstractMap.SimpleEntry<>(ex, mes);
                if (ex instanceof ApiException)
                {
                    var apiEx = (ApiException)ex;
                    var headers = apiEx.getResponseHeaders();
                    var headersText = headers == null ? "" : headers.entrySet().stream().map(
                            entry -> entry.getKey() + ": " + String.join(" \n", entry.getValue())
                    ).collect(Collectors.joining());
                    mes += String.format(Locale.ROOT, " %s (ErrorCode: %d), ResponseHeaders: \n%s\n ResponseBody: \n%s\n",
                            Constants.KINO_API_ERROR_MES, apiEx.getCode(), headersText, apiEx.getResponseBody());
                }
                Log.e(Constants.LOG_TAG, mes.isEmpty() ? Constants.UNKNOWN_WEB_ERROR_MES : mes, ex);
            }
            return null;
        }
    
    
       @Override
        protected void onPostExecute(Void unused)
        {
            super.onPostExecute(unused);
            var progBar = findViewById(R.id.progress_bar);
            progBar.setVisibility(View.GONE);
            if (downloadTask.getError() != null)
            {
                var mes = downloadTask.getError().getValue();
                showSnackBar(Constants.UNKNOWN_WEB_ERROR_MES);
            }
            else
            {
                Log.d(Constants.LOG_TAG, "Загрузка Веб-ресурса завершена успешно");
                fillCardUI();
            }
        }
    }
    
    //endregion 'Типы'
    
    
    
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card);
        
        MaterialToolbar customToolBar = findViewById(R.id.top_tool_bar);
        customToolBar.setTitle(getIntent().getStringExtra(Constants.ADAPTER_TITLE));
        this.setSupportActionBar(customToolBar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        
        imgPoster = findViewById(R.id.poster_image_view);
        txtHeader = findViewById(R.id.card_title);
        txtContent = findViewById(R.id.card_content);
        androidContentView = findViewById(android.R.id.content);
        
        filmId = getIntent().getStringExtra(Constants.ADAPTER_FILM_ID);
        getFilmDataAsync();
    }
    
    /**
     * Метод заполнения UI карточки фильма из скачанных данных
     */
    private void fillCardUI()
    {
        // параметры fit() и centerCrop() сильно замедляют загрузку.
        Picasso.get().load(_cardData.get(Constants.ADAPTER_POSTER_PREVIEW_URL)).fit().centerCrop().into(imgPoster);
        txtHeader.setText(_cardData.get(Constants.ADAPTER_TITLE));
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);
        txtContent.setText(_cardData.get(Constants.ADAPTER_CONTENT));
    }
    
    /**
     * Метод отображения всплывющей подсказки
     */
    private void showSnackBar(String message)
    {
        var popup = Snackbar.make(androidContentView, message, Snackbar.LENGTH_INDEFINITE);
        popup.setAction(R.string.repeat_button_caption, view -> {
            getFilmDataAsync();
            popup.dismiss();
        });
        popup.show();
    }
    
    private void getFilmDataAsync()
    {
        if (downloadTask != null && !downloadTask.isCancelled() && (downloadTask.getStatus() == AsyncTask.Status.RUNNING))
        {
            downloadTask.cancel(true);
        }
        downloadTask = (WebDataDownloadTask)new WebDataDownloadTask(FilmsApiHelper.getFilmsApi()).execute();
    }
}