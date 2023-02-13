package org.mmu.tinkoffkinolab;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Transformation;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import io.swagger.client.ApiException;
import io.swagger.client.api.FilmsApi;

public class MainActivity extends AppCompatActivity {

    
    //region 'Типы'
    
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
            clearLists();
            progBar.setVisibility(View.VISIBLE);
            Log.d(Constants.LOG_TAG, "Начало загрузки веб-ресурса...");
        }
        
        @Override
        protected Void doInBackground(String... request)
        {
            try
            {
                var response = filmsApi.apiV22FilmsTopGet(Constants.TopFilmsType.TOP_100_POPULAR_FILMS.name(), 1);
                for (var filmData : response.getFilms())
                {
                    if (isCancelled())
                    {
                        return null;
                    }
                    var id = String.valueOf(filmData.getFilmId());
                    Optional<String> name = Optional.ofNullable(isRus ? filmData.getNameRu() : filmData.getNameEn());
                    _cardList.add(Map.of(Constants.ADAPTER_TITLE, name.orElse(id),
                            Constants.ADAPTER_CONTENT,filmData.getGenres().get(0).getGenre() + " (" + filmData.getYear() + ")",
                            Constants.ADAPTER_FILM_ID, id, Constants.ADAPTER_POSTER_PREVIEW_URL, filmData.getPosterUrlPreview())
                    );
                }
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
            progBar.setVisibility(View.GONE);
            if (error != null)
            {
                var mes = error.getValue();
                showSnackBar(UNKNOWN_WEB_ERROR_MES);
                if (!mes.isBlank())
                {
                    txtQuery.setError(mes);
                }
            }
            else
            {
                Log.d(Constants.LOG_TAG, "Загрузка Веб-ресурса завершена успешно");
                LinearLayout cardsContainer = findViewById(R.id.card_linear_lyaout);
                var results = new ArrayList<View>();
    
                for (int i = 0; i < _cardList.size(); i++)
                {
                    LayoutInflater inflater = getLayoutInflater();
                    var listItem = inflater.inflate(R.layout.list_item, null);
                    var cardData = _cardList.get(i);
                    var id = cardData.get(Constants.ADAPTER_FILM_ID);
                    ((TextView)listItem.findViewById(R.id.film_id_holder)).setText(id);
                    var title = cardData.get(Constants.ADAPTER_TITLE);
                    ((TextView)listItem.findViewById(R.id.card_title)).setText(title);
                    ((TextView)listItem.findViewById(R.id.card_content)).setText(cardData.get(Constants.ADAPTER_CONTENT));
                    var imgView = ((ImageView)listItem.findViewById(R.id.poster_preview));
                    Picasso.get().load(Uri.parse(cardData.get(Constants.ADAPTER_POSTER_PREVIEW_URL))).into(imgView);
                    cardsContainer.addView(listItem);
                    listItem.setOnClickListener(v -> showFilmCardActivity(id, title));
                }
            }
        }
    }
    
    //endregion 'Типы'
    
    
    
    private static final List<Map<String, String>> _cardList = new ArrayList<>();
    private boolean isRus;
    private MaterialToolbar customToolBar;
    
    private ProgressBar progBar;
    
    /**
     * Обязательно пересоздавать задачу перед каждым вызовом!
     */
    private AsyncTask<String, Void, Void> downloadTask;
    private TextInputEditText txtQuery;
    private View androidContentView;
    
    
    
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.w(Constants.LOG_TAG, "start of onCreate function");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        isRus = Locale.getDefault().getLanguage().equalsIgnoreCase("ru");
        androidContentView = findViewById(android.R.id.content);
        progBar = findViewById(R.id.progress_bar);
        progBar.setVisibility(View.INVISIBLE);
        txtQuery = findViewById(R.id.txt_input);
        this.initViews();
        showPopularFilmsAsync();
        Log.w(Constants.LOG_TAG, "end of onCreate function");
    }

    /**
     * Метод настройки виджетов
     */
    private void initViews()
    {
        customToolBar = findViewById(R.id.top_toolbar);
        this.setSupportActionBar(customToolBar);
        txtQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE)
            {
                clearLists();
                var text  = Objects.requireNonNull(txtQuery.getText()).toString().replace("null", "");
                getTopFilmsAsync(text);
                return false;
            }
            return true;
        });
    }
    
    /**
     * Метод отображения всплывющей подсказки
     */
    private void showSnackBar(String message)
    {
        var popup = Snackbar.make(this.androidContentView, message, Snackbar.LENGTH_INDEFINITE);
        txtQuery.setEnabled(false);
        popup.setAction(R.string.repeat_button_caption, view -> {
            var text  = Objects.requireNonNull(txtQuery.getText()).toString().replace("null", "");
            this.getTopFilmsAsync(text);
            popup.dismiss();
            txtQuery.setEnabled(true);
        });
        popup.show();
    }
    
    private void getTopFilmsAsync()
    {
        getTopFilmsAsync("");
    }
    /**
     * Получает данные по топ 100 фильмов (первые 20 записей) или по конкретному фильму (если request не пуст)
     *
     * @param request - ИД фильма, инфу по которому нужно получить
     */
    private void getTopFilmsAsync(String request)
    {
        if (downloadTask != null && !downloadTask.isCancelled() && (downloadTask.getStatus() == AsyncTask.Status.RUNNING))
        {
            downloadTask.cancel(true);
        }
        downloadTask = new WebDataDownloadTask(FilmsApiHelper.getFilmsApi()).execute(request);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.action_switch_to_favorites:
            {
                Log.d(Constants.LOG_TAG, String.format("Команда меню \"%s\"", item.getTitle()));
                showFavouriteFilms();
                break;
            }
            case R.id.action_switch_to_popular:
            {
                Log.d(Constants.LOG_TAG, String.format("Команда меню \"%s\"", item.getTitle()));
                showPopularFilmsAsync();
                break;
            }
            default:
            {
                Log.w(Constants.LOG_TAG, "Неизвестная команда меню!");
                break;
            }
        }
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * Метод показа ТОП-100 популярных фильмов
     */
    private void showPopularFilmsAsync()
    {
        clearLists();
        customToolBar.setTitle(R.string.action_popular_title);
        this.getTopFilmsAsync();
    }
    
    /**
     * Заготовка метода показа Избранных фильмов
     */
    private void showFavouriteFilms()
    {
        clearLists();
        customToolBar.setTitle(R.string.action_favourites_title);
    }
    
    private void clearLists()
    {
        _cardList.clear();
        LinearLayout ll = findViewById(R.id.card_linear_lyaout);
        ll.removeAllViews();
    }
    
    /**
     * Метод октрытия окна деталей о фильме
     *
     * @param kinoApiFilmId
     * @param cardTitle Желаемый заголовок карточки
     */
    @NonNull
    private void showFilmCardActivity(String kinoApiFilmId, String cardTitle)
    {
        var switchActivityIntent = new Intent(getApplicationContext(), CardActivity.class);
        switchActivityIntent.putExtra(Constants.ADAPTER_TITLE, cardTitle);
        switchActivityIntent.putExtra(Constants.ADAPTER_FILM_ID, kinoApiFilmId);
        startActivity(switchActivityIntent);
    }
}