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
                groupName = prefs.getString("widget_group_" + appWidgetId, "DWORZEC PKP");
                String jsonConfig = prefs.getString("full_config", "[]");

                Gson gson = new Gson();
                Type listType = new TypeToken<ArrayList<StopConfig>>(){}.getType();
                List<StopConfig> config = gson.fromJson(jsonConfig, listType);

                if (config == null) return null;

                for (StopConfig stop : config) {
                    if (groupName == null || !stop.groupName.equalsIgnoreCase(groupName)) continue;

                    String apiUrl = "https://www.zditm.szczecin.pl/api/v1/displays/" + stop.id;
                    String jsonResponse = downloadUrl(apiUrl);
                    if (jsonResponse == null) continue;

                    JSONObject jsonObject = new JSONObject(jsonResponse);
                    if (!jsonObject.has("departures")) continue;

                    JSONArray departures = jsonObject.getJSONArray("departures");
                    for (int i = 0; i < departures.length(); i++) {
                        JSONObject dep = departures.getJSONObject(i);
                        String line = dep.getString("line_number");

                        boolean lineMatches = false;
                        for(String l : stop.lines) {
                            if(l.trim().equalsIgnoreCase(line.trim())) {
                                lineMatches = true;
                                break;
                            }
                        }

                        if (lineMatches) {
                            // Pobieramy czas rzeczywisty LUB rozkładowy
                            String timeRaw = dep.isNull("time_real") ? dep.getString("time_scheduled") : dep.getString("time_real");
                            int minutes = calculateMin(timeRaw);

                            // Przekazujemy surowy czas (np. "2023-12-29 14:35:00") do obiektu
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
                Collections.sort(allDepartures, (d1, d2) -> Integer.compare(d1.minutes, d2.minutes));
            } catch (Exception e) { e.printStackTrace(); return null; }
            return allDepartures;
        }

        private String downloadUrl(String urlString) throws Exception {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            return sb.toString();
        }

        private int calculateMin(String t) {
            if (t.matches("\\d+")) return Integer.parseInt(t);
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                Date date = sdf.parse(t);
                Date now = sdf.parse(sdf.format(new Date()));
                if (date != null && now != null) {
                    long diff = date.getTime() - now.getTime();
                    int min = (int) (diff / 60000);
                    return Math.max(0, min);
                }
            } catch (Exception e) { return 99; }
            return 99;
        }

        @Override
        protected void onPostExecute(List<Departure> departures) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
            views.setTextViewText(R.id.widgetTitle, groupName != null ? groupName.toUpperCase() : "PRZYSTANEK");

            // Mapowanie ID dla 10 wierszy (skrócone nazwy l=line, d=dir, t=time z nowego XMLa)
            int[] rows = {R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5, R.id.row6, R.id.row7, R.id.row8, R.id.row9, R.id.row10};
            int[] lines = {R.id.l1, R.id.l2, R.id.l3, R.id.l4, R.id.l5, R.id.l6, R.id.l7, R.id.l8, R.id.l9, R.id.l10};
            int[] dirs = {R.id.d1, R.id.d2, R.id.d3, R.id.d4, R.id.d5, R.id.d6, R.id.d7, R.id.d8, R.id.d9, R.id.d10};
            int[] times = {R.id.t1, R.id.t2, R.id.t3, R.id.t4, R.id.t5, R.id.t6, R.id.t7, R.id.t8, R.id.t9, R.id.t10};

            // Reset (ukrycie wszystkich)
            for (int id : rows) views.setViewVisibility(id, View.GONE);

            if (departures != null && !departures.isEmpty()) {
                // Pętla do max 10
                for (int i = 0; i < Math.min(departures.size(), 10); i++) {
                    Departure d = departures.get(i);
                    views.setViewVisibility(rows[i], View.VISIBLE);
                    views.setTextViewText(lines[i], d.line);
                    views.setTextViewText(dirs[i], d.direction); // XML ma singleLine=true, więc sam utnie

                    // LOGIKA CZASU (15 min)
                    String timeDisplay;
                    String icon = d.isReal ? "📶 " : "";

                    if (d.minutes < 15) {
                        timeDisplay = icon + d.minutes + "m";
                    } else {
                        // Parsujemy pełną datę żeby wyciągnąć samą godzinę HH:mm
                        try {
                            // ZDiTM zwraca często pełną datę lub samo HH:mm
                            if(d.fullTime.contains(":")) {
                                // Jeśli to format "2023-12-29 14:35:00" lub "14:35"
                                String[] parts = d.fullTime.split(" ");
                                String onlyTime = parts.length > 1 ? parts[1] : parts[0];
                                // Bierzemy tylko 5 pierwszych znaków (HH:mm)
                                if(onlyTime.length() >= 5) onlyTime = onlyTime.substring(0, 5);
                                timeDisplay = icon + onlyTime;
                            } else {
                                timeDisplay = icon + d.minutes + "m"; // Fallback
                            }
                        } catch (Exception e) {
                            timeDisplay = icon + d.minutes + "m"; // Fallback
                        }
                    }
                    views.setTextViewText(times[i], timeDisplay);
                }
            } else {
                views.setViewVisibility(rows[0], View.VISIBLE);
                views.setTextViewText(dirs[0], "Brak odjazdów");
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

    // Klasa modelu
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