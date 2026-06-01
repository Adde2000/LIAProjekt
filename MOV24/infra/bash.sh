#!/bin/bash
# =============================================================================
# setup-prod.sh
# Sätter upp prod-miljö (rg-app-prod) baserat på rg-app-dev
#
# Förutsättningar:
#   - Azure CLI installerat och inloggad (az login)
#   - Behörighet att skapa resurser i rg-app-prod
#
# Redan skapade (hoppas över om de finns):
#   - Static Web App (swa-app-prod)        → skapas om den saknas
#   - App Service + App Service Plan       → skapas om de saknas
#   - Autoskalningsregler                  → skapas om App Service skapas
#   - Application Insights                 → skapas om den saknas
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# KONFIGURATION – justera dessa värden innan körning
# ---------------------------------------------------------------------------
RG="rg-app-prod"
DEV_RG="rg-app-dev"           # Källmiljö att kopiera inställningar från
LOCATION="westeurope"
ENV="prod"

# SQL
SQL_SERVER="sqlsrv-app-${ENV}"
SQL_DB="sqldb-app-${ENV}"
SQL_ADMIN_USER="LIAProjektSQL"
SQL_ADMIN_PASSWORD="L&95$Qn#v432GV4qCvJq"          # Fyll i eller hämta från KeyVault/env-variabel

# Storage
STORAGE_ACCOUNT="storageapp${ENV}01"   # Max 24 tecken, endast lowercase

# Key Vault
KEY_VAULT="kv-app-${ENV}01"

# Service Bus
SERVICE_BUS="sb-app-${ENV}01"

# OpenAI
OPENAI_ACCOUNT="oai-app-${ENV}01"

# Front Door
AFD_PROFILE="fd-video-app-${ENV}"
AFD_ENDPOINT="video-endpoint"

# VNet
VNET="vnet-${ENV}"
SUBNET_APP="snet-app"
SUBNET_PRIVATE="snet-private"
VNET_ADDRESS="10.0.0.0/16"
SUBNET_APP_PREFIX="10.0.1.0/24"
SUBNET_PRIVATE_PREFIX="10.0.2.0/24"
SUBNET_DEFAULT="default"
SUBNET_DEFAULT_PREFIX="10.0.0.0/24"

# Static Web App (frontend)
SWA_NAME="swa-app-${ENV}"
SWA_LOCATION="$LOCATION"

# App Service Plan + Backend
ASP_NAME="asp-app-${ENV}"
ASP_SKU="S1"                       # Standard-nivå
BACKEND_APP_NAME="app-${ENV}-api"
BACKEND_APP_RUNTIME="JAVA:21:Java SE:21"  # Java 21

# Autoskalning (används om App Service skapas)
AUTOSCALE_MIN=1
AUTOSCALE_MAX=5
AUTOSCALE_DEFAULT=1
AUTOSCALE_CPU_OUT=70               # Skala ut när CPU > 70%
AUTOSCALE_CPU_IN=30                # Skala in när CPU < 30%

# Function App (Service Bus worker)
FUNCTION_APP_NAME="fasb-app-${ENV}"
FUNCTION_RUNTIME="java"
FUNCTION_RUNTIME_VERSION="21"
FUNCTION_STORAGE="${STORAGE_ACCOUNT}" # Delar storage med övriga prod-resurser

# Managed Identity
MSI_NAME="oidc-msi-${ENV}"

# Alerts – e-post för notifieringar
ALERT_EMAIL=""                 # Fyll i

# Backend App Service URL (behövs för availability test)
BACKEND_APP_URL=""             # t.ex. https://app-prod-api.azurewebsites.net/health
FRONTEND_APP_URL=""


# Dev-resursnamn (används vid kopiering av inställningar)
DEV_BACKEND_APP="app-dev-api"
DEV_SERVICE_BUS="sb-app-dev01"
DEV_SQL_SERVER="sqlsrv-app-dev"
DEV_SQL_DB="sqldb-app-dev"
DEV_STORAGE="storageappdev01"
DEV_OPENAI="oai-app-dev01"
DEV_AFD_PROFILE="fd-video-app-dev"
DEV_FUNCTION_APP="fasb-app-dev"

# ---------------------------------------------------------------------------
# HJÄLPFUNKTION
# ---------------------------------------------------------------------------
log() { echo -e "\n\033[1;36m==>\033[0m $*"; }

# Säkerställ att resursgruppen finns
if az group show --name "$RG" &>/dev/null; then
  echo "Resursgrupp '$RG' finns redan – fortsätter."
else
  echo "Skapar resursgrupp '$RG'..."
  az group create \
    --name "$RG" \
    --location "$LOCATION"
fi

# ---------------------------------------------------------------------------
# 1. MANAGED IDENTITY
# ---------------------------------------------------------------------------
log "1. Skapar Managed Identity..."
az identity create \
  --name "$MSI_NAME" \
  --resource-group "$RG" \
  --location "$LOCATION"

MSI_ID=$(az identity show --name "$MSI_NAME" --resource-group "$RG" --query id -o tsv)
MSI_PRINCIPAL=$(az identity show --name "$MSI_NAME" --resource-group "$RG" --query principalId -o tsv)

# ---------------------------------------------------------------------------
# 2. STATIC WEB APP (frontend) – skapas om den inte redan finns
# ---------------------------------------------------------------------------
log "2. Kontrollerar Static Web App..."

if az staticwebapp show --name "$SWA_NAME" --resource-group "$RG" &>/dev/null; then
  echo "  Static Web App '$SWA_NAME' finns redan – hoppar över."
else
  echo "  Skapar Static Web App '$SWA_NAME'..."
  az staticwebapp create \
    --name "$SWA_NAME" \
    --resource-group "$RG" \
    --location "$SWA_LOCATION" \
    --sku Standard
fi

# ---------------------------------------------------------------------------
# 3. APP SERVICE PLAN + BACKEND APP SERVICE + AUTOSKALNING
#    Skapas om de inte redan finns
# ---------------------------------------------------------------------------
log "3. Kontrollerar App Service Plan och backend..."

APP_SERVICE_CREATED=false

if az appservice plan show --name "$ASP_NAME" --resource-group "$RG" &>/dev/null; then
  echo "  App Service Plan '$ASP_NAME' finns redan – hoppar över."
else
  echo "  Skapar App Service Plan '$ASP_NAME'..."
  az appservice plan create \
    --name "$ASP_NAME" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --sku "$ASP_SKU" \
    --is-linux
fi

if az webapp show --name "$BACKEND_APP_NAME" --resource-group "$RG" &>/dev/null; then
  echo "  App Service '$BACKEND_APP_NAME' finns redan – hoppar över."
else
  echo "  Skapar App Service '$BACKEND_APP_NAME'..."
  az webapp create \
    --name "$BACKEND_APP_NAME" \
    --resource-group "$RG" \
    --plan "$ASP_NAME" \
    --runtime "$BACKEND_APP_RUNTIME" \
    --assign-identity "$MSI_ID"

  APP_SERVICE_CREATED=true

  # Sätt alltid HTTPS-only och minimalt TLS
  az webapp update \
    --name "$BACKEND_APP_NAME" \
    --resource-group "$RG" \
    --https-only true

  az webapp config set \
    --name "$BACKEND_APP_NAME" \
    --resource-group "$RG" \
    --min-tls-version 1.2
fi

# Autoskalning – skapas bara om App Service skapades i detta körning
if [ "$APP_SERVICE_CREATED" = true ]; then
  echo "  Skapar autoskalningsregler..."
  ASP_ID=$(az appservice plan show --name "$ASP_NAME" --resource-group "$RG" --query id -o tsv)

  az monitor autoscale create \
    --name "autoscale-backend-${ENV}" \
    --resource-group "$RG" \
    --resource "$ASP_ID" \
    --min-count "$AUTOSCALE_MIN" \
    --max-count "$AUTOSCALE_MAX" \
    --count "$AUTOSCALE_DEFAULT"

  AUTOSCALE_ID=$(az monitor autoscale show \
    --name "autoscale-backend-${ENV}" --resource-group "$RG" --query id -o tsv)

  # Skala ut: CPU > AUTOSCALE_CPU_OUT% i 5 min → +1 instans
  az monitor autoscale rule create \
    --autoscale-name "autoscale-backend-${ENV}" \
    --resource-group "$RG" \
    --scale out 1 \
    --condition "CpuPercentage > ${AUTOSCALE_CPU_OUT} avg 5m" \
    --cooldown 5

  # Skala in: CPU < AUTOSCALE_CPU_IN% i 5 min → -1 instans
  az monitor autoscale rule create \
    --autoscale-name "autoscale-backend-${ENV}" \
    --resource-group "$RG" \
    --scale in 1 \
    --condition "CpuPercentage < ${AUTOSCALE_CPU_IN} avg 5m" \
    --cooldown 10
else
  echo "  Autoskalning hoppas över (App Service fanns redan)."
fi


# ---------------------------------------------------------------------------
# 3b. FUNCTION APP (Service Bus worker) – skapas om den inte redan finns
# ---------------------------------------------------------------------------
log "3b. Kontrollerar Function App..."

# Flex Consumption hanterar sin egen plan – ingen separat az appservice plan create
if az functionapp show --name "$FUNCTION_APP_NAME" --resource-group "$RG" &>/dev/null; then
  echo "  Function App '$FUNCTION_APP_NAME' finns redan – hoppar över."
else
  echo "  Skapar Function App '$FUNCTION_APP_NAME' (Flex Consumption)..."
  az functionapp create \
    --name "$FUNCTION_APP_NAME" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --runtime "$FUNCTION_RUNTIME" \
    --runtime-version "$FUNCTION_RUNTIME_VERSION" \
    --storage-account "$FUNCTION_STORAGE" \
    --flexconsumption-location "$LOCATION" \
    --assign-identity "$MSI_ID"

  az functionapp config set \
    --name "$FUNCTION_APP_NAME" \
    --resource-group "$RG" \
    --min-tls-version 1.2

  az functionapp update \
    --name "$FUNCTION_APP_NAME" \
    --resource-group "$RG" \
    --set httpsOnly=true
fi

# ---------------------------------------------------------------------------
# 4. VIRTUAL NETWORK + NSG:er
# ---------------------------------------------------------------------------
log "4. Skapar VNet och NSG:er..."

az network nsg create \
  --name "nsg-app" \
  --resource-group "$RG" \
  --location "$LOCATION"

az network nsg create \
  --name "nsg-private" \
  --resource-group "$RG" \
  --location "$LOCATION"

az network vnet create \
  --name "$VNET" \
  --resource-group "$RG" \
  --location "$LOCATION" \
  --address-prefixes "$VNET_ADDRESS"

az network vnet subnet create \
  --name "$SUBNET_DEFAULT" \
  --vnet-name "$VNET" \
  --resource-group "$RG" \
  --address-prefixes "$SUBNET_DEFAULT_PREFIX"

az network vnet subnet create \
  --name "$SUBNET_APP" \
  --vnet-name "$VNET" \
  --resource-group "$RG" \
  --address-prefixes "$SUBNET_APP_PREFIX" \
  --network-security-group "nsg-app"

az network vnet subnet create \
  --name "$SUBNET_PRIVATE" \
  --vnet-name "$VNET" \
  --resource-group "$RG" \
  --address-prefixes "$SUBNET_PRIVATE_PREFIX" \
  --network-security-group "nsg-private" \
  --disable-private-endpoint-network-policies true

VNET_ID=$(az network vnet show --name "$VNET" --resource-group "$RG" --query id -o tsv)
SUBNET_PRIVATE_ID=$(az network vnet subnet show \
  --name "$SUBNET_PRIVATE" --vnet-name "$VNET" --resource-group "$RG" \
  --query id -o tsv)

# ---------------------------------------------------------------------------
# 3. KEY VAULT + Private Endpoint
# ---------------------------------------------------------------------------
log "5. Skapar Key Vault..."

az keyvault create \
  --name "$KEY_VAULT" \
  --resource-group "$RG" \
  --location "$LOCATION" \
  --sku standard \
  --enable-rbac-authorization true \
  --public-network-access Disabled

KV_ID=$(az keyvault show --name "$KEY_VAULT" --resource-group "$RG" --query id -o tsv)

# Ge MSI åtkomst till KV
az role assignment create \
  --role "Key Vault Secrets User" \
  --assignee "$MSI_PRINCIPAL" \
  --scope "$KV_ID"

log "3b. Private DNS Zone – Key Vault..."
az network private-dns zone create \
  --resource-group "$RG" \
  --name "privatelink.vaultcore.azure.net"

az network private-dns link vnet create \
  --resource-group "$RG" \
  --zone-name "privatelink.vaultcore.azure.net" \
  --name "${ENV}-link" \
  --virtual-network "$VNET_ID" \
  --registration-enabled false

az network private-endpoint create \
  --name "pe-kv-${ENV}" \
  --resource-group "$RG" \
  --location "$LOCATION" \
  --subnet "$SUBNET_PRIVATE_ID" \
  --private-connection-resource-id "$KV_ID" \
  --group-id vault \
  --connection-name "pe-kv-${ENV}-conn"

KV_PE_NIC=$(az network private-endpoint show \
  --name "pe-kv-${ENV}" --resource-group "$RG" \
  --query "networkInterfaces[0].id" -o tsv)

KV_PE_IP=$(az network nic show --ids "$KV_PE_NIC" \
  --query "ipConfigurations[0].privateIPAddress" -o tsv)

az network private-dns record-set a add-record \
  --resource-group "$RG" \
  --zone-name "privatelink.vaultcore.azure.net" \
  --record-set-name "$KEY_VAULT" \
  --ipv4-address "$KV_PE_IP"

# ---------------------------------------------------------------------------
# 4. STORAGE ACCOUNT + Private Endpoint
# ---------------------------------------------------------------------------
log "6. Skapar Storage Account..."

az storage account create \
  --name "$STORAGE_ACCOUNT" \
  --resource-group "$RG" \
  --location "$LOCATION" \
  --sku Standard_LRS \
  --kind StorageV2 \
  --public-network-access Disabled \
  --allow-blob-public-access false \
  --min-tls-version TLS1_2

STORAGE_ID=$(az storage account show --name "$STORAGE_ACCOUNT" --resource-group "$RG" --query id -o tsv)

log "4b. Private DNS Zone – Blob..."
az network private-dns zone create \
  --resource-group "$RG" \
  --name "privatelink.blob.core.windows.net"

az network private-dns link vnet create \
  --resource-group "$RG" \
  --zone-name "privatelink.blob.core.windows.net" \
  --name "${ENV}-link" \
  --virtual-network "$VNET_ID" \
  --registration-enabled false

az network private-endpoint create \
  --name "pe-blob-${ENV}" \
  --resource-group "$RG" \
  --location "$LOCATION" \
  --subnet "$SUBNET_PRIVATE_ID" \
  --private-connection-resource-id "$STORAGE_ID" \
  --group-id blob \
  --connection-name "pe-blob-${ENV}-conn"

BLOB_PE_NIC=$(az network private-endpoint show \
  --name "pe-blob-${ENV}" --resource-group "$RG" \
  --query "networkInterfaces[0].id" -o tsv)

BLOB_PE_IP=$(az network nic show --ids "$BLOB_PE_NIC" \
  --query "ipConfigurations[0].privateIPAddress" -o tsv)

az network private-dns record-set a add-record \
  --resource-group "$RG" \
  --zone-name "privatelink.blob.core.windows.net" \
  --record-set-name "$STORAGE_ACCOUNT" \
  --ipv4-address "$BLOB_PE_IP"

# ---------------------------------------------------------------------------
# 5. SQL SERVER + DATABAS + Private Endpoint
# ---------------------------------------------------------------------------
log "7. Skapar SQL Server och databas..."

if [ -z "$SQL_ADMIN_PASSWORD" ]; then
  echo "FEL: SQL_ADMIN_PASSWORD är inte satt. Avbryter." >&2
  exit 1
fi

az sql server create \
  --name "$SQL_SERVER" \
  --resource-group "$RG" \
  --location "$LOCATION" \
  --admin-user "$SQL_ADMIN_USER" \
  --admin-password "$SQL_ADMIN_PASSWORD"

az sql server update \
  --name "$SQL_SERVER" \
  --resource-group "$RG" \
  --set publicNetworkAccess=Disabled

az sql db create \
  --name "$SQL_DB" \
  --server "$SQL_SERVER" \
  --resource-group "$RG" \
  --tier GeneralPurpose \
  --family Gen5 \
  --capacity 2 \
  --zone-redundant false

SQL_ID=$(az sql server show --name "$SQL_SERVER" --resource-group "$RG" --query id -o tsv)

log "5b. Private DNS Zone – SQL..."
az network private-dns zone create \
  --resource-group "$RG" \
  --name "privatelink.database.windows.net"

az network private-dns link vnet create \
  --resource-group "$RG" \
  --zone-name "privatelink.database.windows.net" \
  --name "${ENV}-link" \
  --virtual-network "$VNET_ID" \
  --registration-enabled false

az network private-endpoint create \
  --name "pe-sql-${ENV}" \
  --resource-group "$RG" \
  --location "$LOCATION" \
  --subnet "$SUBNET_PRIVATE_ID" \
  --private-connection-resource-id "$SQL_ID" \
  --group-id sqlServer \
  --connection-name "pe-sql-${ENV}-conn"

SQL_PE_NIC=$(az network private-endpoint show \
  --name "pe-sql-${ENV}" --resource-group "$RG" \
  --query "networkInterfaces[0].id" -o tsv)

SQL_PE_IP=$(az network nic show --ids "$SQL_PE_NIC" \
  --query "ipConfigurations[0].privateIPAddress" -o tsv)

az network private-dns record-set a add-record \
  --resource-group "$RG" \
  --zone-name "privatelink.database.windows.net" \
  --record-set-name "$SQL_SERVER" \
  --ipv4-address "$SQL_PE_IP"

# ---------------------------------------------------------------------------
# 6. SERVICE BUS
# ---------------------------------------------------------------------------
log "8. Skapar Service Bus..."

az servicebus namespace create \
  --name "$SERVICE_BUS" \
  --resource-group "$RG" \
  --location "$LOCATION" \
  --sku Standard

# ---------------------------------------------------------------------------
# 7. AZURE OPENAI + Private Endpoint
# ---------------------------------------------------------------------------
log "9. Skapar Azure OpenAI..."

az cognitiveservices account create \
  --name "$OPENAI_ACCOUNT" \
  --resource-group "$RG" \
  --location "$LOCATION" \
  --kind OpenAI \
  --sku S0 \
  --custom-domain "$OPENAI_ACCOUNT" \
  --public-network-access Disabled

OAI_ID=$(az cognitiveservices account show \
  --name "$OPENAI_ACCOUNT" --resource-group "$RG" --query id -o tsv)

log "7b. Private DNS Zone – OpenAI..."
az network private-dns zone create \
  --resource-group "$RG" \
  --name "privatelink.openai.azure.com"

az network private-dns link vnet create \
  --resource-group "$RG" \
  --zone-name "privatelink.openai.azure.com" \
  --name "${ENV}-link" \
  --virtual-network "$VNET_ID" \
  --registration-enabled false

az network private-endpoint create \
  --name "pe-oai-${ENV}" \
  --resource-group "$RG" \
  --location "$LOCATION" \
  --subnet "$SUBNET_PRIVATE_ID" \
  --private-connection-resource-id "$OAI_ID" \
  --group-id account \
  --connection-name "pe-oai-${ENV}-conn"

OAI_PE_NIC=$(az network private-endpoint show \
  --name "pe-oai-${ENV}" --resource-group "$RG" \
  --query "networkInterfaces[0].id" -o tsv)

OAI_PE_IP=$(az network nic show --ids "$OAI_PE_NIC" \
  --query "ipConfigurations[0].privateIPAddress" -o tsv)

az network private-dns record-set a add-record \
  --resource-group "$RG" \
  --zone-name "privatelink.openai.azure.com" \
  --record-set-name "$OPENAI_ACCOUNT" \
  --ipv4-address "$OAI_PE_IP"

# ---------------------------------------------------------------------------
# 8. AZURE FRONT DOOR (CDN-profil + endpoint)
# ---------------------------------------------------------------------------
log "10. Skapar Azure Front Door..."

az afd profile create \
  --profile-name "$AFD_PROFILE" \
  --resource-group "$RG" \
  --sku Standard_AzureFrontDoor

az afd endpoint create \
  --endpoint-name "$AFD_ENDPOINT" \
  --profile-name "$AFD_PROFILE" \
  --resource-group "$RG"

# ---------------------------------------------------------------------------
# 9. MONITORING – Action Groups
# ---------------------------------------------------------------------------
log "11. Skapar Action Groups..."

if [ -z "$ALERT_EMAIL" ]; then
  echo "VARNING: ALERT_EMAIL är inte satt – e-postaviseringar skapas utan mottagare." >&2
fi

# E-post notifiering
az monitor action-group create \
  --name "Email notification" \
  --resource-group "$RG" \
  --short-name "email-notif" \
  ${ALERT_EMAIL:+--action email emailreceiver "$ALERT_EMAIL"}

# Alert action group (generell)
az monitor action-group create \
  --name "ag-alerts-${ENV}" \
  --resource-group "$RG" \
  --short-name "ag-alerts" \
  ${ALERT_EMAIL:+--action email emailreceiver "$ALERT_EMAIL"}

# Teams webhook – fyll i webhook-URL om du vill ha Teams-notiser
# TEAMS_WEBHOOK_URL=""
# az monitor action-group create \
#   --name "teams-webhook" \
#   --resource-group "$RG" \
#   --short-name "teams" \
#   --action webhook teamsreceiver "$TEAMS_WEBHOOK_URL" \
#     --webhook-properties useCommonAlertSchema=true

AG_ALERTS_ID=$(az monitor action-group show \
  --name "ag-alerts-${ENV}" --resource-group "$RG" --query id -o tsv)

# ---------------------------------------------------------------------------
# 10. MONITORING – Metric Alerts (CPU, requests, response time)
# ---------------------------------------------------------------------------
log "12. Skapar Metric Alerts..."

# Hämta App Service resource ID
BACKEND_APP_ID=$(az webapp show \
  --name "$BACKEND_APP_NAME" --resource-group "$RG" --query id -o tsv 2>/dev/null || echo "")

if [ -n "$BACKEND_APP_ID" ]; then

  az monitor metrics alert create \
    --name "alrt-cpu-${ENV}" \
    --resource-group "$RG" \
    --scopes "$BACKEND_APP_ID" \
    --condition "avg CpuPercentage > 80" \
    --window-size 5m \
    --evaluation-frequency 1m \
    --severity 2 \
    --description "CPU > 80% på backend" \
    --action "$AG_ALERTS_ID"

  az monitor metrics alert create \
    --name "alert-requests-${ENV}" \
    --resource-group "$RG" \
    --scopes "$BACKEND_APP_ID" \
    --condition "total Http5xx > 10" \
    --window-size 5m \
    --evaluation-frequency 1m \
    --severity 2 \
    --description "Fler än 10 HTTP 5xx-fel på 5 min" \
    --action "$AG_ALERTS_ID"

  az monitor metrics alert create \
    --name "alert-response-${ENV}" \
    --resource-group "$RG" \
    --scopes "$BACKEND_APP_ID" \
    --condition "avg HttpResponseTime > 3" \
    --window-size 5m \
    --evaluation-frequency 1m \
    --severity 3 \
    --description "Svarstid > 3 sek" \
    --action "$AG_ALERTS_ID"

else
  echo "VARNING: Kunde inte hitta App Service '$BACKEND_APP_NAME' – metric alerts för backend skapas inte." >&2
fi

# ---------------------------------------------------------------------------
# 13. APPLICATION INSIGHTS – skapas om de inte redan finns
# ---------------------------------------------------------------------------
log "13. Kontrollerar Application Insights..."

AI_NAME="app-${ENV}-api"
if az monitor app-insights component show --app "$AI_NAME" --resource-group "$RG" &>/dev/null; then
  echo "  Application Insights '$AI_NAME' finns redan – hoppar över."
else
  echo "  Skapar Application Insights '$AI_NAME'..."
  az monitor app-insights component create \
    --app "$AI_NAME" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --kind web \
    --application-type web
fi

AI_ID=$(az monitor app-insights component show \
  --app "$AI_NAME" --resource-group "$RG" --query id -o tsv)


# ---------------------------------------------------------------------------
# 14. AVAILABILITY TESTS
# ---------------------------------------------------------------------------
log "14. Skapar Availability Tests..."

if [ -n "$BACKEND_APP_URL" ]; then
  az monitor app-insights web-test create \
    --name "availability test-app-${ENV}-api" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --defined-web-test-kind ping \
    --description "Availability test för API" \
    --enabled true \
    --frequency 300 \
    --timeout 30 \
    --locations Id=emea-se-sto-edge \
    --request-url "$BACKEND_APP_URL" \
    --app-insights-id "$AI_ID"
else
  echo "  VARNING: BACKEND_APP_URL är inte satt – availability test för API skapas inte." >&2
fi


# ---------------------------------------------------------------------------
# 15. KOPIERA INSTÄLLNINGAR FRÅN DEV
# ---------------------------------------------------------------------------
log "15. Kopierar inställningar från dev-miljön..."

# --- App Service: app settings och connection strings ---
echo "  App Service: kopierar app settings..."
DEV_APPSETTINGS=$(az webapp config appsettings list   --name "$DEV_BACKEND_APP" --resource-group "$DEV_RG" -o json 2>/dev/null || echo "[]")

if [ "$DEV_APPSETTINGS" != "[]" ] && [ -n "$DEV_APPSETTINGS" ]; then
  # Filtrera bort inställningar med dev-specifika värden och ersätt env-referenser
  PROD_APPSETTINGS=$(echo "$DEV_APPSETTINGS" |     python3 -c "
import json, sys
settings = json.load(sys.stdin)
# Ersätt 'dev' med 'prod' i värden där det förekommer
for s in settings:
    s['value'] = s['value'].replace('-dev', '-prod').replace('dev01', 'prod01')
print(json.dumps(settings))
")
  az webapp config appsettings set     --name "$BACKEND_APP_NAME"     --resource-group "$RG"     --settings "$PROD_APPSETTINGS" > /dev/null
  echo "  App settings kopierade."
else
  echo "  Inga app settings hittades i dev."
fi

echo "  App Service: kopierar connection strings..."
DEV_CONNSTRINGS=$(az webapp config connection-string list   --name "$DEV_BACKEND_APP" --resource-group "$DEV_RG" -o json 2>/dev/null || echo "{}")

if [ "$DEV_CONNSTRINGS" != "{}" ] && [ -n "$DEV_CONNSTRINGS" ]; then
  echo "$DEV_CONNSTRINGS" | python3 -c "
import json, sys, subprocess
cs = json.load(sys.stdin)
for name, obj in cs.items():
    val = obj['value'].replace('-dev', '-prod').replace('dev01', 'prod01')
    cs_type = obj['type']
    subprocess.run([
        'az', 'webapp', 'config', 'connection-string', 'set',
        '--name', '$BACKEND_APP_NAME',
        '--resource-group', '$RG',
        '--connection-string-type', cs_type,
        '--settings', f'{name}={val}'
    ])
"
  echo "  Connection strings kopierade."
else
  echo "  Inga connection strings hittades i dev."
fi


# --- Function App: app settings ---
echo "  Function App: kopierar app settings..."
DEV_FA_SETTINGS=$(az functionapp config appsettings list \
  --name "$DEV_FUNCTION_APP" --resource-group "$DEV_RG" -o json 2>/dev/null || echo "[]")

if [ "$DEV_FA_SETTINGS" != "[]" ] && [ -n "$DEV_FA_SETTINGS" ]; then
  PROD_FA_SETTINGS=$(echo "$DEV_FA_SETTINGS" | \
    python3 -c "
import json, sys
settings = json.load(sys.stdin)
# Hoppa över inbyggda Function App-inställningar som sätts automatiskt
skip = {'FUNCTIONS_WORKER_RUNTIME', 'FUNCTIONS_EXTENSION_VERSION', 'AzureWebJobsStorage', 'WEBSITE_RUN_FROM_PACKAGE'}
settings = [s for s in settings if s['name'] not in skip]
for s in settings:
    s['value'] = s['value'].replace('-dev', '-prod').replace('dev01', 'prod01')
print(json.dumps(settings))
")
  az functionapp config appsettings set \
    --name "$FUNCTION_APP_NAME" \
    --resource-group "$RG" \
    --settings "$PROD_FA_SETTINGS" > /dev/null
  echo "  Function App settings kopierade."
else
  echo "  Inga app settings hittades för Function App i dev."
fi


# --- Service Bus: köer och topics ---
echo "  Service Bus: kopierar köer..."
DEV_QUEUES=$(az servicebus queue list   --namespace-name "$DEV_SERVICE_BUS"   --resource-group "$DEV_RG"   --query "[].name" -o tsv 2>/dev/null || echo "")

if [ -n "$DEV_QUEUES" ]; then
  while IFS= read -r queue_name; do
    # Hämta kö-egenskaper från dev
    QUEUE_PROPS=$(az servicebus queue show       --namespace-name "$DEV_SERVICE_BUS"       --resource-group "$DEV_RG"       --name "$queue_name" -o json)

    LOCK_DURATION=$(echo "$QUEUE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin)['lockDuration'])")
    MAX_SIZE=$(echo "$QUEUE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin)['maxSizeInMegabytes'])")
    MAX_DELIVERY=$(echo "$QUEUE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin)['maxDeliveryCount'])")

    az servicebus queue create       --namespace-name "$SERVICE_BUS"       --resource-group "$RG"       --name "$queue_name"       --lock-duration "$LOCK_DURATION"       --max-size "$MAX_SIZE"       --max-delivery-count "$MAX_DELIVERY" > /dev/null
    echo "    Skapade kö: $queue_name"
  done <<< "$DEV_QUEUES"
fi

echo "  Service Bus: kopierar topics..."
DEV_TOPICS=$(az servicebus topic list   --namespace-name "$DEV_SERVICE_BUS"   --resource-group "$DEV_RG"   --query "[].name" -o tsv 2>/dev/null || echo "")

if [ -n "$DEV_TOPICS" ]; then
  while IFS= read -r topic_name; do
    TOPIC_PROPS=$(az servicebus topic show       --namespace-name "$DEV_SERVICE_BUS"       --resource-group "$DEV_RG"       --name "$topic_name" -o json)

    MAX_SIZE=$(echo "$TOPIC_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin)['maxSizeInMegabytes'])")

    az servicebus topic create       --namespace-name "$SERVICE_BUS"       --resource-group "$RG"       --name "$topic_name"       --max-size "$MAX_SIZE" > /dev/null
    echo "    Skapade topic: $topic_name"

    # Kopiera subscriptions per topic
    DEV_SUBS=$(az servicebus topic subscription list       --namespace-name "$DEV_SERVICE_BUS"       --resource-group "$DEV_RG"       --topic-name "$topic_name"       --query "[].name" -o tsv 2>/dev/null || echo "")

    if [ -n "$DEV_SUBS" ]; then
      while IFS= read -r sub_name; do
        az servicebus topic subscription create           --namespace-name "$SERVICE_BUS"           --resource-group "$RG"           --topic-name "$topic_name"           --name "$sub_name" > /dev/null
        echo "      Skapade subscription: $sub_name"
      done <<< "$DEV_SUBS"
    fi
  done <<< "$DEV_TOPICS"
fi

# --- Storage: containers ---
echo "  Storage: kopierar containers..."
DEV_STORAGE_KEY=$(az storage account keys list   --account-name "$DEV_STORAGE"   --resource-group "$DEV_RG"   --query "[0].value" -o tsv 2>/dev/null || echo "")

if [ -n "$DEV_STORAGE_KEY" ]; then
  DEV_CONTAINERS=$(az storage container list     --account-name "$DEV_STORAGE"     --account-key "$DEV_STORAGE_KEY"     --query "[].name" -o tsv 2>/dev/null || echo "")

  PROD_STORAGE_KEY=$(az storage account keys list     --account-name "$STORAGE_ACCOUNT"     --resource-group "$RG"     --query "[0].value" -o tsv)

  if [ -n "$DEV_CONTAINERS" ]; then
    while IFS= read -r container_name; do
      # Hämta access level från dev
      ACCESS=$(az storage container show         --name "$container_name"         --account-name "$DEV_STORAGE"         --account-key "$DEV_STORAGE_KEY"         --query "properties.publicAccess" -o tsv 2>/dev/null || echo "off")
      [ "$ACCESS" = "None" ] && ACCESS="off"

      az storage container create         --name "$container_name"         --account-name "$STORAGE_ACCOUNT"         --account-key "$PROD_STORAGE_KEY"         --public-access "$ACCESS" > /dev/null
      echo "    Skapade container: $container_name"
    done <<< "$DEV_CONTAINERS"
  fi
else
  echo "  Kunde inte läsa storage-containers från dev (kontrollera behörigheter)."
fi

# --- Azure OpenAI: deployments ---
echo "  Azure OpenAI: kopierar model deployments..."
DEV_DEPLOYMENTS=$(az cognitiveservices account deployment list   --name "$DEV_OPENAI"   --resource-group "$DEV_RG" -o json 2>/dev/null || echo "[]")

if [ "$DEV_DEPLOYMENTS" != "[]" ] && [ -n "$DEV_DEPLOYMENTS" ]; then
  echo "$DEV_DEPLOYMENTS" | python3 -c "
import json, sys, subprocess
deployments = json.load(sys.stdin)
for d in deployments:
    name = d['name']
    model_name = d['properties']['model']['name']
    model_version = d['properties']['model']['version']
    model_format = d['properties']['model']['format']
    capacity = d['sku']['capacity']
    sku_name = d['sku']['name']
    print(f'    Deploying {name} ({model_name} {model_version})...')
    subprocess.run([
        'az', 'cognitiveservices', 'account', 'deployment', 'create',
        '--name', '$OPENAI_ACCOUNT',
        '--resource-group', '$RG',
        '--deployment-name', name,
        '--model-name', model_name,
        '--model-version', model_version,
        '--model-format', model_format,
        '--sku-name', sku_name,
        '--sku-capacity', str(capacity)
    ])
"
  echo "  OpenAI deployments kopierade."
else
  echo "  Inga OpenAI deployments hittades i dev."
fi

# --- Front Door: origin groups, origins och routes ---
echo "  Front Door: kopierar origin groups, origins och routes..."
DEV_ORIGIN_GROUPS=$(az afd origin-group list   --profile-name "$DEV_AFD_PROFILE"   --resource-group "$DEV_RG"   --query "[].name" -o tsv 2>/dev/null || echo "")

if [ -n "$DEV_ORIGIN_GROUPS" ]; then
  while IFS= read -r og_name; do
    OG_PROPS=$(az afd origin-group show       --profile-name "$DEV_AFD_PROFILE"       --resource-group "$DEV_RG"       --origin-group-name "$og_name" -o json)

    PROBE_PATH=$(echo "$OG_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('healthProbeSettings',{}).get('probePath','/'))")
    PROBE_INTERVAL=$(echo "$OG_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('healthProbeSettings',{}).get('probeIntervalInSeconds',100))")

    az afd origin-group create       --origin-group-name "$og_name"       --profile-name "$AFD_PROFILE"       --resource-group "$RG"       --probe-path "$PROBE_PATH"       --probe-interval-in-seconds "$PROBE_INTERVAL" > /dev/null
    echo "    Skapade origin group: $og_name"

    # Kopiera origins inom gruppen
    DEV_ORIGINS=$(az afd origin list       --profile-name "$DEV_AFD_PROFILE"       --resource-group "$DEV_RG"       --origin-group-name "$og_name" -o json 2>/dev/null || echo "[]")

    echo "$DEV_ORIGINS" | python3 -c "
import json, sys, subprocess
origins = json.load(sys.stdin)
for o in origins:
    host = o['hostName'].replace('-dev', '-prod')
    name = o['name']
    http_port = o.get('httpPort', 80)
    https_port = o.get('httpsPort', 443)
    priority = o.get('priority', 1)
    weight = o.get('weight', 1000)
    print(f'      Skapar origin: {name} -> {host}')
    subprocess.run([
        'az', 'afd', 'origin', 'create',
        '--origin-name', name,
        '--origin-group-name', '$og_name',
        '--profile-name', '$AFD_PROFILE',
        '--resource-group', '$RG',
        '--host-name', host,
        '--http-port', str(http_port),
        '--https-port', str(https_port),
        '--priority', str(priority),
        '--weight', str(weight)
    ])
"
  done <<< "$DEV_ORIGIN_GROUPS"
fi

# Kopiera routes
DEV_ROUTES=$(az afd route list   --profile-name "$DEV_AFD_PROFILE"   --resource-group "$DEV_RG"   --endpoint-name "video-endpoint"   --query "[].name" -o tsv 2>/dev/null || echo "")

if [ -n "$DEV_ROUTES" ]; then
  while IFS= read -r route_name; do
    ROUTE_PROPS=$(az afd route show       --profile-name "$DEV_AFD_PROFILE"       --resource-group "$DEV_RG"       --endpoint-name "video-endpoint"       --route-name "$route_name" -o json)

    PATTERNS=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; print(' '.join(json.load(sys.stdin).get('patternsToMatch', ['/*'])))")
    OG=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['originGroup']['id'].split('/')[-1])")
    HTTPS=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('httpsRedirect','Enabled'))")

    az afd route create       --route-name "$route_name"       --profile-name "$AFD_PROFILE"       --resource-group "$RG"       --endpoint-name "$AFD_ENDPOINT"       --origin-group "$OG"       --patterns-to-match $PATTERNS       --https-redirect "$HTTPS"       --forwarding-protocol MatchRequest > /dev/null
    echo "    Skapade route: $route_name"
  done <<< "$DEV_ROUTES"
fi

# --- SQL: brandväggsregler ---
echo "  SQL: kopierar brandväggsregler..."
DEV_FW_RULES=$(az sql server firewall-rule list   --server "$DEV_SQL_SERVER"   --resource-group "$DEV_RG" -o json 2>/dev/null || echo "[]")

if [ "$DEV_FW_RULES" != "[]" ] && [ -n "$DEV_FW_RULES" ]; then
  echo "$DEV_FW_RULES" | python3 -c "
import json, sys, subprocess
rules = json.load(sys.stdin)
for r in rules:
    subprocess.run([
        'az', 'sql', 'server', 'firewall-rule', 'create',
        '--server', '$SQL_SERVER',
        '--resource-group', '$RG',
        '--name', r['name'],
        '--start-ip-address', r['startIpAddress'],
        '--end-ip-address', r['endIpAddress']
    ])
    print(f'    Kopierade brandväggsregel: {r["name"]}')
"
fi

# ---------------------------------------------------------------------------
# KLART
# ---------------------------------------------------------------------------
log "Klart! Alla prod-resurser är skapade i '$RG'."
echo ""
echo "Kom ihåg att:"
echo "  1. Sätt hemligheter i Key Vault '$KEY_VAULT'"
echo "  2. Koppla App Service till VNet (VNet Integration) om den inte skapades nu"
echo "  4. Verifiera att kopierade app settings/connection strings pekar rätt i prod"