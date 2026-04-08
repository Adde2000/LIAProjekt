# Vecka 1

## Frågor
- Vad är skillnad mellan Admin och Kursansvarig? Vad ska dem ha för access? Är det Admin kan ändra allt, kursansvarig kan bara ändra sin kurs?
- Vad för typer av tester är det? Flervalsfrågor, skrivfrågor, annat? Om skrivfrågor så behöver kursansvarig rätta innan nästa del låses upp
- Ska kursdeltagare bjudas in till kurser eller se alla och kunna registrera sig för kurser?
- API nyckel till en stateful API för minne till chattbot
- ska vi fylla i dokumentet handledare LIA
- planera spårbarhet? loggning?
- microservices för frontend och backend?


## Kravanalys och user stories
- Kartlägg portalens alla funktionskrav.
- Skriv user stories för administratör, kursansvarig och deltagare.
- Definiera MVP – vad måste finnas i v. 12?
- Välj och motivera frontendramverk och backend-stack (Java / Spring Boot).

### User Stories
- Användare ska kunna logga in och få tillgång till sin användarroll
- Kursansvarig kan ladda upp material och tester
- Deltagare kan komma åt material för en del, och utföra test på den delen
- Deltagare kommer inte åt nästa del utan klarat test på tidigare del
- När deltagare klarar en del så ökas en progressindikator procentuellt till hur många delar det finns.
- E-postnotifieringar vid registrering, avklarat test och slutförd kurs
- Admin kan CRUD för AI-personas
- Deltagare ska kunna ställa frågor till en/flera AI-persona kopplade till kursen

### MVP
1. En administratör kan logga in och skapa en kurs med minst ett avsnitt och ett test.
2. En kursdeltagare kan logga in, ta del av avsnittsinnehållet och genomföra testet.
3. Testet blockerar åtkomst till nästa avsnitt tills det är godkänt.
4. Minst en AI-karaktär svarar på frågor i chatten med ett svar som är kontextuellt relevant.
5. Applikationen körs i molnmiljö – inte på en lokal dator.
6. Entra ID hanterar autentiseringen.
7. Portalen skickar minst ett e-postmeddelande via Exchange Online.

### Ramverk
- Java 21
- Spring Boot
- PostgreSQL / MySQL (relationsdabas) Användare, kurser, material är alla kopplade med relationer. Samma med kopplandet av progression i kurser till deltagare.
- Entra ID som ger oss JWT
- React - React lämpar sig väl för webbapplikationer med dynamiskt innehåll och interaktiva funktioner, såsom hantering av kurser, tester och visning av progression.
- Docker - för att bygga images till pipeline

## Grundfunktioner
- Inloggning med roller: administratör, kursansvarig och kursdeltagare
- Kursadministration: skapa och redigera kurser, avsnitt och material
- Stöd för textbaserat material och inbäddade videofilmer
- Test efter varje avsnitt – deltagaren måste klara testet före upplåsning av nästa avsnitt
- Progressionsspårning per deltagare och kurs
- E-postnotifieringar vid registrering, avklarat test och slutförd kurs

## AI-funktioner
- Chattgränssnitt där deltagaren kan ställa frågor till fiktiva AI-drivna karaktärer kopplade till respektive kurs
- Karaktärerna svarar kontextuellt baserat på kursens innehåll via LLMintegration
  (t.ex. Azure OpenAI eller OpenAI API)
- Valfri utvidgning: röstinmatning(speech-to-text) och talsvar (text- tospeech) för tillgängligare interaktion
- Promptdesign för att styra karaktärernas personlighet, kunskapsområde och gränser

## Primärt ansvar
- Frontend och backend i Java / Spring Boot.
- Databasmodell.
- REST API.
- Autentiseringsflöde mot Entra ID.
- Testmotor med låslogik.
- AI-integration mot LLM-API.
- Eventuell röstfunktion.
- CI/CD-pipeline.