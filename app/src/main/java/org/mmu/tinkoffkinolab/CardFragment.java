package org.mmu.tinkoffkinolab;

import static org.mmu.tinkoffkinolab.Constants.LOG_TAG;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.util.AbstractMap;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import io.swagger.client.ApiException;
import io.swagger.client.api.FilmsApi;

/**
 * A simple {@link Fragment} subclass.
 * create an instance of this fragment.
 */
public class CardFragment extends Fragment
{
   
    
    //region 'Типы'
    
    private class WebDataDownloadTask extends AsyncTask<String, Void, Void>
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
        protected Void doInBackground(String... filmId)
        {
            try
            {
                final var response = filmsApi.apiV22FilmsIdGet(Integer.parseInt(filmId[0]));
                final var engName = Objects.requireNonNullElse(response.getNameEn(), "");
                _cardData.put(Constants.ADAPTER_TITLE, response.getNameRu() + (!engName.isBlank() ?
                        System.lineSeparator() + "[" + engName + "]" : ""));
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
                    final var apiEx = (ApiException) ex;
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
         * @param unused The result of the operation computed by {@link #doInBackground}.
         * @apiNote Этот метод выполняется в потоке интерфейса
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
    
    
    //region 'Поля и константы'
    
    private static final Map<String, String> _cardData = new HashMap<>();
    private ImageView imgPoster;
    
    private WebDataDownloadTask downloadTask;
    private TextView txtHeader;
    private TextView txtContent;
    private View progBar;
    
    private String filmId_param;
    private String filmTitle_param;
    
    //endregion 'Поля и константы'
    
    
    //region 'События'
    
    interface DataLoadSuccessListener extends EventListener
    {
        void onFilmDataLoaded();
    }
    
    public DataLoadSuccessListener getDataLoadListener()
    {
        return dataLoadListener;
    }
    
    public void setDataLoadListener(DataLoadSuccessListener dataLoadListener)
    {
        this.dataLoadListener = dataLoadListener;
    }
    
    public void removeDataLoadListener()
    {
        this.dataLoadListener = null;
    }
    
    private DataLoadSuccessListener dataLoadListener;
    
    //endregion 'События'
    
    
    // Required empty public constructor
    public CardFragment()
    {
    }
    
    
    
    //region 'Обработчики'
    
   
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.fragment_card, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);
        imgPoster = view.findViewById(R.id.poster_image_view);
        txtHeader = view.findViewById(R.id.card_title);
        txtContent = view.findViewById(R.id.card_content);
        progBar = view.findViewById(R.id.progress_bar);
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
                if (dataLoadListener != null)
                {
                    dataLoadListener.onFilmDataLoaded();
                }
                txtHeader.setText(Objects.requireNonNullElse(_cardData.get(Constants.ADAPTER_TITLE),
                        filmTitle_param));
                final var textContent = _cardData.get(Constants.ADAPTER_CONTENT);
                txtContent.setText(Objects.requireNonNullElse(textContent, ""));
                imgPoster.setOnClickListener(v1 ->
                    Utils.showFullScreenPhoto(Uri.parse(posterUrl), v1.getContext())
                );
                final ScrollView sv = requireActivity().findViewById(R.id.fragment_scroll);
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
        getFilmDataAsync(filmId_param, filmTitle_param);
    }
    
    public void getFilmDataAsync(String id, String title)
    {
        clearView();
        filmId_param = id;
        filmTitle_param = title;
        if (downloadTask != null && !downloadTask.isCancelled() && (downloadTask.getStatus() == AsyncTask.Status.RUNNING))
        {
            downloadTask.cancel(true);
        }
        downloadTask = (WebDataDownloadTask)new WebDataDownloadTask(FilmsApiHelper.getFilmsApi()).execute(id);
    }
    
    private void clearView()
    {
        imgPoster.setImageDrawable(null);
        txtContent.setText("");
        txtHeader.setText("");
    }
    
    //endregion 'Методы'
}