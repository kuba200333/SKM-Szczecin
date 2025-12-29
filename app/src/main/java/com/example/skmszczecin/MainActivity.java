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

    // Lista wszystkich przystanków (pobierana z sieci)
    private final List<Stop> allStops = new ArrayList<>();
    private ArrayAdapter<Stop> spinnerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicjalizacja Spinnera z "Loading..."
        allStops.add(new Stop("", "Pobieranie listy przystanków..."));

        prefs = getSharedPreferences("SKM_PREFS", Context.MODE_PRIVATE);
        loadConfig();

        Spinner spinner = findViewById(R.id.stopSpinner);
        AutoCompleteTextView groupInput = findViewById(R.id.groupInput);
        EditText linesInput = findViewById(R.id.linesInput);
        Button btnAdd = findViewById(R.id.addButton);
        ListView listView = findViewById(R.id.configListView);

        // Adapter dla listy rozwijanej (Spinner)
        spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, allStops);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);

        // Uruchom pobieranie pełnej listy przystanków z ZDiTM
        new FetchStopsTask().execute();

        // Podpowiedzi dla grup
        updateGroupSuggestions(groupInput);

        // Nasz własny adapter listy (ładny wygląd skonfigurowanych grup)
        adapter = new ConfigAdapter(this, configList);
        listView.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> {
            Stop selectedStop = (Stop) spinner.getSelectedItem();
            // Zabezpieczenie przed dodaniem "Pobieranie..."
            if (selectedStop == null || selectedStop.id.isEmpty()) {
                Toast.makeText(this, "Wybierz prawidłowy przystanek!", Toast.LENGTH_SHORT).show();
                return;
            }

            String groupName = groupInput.getText().toString().trim().toUpperCase();
            String linesStr = linesInput.getText().toString().trim();

            if (groupName.isEmpty()) {
                Toast.makeText(this, "Wpisz nazwę grupy!", Toast.LENGTH_SHORT).show();
                return;
            }

            List<String> lines = Arrays.asList(linesStr.split("\\s*,\\s*"));

            // Dodajemy nowy wpis
            configList.add(new StopConfig(selectedStop.id, selectedStop.name, groupName, lines));

            saveConfig();
            adapter.notifyDataSetChanged();
            updateGroupSuggestions(groupInput); // Odśwież podpowiedzi grup
            refreshWidget();

            Toast.makeText(this, "Dodano: " + selectedStop.name, Toast.LENGTH_SHORT).show();

            // Opcjonalnie: wyczyść pola po dodaniu
            linesInput.setText("");
        });
    }

    // --- Klasa do pobierania listy przystanków z ZDiTM ---
    private class FetchStopsTask extends AsyncTask<Void, Void, List<Stop>> {
        @Override
        protected List<Stop> doInBackground(Void... voids) {
            List<Stop> fetchedStops = new ArrayList<>();
            try {
                // Oficjalne API ZDiTM zwracające wszystkie przystanki
                URL url = new URL("https://www.zditm.szczecin.pl/api/v1/stops");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                // Parsowanie JSON
                JSONObject root = new JSONObject(sb.toString());
                JSONArray data = root.optJSONArray("data"); // API zazwyczaj zwraca dane w polu "data"
                if (data == null) data = new JSONArray(sb.toString()); // Fallback jeśli to czysta tablica

                for (int i = 0; i < data.length(); i++) {
                    JSONObject obj = data.getJSONObject(i);
                    // "number" to ID używane do zapytań o odjazdy, "name" to nazwa wyświetlana
                    String number = obj.getString("number");
                    String name = obj.getString("name");
                    // Filtrowanie (opcjonalnie): pomijamy te bez numeru
                    if (number != null && !number.isEmpty()) {
                        fetchedStops.add(new Stop(number, name));
                    }
                }
                // Sortowanie alfabetyczne
                Collections.sort(fetchedStops, (s1, s2) -> s1.name.compareToIgnoreCase(s2.name));

            } catch (Exception e) {
                e.printStackTrace();
            }
            return fetchedStops;
        }

        @Override
        protected void onPostExecute(List<Stop> result) {
            if (result != null && !result.isEmpty()) {
                allStops.clear();
                allStops.addAll(result);
                spinnerAdapter.notifyDataSetChanged();
                // Opcjonalnie: Toast po załadowaniu
                // Toast.makeText(MainActivity.this, "Załadowano przystanki", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Błąd pobierania listy przystanków", Toast.LENGTH_LONG).show();
                // Fallback: dodaj kilka podstawowych jeśli pobieranie się nie uda
                if(allStops.size() <= 1) { // jeśli tylko "Pobieranie..."
                    allStops.clear();
                    allStops.add(new Stop("22512", "Dworzec Główny (Kolumba)"));
                    allStops.add(new Stop("11524", "Plac Rodła"));
                    // ... (możesz dodać więcej awaryjnych)
                    spinnerAdapter.notifyDataSetChanged();
                }
            }
        }
    }

    // --- Reszta klas i metod (Adaptery itp.) ---

    private class ConfigAdapter extends ArrayAdapter<StopConfig> {
        public ConfigAdapter(Context context, List<StopConfig> list) {
            super(context, 0, list);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.list_item_config, parent, false);
            }
            StopConfig item = getItem(position);

            TextView txtGroup = convertView.findViewById(R.id.itemGroup);
            TextView txtName = convertView.findViewById(R.id.itemName);
            TextView txtLines = convertView.findViewById(R.id.itemLines);
            Button btnDelete = convertView.findViewById(R.id.btnDelete);

            txtGroup.setText(item.groupName);
            txtName.setText(item.name);
            txtLines.setText("Linie: " + String.join(", ", item.lines));

            btnDelete.setOnClickListener(v -> {
                configList.remove(position);
                saveConfig();
                notifyDataSetChanged();
                refreshWidget();
                updateGroupSuggestions(findViewById(R.id.groupInput));
            });

            return convertView;
        }
    }

    private void updateGroupSuggestions(AutoCompleteTextView autoComplete) {
        List<String> groups = new ArrayList<>();
        for (StopConfig c : configList) {
            if (!groups.contains(c.groupName)) groups.add(c.groupName);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, groups);
        autoComplete.setAdapter(adapter);
    }

    // Klasa pomocnicza dla Spinnera (ID i Nazwa)
    private static class Stop {
        String id, name;
        Stop(String id, String name) { this.id = id; this.name = name; }
        // To metoda, której Spinner używa do wyświetlania tekstu na liście
        @Override
        public String toString() {
            if (id.isEmpty()) return name; // Obsługa napisu "Pobieranie..."
            return name + " [" + id + "]";
        }
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