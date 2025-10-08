## 🎮 Új funkciók és rendszerek

-   **Gadget rendszer elkészítése** külön `GadgetSystem` osztályba szervezve\
    → Tisztább `update()` és `render()` logika.\
    → Kezeli az *Orbit Blade* és *Laser* működését.

-   **Hangkezelés (SoundManager)**

    -   Hozzáadva: `isPlaying(name)` → ellenőrzi, hogy szól-e a hang.\
    -   Új metódus: `playOverlap(name)` → ugyanaz a hang többször is,
        átfedésben lejátszható.

-   **Hangok**

    -   Új hangok hozzáadása

-   **LevelUp menü vizuális frissítés**

    -   Szöveges szintjelző helyett **sárga/fekete négyzetek**.\
    -   Aktuális szint + következő szint külön színnel (halvány sárga).

    **Játékmenet változtatás**

    -   Percenként több ellenség jön
    -   Minden harmadik percben erősödnek az ellenségek

    **UI**

    -   Jobb felső sarokban idő számláló



    **Kód szétdarabolása külön osztályokba, mert már nem volt átlátható az 1500sor(majdnem belerokkantam)**


## További feladatok 

-   **További gadgetek vagy powerupok hozzáadása (opcionális)**
-   **Bossfight pl a 10. és 15.percben vagy bizonyos számú elért pontnál**
-   **Kitalálni mikor nyerünk utolsó bossfight vagy bizonyos pont elérése**
-   **Grafikai elemek elhelyezése az alakzatok helyett**
-   **Még bármi ami eszünkbe jut**



