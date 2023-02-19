package org.mmu.tinkoffkinolab;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PictureDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Debug;
import android.provider.ContactsContract;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
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
                            Constants.ADAPTER_CONTENT, filmData.getGenres().get(0).getGenre() + " (" + filmData.getYear() + ")",
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
                    var apiEx = (ApiException) ex;
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
            swipeRefreshContainer.setRefreshing(false);
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
    
    //endregion 'Поля и константы'
    
    
    //region 'Свойства'
    
    private Map<String, Map<String, String>> _favouritesMap;
    
    /**
     * Возвращает список избранных фильмов
     */
    public Map<String, Map<String, String>> getFavouritesMap()
    {
        if (_favouritesMap == null)
        {
            _favouritesMap = new HashMap<>();
        }
        return _favouritesMap;
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
        Log.w(Constants.LOG_TAG, "---------------- Start of onCreate() method");
        setContentView(R.layout.activity_main);
        customToolBar = findViewById(R.id.top_toolbar);
        this.setSupportActionBar(customToolBar);
        
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
        
        this.setEventHandlers();
        
        showPopularFilmsAsync(true);
        Log.w(Constants.LOG_TAG, "End of onCreate() method ---------------------");
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
            case R.id.action_go_to_top:
            {
                swipeRefreshContainer.getChildAt(0).scrollTo(0, 0);
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
        swipeRefreshContainer.setOnRefreshListener(this::refeshUIContent);
        // ищем текст по кнопке ВВОД на клавиатуре
        txtQuery.setOnEditorActionListener((v, actionId, event) -> {
            // N.B. Похоже, только для действия Done можно реализовать автоскрытие клавиатуры - при остальных клава остаётся на экране после клика
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH)
            {
                Toast.makeText(this, "Фильтрация ещё не реализована!", Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        });
        // Обработчик изменения текста в поисковом контроле
        txtQuery.addTextChangedListener(new TextWatcher()
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
        });
        // при прокрутке списка фильмов до конца подгружаем следующую страницу результатов (если есть)
        final var scroller = findViewById(R.id.card_scroller);
        scroller.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
        {
            boolean isBottomReached = cardsContainer.getBottom() - v.getBottom() - scrollY == 0;
            if (isBottomReached && this._currentViewMode == ViewMode.POPULAR && _currentPageNumber < _topFilmsPagesCount)
            {
                showPopularFilmsAsync(false);
            }
            else if (scrollY > 20)
            {
                inputPanel.setCardElevation(10 * getResources().getDisplayMetrics().density);
            }
            else if (scrollY == 0)
            {
                inputPanel.setCardElevation(0);
            }
        });
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
            final var listItem = _layoutInflater.inflate(R.layout.list_item, null);
            final var cardData = _cardList.get(i);
            final var id = cardData.get(Constants.ADAPTER_FILM_ID);
            final var idField = ((TextView) listItem.findViewById(R.id.film_id_holder));
            idField.setText(id);
            final var title = cardData.get(Constants.ADAPTER_TITLE);
            ((TextView) listItem.findViewById(R.id.card_title)).setText(title);
            ((TextView) listItem.findViewById(R.id.card_content)).setText(cardData.get(Constants.ADAPTER_CONTENT));
            final var imgView = ((ImageView) listItem.findViewById(R.id.poster_preview));
            final var imageUrl = cardData.get(Constants.ADAPTER_POSTER_PREVIEW_URL);
            Picasso.get().load(imageUrl).transform(new RoundedCornersTransformation(30, 10)).into(imgView);
            cardsContainer.addView(listItem);
            listItem.setOnClickListener(v -> showFilmCardActivity(id, title));
            listItem.setOnLongClickListener(b -> {
                if (addToOrRemoveFromFavourites(id, cardData, imgView.getDrawable()))
                {
                    Toast.makeText(this, "Фильм добавлен в Избранное", Toast.LENGTH_SHORT).show();
                }
                else
                {
                    Toast.makeText(this, "Фильм удалён из списка Избранных", Toast.LENGTH_SHORT).show();
                }
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
     *
     * @implSpec TODO: Возможно стоит уводить метод в отдельный поток, на случай если запись файла будет длиться дольше 5 секунд!
     */
    private boolean addToOrRemoveFromFavourites(String id, Map<String, String> cardData, Drawable image)
    {
        final String FAVOURITES_CASH_DIR_NAME = "favourites_image_cash";
        final var IMAGE_CASH_DIR_PATH = new File(this.getCacheDir(), FAVOURITES_CASH_DIR_NAME);
        if (!IMAGE_CASH_DIR_PATH.exists() && !IMAGE_CASH_DIR_PATH.mkdir())
        {
            Log.w(Constants.LOG_TAG, "Ошибка создания подкаталога для кэша постеров к фильмам");
        }
        final var imgPreviewFilePath = new File(IMAGE_CASH_DIR_PATH, "preview_" + id + ".jpg");
        
        if (this.getFavouritesMap().containsKey(id))
        {
            this.deleteFile(imgPreviewFilePath.toString());
            this.getFavouritesMap().remove(id);
            return false;
        }
        // Доп. требование ТЗ - "карточки избранных фильмов должны быть доступны офлайн"
        // малый постер
        try (var outStream = new FileOutputStream(imgPreviewFilePath))
        {
            convertDrawableToBitmap(image).compress(Bitmap.CompressFormat.JPEG, 80, outStream);
        }
        catch (IOException e)
        {
            Log.e(Constants.LOG_TAG, "Ошибка записи в файл", e);
            Toast.makeText(this,"Что-то пошло не так: Ошибка записи в файл", Toast.LENGTH_SHORT).show();
        }
        final var curData = new ArrayList<>(cardData.entrySet());
        curData.add(Map.entry(Constants.ADAPTER_IMAGE_PREVIEW_FILE_PATH, imgPreviewFilePath.toString()));
        final var filmData = Map.ofEntries(curData.toArray(new Map.Entry[0]));
        this.getFavouritesMap().put(id, cardData);
        return true;
    }
    
    /**
     * Метод преобразования изображений из виджета в формат пригодный для записи в файл
     *
     * @implNote Альтернативный вариант - получать {@link Bitmap} из самого {@link ImageView#getDrawingCache()} (устаревшее)
     * @see <a href="https://stackoverflow.com/a/34026527/2323972">Взято отсюда: How can I write a Drawable resource to a File</a>
     */
    public Bitmap convertDrawableToBitmap(Drawable pd)
    {
        Bitmap bm = Bitmap.createBitmap(pd.getIntrinsicWidth(), pd.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bm);
        //canvas.drawPicture(pd.getPicture());
        pd.draw(canvas);
        return bm;
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
     * @param cardTitle     Желаемый заголовок карточки
     */
    @NonNull
    public void showFilmCardActivity(String kinoApiFilmId, String cardTitle)
    {
        var switchActivityIntent = new Intent(getApplicationContext(), CardActivity.class);
        switchActivityIntent.putExtra(Constants.ADAPTER_TITLE, cardTitle);
        switchActivityIntent.putExtra(Constants.ADAPTER_FILM_ID, kinoApiFilmId);
        startActivity(switchActivityIntent);
    }
    
    //endregion 'Методы'
    
    
}