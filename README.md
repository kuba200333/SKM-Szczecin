# SKM Szczecin - Widget Odjazdów ZDiTM 🚌

Prosta i lekka aplikacja na Androida oferująca estetyczny widget z odjazdami komunikacji miejskiej w Szczecinie (ZDiTM) w czasie rzeczywistym.

## 📱 Funkcje

* **Dane na żywo (LIVE):** Pokazuje rzeczywisty czas przyjazdu (z GPS) oznaczony ikoną 📶.
* **Inteligentny format czasu:**
    * Poniżej 15 minut: format minutowy (np. `4m`, `12m`).
    * Powyżej 15 minut: pełna godzina odjazdu (np. `14:35`).
* **Grupy przystanków:** Możliwość zdefiniowania własnych grup (np. "DOM", "PRACA", "UCZELNIA") i przypisania do nich konkretnych linii.
* **Obsługa wielu widgetów:** Możesz dodać wiele widgetów na pulpit (lub stworzyć stos w One UI), a każdy z nich może wyświetlać inną grupę przystanków.
* **Nowoczesny wygląd:** Ciemny motyw, czytelna tabela, brak zbędnych elementów.

## 🛠️ Technologie

* **Język:** Java
* **Platforma:** Android (Min SDK 24)
* **API:** ZDiTM Szczecin (nieoficjalne wykorzystanie publicznych danych)
* **Biblioteki:**
    * `Gson` (parsowanie konfiguracji JSON)
* **Architektura:**
    * `AppWidgetProvider` & `AsyncTask` do obsługi widgetu.
    * `SharedPreferences` do przechowywania konfiguracji grup.

## 🚀 Jak używać

1.  Otwórz aplikację i dodaj swoje ulubione przystanki oraz linie.
2.  Nadaj im nazwę grupy (np. "DWORZEC").
3.  Wyjdź na pulpit i dodaj widget **SKM Szczecin**.
4.  Wybierz grupę, którą chcesz wyświetlać na tym widgecie.
5.  Gotowe! Kliknij w widget, aby odświeżyć dane.

