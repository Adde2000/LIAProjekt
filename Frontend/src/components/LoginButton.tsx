import type { IPublicClientApplication } from "@azure/msal-browser";
import { loginRedirect } from "../auth/authRedirect";

export default function LoginButton({ instance }: { instance: IPublicClientApplication }) {

    return (
        <button onClick={() => loginRedirect(instance)}>
            Login
        </button>
    );
}