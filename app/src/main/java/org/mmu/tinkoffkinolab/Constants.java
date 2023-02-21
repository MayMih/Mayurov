package org.mmu.tinkoffkinolab;


import jp.wasabeef.picasso.transformations.RoundedCornersTransformation;

public class Constants
{
    public static final String ADAPTER_FILM_ID = "ID";
    public static final String ADAPTER_TITLE = "Title";
    public static final String ADAPTER_CONTENT = "Content";
    public static final String ADAPTER_IMAGE_PREVIEW_FILE_PATH = "preview_file_path";
    public static final String FAVOURITES_CASH_DIR_NAME = "favourites_image_cache";
    public static final String LOG_TAG = MainActivity.class.getSimpleName();
    public static final String FAVOURITES_LIST_FILE_NAME = "favourites.txt";
    public static final String KEY_VALUE_SEPARATOR = "=";
    public static final String FILM_START_TOKEN = "{";
    public static final String FILM_END_TOKEN = "}";
    public static final RoundedCornersTransformation ROUNDED_CORNERS_TRANSFORMATION = new RoundedCornersTransformation(30, 10);
    /**
     * Демо-ключ неофициального API Кинопоиска
     *
     * @see <a href="https://kinopoiskapiunofficial.tech/">Kinopoisk Api Unofficial</a>
     *
     * @apiNote Этот ключ не имеет ограничений по количеству запросов в сутки, но имеет ограничение
     *      20 запросов в секунду.
     *
     * @implSpec В качестве альтернативы вы можете зарегистрироваться самостоятельно и получить
     *      собственный ключ, но тогда будет действовать ограничение в 500 запросов в день.
     */
    public static final String KINO_DEMO_API_KEY = "e30ffed0-76ab-4dd6-b41f-4c9da2b2735b";
    public static final String ADAPTER_POSTER_PREVIEW_URL = "ImageUrl";
    public static final String UNKNOWN_WEB_ERROR_MES = "Ошибка загрузки данных по сети:";
    public static final String KINO_API_ERROR_MES = "Ошибка API KinoPoisk";
    
    enum TopFilmsType
    {
        /**
         * Почему-то загружает ТОП-700 (а не 100 фильмов?!)
         */
        TOP_100_POPULAR_FILMS,
        TOP_250_BEST_FILMS,
        TOP_AWAIT_FILMS
    }
}
