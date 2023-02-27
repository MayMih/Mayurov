package org.mmu.tinkoffkinolab;

import static org.mmu.tinkoffkinolab.Constants.ADAPTER_FILM_ID;
import static org.mmu.tinkoffkinolab.Constants.ADAPTER_TITLE;
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
    
    private String filmId;
    

    
    
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
        
        filmId = getIntent().getStringExtra(Constants.ADAPTER_FILM_ID);
        
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks()
        {
            private CardFragment cardFragment;
            @Override
            public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment f,
                                              @NonNull View v, @Nullable Bundle savedInstanceState)
            {
                super.onFragmentViewCreated(fm, f, v, savedInstanceState);
                cardFragment = ((CardFragment)f);
                cardFragment.setDataLoadListener(() -> onDataLoaded_Handler());
                cardFragment.getFilmDataAsync(filmId, customToolBar.getTitle().toString());
            }
    
            @Override
            public void onFragmentDestroyed(@NonNull FragmentManager fm, @NonNull Fragment f)
            {
                super.onFragmentDestroyed(fm, f);
                cardFragment.removeDataLoadListener();
            }
        }, false);
        
    }
    
    public void onDataLoaded_Handler()
    {
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);
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
    
    
    
    

    
}