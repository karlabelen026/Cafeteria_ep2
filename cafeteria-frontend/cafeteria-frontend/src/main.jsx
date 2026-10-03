import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { CartProvider } from './context/CartContext.jsx';
import { ToastProvider } from './context/ToastContext.jsx';
import App from './App.jsx';
import './index.css';

const authDisabled = import.meta.env.VITE_AUTH_DISABLED === 'true';

async function bootstrap() {
  let AppTree;

  if (authDisabled) {
    // Modo noauth: MSAL nunca se instancia (evita el error crypto_nonexistent en HTTP).
    AppTree = (
      <React.StrictMode>
        <ToastProvider>
          <CartProvider>
            <BrowserRouter>
              <App />
            </BrowserRouter>
          </CartProvider>
        </ToastProvider>
      </React.StrictMode>
    );
  } else {
    // Modo con Azure Entra ID: MSAL se inicializa solo cuando hay HTTPS disponible.
    const { PublicClientApplication, EventType } = await import('@azure/msal-browser');
    const { MsalProvider } = await import('@azure/msal-react');
    const { msalConfig } = await import('./auth/authConfig');

    const msalInstance = new PublicClientApplication(msalConfig);

    // msal-browser v3 exige initialize() antes de cualquier otro metodo. Sin
    // esto (y sin el handleRedirectPromise de abajo), la pagina se pintaba
    // antes de que MSAL alcanzara a procesar la vuelta del redirect de Azure:
    // la sesion quedaba "a medias" hasta la siguiente recarga manual.
    await msalInstance.initialize();

    const result = await msalInstance.handleRedirectPromise().catch((e) => {
      window.__msalError = e;
      console.error('MSAL redirect error', e);
      return null;
    });

    if (result?.account) {
      msalInstance.setActiveAccount(result.account);
    } else if (!msalInstance.getActiveAccount() && msalInstance.getAllAccounts().length > 0) {
      msalInstance.setActiveAccount(msalInstance.getAllAccounts()[0]);
    }

    msalInstance.addEventCallback((event) => {
      if (event.eventType === EventType.LOGIN_SUCCESS && event.payload?.account) {
        msalInstance.setActiveAccount(event.payload.account);
      }
    });

    AppTree = (
      <React.StrictMode>
        <MsalProvider instance={msalInstance}>
          <ToastProvider>
            <CartProvider>
              <BrowserRouter>
                <App />
              </BrowserRouter>
            </CartProvider>
          </ToastProvider>
        </MsalProvider>
      </React.StrictMode>
    );
  }

  ReactDOM.createRoot(document.getElementById('root')).render(AppTree);
}

bootstrap();

