package com.example.skmszczecin;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.view.View;
import android.widget.RemoteViews;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class SKMWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        SharedPreferences prefs = context.getSharedPreferences("SKM_PREFS", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        for (int appWidgetId : appWidgetIds) {
            editor.remove("widget_group_" + appWidgetId);
        }
        editor.apply();
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        new DownloadTask(context, appWidgetManager, appWidgetId).execute();
    }

    private static class DownloadTask extends AsyncTask<Void, Void, List<Departure>> {
        private Context context;
        private AppWidgetManager appWidgetManager;
        private int appWidgetId;
        private String groupName;
        private String debugInfo = ""; // Zmienna do diagnostyki

        public DownloadTask(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
            this.context = context;
            this.appWidgetManager = appWidgetManager;
            this.appWidgetId = appWidgetId;
        }

        @Override
        protected List<Departure> doInBackground(Void... voids) {
            List<Departure> allDepartures = new ArrayList<>();
            try {
                SharedPreferences prefs = context.getSharedPreferences("SKM_PREFS", Context.MODE_PRIVATE);

                // Pobieramy grupę (domyślnie pustą, żeby wymusić konfigurację)
                groupName = prefs.getString("widget_group_" + appWidgetId, "");

                if (groupName.isEmpty()) {
                    debugInfo = "Skonfiguruj widget!";
                    return null;
                }

                String jsonConfig = prefs.getString("full_config", "[]");
                Gson gson = new Gson();
                Type listType = new TypeToken<ArrayList<StopConfig>>(){}.getType();
                List<StopConfig> config = gson.fromJson(jsonConfig, listType);

                if (config == null || config.isEmpty()) {
                    debugInfo = "Brak przystanków w aplikacji";
                    return null;
                }

                boolean groupFound = false;

                for (StopConfig stop : config) {
                    // Ignoruj wielkość liter przy sprawdzaniu grupy
                    if (stop.groupName == null || !stop.groupName.trim().equalsIgnoreCase(groupName.trim())) continue;

                    groupFound = true;
                    String apiUrl = "https://www.zditm.szczecin.pl/api/v1/displays/" + stop.id;
                    String jsonResponse = downloadUrl(apiUrl);

                    if (jsonResponse == null) continue;

                    JSONObject jsonObject = new JSONObject(jsonResponse);
                    if (!jsonObject.has("departures")) continue;

                    JSONArray departures = jsonObject.getJSONArray("departures");
                    for (int i = 0; i < departures.length(); i++) {
                        JSONObject dep = departures.getJSONObject(i);
                        String line = dep.getString("line_number").trim(); // Ważne: trim()

                        // Sprawdzamy czy linia pasuje
                        boolean lineMatches = false;
                        if (stop.lines == null || stop.lines.isEmpty()) {
                            // Jeśli użytkownik nie wpisał linii, pokazujemy wszystkie (opcjonalnie)
                            lineMatches = true;
                        } else {
                            for(String l : stop.lines) {
                                if(l.trim().equalsIgnoreCase(line)) {
                                    lineMatches = true;
                                    break;
                                }
                            }
                        }

                        if (lineMatches) {
                            String timeRaw;
                            if (!dep.isNull("time_real")) {
                                timeRaw = dep.getString("time_real");
                            } else {
                                timeRaw = dep.getString("time_scheduled");
                            }

                            int minutes = calculateMin(timeRaw);

                            allDepartures.add(new Departure(
                                    line,
                                    dep.getString("direction"),
                                    minutes,
                                    timeRaw,
                                    !dep.isNull("time_real")
                            ));
                        }
                    }
                }

                if (!groupFound) {
                    debugInfo = "Brak grupy: " + groupName;
                } else if (allDepartures.isEmpty()) {
                    debugInfo = "Brak kursów dla: " + groupName;
                }

                Collections.sort(allDepartures, (d1, d2) -> Integer.compare(d1.minutes, d2.minutes));

            } catch (Exception e) {
                e.printStackTrace();
                debugInfo = "Błąd: " + e.getMessage();
                return null;
            }
            return allDepartures;
        }

        private String downloadUrl(String urlString) throws Exception {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() != 200) return null;

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            return sb.toString();
        }

        private int calculateMin(String t) {
            // Jeśli API zwraca "12" lub "12 min"
            if (t.matches("^\\d+(\\s*min)?$")) {
                return Integer.parseInt(t.replaceAll("\\D", ""));
            }
            try {
                // Obsługa formatów HH:mm i HH:mm:ss
                SimpleDateFormat sdf = new SimpleDateFormat(t.length() > 5 ? "HH:mm:ss" : "HH:mm", Locale.getDefault());
                Date date = sdf.parse(t);
                Date now = sdf.parse(sdf.format(new Date()));

                if (date != null && now != null) {
                    long diff = date.getTime() - now.getTime();

                    // Obsługa północy (np. jest 23:50, autobus 00:10)
                    if (diff < -12 * 3600 * 1000) {
                        diff += 24 * 3600 * 1000; // Dodaj 24h
                    } else if (diff > 12 * 3600 * 1000) {
                        diff -= 24 * 3600 * 1000; // Odejmij 24h
                    }

                    int min = (int) TimeUnit.MILLISECONDS.toMinutes(diff);
                    return Math.max(0, min);
                }
            } catch (Exception e) { return 999; }
            return 999;
        }

        @Override
        protected void onPostExecute(List<Departure> departures) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

            // Tytuł
            views.setTextViewText(R.id.widgetTitle, groupName != null && !groupName.isEmpty() ? groupName.toUpperCase() : "SKM SZCZECIN");

            // ID wierszy (musi pasować do XML z poprzedniej odpowiedzi)
            int[] rows = {R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5, R.id.row6, R.id.row7, R.id.row8, R.id.row9, R.id.row10};
            int[] lines = {R.id.l1, R.id.l2, R.id.l3, R.id.l4, R.id.l5, R.id.l6, R.id.l7, R.id.l8, R.id.l9, R.id.l10};
            int[] dirs = {R.id.d1, R.id.d2, R.id.d3, R.id.d4, R.id.d5, R.id.d6, R.id.d7, R.id.d8, R.id.d9, R.id.d10};
            int[] times = {R.id.t1, R.id.t2, R.id.t3, R.id.t4, R.id.t5, R.id.t6, R.id.t7, R.id.t8, R.id.t9, R.id.t10};

            // Reset widoku
            for (int id : rows) views.setViewVisibility(id, View.GONE);

            if (departures != null && !departures.isEmpty()) {
                for (int i = 0; i < Math.min(departures.size(), 10); i++) {
                    Departure d = departures.get(i);
                    views.setViewVisibility(rows[i], View.VISIBLE);
                    views.setTextViewText(lines[i], d.line);
                    views.setTextViewText(dirs[i], d.direction); // XML utnie za długie

                    // Nowa logika czasu
                    String timeDisplay;
                    String icon = d.isReal ? "📶" : "";

                    if (d.minutes < 15) {
                        timeDisplay = icon + d.minutes + "m";
                    } else {
                        // Wyciągamy HH:mm z pełnej daty/czasu
                        String cleanTime = d.fullTime;
                        try {
                            if (cleanTime.contains(" ")) cleanTime = cleanTime.split(" ")[1]; // Ucięcie daty
                            if (cleanTime.length() >= 5) cleanTime = cleanTime.substring(0, 5); // Ucięcie sekund
                        } catch(Exception e){}
                        timeDisplay = icon + cleanTime;
                    }
                    views.setTextViewText(times[i], timeDisplay);
                }
            } else {
                // Wyświetl diagnostykę, jeśli brak odjazdów
                views.setViewVisibility(rows[0], View.VISIBLE);
                views.setTextViewText(dirs[0], !debugInfo.isEmpty() ? debugInfo : "Brak odjazdów");
                views.setTextViewText(lines[0], "");
                views.setTextViewText(times[0], "");
            }

            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            views.setTextViewText(R.id.lastUpdate, "Akt: " + sdf.format(new Date()));

            Intent intent = new Intent(context, SKMWidget.class);
            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widgetLayout, pendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    private static class Departure {
        String line, direction, fullTime;
        int minutes;
        boolean isReal;

        Departure(String l, String d, int m, String ft, boolean r) {
            line = l;
            direction = d;
            minutes = m;
            fullTime = ft;
            isReal = r;
        }
    }
}