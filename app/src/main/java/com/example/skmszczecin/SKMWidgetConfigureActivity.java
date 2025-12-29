package com.example.skmszczecin;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SKMWidgetConfigureActivity extends Activity {
    int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_config); // UPEWNIJ SIĘ ŻE MASZ TEN LAYOUT

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        SharedPreferences prefs = getSharedPreferences("SKM_PREFS", Context.MODE_PRIVATE);
        String json = prefs.getString("full_config", "[]");

        // Parsowanie listy grup
        List<StopConfig> config = new Gson().fromJson(json, new TypeToken<List<StopConfig>>(){}.getType());
        Set<String> groupsSet = new HashSet<>();
        if (config != null) {
            for (StopConfig s : config) {
                if (s.groupName != null) groupsSet.add(s.groupName);
            }
        }

        List<String> groupList = new ArrayList<>(groupsSet);
        if (groupList.isEmpty()) groupList.add("Brak grup - skonfiguruj w aplikacji");

        ListView lv = findViewById(R.id.groupListView);
        lv.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, groupList));

        lv.setOnItemClickListener((parent, view, position, id) -> {
            String selectedGroup = groupList.get(position);
            prefs.edit().putString("widget_group_" + mAppWidgetId, selectedGroup).apply();

            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
            SKMWidget.updateAppWidget(this, appWidgetManager, mAppWidgetId);

            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
            setResult(RESULT_OK, resultValue);
            finish();
        });
    }
}