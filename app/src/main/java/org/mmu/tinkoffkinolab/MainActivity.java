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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
    private LayoutInflater layoutInflater;
    
    
    //region 'Типы'
    
    private enum ViewMode
    {
        NONE,
        FAVOURITES,
        POPULAR
    }
    
    private class WebDataDownloadTask extends AsyncTask<Integer, Void, Integer>
    {
        private static final int FILMS_COUNT_PER_PAGE = 20;
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
            progBar.setVisibility(View.VISIBLE);
            progBar.bringToFront();
            Log.d(Constants.LOG_TAG, "Начало загрузки веб-ресурса...");
        }
        
        @Override
        protected Integer doInBackground(Integer... pageNumber)
        {
            int res = 0;
            try
            {
                final var response = filmsApi.apiV22FilmsTopGet(Constants.TopFilmsType.TOP_100_POPULAR_FILMS.name(), pageNumber[0]);
                res = response.getPagesCount();
                for (var filmData : response.getFilms())
                {
                    if (isCancelled())
                    {
                        return null;
                    }
                    final var id = String.valueOf(filmData.getFilmId());
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
            return res;
        }

        @Override
        protected void onPostExecute(Integer pagesCount)
        {
            super.onPostExecute(pagesCount);
            progBar.setVisibility(View.GONE);
            if (error != null)
            {
                _lastSnackBar = showErrorSnackBar(UNKNOWN_WEB_ERROR_MES);
            }
            else
            {
                Log.d(Constants.LOG_TAG, "Загрузка Веб-ресурса завершена успешно");
                fillFilmListUI((_currentPageNumber - 1) * FILMS_COUNT_PER_PAGE);
                _topFilmsPagesCount = pagesCount;
                if (_currentPageNumber < _topFilmsPagesCount)
                {
                    _currentPageNumber++;
                }
                if (_lastSnackBar != null && _lastSnackBar.isShown())
                {
                    _lastSnackBar.dismiss();
                }
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
    private Integer _topFilmsPagesCount = 1;
    /**
     * Содержит номер страницы, которая будет запрошена
     *
     * @implSpec НЕ изменять - управляется классом {@link WebDataDownloadTask}
     */
    private int _currentPageNumber = 1;
    
    /**
     * Обязательно пересоздавать задачу перед каждым вызовом!
     */
    private AsyncTask<Integer, Void, Integer> downloadTask;
    private TextInputEditText txtQuery;
    private View androidContentView;
    private LinearLayout cardsContainer;
    private ViewMode _currentViewMode;
    
    //endregion 'Поля и константы'
    

    
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
    
        layoutInflater = getLayoutInflater();
        androidContentView = findViewById(android.R.id.content);
        progBar = findViewById(R.id.progress_bar);
        progBar.setVisibility(View.GONE);
        txtQuery = findViewById(R.id.txt_input);
        cardsContainer = findViewById(R.id.card_linear_lyaout);
        customToolBar = findViewById(R.id.top_toolbar);
        this.setSupportActionBar(customToolBar);
        
        this.setEventHandlers();
        
        showPopularFilmsAsync(true);
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
                showPopularFilmsAsync(true);
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
     * Метод настройки событий виджетов
     */
    private void setEventHandlers()
    {
        // обновляем страницу свайпом сверху
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
        // ищем текст по кнопке ВВОД на клавиатуре
        txtQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE)
            {
                Toast.makeText(this, "Фильтрация пока не реализована", Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        });
        // при прокрутке списка фильмов до конца подгружаем следующую страницу результатов (если есть)
        final ScrollView scroller = findViewById(R.id.card_scroll);
        scroller.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
        {
            boolean isBottomReached = cardsContainer.getBottom() - v.getBottom() - scrollY == 0;
            if (isBottomReached && this._currentViewMode == ViewMode.POPULAR && _currentPageNumber < _topFilmsPagesCount)
            {
                //Toast.makeText(this,"Загружаю данные, ожидайте...", Toast.LENGTH_SHORT).show();
                showPopularFilmsAsync(false);
            }
        });
    }
    
    /**
     * Метод перезагрузки содержимого страницы (списка фильмов)
     */
    private void refeshUIContent()
    {
        if (this._currentViewMode == ViewMode.POPULAR)
        {
            showPopularFilmsAsync(true);
        }
        else if (this._currentViewMode == ViewMode.FAVOURITES)
        {
            showFavouriteFilms();
        }
    }
    
    /**
     * Метод заполнения списка фильмов на основе сохранённых данных из {@link #_cardList}
     */
    private void fillFilmListUI(int startItemIndex)
    {
        for (int i = startItemIndex; i < _cardList.size(); i++)
        {
            final var listItem = layoutInflater.inflate(R.layout.list_item, null);
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
     * Метод отображения всплывющей подсказки с сообщением об ошибке и кнопкой "Повторить"
     */
    private Snackbar showErrorSnackBar(String message)
    {
        var popup = Snackbar.make(this.androidContentView, message, Snackbar.LENGTH_INDEFINITE);
        popup.setAction(R.string.repeat_button_caption, view -> {
            var text  = Objects.requireNonNull(txtQuery.getText()).toString().replace("null", "");
            this.startTopFilmsDownloadTask(_currentPageNumber);
            popup.dismiss();
        });
        popup.show();
        return popup;
    }
    
    /**
     * Получает данные топ 100 фильмов (20 записей на страницу)
     *
     * @apiNote Если задача уже запущена, то сначала отменяет её и запускает заново!
     */
    private void startTopFilmsDownloadTask(int pageNumber)
    {
        if (downloadTask != null && !downloadTask.isCancelled() && (downloadTask.getStatus() == AsyncTask.Status.RUNNING))
        {
            if (pageNumber > 1)
            {
                downloadTask.cancel(true);
            }
            else
            {
                Toast.makeText(this, "Ожидайте, идёт загрузка...", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        downloadTask = new WebDataDownloadTask(FilmsApiHelper.getFilmsApi()).execute(pageNumber);
    }
    
    /**
     * Метод показа ТОП-100 популярных фильмов
     *
     * @param isClearBeforeShow если True, текущий список очищается и показ начинается с первой страницы
     */
    private void showPopularFilmsAsync(boolean isClearBeforeShow)
    {
        if (isClearBeforeShow)
        {
            _currentPageNumber = 1;
            clearLists();
        }
        if (_currentViewMode != ViewMode.POPULAR)
        {
            _currentViewMode = ViewMode.POPULAR;
            customToolBar.setTitle(R.string.action_popular_title);
        }
        this.startTopFilmsDownloadTask(_currentPageNumber);
    }
    
    /**
     * Заготовка метода показа Избранных фильмов
     */
    private void showFavouriteFilms()
    {
        _currentViewMode = ViewMode.FAVOURITES;
        clearLists();
        customToolBar.setTitle(R.string.action_favourites_title);
    }
    
    private void clearLists()
    {
        _cardList.clear();
        cardsContainer.removeAllViews();
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