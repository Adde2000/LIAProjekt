import type { IPublicClientApplication } from "@azure/msal-browser";
import { loginRedirect } from "../auth/authRedirect";
import vinkelbodaLogo from "../assets/vinkelboda_logo_vektor .svg";
import "../styles/login.css";

export default function LoginButton({ instance }: { instance: IPublicClientApplication }) {

    return (
        <div className="vmv-login-page">
            <header className="vmv-header">
                <img src={vinkelbodaLogo} alt="Vinkelboda logotyp" className="vmv-logo" />
            </header>

            <main className="vmv-login-main">
                <p className="vmv-login-eyebrow">Välkommen</p>
                <button
                    className="vmv-login-btn"
                    onClick={() => loginRedirect(instance)}
                >
                    Logga in med Microsoft
                </button>
            </main>
        </div>
    );
}