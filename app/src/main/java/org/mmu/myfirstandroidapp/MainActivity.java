package org.mmu.myfirstandroidapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.FilmsApi;

public class MainActivity extends AppCompatActivity {
    private boolean isRus;
    private MaterialToolbar customToolBar;
    
    //region 'Типы'
    
    private enum TopFilmsType
    {
        TOP_100_POPULAR_FILMS,
        TOP_250_BEST_FILMS,
        TOP_AWAIT_FILMS
    }
    
    private class WebDataDownloadTask extends AsyncTask<String, Void, Void>
    {
        private final FilmsApi filmsApi;
        private Map.Entry<Exception, String> error;
        private static final String UNKNOWN_WEB_ERROR_MES = "Ошибка загрузки данных по сети:";
        private static final String KINO_API_ERROR_MES = "Ошибка API KinoPoisk";
        
        
        public WebDataDownloadTask(FilmsApi engine)
        {
            filmsApi = engine;
        }
        
        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
            _cardList.clear();
            cardsAdapter.notifyDataSetChanged();
            progBar.setVisibility(View.VISIBLE);
            Log.d(LOG_TAG, "Начало загрузки веб-ресурса...");
        }
        
        @Override
        protected Void doInBackground(String... request)
        {
            try
            {
                var response = filmsApi.apiV22FilmsTopGet(TopFilmsType.TOP_100_POPULAR_FILMS.name(), 1);
                for (var filmData : response.getFilms())
                {
                    if (isCancelled())
                    {
                        return null;
                    }
                    _cardList.add(Map.of(ADAPTER_KEY_TITLE, isRus ? filmData.getNameRu() : filmData.getNameEn(),
                            ADAPTER_CONTENT,filmData.getGenres().get(0).getGenre() + " (" + filmData.getYear() + ")")
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
                    mes += String.format(Locale.ROOT, " %s (ErrorCode: %d), ResponseHeaders: \n%s\n ResponseBody: \n%s\n",
                            KINO_API_ERROR_MES, apiEx.getCode(), headers.entrySet().stream().map(entry ->
                                entry.getKey() + ": " + String.join(" \n", entry.getValue())
                            ).collect(Collectors.joining()), apiEx.getResponseBody());
                }
                Log.e(LOG_TAG, mes.isEmpty() ? UNKNOWN_WEB_ERROR_MES : mes, ex);
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
                showSnackBar(error instanceof ApiException ? KINO_API_ERROR_MES : (mes.isEmpty() ?
                        UNKNOWN_WEB_ERROR_MES : mes));
                if (!mes.isBlank())
                {
                    txtQuery.setError(mes);
                }
            }
            else
            {
                Log.d(LOG_TAG, "Загрузка Веб-ресурса завершена успешно");
                cardsAdapter.notifyDataSetChanged();
            }
        }
    }
    
    //endregion 'Типы'
    
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    /**
     * Демо-ключ неофициального API Книнопоиска
     *
     * @see <a href="https://kinopoiskapiunofficial.tech/">Kinopoisk Api Unofficial</a>
     *
     * @apiNote Этот ключ не имеет ограничений по количеству запросов в сутки, но имеет ограничение
     *      20 запросов в секунду.
     *
     * @implSpec В качестве альтернативы вы можете зарегистрироваться самостоятельно и получить
     *      собственный ключ, но тогда будет действовать ограничение в 500 запросов в день.
     */
    private static final String KINO_DEMO_API_KEY = "e30ffed0-76ab-4dd6-b41f-4c9da2b2735b";
    public static final String ADAPTER_KEY_TITLE = "Title";
    public static final String ADAPTER_CONTENT = "Content";
    private static final List<Map<String, String>> _cardList = new ArrayList<>(List.of(
            Map.of(
                    ADAPTER_KEY_TITLE, "Заголовок 1",
                    ADAPTER_CONTENT, "Данные 1"
            ),
            Map.of(
                    ADAPTER_KEY_TITLE, "Заголовок 2",
                    ADAPTER_CONTENT, "Данные 2"
            ),
            Map.of(
                    ADAPTER_KEY_TITLE, "Заголовок 3",
                    ADAPTER_CONTENT, "Данные 3"
            )
    ));
    
    private ProgressBar progBar;
    private FilmsApi filmsApi;
    
    /**
     * Обязательно пересоздавать задачу перед каждым вызовом!
     */
    private AsyncTask<String, Void, Void> downloadTask;
    private TextInputEditText txtQuery;
    private View androidContentView;
    private SimpleAdapter cardsAdapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.w(LOG_TAG, "start of onCreate function");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        isRus = Locale.getDefault().getLanguage().equalsIgnoreCase("ru");
        androidContentView = findViewById(android.R.id.content);
        progBar = findViewById(R.id.progress_bar);
        progBar.setVisibility(View.INVISIBLE);
        txtQuery = findViewById(R.id.txt_input);
        this.initViews();
        filmsApi = initFilmsApi();
        showPopular();
        Log.w(LOG_TAG, "end of onCreate function");
    }
    
    private FilmsApi initFilmsApi()
    {
        var api = new ApiClient();
        api.setApiKey(KINO_DEMO_API_KEY);
        return new FilmsApi(api);
    }
    
    
    /**
     * Метод настройки виджетов
     */
    private void initViews()
    {
        customToolBar = findViewById(R.id.top_toolbar);
        this.setSupportActionBar(customToolBar);
        final ListView lvCards = findViewById(R.id.card_list);
        cardsAdapter = new SimpleAdapter(getApplicationContext(), _cardList, R.layout.list_item,
                new String[]{ADAPTER_KEY_TITLE, ADAPTER_CONTENT}, new int[]{R.id.card_title, R.id.card_content});
        lvCards.setAdapter(cardsAdapter);
        txtQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE)
            {
                _cardList.clear();
                cardsAdapter.notifyDataSetChanged();
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
        downloadTask = new WebDataDownloadTask(filmsApi).execute(request);
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
                Log.d(LOG_TAG, String.format("Команда меню \"%s\"", item.getTitle()));
                showFavourites();
                break;
            }
            case R.id.action_switch_to_popular:
            {
                Log.d(LOG_TAG, String.format("Команда меню \"%s\"", item.getTitle()));
                showPopular();
                break;
            }
            default:
            {
                Log.w(LOG_TAG, "Неизвестная команда меню!");
                break;
            }
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void showPopular()
    {
        _cardList.clear();
        cardsAdapter.notifyDataSetChanged();
        customToolBar.setTitle(R.string.action_popular_title);
        this.getTopFilmsAsync();
    }
    
    private void showFavourites()
    {
        _cardList.clear();
        cardsAdapter.notifyDataSetChanged();
        customToolBar.setTitle(R.string.action_favourites_title);
    }
}