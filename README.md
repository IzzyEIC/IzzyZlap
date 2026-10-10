# Złap

Aplikacja na Androida do łapania rzeczy, które wpadają w trakcie dnia („odpiszę później”), i do upartego przypominania o nich wieczorem, aż będą zrobione.

## Jak działa

- Wszystko, co złapiesz, trafia na **dziś**. Możesz też wybrać jutro albo inny dzień.
- Od **18:00** przychodzi **jedno** zbiorcze powiadomienie: „Masz N rzeczy do zrobienia”.
- Dopóki lista nie jest pusta, powiadomienie wraca **co 15 minut**, do **21:00**.
- Niezrobione rzeczy przechodzą na następny dzień jako **zaległe**.
- Pusta lista oznacza ciszę.
- Lista jest zapisana w bazie na telefonie, a po restarcie przypomnienia ustawiają się same.
- Godziny i odstęp zmienisz w ustawieniach (⚙️).

## Instalacja na telefonie

1. Na telefonie otwórz **github.com/IzzyEIC/IzzyZlap/releases/latest**.
2. Pobierz plik `Zlap-NN.apk` i otwórz go.
3. Za pierwszym razem telefon zapyta, czy zezwolić przeglądarce na instalację aplikacji. Zezwól.
4. Otwórz Złap, stuknij czerwony baner **„Dokończ konfigurację telefonu”** i przejdź wszystkie kroki.
5. Na końcu użyj przycisku **„Próbne przypomnienie za 1 minutę”**, zablokuj telefon i poczekaj.

Każda zmiana w kodzie wypchnięta na `main` automatycznie buduje nową wersję w zakładce Releases. Nową wersję instalujesz tak samo, a lista zadań zostaje.

## Klucz podpisu (jednorazowo)

Aplikacja musi być zawsze podpisana tym samym kluczem, inaczej telefon nie pozwoli zainstalować aktualizacji bez odinstalowania (czyli bez utraty listy). Klucz **nie jest w repo**: leży lokalnie w folderze `IzzyZlap-klucz` obok repo, a GitHub dostaje go jako sekrety:

1. Wejdź w repo na GitHubie → **Settings → Secrets and variables → Actions → New repository secret**.
2. Nazwa `ZLAP_KEYSTORE_BASE64`, wartość: cała zawartość pliku `IzzyZlap-klucz/ZLAP_KEYSTORE_BASE64.txt`.
3. Drugi sekret: nazwa `ZLAP_KEYSTORE_PASSWORD`, wartość: zawartość `IzzyZlap-klucz/ZLAP_KEYSTORE_PASSWORD.txt`.
4. Zakładka **Actions → Build APK → Run workflow**.

Nie usuwaj folderu `IzzyZlap-klucz`. To jedyna kopia klucza.

## Plan

| Etap | Zakres | Status |
|---|---|---|
| 1 | Lista, ręczne dodawanie, przypomnienia 18:00–21:00 co 15 min, odporność na restart, konfiguracja Xiaomi | ✅ |
| 2 | Kafelek „Złap” w szybkich ustawieniach i łapanie z listy ostatnich powiadomień jednym tapnięciem | ⏳ |
| 3 | „Udostępnij → Złap”, otwieranie aplikacji źródłowej po kliknięciu zadania | ⏳ |
| 4 | Opcjonalnie: pływająca bańka | ? |

## Technicznie

Kotlin, Jetpack Compose, Room, AlarmManager (dokładne alarmy). APK budują GitHub Actions (`.github/workflows/build.yml`).
