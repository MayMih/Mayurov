package org.mmu.tinkoffkinolab;

import static org.mmu.tinkoffkinolab.Constants.LOG_TAG;

import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.squareup.picasso.Callback;
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
    
    //region 'Поля и константы'
    private static final Map<String, String> _cardData = new HashMap<>();
    private ImageView imgPoster;
    private String filmId;
    
    private WebDataDownloadTask downloadTask;
    private TextView txtHeader;
    private TextView txtContent;
    private View progBar;
    private boolean _isHorizontal;
    
    //endregion 'Поля и константы'
    
    
    
    //region 'Типы'
    
    private class WebDataDownloadTask extends AsyncTask<Void, Void, Void>
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
            progBar.setVisibility(View.VISIBLE);
            Log.d(LOG_TAG, "Начало загрузки веб-ресурса...");
        }
        
        @Override
        protected Void doInBackground(Void... unused)
        {
            try
            {
                final var response = filmsApi.apiV22FilmsIdGet(Integer.parseInt(filmId));
                final var engName = Objects.requireNonNullElse(response.getNameEn(), "");
                _cardData.put(Constants.ADAPTER_TITLE, response.getNameRu() + (!engName.isBlank() ?
                        System.lineSeparator() + "[" + engName +"]" : ""));
                final var genres = "\n\n Жанры: " + response.getGenres().stream()
                        .map(g -> g.getGenre() + ", ")
                        .collect(Collectors.joining())
                        .replaceFirst(",\\s*$", "");
                final var countries = "\n\n Страны: " + response.getCountries().stream()
                        .map(country -> country.getCountry() + ", ")
                        .collect(Collectors.joining()).replaceFirst(",\\s*$", "");
                final var res = Objects.requireNonNullElse(response.getDescription(), "") +
                        genres + countries;
                _cardData.put(Constants.ADAPTER_CONTENT, res);
                _cardData.put(Constants.ADAPTER_POSTER_PREVIEW_URL, response.getPosterUrl());
            }
            catch (RuntimeException ex)
            {
                var mes = Objects.requireNonNullElse(ex.getMessage(), "");
                error = new AbstractMap.SimpleEntry<>(ex, mes);
                if (ex instanceof ApiException)
                {
                    final var apiEx = (ApiException)ex;
                    final var headers = apiEx.getResponseHeaders();
                    final var headersText = headers == null ? "" : headers.entrySet().stream()
                            .map(entry -> entry.getKey() + ": " + String.join(" \n", entry.getValue()))
                            .collect(Collectors.joining());
                    mes += String.format(Locale.ROOT, " %s (ErrorCode: %d), ResponseHeaders: \n%s\n ResponseBody: \n%s\n",
                            Constants.KINO_API_ERROR_MES, apiEx.getCode(), headersText, apiEx.getResponseBody());
                }
                Log.e(LOG_TAG, mes.isEmpty() ? Constants.UNKNOWN_WEB_ERROR_MES : mes, ex);
            }
            return null;
        }
    
        /**
         * @apiNote Этот метод выполняется в потоке интерфейса
         *
         * @param unused The result of the operation computed by {@link #doInBackground}.
         */
        @Override
        protected void onPostExecute(Void unused)
        {
            super.onPostExecute(unused);
            progBar.setVisibility(View.GONE);
            if (downloadTask.getError() != null)
            {
                final var mes = downloadTask.getError().getValue();
                showSnackBar(Constants.UNKNOWN_WEB_ERROR_MES);
            }
            else
            {
                Log.d(LOG_TAG, "Загрузка Веб-ресурса завершена успешно");
                fillCardUI();
            }
        }
    }
    
    //endregion 'Типы'
    
    
    
    
    //region 'Обработчики'
    
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card);
        
        final MaterialToolbar customToolBar = findViewById(R.id.top_tool_bar);
        customToolBar.setTitle(getIntent().getStringExtra(Constants.ADAPTER_TITLE));
        this.setSupportActionBar(customToolBar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        progBar = findViewById(R.id.progress_bar);
        
        filmId = getIntent().getStringExtra(Constants.ADAPTER_FILM_ID);
        
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks()
        {
            @Override
            public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment f,
                                              @NonNull View v, @Nullable Bundle savedInstanceState)
            {
                imgPoster = v.findViewById(R.id.poster_image_view);
                txtHeader = v.findViewById(R.id.card_title);
                txtContent = v.findViewById(R.id.card_content);
                super.onFragmentViewCreated(fm, f, v, savedInstanceState);
                getFilmDataAsync();
            }
        }, false);
    }
    
    
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
        {
            _isHorizontal = true;
        }
        else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT)
        {
            _isHorizontal = false;
        }
    }
    
    /**
     * Обработчик нажатия кнопки меню Назад
     *
     * @implNote Без этого Кнопка "Назад" игнорирует сохранённое состояние виджетов MainActivity
     * @param item The menu item that was selected.
     * @see <a href="https://stackoverflow.com/a/27807976/2323972">Взято отсюда: If you want
     *      ActionBar back button behave same way as hardware back button</a>
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item)
    {
        if (item.getItemId() == android.R.id.home)
        {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    //endregion 'Обработчики'
    
    
    
    
    //region 'Методы'
    
    /**
     * Метод заполнения UI карточки фильма из скачанных данных
     */
    private void fillCardUI()
    {
        progBar.setVisibility(View.VISIBLE);
        // N.B.: параметры fit() и centerCrop() могут сильно замедлять загрузку!
        final var posterUrl = _cardData.get(Constants.ADAPTER_POSTER_PREVIEW_URL);
        Picasso.get().load(posterUrl).into(imgPoster, new Callback()
        {
            @Override
            public void onSuccess()
            {
                progBar.setVisibility(View.GONE);
                Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);
                //noinspection ConstantConditions
                txtHeader.setText(Objects.requireNonNullElse(_cardData.get(Constants.ADAPTER_TITLE),
                        getSupportActionBar().getTitle()));
                final var textContent = _cardData.get(Constants.ADAPTER_CONTENT);
                txtContent.setText(Objects.requireNonNullElse(textContent, ""));
                imgPoster.setOnClickListener(v1 -> {
                    Utils.showFullScreenPhoto(Uri.parse(posterUrl), v1.getContext());
                });
                final ScrollView sv = findViewById(R.id.fragment_scroll);
                sv.postDelayed(() -> {
                    if (!Utils.isViewOnScreen(txtContent))
                    {
                        sv.smoothScrollTo(0, imgPoster.getHeight() / 2);
                    }
                }, 500);
            }
    
            @Override
            public void onError(Exception e)
            {
                progBar.setVisibility(View.GONE);
                Log.e(LOG_TAG, "Ошибка загрузки большого постера", e);
            }
        });
    }
    
    /**
     * Метод отображения всплывющей подсказки
     */
    private void showSnackBar(String message)
    {
        var popup = Snackbar.make(this.imgPoster, message, Snackbar.LENGTH_INDEFINITE);
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
    
    //endregion 'Методы'
    
}