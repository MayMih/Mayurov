package org.mmu.tinkoffkinolab;

import static org.mmu.tinkoffkinolab.Constants.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.FragmentContainerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Debug;
import android.text.Editable;
import android.text.TextWatcher;
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
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.swagger.client.ApiException;
import io.swagger.client.api.FilmsApi;

public class MainActivity extends AppCompatActivity
{

    
    
    //region 'Типы'
    
    private enum ViewMode
    {
        NONE,
        FAVOURITES,
        POPULAR
    }
    
    private class WebDataDownloadTask extends AsyncTask<Integer, Void, Integer>
    {
        private static final String UNKNOWN_WEB_ERROR_MES = "Ошибка загрузки данных по сети:";
        private static final String KINO_API_ERROR_MES = "Ошибка API KinoPoisk";
        public static final int FILMS_COUNT_PER_PAGE = 20;
        private final FilmsApi filmsApi;
        private final ProgressBar refreshProgBar = findViewById(R.id.progress_bar_bottom);
        private Map.Entry<Exception, String> error;
        
        
        
        public WebDataDownloadTask(FilmsApi engine)
        {
            filmsApi = engine;
        }
        
        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
            if (_nextPageNumber == 1)
            {
                progBar.setVisibility(View.VISIBLE);
                progBar.bringToFront();
            }
            else
            {
                refreshProgBar.setVisibility(View.VISIBLE);
            }
            Log.d(LOG_TAG, "Начало загрузки веб-ресурса...");
        }
        
        @Override
        protected Integer doInBackground(Integer... pageNumber)
        {
            return downloadPopularFilmList(pageNumber[0]);
        }
    
        @Override
        protected void onPostExecute(Integer pagesCount)
        {
            super.onPostExecute(pagesCount);
            progBar.setVisibility(View.GONE);
            refreshProgBar.setVisibility(View.GONE);
            swipeRefreshContainer.setRefreshing(false);
            if (error != null)
            {
                lastSnackBar = showErrorSnackBar(UNKNOWN_WEB_ERROR_MES);
            }
            else
            {
                Log.d(LOG_TAG, "Загрузка Веб-ресурса завершена успешно");
                fillCardListUIFrom((_nextPageNumber - 1) * FILMS_COUNT_PER_PAGE, _cardList);
                _topFilmsPagesCount = pagesCount;
                // наращивать номер страницы можно ТОЛЬКО после успешной загрузки, иначе можно накрутить номер
                if (_nextPageNumber < _topFilmsPagesCount)
                {
                    _nextPageNumber++;
                }
                if (lastSnackBar != null && lastSnackBar.isShown())
                {
                    lastSnackBar.dismiss();
                }
            }
        }
    
        /**
         * Метод загрузки списка популярных Фильмов
         *
         * @param pageNumber Номер страницы, с которой надо начинать загрузку
         *
         * @return Общее Кол-во страниц на сервере или 0 (если операция была прервана пользователем)
         *
         * @apiNote Не выбрасывает исключений
         */
        private int downloadPopularFilmList(int pageNumber)
        {
            int res = 0;
            try
            {
                final var response = filmsApi.apiV22FilmsTopGet(
                        TopFilmsType.TOP_100_POPULAR_FILMS.name(),
                        pageNumber);
                res = response.getPagesCount();
                for (var filmData : response.getFilms())
                {
                    if (isCancelled())
                    {
                        return 0;
                    }
                    final var id = String.valueOf(filmData.getFilmId());
                    Optional<String> name = Optional.ofNullable(isRus ? filmData.getNameRu() : filmData.getNameEn());
                    _cardList.add(Map.of(Constants.ADAPTER_FILM_ID, id,
                            Constants.ADAPTER_TITLE, name.orElse(id),
                            Constants.ADAPTER_CONTENT, filmData.getGenres().get(0).getGenre() +
                                    " (" + filmData.getYear() + ")",
                            Constants.ADAPTER_POSTER_PREVIEW_URL, filmData.getPosterUrlPreview())
                    );
                }
            }
            catch (RuntimeException ex)
            {
                var mes = Objects.requireNonNullElse(ex.getMessage(), "");
                error = new AbstractMap.SimpleEntry<>(ex, mes);
                if (ex instanceof ApiException)
                {
                    var apiEx = (ApiException) ex;
                    var headers = apiEx.getResponseHeaders();
                    var headersText = headers == null ? "" : headers.entrySet().stream().map(
                            entry -> entry.getKey() + ": " + String.join(" \n", entry.getValue())
                    ).collect(Collectors.joining());
                    mes += String.format(Locale.ROOT, " %s (ErrorCode: %d), ResponseHeaders: \n%s\n ResponseBody: \n%s\n",
                            KINO_API_ERROR_MES, apiEx.getCode(), headersText, apiEx.getResponseBody());
                }
                Log.e(LOG_TAG, mes.isEmpty() ? UNKNOWN_WEB_ERROR_MES : mes, ex);
            }
            return res;
        }
    
    
    }
    
    //endregion 'Типы'
    
    
    
    //region 'Поля и константы'
    private static final List<Map<String, String>> _cardList = new ArrayList<>();
    private static ViewMode _currentViewMode;
    /**
     * Содержит номер страницы, которая будет запрошена
     *
     * @implSpec НЕ изменять - управляется классом {@link WebDataDownloadTask}
     */
    private static int _nextPageNumber = 1;
    private static int _topFilmsPagesCount = 1;
    
    public File favouritesListFilePath;
    private File imageCacheDirPath;
    private boolean isRus;
    private MaterialToolbar customToolBar;
    private Snackbar lastSnackBar;
    private ProgressBar progBar;
    private CardView inputPanel;
    private SwipeRefreshLayout swipeRefreshContainer;
    /**
     * @apiNote Обязательно пересоздавать задачу перед каждым вызовом!
     */
    private AsyncTask<Integer, Void, Integer> downloadTask;
    private TextInputEditText txtQuery;
    private View coordinatorView;
    private LinearLayout cardsContainer;
    private LayoutInflater layoutInflater;
    private boolean isFiltered;
    private int lastListViewPos;
    private int lastListViewPos2;
    private View scroller;
    private boolean isLandscape;
    private CardFragment detailsFragment;
    private LinearLayout horizontalContainer;
    
    //endregion 'Поля и константы'
    
    
    
    //region 'Свойства'
    
    private static Map<String, Map<String, String>> favouritesMap;
    
    /**
     * Возвращает список избранных фильмов
     *
     * @implSpec ВНИМАНИЕ: ИД фильма должен идти первой строчкой, иначе метод загрузки из файла не будет
     * работать корректно ({@link #loadFavouritesList()})
     */
    public Map<String, Map<String, String>> getFavouritesMap()
    {
        if (favouritesMap == null)
        {
            favouritesMap = new HashMap<>();
        }
        return favouritesMap;
    }
    
    private AlertDialog confirmClearDialog;
    
    public AlertDialog getConfirmClearDialog()
    {
        if (confirmClearDialog == null)
        {
            confirmClearDialog = new AlertDialog.Builder(this)
                    .setIcon(R.drawable.round_warning_amber_24)
                    .setTitle(R.string.confirm_dialog_title).setMessage(R.string.confirm_clear_dialog_text)
                    .setNegativeButton(android.R.string.no, null)
                    .setPositiveButton(android.R.string.yes, (dialog, which) ->
                        clearList(false)
                    )
                    .create();
        }
        return confirmClearDialog;
    }
    
    
    private TextWatcher searchTextChangeWatcher;
    
    /**
     * Обработчик изменения текста в виджете Поиска
     */
    @NonNull
    private TextWatcher getSearchTextChangeWatcher()
    {
        if (this.searchTextChangeWatcher == null)
        {
            this.searchTextChangeWatcher = new TextWatcher()
            {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after)
                {
                }
                
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count)
                {
                }
                
                @Override
                public void afterTextChanged(Editable s)
                {
                    final var query = s.toString();
                    if (query.isBlank())
                    {
                        showFilmCardsUI();
                    }
                    else
                    {
                        filterCardListUI(s.toString(), getCurrentFilmListStream());
                    }
                }
            };
        }
        return searchTextChangeWatcher;
    }
    
    //endregion 'Свойства'
    
    
    
    //region 'Обработчики'
    
    /**
     * При создании Экрана навешиваем обработчик обновления списка свайпом
     *
     * @apiNote Вызывается при запуске программы, а также, если процесс был вытеснен из памяти
     *
     * @param savedInstanceState If the activity is being re-initialized after
     *                           previously being shut down then this Bundle contains the data it most
     *                           recently supplied in {@link #onSaveInstanceState}.  <b><i>Note: Otherwise it is null.</i></b>
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Log.d(LOG_TAG, "---------------- In the onCreate() method");
        setContentView(R.layout.activity_main);
        customToolBar = findViewById(R.id.top_toolbar);
        this.setSupportActionBar(customToolBar);
        // восстанавливаем список избранного из файла
        this.favouritesListFilePath = new File(this.getFilesDir(), FAVOURITES_LIST_FILE_NAME);
        if (getFavouritesMap().isEmpty() && this.favouritesListFilePath.exists())
        {
            loadFavouritesList();
            Log.d(LOG_TAG, "Список избранного загружен из файла:\n " + this.favouritesListFilePath);
        }
        // закруглённые углы для верхней панели
        Utils.setRoundedBottomToolbarStyle(customToolBar, 25);
        if (Debug.isDebuggerConnected())
        {
            Picasso.get().setIndicatorsEnabled(true);
            //Picasso.get().setLoggingEnabled(true);
        }
        this.isRus = Locale.getDefault().getLanguage().equalsIgnoreCase("ru");
    
        this.layoutInflater = getLayoutInflater();
        this.coordinatorView = findViewById(R.id.root_container);
        this.progBar = findViewById(R.id.progress_bar);
        this.progBar.setVisibility(View.GONE);
        this.txtQuery = findViewById(R.id.txt_input);
        this.cardsContainer = findViewById(R.id.card_linear_lyaout);
        this.inputPanel = findViewById(R.id.input_panel);
        //inputPanel.setBackgroundResource(R.drawable.rounded_bottom_shape);
        this.swipeRefreshContainer = findViewById(R.id.film_list_swipe_refresh_container);
        this.swipeRefreshContainer.setColorSchemeResources(R.color.biz, R.color.neo, R.color.neo_dark, R.color.purple_light);
        this.imageCacheDirPath = new File(this.getCacheDir(), Constants.FAVOURITES_CASH_DIR_NAME);
        this.scroller = findViewById(R.id.card_scroller);
        this.isLandscape = this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        this.horizontalContainer = findViewById(R.id.horizontal_view);
    
        this.setEventHandlers();
        
        if (_cardList.isEmpty())
        {
            switchUIToPopularFilmsAsync(true, true);
        }
        Log.d(LOG_TAG, "Exit of onCreate() method ---------------------");
    }
    
    /**
     * @apiNote Этот метод всегда вызывается после {@link #onCreate(Bundle)}, а также после
     *      {@link #onRestart()}, например после нажатия кнопки назад на Activity вызванной из этой.
     */
    @Override
    protected void onStart()
    {
        super.onStart();
        Log.d(LOG_TAG, "---------------- In the onStart() method");
        this.horizontalContainer.setWeightSum(1);
        Log.w(LOG_TAG, "Exit of onStart() method ---------------------");
    }
    
    /**
     * Метод восстановления состояния Активити - Вызывается после {@link #onStart()}
     *
     * @param savedInstanceState the data most recently supplied in {@link #onSaveInstanceState}.
     *
     */
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState)
    {
        super.onRestoreInstanceState(savedInstanceState);
        // обновляем заголовок в Тулбаре
        List<Map<String, String>> sourceList = null;
        if (_currentViewMode == ViewMode.FAVOURITES)
        {
            switchUIToFavouriteFilms(false);
            sourceList = new ArrayList<>(getFavouritesMap().values());
        }
        else if (_currentViewMode == ViewMode.POPULAR)
        {
            switchUIToPopularFilmsAsync(false, false);
            sourceList = _cardList;
        }
        fillCardListUIFrom((_nextPageNumber - 2) * WebDataDownloadTask.FILMS_COUNT_PER_PAGE,
                Objects.requireNonNull(sourceList));
        final String query = Objects.requireNonNull(txtQuery.getText()).toString();
        if (!query.isBlank())
        {
            filterCardListUI(query, this.getCurrentFilmListStream());
        }
        Log.d(LOG_TAG, "---------------------- состояние восстановлено! ----------------");
    }
    
    /**
     * Обработчик поворота экрана
     *
     * @param newConfig The new device configuration.
     */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
        {
            isLandscape = true;
            if (Debug.isDebuggerConnected())
            {
                Toast.makeText(this, "landscape", Toast.LENGTH_SHORT).show();
            }
        }
        else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT)
        {
            isLandscape = false;
            if (Debug.isDebuggerConnected())
            {
                Toast.makeText(this, "portrait", Toast.LENGTH_SHORT).show();
            }
        }
        onScreenRotate();
    }
    
    @Override
    protected void onResume()
    {
        super.onResume();
        {
            onScreenRotate();
        }
    }
    
    private void onScreenRotate()
    {
        final var isBlank = Objects.requireNonNull(txtQuery.getText()).toString().isBlank();
        this.inputPanel.setVisibility(this.isLandscape && isBlank ? View.GONE : View.VISIBLE);
        FragmentContainerView fw = findViewById(R.id.details_view);
        if (!this.isLandscape)
        {
            for (var fragment : getSupportFragmentManager().getFragments()) {
                if (fragment instanceof CardFragment) {
                    getSupportFragmentManager().beginTransaction().remove(fragment).commit();
                }
            }
            this.horizontalContainer.setWeightSum(1);
        }
    }
    
    /**
     * Обработчик сохранения состояния программы - если список Избранного пуст, то удаляет кеш изображений
     *
     * @param outState Bundle in which to place your saved state.
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState)
    {
        super.onSaveInstanceState(outState);
        // TODO: Нужно как-то определять, когда происходит выход из программы. а не поворот, чтобы
        // не перезаписывать файл при каждом повороте - если же это невозможно, то лучше наверно будет
        // обновлять файл избранного не при переключении Экранов, а при изменении списка Избранного,
        // т.к. по идее это должно происходить реже.
        if (favouritesMap == null || favouritesMap.isEmpty())
        {
            this.deleteFile(Constants.FAVOURITES_LIST_FILE_NAME);
            if (this.imageCacheDirPath.exists())
            {
                //noinspection ResultOfMethodCallIgnored
                Arrays.stream(Objects.requireNonNull(this.imageCacheDirPath.listFiles())).parallel()
                        .forEach(File::delete);
            }
        }
        else
        {
            saveFavouritesList();
        }
        Log.d(LOG_TAG, "-------------------------- состояние сохранено! ----------------");
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
        Log.d(LOG_TAG, String.format("Команда меню \"%s\"", item.getTitle()));
        switch (item.getItemId())
        {
            case R.id.action_switch_to_favorites:
            {
                switchUIToFavouriteFilms();
                break;
            }
            case R.id.action_switch_to_popular:
            {
                switchUIToPopularFilmsAsync(false, false);
                break;
            }
            case R.id.action_refresh:
            {
                refreshUIContent();
                break;
            }
            case R.id.action_go_to_top:
            {
                // N.B. этот код достаточно надёжен, т.к. внутри SwipeRefreshContainer может быть только
                // что-то типа ScrollView
                swipeRefreshContainer.getChildAt(0).scrollTo(0, 0);
                break;
            }
            case R.id.action_clear_list:
            {
                if (_currentViewMode == ViewMode.FAVOURITES && !favouritesMap.isEmpty())
                {
                    this.getConfirmClearDialog().show();
                }
                else
                {
                    clearList(false);
                }
                break;
            }
            case R.id.action_show_search_bar:
            {
                this.inputPanel.setVisibility(this.inputPanel.getVisibility() == View.VISIBLE ?
                        View.GONE : View.VISIBLE);
                break;
            }
            default:
            {
                Log.w(LOG_TAG, "Неизвестная команда меню - действие не назначено!");
                break;
            }
        }
        return super.onOptionsItemSelected(item);
    }
    
    
    @NonNull
    private View.OnClickListener getOnLikeButtonClickListener(Map<String, String> cardData, String id,
                                                              ImageView sourceView, ImageView likeButton)
    {
        return (View v) -> {
            if (addToOrRemoveFromFavourites(id, cardData, sourceView.getDrawable()))
            {
                if (v != likeButton)
                {
                    Toast.makeText(this, "Фильм добавлен в Избранное", Toast.LENGTH_SHORT).show();
                }
                likeButton.setImageResource(R.drawable.baseline_favorite_24);
            }
            else
            {
                if (v != likeButton)
                {
                    Toast.makeText(this, "Фильм удалён из списка Избранных", Toast.LENGTH_SHORT).show();
                }
                likeButton.setImageResource(R.drawable.baseline_favorite_border_24);
            }
        };
    }
    
    /**
     * Обработчик прокрутки списка фильмов до конца - подгружает новую "страницу" в конец списка топов
     */
    private void onScrollChange(@NonNull View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY)
    {
        final boolean isBottomReached = cardsContainer.getBottom() - v.getBottom() - scrollY == 0;
        if (isBottomReached && !isFiltered)
        {
            if (_currentViewMode == ViewMode.POPULAR && _nextPageNumber < _topFilmsPagesCount)
            {
                switchUIToPopularFilmsAsync(false, true);
            }
        }
        else if (scrollY > 20)
        {
            this.inputPanel.setCardElevation(10 * getResources().getDisplayMetrics().density);
        }
        else if (scrollY == 0)
        {
            this.inputPanel.setCardElevation(0);
        }
    }
    

    
    /**
     * @return Возвращает нужный поток Фильмов в зависимости от выбранной вкладки UI
     */
    private Stream<Map<String, String>> getCurrentFilmListStream()
    {
        return _currentViewMode == ViewMode.FAVOURITES ?
                getFavouritesMap().values().stream() : _currentViewMode == ViewMode.POPULAR ?
                _cardList.stream() : Stream.empty();
    }
    
    /**
     * Обработчик клика на элемент списка (карточку Фильма) - открывает окно с подробным описанием Фильма.
     *
     * @param id    ИД Фильма в API Kinopoisk
     * @param title Название (будет отображаться в заголовке карточки до тех пор, пока не будет загружено подробное описание)
     */
    @NonNull
    private View.OnClickListener getOnListItemClickListener(String id, String title)
    {
        return (View v) -> {
            if (this.isLandscape)
            {
                if (this.detailsFragment == null)
                {
                    this.detailsFragment = new CardFragment();
                    getSupportFragmentManager().beginTransaction().add(R.id.details_view, this.detailsFragment)
                            .setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out)
                            .runOnCommit(() ->
                                detailsFragment.getFilmDataAsync(id, title)).commit();
                }
                else
                {
                    detailsFragment.getFilmDataAsync(id, title);
                }
                horizontalContainer.setWeightSum(2);
            }
            else
            {
                showFilmCardActivity(id, title);
            }
        };
    }
    
    /**
     * Обработчик скрытия панели Поиска - очищает поле ввода поискового запроса
     */
    private void onSearchBarVisibleChanged()
    {
        if (this.inputPanel.getVisibility() == View.GONE)
        {
            Log.d(LOG_TAG, "Поле поиска скрыто - очищаю ввод!");
            Objects.requireNonNull(txtQuery.getText()).clear();
            this.customToolBar.setElevation(10 * getResources().getDisplayMetrics().density);
        }
        else
        {
            this.customToolBar.setElevation(0);
        }
    }
    
    //endregion 'Обработчики'
    
    
    
    //region 'Методы'
    
    /**
     * Метод настройки событий виджетов
     */
    private void setEventHandlers()
    {
        // обновляем страницу свайпом сверху
        this.swipeRefreshContainer.setOnRefreshListener(this::refreshUIContent);
        // ищем текст по кнопке ВВОД на клавиатуре
        this.txtQuery.setOnEditorActionListener((v, actionId, event) -> {
            // N.B. Похоже, только для действия Done можно реализовать авто скрытие клавиатуры - при
            //      остальных клава остаётся на экране после клика
            return actionId != EditorInfo.IME_ACTION_DONE && actionId != EditorInfo.IME_ACTION_GO &&
                    actionId != EditorInfo.IME_ACTION_SEARCH;
        });
        // Обработчик изменения текста в поисковом контроле
        this.txtQuery.addTextChangedListener(getSearchTextChangeWatcher());
        // при прокрутке списка фильмов до конца подгружаем следующую страницу результатов (если есть)
        this.scroller.setOnScrollChangeListener(this::onScrollChange);
        // при скрытии панели Поиска очищаем текст фильтра
        this.inputPanel.getViewTreeObserver().addOnGlobalLayoutListener(this::onSearchBarVisibleChanged);
    }
    
    /**
     * Метод показа (вывода из невидимости) всех карточек фильмов
     *
     * @apiNote Вызов имеет смысл, только если перед этим был вызов {@link #filterCardListUI(String, Stream)} )}
     * @implNote Сбрасывает признак фильтрации списка {@link #isFiltered}
     */
    private void showFilmCardsUI()
    {
        if (isFiltered)
        {
            for (int i = 0; i < cardsContainer.getChildCount(); i++)
            {
                cardsContainer.getChildAt(i).setVisibility(View.VISIBLE);
            }
        }
        isFiltered = false;
    }
    
    /**
     * Метод фильтрации списка Фильмов на экране
     *
     * @param query строка для поиска любого из слов названия фильма
     * @apiNote Метод не выдаёт совпадения для слов короче 3-х символов (для исключения предлогов)
     * @implSpec При пустом <code>query</code> не делает НИЧЕГО - для отмены фильтра используйте {@link #showFilmCardsUI()}
     */
    private void filterCardListUI(String query, Stream<Map<String, String>> cardStream)
    {
        if (query.isBlank())
        {
            return;
        }
        final var foundFilms = cardStream.filter(x -> {
                    final var words = Arrays.stream(Objects.requireNonNull(x.get(Constants.ADAPTER_TITLE)).split(" "));
                    return words.anyMatch(t -> t.length() > 2 && t.toLowerCase().startsWith(query.strip().toLowerCase()));
                })
                .collect(Collectors.toList());
        
        for (int i = 0; i < cardsContainer.getChildCount(); i++)
        {
            final var filmCardView = cardsContainer.getChildAt(i);
            if (filmCardView.getId() == R.id.film_card_view)
            {
                final var txtFilmId = filmCardView.findViewById(R.id.film_id_holder);
                if (txtFilmId instanceof TextView)
                {
                    final var filmId = ((TextView) txtFilmId).getText().toString();
                    if (foundFilms.stream().noneMatch(film -> Objects.requireNonNull(
                            film.get(Constants.ADAPTER_FILM_ID)).equalsIgnoreCase(filmId)))
                    {
                        filmCardView.setVisibility(View.GONE);
                    }
                    else
                    {
                        filmCardView.setVisibility(View.VISIBLE);
                    }
                }
            }
        }
        isFiltered = true;
    }
    
    /**
     * Метод перезагрузки содержимого страницы (списка фильмов)
     */
    private void refreshUIContent()
    {
        Objects.requireNonNull(txtQuery.getText()).clear();
        if (_currentViewMode == ViewMode.POPULAR)
        {
            switchUIToPopularFilmsAsync(true, true);
        }
        else if (_currentViewMode == ViewMode.FAVOURITES)
        {
            cardsContainer.setVisibility(View.INVISIBLE);
            switchUIToFavouriteFilms(true);
            // добавляем задержку, чтобы юзер мог четко определить замену списка на новый
            cardsContainer.postDelayed(() -> {
                swipeRefreshContainer.setRefreshing(false);
                cardsContainer.setVisibility(View.VISIBLE);
            }, 100);
        }
    }
    
    /**
     * Метод заполнения списка фильмов на основе сохранённых данных из {@link #_cardList}
     *
     * @implNote Также навешивает обработчики на вновь созданные элементы списка
     */
    private void fillCardListUIFrom(int startItemIndex, List<Map<String, String>> cardList)
    {
        for (int i = startItemIndex; i < cardList.size(); i++)
        {
            @SuppressLint("InflateParams")
            final var listItem = layoutInflater.inflate(R.layout.list_item, null);
            final var cardData = cardList.get(i);
            final var id = cardData.get(Constants.ADAPTER_FILM_ID);
            ((TextView) listItem.findViewById(R.id.film_id_holder)).setText(id);
            final var title = cardData.get(Constants.ADAPTER_TITLE);
            ((TextView) listItem.findViewById(R.id.card_title)).setText(title);
            ((TextView) listItem.findViewById(R.id.card_content)).setText(cardData.get(ADAPTER_CONTENT));
            final var imgView = ((ImageView) listItem.findViewById(R.id.poster_preview));
            final var imageUrl = cardData.get(Constants.ADAPTER_POSTER_PREVIEW_URL);
            final var cachedImageFilePath = cardData.get(ADAPTER_IMAGE_PREVIEW_FILE_PATH);
            File previewImageFile = null;
            if (cachedImageFilePath != null && !cachedImageFilePath.isBlank())
            {
                previewImageFile = new File(cachedImageFilePath);
            }
            if (previewImageFile == null)
            {
                Picasso.get().load(imageUrl).transform(ROUNDED_CORNERS_TRANSFORMATION).into(imgView);
            }
            else
            {
                if (previewImageFile.exists())
                {
                    imgView.setImageURI(Uri.fromFile(previewImageFile));
                    Log.d(LOG_TAG, "Картинка загружена из файла: " + cachedImageFilePath);
                }
                else
                {
                    restoreImageCacheFromURL(imgView, imageUrl, cachedImageFilePath);
                    Log.d(LOG_TAG, "Промах кэша - видимо файл был удалён - картинка будет загружена из Сети:\n " +
                            cachedImageFilePath);
                }
            }
            cardsContainer.addView(listItem);
            listItem.setOnClickListener(this.getOnListItemClickListener(id, title));
            final ImageView imgViewLike = listItem.findViewById(R.id.like_image_view);
            // TODO: возможно фильм нужно искать по названию, а не по ИД, на случай, если ИД поменяется?
            if (getFavouritesMap().containsKey(id))
            {
                imgViewLike.setImageResource(R.drawable.baseline_favorite_24);
            }
            final var likeButtonClickHandler = this.getOnLikeButtonClickListener(
                    cardData, id, imgView, imgViewLike);
            imgViewLike.setOnClickListener(likeButtonClickHandler);
            // Требование ТЗ: "При длительном клике на карточку, фильм помещается в избранное"
            listItem.setOnLongClickListener(v -> {
                likeButtonClickHandler.onClick(v);
                return true;
            });
        }
    }
    
    private void restoreImageCacheFromURL(ImageView imgView, String imageUrl, String cachedImageFilePath)
    {
        Picasso.get().load(imageUrl).transform(ROUNDED_CORNERS_TRANSFORMATION)
                .into(imgView, new Callback()
                {
                    @Override
                    public void onSuccess()
                    {
                        //TODO: без задержки, картинки не успевают прорисоваться, но слишком большая задержка,
                        //  тоже опасна - юзер уже мог переключить представление (хотя объект не должен уничтожаться).
                        imgView.postDelayed(() -> Utils.extractImageToDiskCache(imgView, cachedImageFilePath), 300);
                    }
                    
                    @Override
                    public void onError(Exception e)
                    {
                        Log.e(LOG_TAG, "Ошибка загрузки мини постера фильма по адресу:\n " + imageUrl, e);
                    }
                });
    }
    
    /**
     * Метод добавления фильма в список Избранного (или удаления из него)
     *
     * @param id       ИД фильма - для быстрого поиска в списке
     * @param cardData Список данных о фильме (пары ключ-значение)
     * @param image    миниПостер фильма для сохранения в кэше (по ТЗ)
     * @return True - фильм добавлен в список Избранного, False - удалён из него.
     * @implSpec TODO: Возможно стоит уводить метод в отдельный поток, на случай если запись файла будет длиться дольше 5 секунд!
     */
    private boolean addToOrRemoveFromFavourites(String id, Map<String, String> cardData, Drawable image)
    {
        if (!this.imageCacheDirPath.exists() && !this.imageCacheDirPath.mkdir())
        {
            Log.w(LOG_TAG, "Ошибка создания подкаталога для кэша постеров к фильмам");
        }
        final var imgPreviewFilePath = new File(this.imageCacheDirPath, "preview_" + id + ".webp");
        
        if (this.getFavouritesMap().containsKey(id))
        {
            if (!imgPreviewFilePath.delete())
            {
                Log.w(LOG_TAG, "Не удалось удалить файл " + imgPreviewFilePath);
            }
            this.getFavouritesMap().remove(id);
            return false;
        }
        // Доп. требование ТЗ - "карточки избранных фильмов должны быть доступны офлайн"
        // малый постер
        try (var outStream = new FileOutputStream(imgPreviewFilePath))
        {
            Utils.convertDrawableToBitmap(image).compress(Bitmap.CompressFormat.WEBP, 80, outStream);
        }
        catch (IOException e)
        {
            Log.e(LOG_TAG, "Ошибка записи в файл", e);
            Toast.makeText(this, "Что-то пошло не так: Ошибка записи в файл", Toast.LENGTH_SHORT).show();
        }
        final var curData = new ArrayList<>(cardData.entrySet());
        curData.add(Map.entry(Constants.ADAPTER_IMAGE_PREVIEW_FILE_PATH, imgPreviewFilePath.toString()));
        @SuppressWarnings("unchecked") final Map<String, String> filmData = Map.ofEntries(curData.toArray(new Map.Entry[0]));
        this.getFavouritesMap().put(id, filmData);
        return true;
    }
    
    /**
     * Метод отображения всплывющей подсказки с сообщением об ошибке и кнопкой "Повторить"
     */
    private Snackbar showErrorSnackBar(String message)
    {
        var popup = Snackbar.make(coordinatorView, message, Snackbar.LENGTH_INDEFINITE);
        popup.setAction(R.string.repeat_button_caption, view -> {
            var text = Objects.requireNonNull(txtQuery.getText()).toString().replace("null", "");
            this.startTopFilmsDownloadTask(_nextPageNumber);
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
     * Метод показа ТОП-100 популярных фильмов - асинхронно загружает список фильмов из Сети
     *
     * @param isBeginFromPageOne если True, текущий список очищается и показ начинается с первой страницы
     */
    private void switchUIToPopularFilmsAsync(boolean isBeginFromPageOne, boolean isDownloadNew)
    {
        customToolBar.setTitle(R.string.action_popular_title);
        if (_currentViewMode == ViewMode.POPULAR && !isDownloadNew)
        {
            return;
        }
        _currentViewMode = ViewMode.POPULAR;
        lastListViewPos2 = scroller.getScrollY();
        
        if (isBeginFromPageOne && isDownloadNew)
        {
            clearList(false);
        }
        else if (!isDownloadNew)
        {
            clearList(!isBeginFromPageOne);
        }
        if (isBeginFromPageOne)
        {
            _nextPageNumber = 1;
        }
        if (isDownloadNew)
        {
            this.startTopFilmsDownloadTask(_nextPageNumber);
        }
        else
        {
            fillCardListUIFrom(0, _cardList);
            final var query = Objects.requireNonNull(txtQuery.getText()).toString();
            if (query.isBlank() && lastListViewPos > 0)
            {
                // на основе кода отсюда: https://stackoverflow.com/a/3263540/2323972
                scroller.post(() -> scroller.scrollTo(0, lastListViewPos));
            }
            else
            {
                filterCardListUI(query, _cardList.stream());
            }
        }
    }
    
    private void switchUIToFavouriteFilms()
    {
        switchUIToFavouriteFilms(false);
    }
    
    /**
     * Метода показа Избранных фильмов
     */
    private void switchUIToFavouriteFilms(boolean forceRefresh)
    {
        customToolBar.setTitle(R.string.action_favourites_title);
        if (!forceRefresh && _currentViewMode == ViewMode.FAVOURITES)
        {
            return;
        }
        if (!forceRefresh)
        {
            lastListViewPos = scroller.getScrollY();
        }
        _currentViewMode = ViewMode.FAVOURITES;
        clearList(true);
        if (!getFavouritesMap().isEmpty())
        {
            fillCardListUIFrom(0, new ArrayList<>(getFavouritesMap().values()));
            final var query = Objects.requireNonNull(txtQuery.getText()).toString();
            if (!query.isBlank())
            {
                filterCardListUI(query, getFavouritesMap().values().stream());
            }
            else if (!forceRefresh)
            {
                scroller.post(() -> scroller.scrollTo(0, lastListViewPos2));
            }
        }
    }
    
    private void clearList(boolean uiOnly)
    {
        if (!uiOnly)
        {
            if (_currentViewMode == ViewMode.FAVOURITES)
            {
                favouritesMap.clear();
            }
            else if (_currentViewMode == ViewMode.POPULAR)
            {
                _cardList.clear();
            }
        }
        cardsContainer.removeAllViews();
    }
    
    /**
     * Метод октрытия окна деталей о фильме
     *
     * @param kinoApiFilmId ИД фильма в API кинопоиска
     * @param cardTitle     Желаемый заголовок карточки
     */
    private void showFilmCardActivity(String kinoApiFilmId, String cardTitle)
    {
        final var switchToCardActivityIntent = new Intent(getApplicationContext(), CardActivity.class);
        switchToCardActivityIntent.putExtra(Constants.ADAPTER_TITLE, cardTitle);
        switchToCardActivityIntent.putExtra(Constants.ADAPTER_FILM_ID, kinoApiFilmId);
        final var extraData = new Bundle(2);
        extraData.clear();
        extraData.putString(Constants.ADAPTER_TITLE, cardTitle);
        startActivity(switchToCardActivityIntent);
    }
    
    /**
     * Метод загрузки списка Избранных фильмов из файла {@link Constants#FAVOURITES_LIST_FILE_NAME}
     */
    private void loadFavouritesList()
    {
        final var tempValuesMap = new HashMap<String, String>();
        try (var fr = new BufferedReader(new FileReader(this.favouritesListFilePath));
             var fileLines = fr.lines())
        {
            fileLines.forEach(line -> {
                if (line.equalsIgnoreCase(FILM_END_TOKEN))
                {
                    getFavouritesMap().put(tempValuesMap.get(ADAPTER_FILM_ID), Map.copyOf(tempValuesMap));
                    tempValuesMap.clear();
                }
                else if (!line.equalsIgnoreCase(FILM_START_TOKEN))
                {
                    final var pair = line.split(KEY_VALUE_SEPARATOR);
                    tempValuesMap.put(pair[0], pair[1]);
                }
            });
        }
        catch (IOException | RuntimeException e)
        {
            Log.e(LOG_TAG, "Ошибка чтения файла: " + FAVOURITES_LIST_FILE_NAME, e);
            Toast.makeText(this, "Не удалось прочитать файл Избранного!", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Метод записи файла со списком Избранного (путь: {@link Constants#FAVOURITES_LIST_FILE_NAME})
     */
    private void saveFavouritesList()
    {
        try (BufferedWriter fw = new BufferedWriter(new FileWriter(this.favouritesListFilePath)))
        {
            favouritesMap.forEach((id, filmData) -> {
                try
                {
                    fw.write(Constants.FILM_START_TOKEN);
                    fw.write(System.lineSeparator());
                    for (var entry : filmData.entrySet())
                    {
                        fw.write(entry.getKey());
                        fw.write(KEY_VALUE_SEPARATOR);
                        fw.write(entry.getValue());
                        fw.write(System.lineSeparator());
                    }
                    fw.write(Constants.FILM_END_TOKEN);
                    fw.write(System.lineSeparator());
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            });
        }
        catch (IOException | RuntimeException e)
        {
            Log.e(LOG_TAG, "Ошибка записи файла: " + this.favouritesListFilePath.toString(), e);
        }
    }
    
    //endregion 'Методы'
    
    
}