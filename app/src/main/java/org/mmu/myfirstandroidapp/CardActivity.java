package org.mmu.myfirstandroidapp;

import android.app.Activity;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.Objects;

public class CardActivity extends AppCompatActivity
{
    private String filmId;
    
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card);
    
        MaterialToolbar customToolBar = findViewById(R.id.top_tool_bar);
        customToolBar.setTitle( getIntent().getStringExtra(Constants.ADAPTER_TITLE));
        this.setSupportActionBar(customToolBar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        
        filmId = getIntent().getStringExtra(Constants.ADAPTER_FILM_ID);
        
    }
}