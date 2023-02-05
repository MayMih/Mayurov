package org.mmu.myfirstandroidapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.squareup.picasso.Picasso;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import io.swagger.client.ApiException;
import io.swagger.client.api.FilmsApi;
import io.swagger.client.model.Country;
import io.swagger.client.model.Film;
import io.swagger.client.model.Genre;

public class CardActivity extends AppCompatActivity
{
    private static final Map<String, String> _cardData = new HashMap<>();
    private String filmId;
    
    private AsyncTask<String, Void, Void> downloadTask;
    
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card);
    
        MaterialToolbar customToolBar = findViewById(R.id.top_tool_bar);
        customToolBar.setTitle( getIntent().getStringExtra(Constants.ADAPTER_TITLE));
        this.setSupportActionBar(customToolBar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        
        filmId = getIntent().getStringExtra(Constants.ADAPTER_FILM_ID);
        
        getFilmDataAsync();
        
    }
    
    private class WebDataDownloadTask extends AsyncTask<String, Void, Void>
    {
        private final FilmsApi filmsApi;
        private Map.Entry<Exception, String> error;
        private static final String UNKNOWN_WEB_ERROR_MES = "Ошибка загрузки данных по сети:";
        private static final String KINO_API_ERROR_MES = "Ошибка API KinoPoisk";
        
        /**
         * Причёсанный код отсюда: <a href="https://stackoverflow.com/a/3681726/2323972">...</a>
         */
        private Bitmap downloadImageBitmap(String url)
        {
            Bitmap bm = null;
            try
            {
                URL aURL = new URL(url);
                URLConnection conn = aURL.openConnection();
                conn.connect();
                try (InputStream is = conn.getInputStream())
                {
                    try (BufferedInputStream bis = new BufferedInputStream(is))
                    {
                        bm = BitmapFactory.decodeStream(bis);
                    }
                }
            }
            catch (Exception e)
            {
                Log.e(Constants.LOG_TAG, "Error getting bitmap", e);
            }
            return bm;
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
                        "\n\nСтраны:" + response.getCountries().stream().map(country ->
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
                            KINO_API_ERROR_MES, apiEx.getCode(), headersText, apiEx.getResponseBody());
                }
                Log.e(Constants.LOG_TAG, mes.isEmpty() ? UNKNOWN_WEB_ERROR_MES : mes, ex);
            }
            return null;
        }
    
    
       @Override
        protected void onPostExecute(Void unused)
        {
            super.onPostExecute(unused);
            var progBar = findViewById(R.id.progress_bar);
            progBar.setVisibility(View.GONE);
            if (error != null)
            {
                var mes = error.getValue();
                showSnackBar(UNKNOWN_WEB_ERROR_MES);
            }
            else
            {
                Log.d(Constants.LOG_TAG, "Загрузка Веб-ресурса завершена успешно");
                LinearLayout cardsContainer = findViewById(R.id.card_linear_lyaout);
                ImageView imgView = ((ImageView)findViewById(R.id.imageView));
                Picasso.get().load(Uri.parse(_cardData.get(Constants.ADAPTER_POSTER_PREVIEW_URL))).into(imgView);
                TextView txtHeader = findViewById(R.id.card_title);
                txtHeader.setText(_cardData.get(Constants.ADAPTER_TITLE));
                TextView txtContent = findViewById(R.id.card_content);
                txtContent.setText(_cardData.get(Constants.ADAPTER_CONTENT));
            }
        }
    }
    
    /**
     * Метод отображения всплывющей подсказки
     */
    private void showSnackBar(String message)
    {
        var androidContentView = findViewById(android.R.id.content);
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
        downloadTask = new WebDataDownloadTask(FilmsApiHelper.filmsApi).execute();
    }
}