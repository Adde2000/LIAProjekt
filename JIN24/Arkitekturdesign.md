## Välj ORM.
Spring Data JPA med Hibernate
(Boolean i Java blir konverterat till BIT för Azure SQL)

## Frontend
React och TypeScript

## Deployment-scripts
I dev branchen testas bara koden och när dev mergas in till main testas, byggs till .jar och deployas det.
Vi möter MOV i CI/CD på github actions

Tester kör, sen byggs backend till en .jar och deployas till en, av MOV definierad, server. Frontend byggs sen och deployas till en, av MOV definierad, server
Det är samma steg för både dev- och main-branchen, men de deployas till olika ställen.

## Designa testmotorns låslogik.

### Regler för test och upplåsning:
- Ett avsnitt låses upp först när föregående test resultat är godkänt (om inte första avsnitt)
- Test kan göras om vid underkänt resultat
- Resultatet sparas automatiskt när testet avslutas
- Godkänt resultat defineras som minst 100 procent rätt

### Om användaren stänger webbläsaren:
Då tester kommer vara korta och kräva alla rätt så kommer testet inte sparas föränn det är färdigt och inlämnat.

### Vid avklarat test
- Antal rätt svar räknas ihop och kontrolleras om godkänt
- Vid godkänt så ändras boolean "passed" till true


## Kursregistrerings-flöde
Användare hämtas från EntraId
Användare registreras till kurs av admin
En ny UserProgress, som är kopplad till både den specifika användaren och kursen, skapas.
Denna fungerar som enrollment-entitet.

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
