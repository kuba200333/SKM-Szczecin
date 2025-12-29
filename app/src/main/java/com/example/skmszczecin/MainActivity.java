package com.example.skmszczecin;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private List<StopConfig> configList;
    private ConfigAdapter adapter;
    private SharedPreferences prefs;
    private Gson gson = new Gson();

    // Lista wszystkich przystanków pobrana z ZDiTM
    private final List<Stop> allStops = new ArrayList<>();
    private ArrayAdapter<Stop> spinnerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("SKM_PREFS", Context.MODE_PRIVATE);
        loadConfig();

        // Inicjalizacja widoków (Upewnij się, że masz takie ID w activity_main.xml)
        Spinner stopSpinner = findViewById(R.id.stopSpinner);
        AutoCompleteTextView groupInput = findViewById(R.id.groupInput);
        EditText linesInput = findViewById(R.id.linesInput);
        Button btnAdd = findViewById(R.id.addButton);
        ListView listView = findViewById(R.id.configListView);

        // 1. Konfiguracja Spinnera z napisem "Pobieranie..."
        allStops.add(new Stop("", "Pobieranie listy przystanków..."));
        spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, allStops);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        stopSpinner.setAdapter(spinnerAdapter);

        // 2. Start pobierania wszystkich przystanków ze Szczecina
        new FetchStopsTask().execute();

        // 3. Konfiguracja podpowiedzi grup
        updateGroupSuggestions(groupInput);

        // 4. Konfiguracja ładnej listy (ConfigAdapter)
        adapter = new ConfigAdapter(this, configList);
        listView.setAdapter(adapter);

        // 5. Obsługa dodawania
        btnAdd.setOnClickListener(v -> {
            Stop selectedStop = (Stop) stopSpinner.getSelectedItem();
            if (selectedStop == null || selectedStop.id.isEmpty()) {
                Toast.makeText(this, "Poczekaj na załadowanie przystanków!", Toast.LENGTH_SHORT).show();
                return;
            }

            String gName = groupInput.getText().toString().trim().toUpperCase();
            String lStr = linesInput.getText().toString().trim();

            if (gName.isEmpty()) {
                Toast.makeText(this, "Podaj nazwę grupy (np. DOM)!", Toast.LENGTH_SHORT).show();
                return;
            }

            List<String> linesList = Arrays.asList(lStr.split("\\s*,\\s*"));

            // Tworzymy nowy obiekt konfiguracji
            configList.add(new StopConfig(selectedStop.id, selectedStop.name, gName, linesList));

            saveConfig();
            adapter.notifyDataSetChanged();
            updateGroupSuggestions(groupInput);
            refreshWidget();

            Toast.makeText(this, "Dodano do grupy " + gName, Toast.LENGTH_SHORT).show();
            linesInput.setText("");
        });
    }

    // --- POBIERANIE WSZYSTKICH PRZYSTANKÓW Z ZDITM ---
    private class FetchStopsTask extends AsyncTask<Void, Void, List<Stop>> {
        @Override
        protected List<Stop> doInBackground(Void... voids) {
            List<Stop> results = new ArrayList<>();
            try {
                URL url = new URL("https://www.zditm.szczecin.pl/api/v1/stops");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                // Obsługa formatu: {"data": [...]} lub po prostu [...]
                String jsonStr = sb.toString();
                JSONArray data;
                if (jsonStr.startsWith("{")) {
                    data = new JSONObject(jsonStr).getJSONArray("data");
                } else {
                    data = new JSONArray(jsonStr);
                }

                for (int i = 0; i < data.length(); i++) {
                    JSONObject obj = data.getJSONObject(i);
                    results.add(new Stop(obj.getString("number"), obj.getString("name")));
                }
                // Sortowanie alfabetyczne nazw
                Collections.sort(results, (s1, s2) -> s1.name.compareToIgnoreCase(s2.name));
            } catch (Exception e) { e.printStackTrace(); }
            return results;
        }

        @Override
        protected void onPostExecute(List<Stop> stops) {
            if (stops != null && !stops.isEmpty()) {
                allStops.clear();
                allStops.addAll(stops);
                spinnerAdapter.notifyDataSetChanged();
            } else {
                Toast.makeText(MainActivity.this, "Błąd pobierania bazy przystanków!", Toast.LENGTH_LONG).show();
            }
        }
    }

    // --- ŁADNY ADAPTER LISTY (STYL WEBOWY) ---
    private class ConfigAdapter extends ArrayAdapter<StopConfig> {
        public ConfigAdapter(Context context, List<StopConfig> list) {
            super(context, 0, list);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                // Upewnij się że masz plik res/layout/list_item_config.xml
                convertView = getLayoutInflater().inflate(R.layout.list_item_config, parent, false);
            }
            StopConfig item = getItem(position);

            TextView txtGroup = convertView.findViewById(R.id.itemGroup);
            TextView txtName = convertView.findViewById(R.id.itemName);
            TextView txtLines = convertView.findViewById(R.id.itemLines);
            View btnDelete = convertView.findViewById(R.id.btnDelete);

            txtGroup.setText(item.groupName);
            txtName.setText(item.name + " (" + item.id + ")");
            txtLines.setText("Linie: " + String.join(", ", item.lines));

            btnDelete.setOnClickListener(v -> {
                configList.remove(position);
                saveConfig();
                notifyDataSetChanged();
                refreshWidget();
            });

            return convertView;
        }
    }

    // Klasa modelu dla Spinnera
    private static class Stop {
        String id, name;
        Stop(String id, String name) { this.id = id; this.name = name; }
        @Override public String toString() {
            return id.isEmpty() ? name : name + " [" + id + "]";
        }
    }

    private void updateGroupSuggestions(AutoCompleteTextView autoComplete) {
        List<String> groups = new ArrayList<>();
        for (StopConfig c : configList) {
            if (!groups.contains(c.groupName)) groups.add(c.groupName);
        }
        autoComplete.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, groups));
    }

    private void loadConfig() {
        String json = prefs.getString("full_config", "[]");
        configList = gson.fromJson(json, new TypeToken<ArrayList<StopConfig>>() {}.getType());
        if (configList == null) configList = new ArrayList<>();
    }

    private void saveConfig() {
        prefs.edit().putString("full_config", gson.toJson(configList)).apply();
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