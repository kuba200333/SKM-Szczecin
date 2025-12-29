package com.example.skmszczecin;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private List<StopConfig> configList;
    private ArrayAdapter<String> adapter;
    private List<String> displayList;
    private SharedPreferences prefs;
    private Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("SKM_PREFS", Context.MODE_PRIVATE);

        if (!prefs.contains("full_config")) {
            String initialJson = "[" +
                    "{\"id\":\"22512\",\"name\":\"Dworzec Główny (Kolumba)\",\"groupName\":\"DWORZEC PKP\",\"lines\":[\"75\"]}," +
                    "{\"id\":\"22921\",\"name\":\"Dworzec Główny (Owocowa)\",\"groupName\":\"DWORZEC PKP\",\"lines\":[\"61\"]}," +
                    "{\"id\":\"22813\",\"name\":\"Plac Zawiszy\",\"groupName\":\"DWORZEC PKP\",\"lines\":[\"1\",\"9\"]}," +
                    "{\"id\":\"20813\",\"name\":\"Osiedle Akademickie\",\"groupName\":\"AKADEMIK\",\"lines\":[\"8\",\"10\"]}," +
                    "{\"id\":\"21111\",\"name\":\"Szwoleżerów\",\"groupName\":\"AKADEMIK\",\"lines\":[\"61\",\"62\",\"241\",\"243\",\"811\"]}," +
                    "{\"id\":\"11524\",\"name\":\"Plac Rodła\",\"groupName\":\"GALAXY\",\"lines\":[\"4\",\"10\"]}," +
                    "{\"id\":\"10721\",\"name\":\"Plac Zwycięstwa\",\"groupName\":\"PLAC ZWYCIĘSTWA\",\"lines\":[\"7\",\"8\",\"10\",\"61\",\"62\"]}," +
                    "{\"id\":\"30212\",\"name\":\"Żołnierska\",\"groupName\":\"UCZELNIA\",\"lines\":[\"5\",\"7\"]}," +
                    "{\"id\":\"10221\",\"name\":\"Turzyn\",\"groupName\":\"TURZYN\",\"lines\":[\"7\"]}," +
                    "{\"id\":\"20423\",\"name\":\"Ku Słońcu\",\"groupName\":\"KU SŁOŃCU\",\"lines\":[\"8\",\"10\"]}" +
                    "]";
            prefs.edit().putString("full_config", initialJson).apply();
        }

        loadConfig();

        EditText inputId = findViewById(R.id.stopIdInput);
        EditText inputName = findViewById(R.id.stopNameInput); // Dodaj to ID do XML
        EditText inputLines = findViewById(R.id.linesInput);   // Dodaj to ID do XML
        Button btnAdd = findViewById(R.id.saveButton);
        ListView listView = findViewById(R.id.configListView); // Dodaj to ID do XML

        displayList = new ArrayList<>();
        updateDisplayList();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayList);
        listView.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> {
            String id = inputId.getText().toString().trim();
            String name = inputName.getText().toString().trim(); // To jest nazwa przystanku
            String linesStr = inputLines.getText().toString().trim();

            if (!id.isEmpty() && !name.isEmpty()) {
                List<String> lines = Arrays.asList(linesStr.split("\\s*,\\s*"));

                // POPRAWKA TUTAJ:
                // Zamiast "Główna", używamy zmiennej 'name', którą wpisałeś w aplikacji.
                // Dzięki temu przystanek trafi do grupy o takiej nazwie, jaką wpiszesz.
                configList.add(new StopConfig(id, name, name.toUpperCase(), lines));

                saveConfig();
                updateDisplayList();
                adapter.notifyDataSetChanged();
                refreshWidget();
                Toast.makeText(this, "Dodano do grupy: " + name.toUpperCase(), Toast.LENGTH_SHORT).show();
            }
        });

        // Usuwanie po kliknięciu na liście
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            configList.remove(position);
            saveConfig();
            updateDisplayList();
            adapter.notifyDataSetChanged();
            refreshWidget();
            return true;
        });
    }

    private void loadConfig() {
        String json = prefs.getString("full_config", "[]");
        Type type = new TypeToken<ArrayList<StopConfig>>() {}.getType();
        configList = gson.fromJson(json, type);
    }

    private void saveConfig() {
        String json = gson.toJson(configList);
        prefs.edit().putString("full_config", json).apply();
    }

    private void updateDisplayList() {
        displayList.clear();
        for (StopConfig s : configList) {
            displayList.add(s.name + " (" + s.id + ") - Linie: " + s.lines);
        }
    }

    private void refreshWidget() {
        Intent intent = new Intent(this, SKMWidget.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        int[] ids = AppWidgetManager.getInstance(getApplication())
                .getAppWidgetIds(new ComponentName(getApplication(), SKMWidget.class));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        sendBroadcast(intent);
    }
}