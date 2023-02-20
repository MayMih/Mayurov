package org.mmu.tinkoffkinolab;

import static org.mmu.tinkoffkinolab.Constants.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.Intent;
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
import com.squareup.picasso.Picasso;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
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
            Log.d(LOG_TAG, "Начало загрузки веб-ресурса...");
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
                    _cardList.add(Map.of(Constants.ADAPTER_FILM_ID, id,
                            Constants.ADAPTER_TITLE, name.orElse(id),
                            Constants.ADAPTER_CONTENT, filmData.getGenres().get(0).getGenre() + " (" + filmData.getYear() + ")",
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
        
        @Override
        protected void onPostExecute(Integer pagesCount)
        {
            super.onPostExecute(pagesCount);
            progBar.setVisibility(View.GONE);
            swipeRefreshContainer.setRefreshing(false);
            if (error != null)
            {
                _lastSnackBar = showErrorSnackBar(UNKNOWN_WEB_ERROR_MES);
            }
            else
            {
                Log.d(LOG_TAG, "Загрузка Веб-ресурса завершена успешно");
                fillCardListUIFrom((_currentPageNumber - 1) * FILMS_COUNT_PER_PAGE, _cardList);
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
    public TextWatcher _textWatcher;
    public File _favouritesListFilePath;
    private File _imageCacheDirPath;
    
    private boolean isRus;
    private MaterialToolbar customToolBar;
    private Snackbar _lastSnackBar;
    private ProgressBar progBar;
    private CardView inputPanel;
    private SwipeRefreshLayout swipeRefreshContainer;
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
    private LayoutInflater _layoutInflater;
    private boolean _isFiltered;
    private int _lastListViewPos;
    private int _lastListViewPos2;
    
    private View scroller;
    
    //endregion 'Поля и константы'
    
    
    
    
    //region 'Свойства'
    
    private Map<String, Map<String, String>> favouritesMap;
    
    /**
     * Возвращает список избранных фильмов
     *
     * @implSpec ВНИМАНИЕ: ИД фильма должен идти первой строчкой, иначе метод загрузки из файла не будет
     *          работать корректно ({@link #loadFavouritesList()})
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
                    .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                        clearList(false);
                    })
                    .create();
        }
        return confirmClearDialog;
    }
    
    //endregion 'Свойства'
    
    
    
    
    //region 'Обработчики'
    
    /**
     * При создании Экрана навешиваем обработчик обновления списка свайпом
     *
     * @param savedInstanceState If the activity is being re-initialized after
     *                           previously being shut down then this Bundle contains the data it most
     *                           recently supplied in {@link #onSaveInstanceState}.  <b><i>Note: Otherwise it is null.</i></b>
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Log.w(LOG_TAG, "---------------- Start of onCreate() method");
        setContentView(R.layout.activity_main);
        customToolBar = findViewById(R.id.top_toolbar);
        this.setSupportActionBar(customToolBar);
        _favouritesListFilePath = new File(this.getFilesDir(), FAVOURITES_LIST_FILE_NAME);
        if (_favouritesListFilePath.exists())
        {
            loadFavouritesList();
        }
        if (Debug.isDebuggerConnected())
        {
            Picasso.get().setIndicatorsEnabled(true);
            Picasso.get().setLoggingEnabled(true);
        }
        isRus = Locale.getDefault().getLanguage().equalsIgnoreCase("ru");
        
        _layoutInflater = getLayoutInflater();
        androidContentView = findViewById(android.R.id.content);
        progBar = findViewById(R.id.progress_bar);
        progBar.setVisibility(View.GONE);
        txtQuery = findViewById(R.id.txt_input);
        cardsContainer = findViewById(R.id.card_linear_lyaout);
        inputPanel = findViewById(R.id.input_panel);
        swipeRefreshContainer = findViewById(R.id.film_list_swipe_refresh_container);
        swipeRefreshContainer.setColorSchemeResources(R.color.biz, R.color.neo, R.color.neo_dark, R.color.purple_light);
        _imageCacheDirPath = new File(this.getCacheDir(), Constants.FAVOURITES_CASH_DIR_NAME);
        scroller = findViewById(R.id.card_scroller);
        
        this._initEventHandlers();
        
        switchUIToPopularFilmsAsync(true, true);
        Log.w(LOG_TAG, "End of onCreate() method ---------------------");
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
        if (favouritesMap == null || favouritesMap.isEmpty())
        {
            this.deleteFile(Constants.FAVOURITES_LIST_FILE_NAME);
            if (_imageCacheDirPath.exists())
            {
                //noinspection ResultOfMethodCallIgnored
                Arrays.stream(Objects.requireNonNull(_imageCacheDirPath.listFiles())).parallel()
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
            default:
            {
                Log.w(LOG_TAG, "Неизвестная команда меню!");
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
    private void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY)
    {
        boolean isBottomReached = cardsContainer.getBottom() - v.getBottom() - scrollY == 0;
        if (isBottomReached)
        {
            if (_currentViewMode == ViewMode.POPULAR && _currentPageNumber < _topFilmsPagesCount)
            {
                switchUIToPopularFilmsAsync(false, true);
            }
        }
        else if (scrollY > 20)
        {
            inputPanel.setCardElevation(10 * getResources().getDisplayMetrics().density);
        }
        else if (scrollY == 0)
        {
            inputPanel.setCardElevation(0);
        }
    }
    
    /**
     * Обработчик изменения текста в виджете Поиска
     */
    @NonNull
    private TextWatcher getSearchTextChangeWatcher()
    {
        if (_textWatcher == null)
        {
            _textWatcher = new TextWatcher()
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
                        filterFilmCardsUI(s.toString());
                    }
                }
            };
        }
        return _textWatcher;
    }
    
    //endregion 'Обработчики'
    
    
    
    
    //region 'Методы'
    
    /**
     * Метод настройки событий виджетов
     */
    private void _initEventHandlers()
    {
        // обновляем страницу свайпом сверху
        swipeRefreshContainer.setOnRefreshListener(this::refreshUIContent);
        // ищем текст по кнопке ВВОД на клавиатуре
        txtQuery.setOnEditorActionListener((v, actionId, event) -> {
            // N.B. Похоже, только для действия Done можно реализовать автоскрытие клавиатуры - при остальных клава остаётся на экране после клика
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH)
            {
                return false;
            }
            return true;
        });
        // Обработчик изменения текста в поисковом контроле
        txtQuery.addTextChangedListener(getSearchTextChangeWatcher());
        // при прокрутке списка фильмов до конца подгружаем следующую страницу результатов (если есть)
        scroller.setOnScrollChangeListener(this::onScrollChange);
    }
    
    /**
     * Метод показа (вывода из невидимости) всех карточек фильмов
     *
     * @apiNote Вызов имеет смысл, только если перед этим был вызов {@link #filterFilmCardsUI(String)}
     * @implNote Сбрасывает признак фильтрации списка {@link #_isFiltered}
     */
    private void showFilmCardsUI()
    {
        if (_isFiltered)
        {
            for (int i = 0; i < cardsContainer.getChildCount(); i++)
            {
                cardsContainer.getChildAt(i).setVisibility(View.VISIBLE);
            }
        }
        _isFiltered = false;
    }
    
    /**
     * Метод фильтрации списка Фильмов на экране
     *
     * @param query строка для поиска любого из слов названия фильма
     * @apiNote Метод не выдаёт совпадения для слов короче 3-х символов (для исключения предлогов)
     * @implSpec При пустом <code>query</code> не делает НИЧЕГО - для отмены фильтра используйте {@link #showFilmCardsUI()}
     */
    private void filterFilmCardsUI(String query)
    {
        if (query.isBlank())
        {
            return;
        }
        final var foundFilms = _cardList.stream().filter(x -> {
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
        _isFiltered = true;
    }
    
    /**
     * Метод перезагрузки содержимого страницы (списка фильмов)
     */
    private void refreshUIContent()
    {
        if (this._currentViewMode == ViewMode.POPULAR)
        {
            switchUIToPopularFilmsAsync(true, true);
        }
        else if (this._currentViewMode == ViewMode.FAVOURITES)
        {
            cardsContainer.setVisibility(View.INVISIBLE);
            switchUIToFavouriteFilms(true);
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
            final var listItem = _layoutInflater.inflate(R.layout.list_item, null);
            final var cardData = cardList.get(i);
            final var id = cardData.get(Constants.ADAPTER_FILM_ID);
            ((TextView) listItem.findViewById(R.id.film_id_holder)).setText(id);
            final var title = cardData.get(Constants.ADAPTER_TITLE);
            ((TextView) listItem.findViewById(R.id.card_title)).setText(title);
            ((TextView) listItem.findViewById(R.id.card_content)).setText(cardData.get(ADAPTER_CONTENT));
            final var imgView = ((ImageView) listItem.findViewById(R.id.poster_preview));
            final var imageUrl = cardData.get(Constants.ADAPTER_POSTER_PREVIEW_URL);
            final var cachedImageFilePath = cardData.getOrDefault(ADAPTER_IMAGE_PREVIEW_FILE_PATH, "");
            //noinspection ConstantConditions
            if (cachedImageFilePath.isEmpty())
            {
                Picasso.get().load(imageUrl).transform(new RoundedCornersTransformation(30, 10)).into(imgView);
            }
            else
            {
                final var previewImageFile = new File(cachedImageFilePath);
                if (previewImageFile.exists())
                {
                    imgView.setImageURI(Uri.fromFile(previewImageFile));
                    //imgView.setImageDrawable(RoundedBitmapDrawable.createFromPath(cachedImageFilePath));
                    Log.d(LOG_TAG, "Картинка загружена из файла: " + cachedImageFilePath);
                }
            }
            cardsContainer.addView(listItem);
            listItem.setOnClickListener(v -> showFilmCardActivity(id, title));
            final ImageView imgViewLike = listItem.findViewById(R.id.like_image_view);
            // TODO: возможно фильм нужно искать по названию, а не по ИД, на случай, если ИД поменяется?
            if (getFavouritesMap().containsKey(id))
            {
                imgViewLike.setImageResource(R.drawable.baseline_favorite_24);
            }
            final var likeButtonClickHandler = getOnLikeButtonClickListener(
                    cardData, id, imgView, imgViewLike);
            imgViewLike.setOnClickListener(likeButtonClickHandler);
            // Требование ТЗ: "При длительном клике на карточку, фильм помещается в избранное"
            listItem.setOnLongClickListener(v -> {
                likeButtonClickHandler.onClick(v);
                return true;
            });
        }
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
        if (!_imageCacheDirPath.exists() && !_imageCacheDirPath.mkdir())
        {
            Log.w(LOG_TAG, "Ошибка создания подкаталога для кэша постеров к фильмам");
        }
        final var imgPreviewFilePath = new File(_imageCacheDirPath, "preview_" + id + ".webp");
        
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
        var popup = Snackbar.make(this.androidContentView, message, Snackbar.LENGTH_INDEFINITE);
        popup.setAction(R.string.repeat_button_caption, view -> {
            var text = Objects.requireNonNull(txtQuery.getText()).toString().replace("null", "");
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
     * Метод показа ТОП-100 популярных фильмов - асинхронно загружает список фильмов из Сети
     *
     * @param ifBeginFromPageOne если True, текущий список очищается и показ начинается с первой страницы
     */
    private void switchUIToPopularFilmsAsync(boolean ifBeginFromPageOne, boolean isDownloadNew)
    {
        if (_currentViewMode == ViewMode.POPULAR && !isDownloadNew)
        {
            return;
        }
        _currentViewMode = ViewMode.POPULAR;
        customToolBar.setTitle(R.string.action_popular_title);
    
        _lastListViewPos2 = scroller.getScrollY();
        
        if (ifBeginFromPageOne && isDownloadNew)
        {
            clearList(false);
        }
        else if (!isDownloadNew)
        {
            clearList(!ifBeginFromPageOne);
        }
        if (ifBeginFromPageOne)
        {
            _currentPageNumber = 1;
        }
        if (isDownloadNew)
        {
            this.startTopFilmsDownloadTask(_currentPageNumber);
        }
        else
        {
            fillCardListUIFrom(0, _cardList);
            // на основе кода отсюда: https://stackoverflow.com/a/3263540/2323972
            scroller.post(() -> scroller.scrollTo(0, _lastListViewPos));
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
        if (!forceRefresh && _currentViewMode == ViewMode.FAVOURITES)
        {
            return;
        }
        if (!forceRefresh)
        {
            _lastListViewPos = scroller.getScrollY();
        }
        _currentViewMode = ViewMode.FAVOURITES;
        clearList(true);
        customToolBar.setTitle(R.string.action_favourites_title);
        if (!getFavouritesMap().isEmpty())
        {
            fillCardListUIFrom(0, new ArrayList<>(getFavouritesMap().values()));
            if (!forceRefresh)
            {
                scroller.post(() -> scroller.scrollTo(0, _lastListViewPos2));
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
    @NonNull
    private void showFilmCardActivity(String kinoApiFilmId, String cardTitle)
    {
        var switchActivityIntent = new Intent(getApplicationContext(), CardActivity.class);
        switchActivityIntent.putExtra(Constants.ADAPTER_TITLE, cardTitle);
        switchActivityIntent.putExtra(Constants.ADAPTER_FILM_ID, kinoApiFilmId);
        startActivity(switchActivityIntent);
    }
    
    /**
     * Метод загрузки списка Избранных фильмов из файла {@link Constants#FAVOURITES_LIST_FILE_NAME}
     */
    private void loadFavouritesList()
    {
        final var tempValuesMap = new HashMap<String, String>();
        try (var fr = new BufferedReader(new FileReader(_favouritesListFilePath));
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
            Log.e(LOG_TAG,"Ошибка чтения файла: " + FAVOURITES_LIST_FILE_NAME, e);
            Toast.makeText(this,"Не удалось прочитать файл Избранного!", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Метод записи файла со списком Избранного (путь: {@link Constants#FAVOURITES_LIST_FILE_NAME})
     */
    private void saveFavouritesList()
    {
        try (BufferedWriter fw = new BufferedWriter(new FileWriter(_favouritesListFilePath)))
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
            Log.e(LOG_TAG,"Ошибка записи файла: " + _favouritesListFilePath.toString(), e);
        }
    }
    
    //endregion 'Методы'
    
    
}