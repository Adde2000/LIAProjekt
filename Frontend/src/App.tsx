import HealthCheck from "./components/HealthCheck";

function App() {

  return (
      <div>

        <h1>LIA Project</h1>

        {/* Testar backend connection */}
        <HealthCheck />

      </div>
  );
}

export default App;