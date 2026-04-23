## Välj ORM.
Spring Data JPA med Hibernate (Motivera)
(Boolean i Java blir konverterat till BIT för Azure SQL)

## Designa testmotorns låslogik.

### Regler för test och upplåsning:
- Ett avsnitt låses upp först när föregående test resultat är godkänt (om inte första avsnitt)
- Test kan göras om vid underkänt resultat
- Resultatet sparas automatiskt när testet avslutas
- Godkänt resultat defineras som minst X procent rätt

### Om användaren stänger webbläsaren:
- Teststatus sparas som "pågående"
- Testet kan återupptas vid nästa inloggning
- inga resultat registreras föränn alla frågar har blivit svarade

### Vid avklarat test
- Antal rätt svar räknas ihop och kontrolleras om godkänt
- Vid godkänt så ändras boolean "passed" till true


## Kursregistrerings-flöde
Användare hämtas från EntraId
Användare registreras till kurs av admin
En ny UserProgress, som är kopplad till både den specifika användaren och kursen, skapas.

## AI session design
#### Stateful:
AiSession lagrar ett externt session-ID från Azure OpenAI (så att systemet kan återuppta samma konversation hos leverantören)

Om en kursdeltagare har använt sessionen i 60 dagar så informeras kursdeltagare att konversationshistoriken är borta och en ny session startas

## Skissa promptdesign för AI-karaktärerna
[BASE SYSTEM PROMPT]
````
"Du är en AI-assistent som svarar enbart baserat på tillhandahållet kursmaterial och en tillhörande persona-beskrivning.
Regler:
- Du får endast använda information som finns i kursmaterialet.
- Om information saknas ska du svara: "Detta finns inte i kursmaterialet."
- Du får inte använda extern kunskap eller gissningar.
- Du ska följa den persona som ges i markdown-filen.
- Du ska svara pedagogiskt och tydligt.
- Du ska ge ett komplett svar direkt, inte i delar eller löpande output.

Om användaren frågar något som inte finns i materialet:
- Svara kort.
- Försök inte resonera vidare."
````
FINAL PROMPT:

[BASE SYSTEM PROMPT]

+ persona

+ kursmaterial

+ user message