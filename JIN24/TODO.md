### Vart bor /aiconversation minnet rent tekniskt?

Tanken med stateful är att den nuvarande konversationen ska minnas av ai:n, så att vi inte behöver skicka all information om karaktären och tidigare meddelanden för varje nytt meddelande

### E-postnotifieringar. Vilken tjänst hanterar själva utskicken?
**Exchange Online** 

Systemet ska kunna skicka automatiska e-postmeddelanden vid definierade händelser, såsom registrering, avklarat test och slutförd kurs.
Utskicken ska hanteras via Microsoft 365-tjänster och kan implementeras med Microsoft Graph API för att säkerställa säker och tillförlitlig leverans.

**NOTE:** Emailservice hanterar logiken och microsoft graph API anropar exchange online
1. Backend (Spring Boot) får en trigger, t.ex. att en deltagare har klarat ett test.
2. Backend använder OAuth2-token = token från Azure AD (Entra ID) för att autentisera mot Graph API.
3. Backend gör ett REST-anrop till Graph API med e-postinnehållet.
4. Microsoft Graph skickar e-posten via Exchange Online.
### Entra ID - efter inloggning hur vet system vilken roll de har? kommer det från Entra ID eller databasen?

Systemet ska använda extern autentisering via Microsoft Entra ID för att verifiera användarens identitet vid inloggning. Efter lyckad inloggning ska systemet hämta användarens roll och behörigheter från den interna databasen.

Roller ska lagras och hanteras i systemets databas och kopplas till respektive användare. Exempel på roller är administratör, kursansvarig och kursdeltagare. Behörigheter i systemet ska baseras på dessa roller och styra åtkomst till funktioner och innehåll.

Systemet ska möjliggöra att roller kan ändras och administreras via systemets administrationsfunktioner utan att ändringar behöver göras i det externa autentiseringssystemet.

### Test-och-upplåsningsmekaniken - vad händer om en deltagare stänger webbläsaren mitt i ett test? Är det ett scenario som behöver definieras nu, eller kan det vänta?
Ett Edge-case. 
Definiera: **Vad händer om ett test avbryts?**
- testet fortsätter där man slutade med hjälp av autosave? 
- testet avbryts och måste göras om?

### Dataformat för AI-personas — ni noterade "markdown, json?" som en öppen fråga. Värt att stänga den tidigt, eftersom promptdesign och CRUD-funktionaliteten för personas beror på det. Hur lagras personlighetsbeskrivningen och kunskapsgränserna — ett databasfält, en fil, något annat?
Lagras som markdown filer

### Har ni en plan för projektets struktur? Inte bara features och krav, utan hur koden faktiskt är uppbyggd — mappar, lager, vad som bor var. Det är lätt att missa i planeringsfasen men kan spara mycket tid när ni är flera som jobbar parallellt.
**Lagerarkitektur** (`controller → service → repository`)

### Versionshantering och samarbete
Ni nämnde att GitHub används för kod och dokumentation. Har ni funderat på om JIN och MOV ska ha separata repos eller ett gemensamt? I ett riktigt projekt brukar ett samlat repo (eller monorepo) göra det lättare att hålla ihop API-kontraktet och undvika att saker hamnar ur synk.

En annan tanke: ni kör redan i Azure-ekosystemet — är det ett alternativ att titta på Azure DevOps (dev.azure.com) för versionshantering och CI/CD? Det integrerar naturligt med resten av er Azure-setup och är inkluderat i Microsoft-licensen ni redan har. Inget måste, men värt att lyfta.

- Prata med MOV om gemensamt repo och Azure DevOps.

### **React med Spring Boot** — Spring Boot har inbyggt stöd för server-side templates (t.ex. Thymeleaf).
Vaadin kan vara ett bra alternativ för oss.
Vaadin möjliggör utveckling av webbgränssnitt med Java och integreras direkt med systemets backend, vilket förenklar utveckling och underhåll.
Ramverket lämpar sig väl för system med formulärbaserade funktioner såsom kursadministration, användarhantering och progressionsspårning.
Även stöd för chat finns inbyggt.

#### Positivt
- allt skrivs i java
- lätt att ändra efter behov
- snabbare utveckling
- Spring security integrerat
- lägre frontend komplexitet än react

#### Negativt
- mer serverbelastning
- lite mer arbete att byta ut mot annat ramverk senare



