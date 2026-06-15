# Användarmanualer — Lärportal Högsbo Säljkonsulter AB

---

# Del 1: Administratör

> Som administratör har du full kontroll över hela lärportalen. Du kan skapa och ta bort kurser, hantera alla användare och koppla AI-assistenter till kurser. Det är viktigt att du är försiktig — ändringar du gör påverkar alla användare.

---

## 1. Logga in

För att komma åt lärportalen behöver du logga in med ditt vanliga Email konto — samma lösenord som du använder för din e-post.

1. Öppna lärportalen i din webbläsare (t.ex. Chrome eller Edge).
2. Klicka på knappen "Logga in".
3. Du skickas till Microsofts inloggningssida — ange din Email och ditt lösenord.
4. Du är nu inloggad och ser adminpanelen.

> 💡 **Tips:** Har du problem att logga in? Kontakta er IT-ansvarig — de kan hjälpa till med kontoinställningar.

---

## 2. Vad ser du när du loggat in?

Högst upp på sidan ser du en meny med flera flikar. Som administratör har du tillgång till dessa:

- **Admin** — din huvudpanel för hantering av allt
- **Användare** — hantera användare
- **Ny Kurs** — Skapa ny kurs och sätt kursledare
- **Hantera Kurser** — se alla kurser i systemet och hantera innehåll
- **AI-assistants** — Lista på alla AI-assistants

---

## 3. Hantera användare

Under Användare-fliken hittar du en lista med alla registrerade användare.

### 3.1 Se alla användare

1. Klicka på "Admin" i menyn högst upp.
2. Klicka på "Användare" i adminpanelen.
3. Nu ser du en lista med alla personer som finns i systemet, med deras namn, Email och roll.

### 3.2 Roller — vad får varje person göra?

| Roll              | Vad personen kan göra                                                |
|-------------------|----------------------------------------------------------------------|
| **Administratör** | Allt — skapa/ta bort kurser, hantera alla användare, koppla AI       |
| **Kursadmin**     | Hantera sina egna kurser — lägga till material, frågor och studenter |
| **Deltagare**     | Läsa sina kurser, göra quiz och använda AI-chatten                   |

> ⚠️ **OBS:** Roller sätts via Microsoft Entra ID (ert Azure-konto). Kontakta er IT-ansvarig om du behöver ändra en persons roll.

---

## 4. Skapa en ny kurs

1. Klicka på "Admin" i toppmenyn.
2. Välj "Ny kurs".
3. Fyll i kursens namn och beskrivning.
4. Välj vem som ska vara kursledare (Kursadmin) för kursen.
5. Klicka på "Skapa kurs". Kursen är nu skapad och den kursansvarige kan börja lägga till material.

> 💡 **Tips:** Ge kursen ett tydligt och beskrivande namn så att deltagarna lätt förstår vad den handlar om.

---

## 5. Ta bort en kurs

> ⛔ **Varning:** När du tar bort en kurs försvinner allt innehåll — sektioner, filer, frågor och deltagarnas resultat. Detta går inte att ångra.

1. Gå till "Admin" → "Hantera kurser".
2. Hitta kursen du vill ta bort i listan.
3. Klicka på "Ta bort" på högra sidan av kursen.
4. Bekräfta borttagningen i rutan som dyker upp.

---

## 6. Koppla en AI-assistent till en kurs

Varje kurs kan kopplas till en AI-assistent som hjälper deltagarna att ställa frågor om kursmaterialet.

1. Gå till "Admin" → "Hantera kurser".
2. Klicka på kursen du vill koppla en assistent till.
3. Under "AI Assistant" i kursen kommer det stå antingen "Välj AI Assistant" eller ett namn i en dropdown beroende på om det finns en assistant kopplad till kursen
4. Välj önskad assistent från listan.
5. Deltagare i kursen kan nu chatta med AI-assistenten.

> 💡 **Tips:** Assistenterna är skapade och hanterade i Azure OpenAI. Kontakta IT om du behöver en ny assistent skapad.

---

## 7. Lägga till deltagare i en kurs

1. Gå till "Admin" → "Hantera kurser".
2. Klicka på kursen.
3. Under "Inregistrerade studenter", klicka "Lägg till studenter"
4. Bocka i de studenter du vill lägga till i kursen och klicka sedan "Bekräfta"
5. Studenterna får nu tillgång till kursen nästa gång de loggar in.

---

## 8. Logga ut <---------------------------------------------------------------------------------

Klicka på ditt namn eller profilikon uppe till höger och välj "Logga ut". Stäng sedan webbläsarfliken.

---
---

# Del 2: Kursadministratör

> Som kursadministratör ansvarar du för dina egna kurser. Du kan lägga till lektioner och material, skapa quiz och se hur deltagarna klarar sig — men du kan inte ändra andra kurser eller hantera alla användare i systemet.

---

## 1. Logga in

Logga in med ditt vanliga jobbkonto — samma som din e-post på jobbet.

1. Öppna lärportalen i din webbläsare.
2. Klicka på "Logga in".
3. Ange din jobbmejl och ditt lösenord på Microsofts inloggningssida.
4. Du är nu inne och ser dina kurser i menyn.

---

## 2. Vad ser du när du loggat in?

Längst upp ser du en meny. Som kursadministratör har du tillgång till:

- **Kurser** — se dina kurser som deltagare ser dem
- **Admin** — din panel för att redigera kurser och se resultat

---

## 3. Se och hantera dina kurser

1. Klicka på "Admin" i toppmenyn.
2. Välj "Hantera kurser". Här ser du de kurser du ansvarar för.
3. Klicka på en kurs för att öppna den och se dess innehåll.

---

## 4. Lägga till Avsnitt (sektioner)

En kurs är uppdelad i avsnitt som kallas sektioner. Skapa dem i den ordning du vill att progressionen av kursen ska gå.

1. Öppna din kurs under Admin → Hantera kurser.
2. Klicka på "Lägg till avsnitt"
3. Skriv in lektionens namn.
5. Klicka "Lägg till".

> 💡 **Tips:** Håll lektionerna korta och fokuserade. En lektion = ett tydligt ämne gör det lättare för deltagarna att följa med.

---

## 5. Ladda upp kursmaterial

Till varje lektion kan du ladda upp filer som deltagarna ska ta del av. Det kan vara PDF-dokument eller videofiler.

1. Öppna kursen du vill lägga till material för.
2. Klicka på det avsnitt du vill lägga upp material för.
3. Klicka på "Ladda upp material".
4. Välj filen från din dator.
5. Vänta tills uppladdningen är klar — du ser ett meddelande när det är klart.

> ℹ️ Filerna lagras säkert i molnet och är bara tillgängliga för deltagare som är anmälda till kursen.

---

## 6. Skapa ett quiz

Efter varje avsnitt kan du lägga till frågor för att testa att deltagarna förstått innehållet.

### 6.1 Lägg till en fråga

1. Öppna avsnittet du vill lägga till frågor för.
2. Klicka på "Hantera quiz"
3. Fyll i frågan och svarsalternativ
4. Markera vilket svar som är rätt till vänster om det rätta svaret.
5. Klicka "Spara fråga".

### 6.2 Redigera eller ta bort en fråga

1. Hitta frågan i listan och uppe i högra hörnet av frågan klicka på "-" för att redigera eller "x" för att ta bort.
2. Gör dina ändringar och spara.

> 💡 **Tips:** Skriv frågor på ett enkelt och tydligt språk. Undvik fällor eller luriga formuleringar — målet är att deltagarna lär sig, inte att de ska misslyckas.

---

## 7. Följa deltagarnas framsteg

Du kan se hur långt varje deltagare har kommit i kursen och hur de klarat quizen.

1. Gå till "Admin" → "Hantera kurser" och öppna din kurs.
2. Klicka på fliken "Studenter".
3. Här ser du en lista med deltagare och hur stor andel av kursen de har slutfört (i procent).

> 💡 **Tips:** Om en deltagare verkar ha fastnat kan du kontakta dem direkt och erbjuda hjälp.

---

## 8. Lägga till deltagare i kursen

1. Gå till "Admin" → "Hantera kurser".
2. Klicka på kursen.
3. Under "Inregistrerade studenter", klicka "Lägg till studenter"
4. Bocka i de studenter du vill lägga till i kursen och klicka sedan "Bekräfta"
5. Studenterna får nu tillgång till kursen nästa gång de loggar in.

---

## 9. AI-assistenten i kursen

Om en AI-assistent är kopplad till din kurs kan deltagarna ställa frågor direkt i en chatt. Assistenten svarar baserat på det material som finns i kursen.

Du kan se och välja vilken assistent som är kopplad till din kurs under "Hantera kurser" och välja kurs.

---

## 10. Logga ut <-------------------------------------------------------------------------

Klicka på ditt namn eller profilikon uppe till höger och välj "Logga ut". Stäng sedan webbläsarfliken.

---
---

# Del 3: Deltagare

> Välkommen till lärportalen! Här hittar du dina kurser, allt kursmaterial och quiz. Du kan också chatta med en AI-assistent som hjälper dig med frågor om kursinnehållet.

---

## 1. Logga in

Du loggar in med samma jobbkonto som du använder till din e-post — inget extra lösenord behövs.

1. Öppna lärportalen i din webbläsare (t.ex. Chrome eller Edge).
2. Klicka på knappen "Logga in".
3. Du skickas till en inloggningssida från Microsoft — ange din jobbmejl och ditt lösenord.
4. Du är nu inloggad och ser dina kurser direkt.

> 💡 **Tips:** Har du problem att logga in? Hör av dig till din chef eller den som ansvarar för kursen.

---

## 2. Hitta dina kurser

När du loggat in hamnar du direkt på kurssidan. Här ser du alla kurser du är anmäld till som kort med kursens namn och en kort beskrivning.

1. Du ser dina kurser listade. Varje kurs visar hur långt du kommit (i procent).
2. Klicka på en kurs för att öppna den.


> 💡 **Tips:** Ser du inte en kurs du förväntar dig? Kontakta den som är ansvarig för kursen — du kanske inte blivit tillagd ännu.

---

## 3. Gå igenom en kurs

Inne i en kurs ser du en lista med avsnitt. Avsnitten är numrerade och du börjar uppifrån.

### 3.1 Öppna en lektion

1. Klicka på det första avsnittet i listan.
2. avsnitt visar en lista med allt material för det avsnittet — dokument och eventuella videor.
3. Klicka på det material du vill läsa eller titta på.

### 3.2 Läsa ett dokument (PDF)

1. Klicka på dokumentet i avsnittet.
2. Dokumentet öppnas direkt i portalen eller laddas ned till din dator.

### 3.3 Titta på en video

1. Klicka på videon i avsnittet.
2. Videon startar i portalen. Använd play/paus-knappen och volymreglaget precis som på YouTube.

> ℹ️ Vissa avsnitt kan vara låsta tills du slutfört den föregående. Uppnå 100% på quiz på ett avsnitt för att låsa upp nästa.

---

## 4. Göra ett quiz

Efter ett avsnitt kan det finnas ett quiz — ett kort test med flervalsfrågor. Quizet hjälper dig att kolla att du förstått innehållet.

1. Klicka på "Ta Quiz" i avsnittet.
2. Läs frågan noga.
3. Klicka på det svar du tror är rätt.
4. Fortsätt med alla frågor.
5. Klicka på "Lämna in" när du är klar.
6. Du får direkt se hur många rätt du hade och om du klarat quizet.

> 💡 **Tips:** Klarade du inte quizet? Inga problem — läs igenom materialet igen och försök på nytt. Det finns inget maxantal försök.

---

## 5. Chatta med AI-assistenten

Om kursen har en AI-assistent kopplad till sig kan du ställa frågor om kursmaterialet direkt i en chatt — precis som att skicka ett textmeddelande.

1. Klicka på "Öppna AI-assistenten" i kursen precis över avsnittslistan.
2. Skriv din fråga i textfältet längst ned.
3. Tryck Enter eller klicka på skickaknappen.
4. Assistenten svarar inom några sekunder.

> ℹ️ AI-assistenten känner till innehållet i just din kurs och svarar utifrån det. Den kan inte svara på frågor utanför kursmaterialet.

> 💡 **Tips:** Skriv dina frågor på vanlig svenska, som om du frågade en kollega. Ju tydligare fråga, desto bättre svar.

---

## 6. Se din framsteg

Du kan alltid se hur långt du har kommit i en kurs.

1. Gå till "Kurser" i menyn.
2. På varje kurskort ser du en procentandel — det visar hur stor del av kursen du har slutfört.

---

## 7. Vanliga frågor

**Jag ser inte min kurs — vad gör jag?**
Du kanske inte blivit tillagd i kursen än. Kontakta kursansvarig.

**Avsnittet är låst — varför?**
Alla kurser är upplagda så att du måste slutföra ett avsnitt innan nästa öppnas. Slutför föregående avsnitt för att gå vidare.

**Jag klarade inte quizet — kan jag försöka igen?**
Ja! Du kan göra om quizet hur många gånger du vill. Läs igenom materialet igen innan du försöker.

**Videon spelar inte upp — vad gör jag?**
Prova att uppdatera sidan (tryck F5 på tangentbordet). Fungerar det fortfarande inte, prova en annan webbläsare som Chrome eller Edge.

**Jag kommer inte ihåg mitt lösenord**
Lösenordet är detsamma som till din Email. Kontakta IT-support om du behöver återställa det.

---

## 8. Logga ut <------------------------------------------------------

Klicka på ditt namn eller profilikon uppe till höger och välj "Logga ut". Det är bra att logga ut om du delar dator med någon annan.