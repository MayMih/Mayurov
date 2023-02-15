package org.mmu.tinkoffkinolab;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.squareup.picasso.Picasso;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import io.swagger.client.ApiException;
import io.swagger.client.api.FilmsApi;
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation;

public class MainActivity extends AppCompatActivity {
    
    
    //region 'Типы'
    
    private enum ViewMode
    {
        NONE,
        FAVOURITES,
        POPULAR
    }
    
    private class WebDataDownloadTask extends AsyncTask<Void, Void, Void>
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
        protected Void doInBackground(Void... unused)
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
                _lastSnackBar = showSnackBar(UNKNOWN_WEB_ERROR_MES);
            }
            else
            {
                if (_lastSnackBar != null && _lastSnackBar.isShown())
                {
                    _lastSnackBar.dismiss();
                }
                Log.d(Constants.LOG_TAG, "Загрузка Веб-ресурса завершена успешно");
                fillFilmListUI();
            }
        }
    }
    
    //endregion 'Типы'
    
    
    
    //region 'Поля и константы'
    private static final List<Map<String, String>> _cardList = new ArrayList<>();
    private boolean isRus;
    private MaterialToolbar customToolBar;
    private Snackbar _lastSnackBar;
    private ProgressBar progBar;
    
    /**
     * Обязательно пересоздавать задачу перед каждым вызовом!
     */
    private AsyncTask<Void, Void, Void> downloadTask;
    private TextInputEditText txtQuery;
    private View androidContentView;
    private LinearLayout cardsContainer;
    private ViewMode currentViewMode;
    
    //endregion 'Поля и константы'
    
//    @ContentView
//    public MainActivity(@LayoutRes int id)
//    {}
    
    
    //region 'Обработчики'
    
    /**
     * При создании Экрана навешиваем обработчик обновления списка свайпом
     *
     * @param savedInstanceState If the activity is being re-initialized after
     *     previously being shut down then this Bundle contains the data it most
     *     recently supplied in {@link #onSaveInstanceState}.  <b><i>Note: Otherwise it is null.</i></b>
     *
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Log.w(Constants.LOG_TAG, "start of onCreate function");
        setContentView(R.layout.activity_main);
        isRus = Locale.getDefault().getLanguage().equalsIgnoreCase("ru");
        
        androidContentView = findViewById(android.R.id.content);
        progBar = findViewById(R.id.progress_bar);
        progBar.setVisibility(View.GONE);
        txtQuery = findViewById(R.id.txt_input);
        cardsContainer = findViewById(R.id.card_linear_lyaout);
        final SwipeRefreshLayout swr = findViewById(R.id.film_list_swipe_refresh_container);
        swr.setOnRefreshListener(() -> {
            try
            {
                refeshUIContent();
            }
            finally
            {
                swr.setRefreshing(false);
            }
        });
        this.initViews();
        
        showPopularFilmsAsync();
        Log.w(Constants.LOG_TAG, "end of onCreate function");
    }
    
    /**
     * @apiNote Почему-то не вызывается?!
     * @param savedInstanceState the data most recently supplied in {@link #onSaveInstanceState}.
     */
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState)
    {
        for (var filmEntry : savedInstanceState.getStringArrayList("FILM_LIST"))
        {
            final var entryMap = new HashMap<String, String>();
            String[] split = filmEntry.split("=");
            for (int i = 0; i < split.length; i++)
            {
                entryMap.put(split[i], split[++i]);
            }
            _cardList.add(entryMap);
        }
        super.onRestoreInstanceState(savedInstanceState);
    }
    
    /**
     * Обработчик сохранения скачанных данных
     * @param outState Bundle in which to place your saved state.
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState)
    {
        super.onSaveInstanceState(outState);
        final ArrayList<String> cardsData = new ArrayList<>(_cardList.size());
        for (var filmItem : _cardList)
        {
            cardsData.addAll(filmItem.entrySet().stream()
                    .map(x -> x.getKey() + "=" + x.getValue())
                    .collect(Collectors.toList()));
        }
        outState.putStringArrayList("FILM_LIST", cardsData);
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
        Log.d(Constants.LOG_TAG, String.format("Команда меню \"%s\"", item.getTitle()));
        switch (item.getItemId())
        {
            case R.id.action_switch_to_favorites:
            {
                showFavouriteFilms();
                break;
            }
            case R.id.action_switch_to_popular:
            {
                showPopularFilmsAsync();
                break;
            }
            case R.id.action_refresh:
            {
                refeshUIContent();
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
    
    //endregion 'Обработчики'
    
    
    
    //region 'Методы'
    
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
                getTopFilmsAsync();
                return false;
            }
            return true;
        });
    }
    
    private void refeshUIContent()
    {
        if (this.currentViewMode == ViewMode.POPULAR)
        {
            showPopularFilmsAsync();
        }
        else if (this.currentViewMode == ViewMode.FAVOURITES)
        {
            showFavouriteFilms();
        }
    }
    
    /**
     * Метод заполнения списка фильмов н основе сохранённых данных из {@link #_cardList}
     */
    private void fillFilmListUI()
    {
        final var results = new ArrayList<View>();
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < _cardList.size(); i++)
        {
            final var listItem = inflater.inflate(R.layout.list_item, null);
            final var cardData = _cardList.get(i);
            final var id = cardData.get(Constants.ADAPTER_FILM_ID);
            final var idField = ((TextView)listItem.findViewById(R.id.film_id_holder));
            idField.setText(id);
            final var title = cardData.get(Constants.ADAPTER_TITLE);
            ((TextView)listItem.findViewById(R.id.card_title)).setText(title);
            ((TextView)listItem.findViewById(R.id.card_content)).setText(cardData.get(Constants.ADAPTER_CONTENT));
            final var imgView = ((ImageView)listItem.findViewById(R.id.poster_preview));
            final var imageUrl = cardData.get(Constants.ADAPTER_POSTER_PREVIEW_URL);
            Picasso.get().load(imageUrl).transform(new RoundedCornersTransformation(30, 10)).into(imgView);
            cardsContainer.addView(listItem);
            listItem.setOnClickListener(v -> showFilmCardActivity(id, title));
        }
    }
    
    /**
     * Метод отображения всплывющей подсказки
     */
    private Snackbar showSnackBar(String message)
    {
        var popup = Snackbar.make(this.androidContentView, message, Snackbar.LENGTH_INDEFINITE);
        txtQuery.setEnabled(false);
        popup.setAction(R.string.repeat_button_caption, view -> {
            var text  = Objects.requireNonNull(txtQuery.getText()).toString().replace("null", "");
            this.getTopFilmsAsync();
            popup.dismiss();
            txtQuery.setEnabled(true);
        });
        popup.show();
        return popup;
    }
    
    /**
     * Получает данные по топ 100 фильмов (первые 20 записей) или по конкретному фильму (если request не пуст)
     */
    private void getTopFilmsAsync()
    {
        if (downloadTask != null && !downloadTask.isCancelled() && (downloadTask.getStatus() == AsyncTask.Status.RUNNING))
        {
            downloadTask.cancel(true);
        }
        downloadTask = new WebDataDownloadTask(FilmsApiHelper.getFilmsApi()).execute();
    }
    
    /**
     * Метод показа ТОП-100 популярных фильмов
     */
    private void showPopularFilmsAsync()
    {
        currentViewMode = ViewMode.POPULAR;
        clearLists();
        customToolBar.setTitle(R.string.action_popular_title);
        this.getTopFilmsAsync();
    }
    
    /**
     * Заготовка метода показа Избранных фильмов
     */
    private void showFavouriteFilms()
    {
        currentViewMode = ViewMode.FAVOURITES;
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
     * @param kinoApiFilmId ИД фильма в API кинопоиска
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
    
    //endregion 'Методы'
    
}