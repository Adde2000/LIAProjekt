#!/bin/bash

# =============================================================================
# SAMMANFATTNING
# =============================================================================
#
# Detta skript sätter upp en komplett miljö i westeurope baserat på
# konfigurationen i dev-miljön (rg-app-dev).
#
# Skriptet är helt idempotent – varje resurs kontrolleras först och skapas
# bara om den inte redan finns. Det innebär att skriptet kan köras flera
# gånger utan att något skapas dubbelt eller misslyckas.
#
# Följande resurser hanteras:
#   1.  Resursgrupp
#   2.  Managed Identity
#   3.  Static Web App (frontend)
#   4.  App Service Plan + App Service (backend, Java 21, S1) + Autoskalning
#   5.  Storage Account (private endpoint + DNS-zon)
#   6.  Function App (Flex Consumption, Java 21, Service Bus worker)
#   7.  Key Vault (private endpoint + DNS-zon)
#   8.  SQL Server + databas (private endpoint + DNS-zon)
#   9.  Service Bus
#   10. Azure OpenAI (private endpoint + DNS-zon)
#   11. Azure Front Door (profil + endpoint)
#   12. Action Groups
#   13. Metric Alerts (CPU, HTTP 5xx, svarstid)
#   14. Application Insights + koppling till App Service
#   15. Availability Test
#   16. Kopiering av inställningar från dev till prod:
#         - Static Web App environment variables + backend-koppling
#         - App Service app settings + connection strings
#         - Function App app settings
#         - Service Bus köer, topics och subscriptions
#         - Storage containers
#         - Azure OpenAI model deployments + AI-assistenter
#         - Front Door origin groups, origins och routes
#         - Key Vault secrets
#         - SQL brandväggsregler
#   17. IAM-roller
#
# All konfiguration (SKU, tier, trösklar m.m.) hämtas dynamiskt från
# motsvarande resurser i dev-miljön för att säkerställa paritet mellan
# miljöerna. Vid eventuella fel mot dev används rimliga defaultvärden.
# =============================================================================

# =============================================================================
# setup-prod.sh
# Sätter upp miljö baserat på rg-app-dev
#
# Förutsättningar:
#   - Azure CLI installerat och inloggad (az login)
#   - Behörighet att skapa resurser i målresursgruppen
# =============================================================================

set -euo pipefail
export MSYS_NO_PATHCONV=1

# ---------------------------------------------------------------------------
# KONFIGURATION – justera dessa värden innan körning
# ---------------------------------------------------------------------------
RG="rg-app-testning80"
DEV_RG="rg-app-dev"           # Källmiljö att kopiera inställningar från
LOCATION="westeurope"
ENV="testning80"

# SQL
SQL_SERVER="sqlsrv-app-${ENV}"
SQL_DB="sqldb-app-${ENV}"
SQL_ADMIN_USER="LIAProjektSQL"
SQL_ADMIN_PASSWORD='L&95$Qn#v432GV4qCvJq'          # Fyll i eller hämta från KeyVault/env-variabel

# Storage
STORAGE_ACCOUNT="storageapp${ENV}01"   # Max 24 tecken, endast lowercase

# Key Vault
KEY_VAULT="kv-app-${ENV}01"

# Service Bus
SERVICE_BUS="sb-app-${ENV}01"

# OpenAI
OPENAI_ACCOUNT="oai-app-${ENV}01"
OPENAI_LOCATION="swedencentral"
SUBSCRIPTION_ID=$(az account show --query id -o tsv)

# Front Door
AFD_PROFILE="fd-video-app-${ENV}"
AFD_ENDPOINT="video-endpoint-${ENV}"

# VNet
VNET="vnet-${ENV}"
SUBNET_APP="snet-app"
SUBNET_PRIVATE="snet-private"
VNET_ADDRESS="10.0.0.0/16"
SUBNET_APP_PREFIX="10.0.1.0/24"
SUBNET_PRIVATE_PREFIX="10.0.2.0/24"
SUBNET_DEFAULT="default"
SUBNET_DEFAULT_PREFIX="10.0.0.0/24"
SUBNET_FUNC="snet-func"
SUBNET_FUNC_PREFIX="10.0.3.0/24"

# Static Web App (frontend)
SWA_NAME="swa-app-${ENV}"
SWA_LOCATION="$LOCATION"

# App Service Plan + Backend
ASP_NAME="asp-app-${ENV}"
ASP_SKU="S1"                       # Standard-nivå
BACKEND_APP_NAME="app-${ENV}-api"
BACKEND_APP_RUNTIME="JAVA:21-java21"  # Java 21

# Autoskalning (används om App Service skapas)
AUTOSCALE_MIN=1
AUTOSCALE_MAX=2
AUTOSCALE_DEFAULT=1
AUTOSCALE_CPU_OUT=70               # Skala ut när CPU > 70% i 5 min
AUTOSCALE_MEM_OUT=75               # Skala ut när minne > 75% i 5 min
AUTOSCALE_CPU_IN=40                # Skala in när CPU < 40% i 15 min

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
BACKEND_APP_URL=""          # t.ex. https://app-prod-api.azurewebsites.net/health


# Dev-resursnamn (används vid kopiering av inställningar)
DEV_SWA="swa-app-dev"
DEV_BACKEND_APP="app-dev-api"
DEV_SERVICE_BUS="sb-app-dev01"
DEV_SQL_SERVER="sqlsrv-app-dev"
DEV_SQL_DB="sqldb-app-dev"
DEV_STORAGE="storageappdev01"
DEV_KEY_VAULT="kv-app-dev01"
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
log "1. Kontrollerar Managed Identity..."
if az identity show --name "$MSI_NAME" --resource-group "$RG" &>/dev/null; then
  echo "  Managed Identity '$MSI_NAME' finns redan – hoppar över."
else
  echo "  Skapar Managed Identity '$MSI_NAME'..."
  az identity create \
    --name "$MSI_NAME" \
    --resource-group "$RG" \
    --location "$LOCATION"
fi

MSI_ID=$(az identity show --name "$MSI_NAME" --resource-group "$RG" --query id -o tsv)
MSI_PRINCIPAL=$(az identity show --name "$MSI_NAME" --resource-group "$RG" --query principalId -o tsv)

# ---------------------------------------------------------------------------
# 2. STATIC WEB APP (frontend) – skapas om den inte redan finns
# ---------------------------------------------------------------------------
log "2. Kontrollerar Static Web App (frontend)..."

if az staticwebapp show --name "$SWA_NAME" --resource-group "$RG" &>/dev/null; then
  echo "  Static Web App '$SWA_NAME' finns redan – uppdaterar inställningar..."
  SWA_DEV_SKU=$(az staticwebapp show --name "$DEV_SWA" --resource-group "$DEV_RG" \
    --query "sku.name" -o tsv 2>/dev/null || echo "Standard")
  az staticwebapp update \
    --name "$SWA_NAME" \
    --resource-group "$RG" \
    --sku "$SWA_DEV_SKU" > /dev/null
else
  echo "  Hämtar Static Web App-konfiguration från dev..."
  SWA_DEV_SKU=$(az staticwebapp show --name "$DEV_SWA" --resource-group "$DEV_RG" \
    --query "sku.name" -o tsv 2>/dev/null || echo "Standard")

  echo "  Skapar Static Web App '$SWA_NAME' (SKU: $SWA_DEV_SKU)..."
  az staticwebapp create \
    --name "$SWA_NAME" \
    --resource-group "$RG" \
    --location "$SWA_LOCATION" \
    --sku "$SWA_DEV_SKU"
fi

# ---------------------------------------------------------------------------
# 3. APP SERVICE PLAN + BACKEND APP SERVICE + AUTOSKALNING
#    Skapas om de inte redan finns
# ---------------------------------------------------------------------------
log "3. Kontrollerar App Service Plan och App Service (backend)..."

APP_SERVICE_CREATED=false

if az appservice plan show --name "$ASP_NAME" --resource-group "$RG" &>/dev/null; then
  echo "  App Service Plan '$ASP_NAME' finns redan – uppdaterar inställningar..."
  DEV_ASP_ID=$(az webapp show --name "$DEV_BACKEND_APP" --resource-group "$DEV_RG" \
    --query "appServicePlanId" -o tsv 2>/dev/null || echo "")
  if [ -n "$DEV_ASP_ID" ]; then
    ASP_DEV_SKU=$(az appservice plan show --ids "$DEV_ASP_ID" \
      --query "sku.name" -o tsv 2>/dev/null || echo "$ASP_SKU")
  else
    ASP_DEV_SKU="$ASP_SKU"
  fi
  az appservice plan update \
    --name "$ASP_NAME" \
    --resource-group "$RG" \
    --sku "$ASP_DEV_SKU" > /dev/null
else
  echo "  Hämtar App Service Plan-konfiguration från dev..."
  DEV_ASP_ID=$(az webapp show --name "$DEV_BACKEND_APP" --resource-group "$DEV_RG" \
    --query "appServicePlanId" -o tsv 2>/dev/null || echo "")
if [ -n "$DEV_ASP_ID" ]; then
    ASP_DEV_SKU=$(az appservice plan show --ids "$DEV_ASP_ID" \
        --query "sku.name" -o tsv 2>/dev/null || echo "$ASP_SKU")
else
    ASP_DEV_SKU="$ASP_SKU"
fi

  echo "  Skapar App Service Plan '$ASP_NAME' (SKU: $ASP_DEV_SKU)..."
  az appservice plan create \
    --name "$ASP_NAME" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --sku "$ASP_DEV_SKU" \
    --is-linux
fi

if az webapp show --name "$BACKEND_APP_NAME" --resource-group "$RG" &>/dev/null; then
  echo "  App Service '$BACKEND_APP_NAME' finns redan – uppdaterar inställningar..."
  APP_DEV_CONFIG=$(az webapp config show \
    --name "$DEV_BACKEND_APP" --resource-group "$DEV_RG" -o json 2>/dev/null || echo "{}")
  APP_DEV_HTTP=$(echo "$APP_DEV_CONFIG" | python3 -c "import json,sys; print(json.load(sys.stdin).get('http20Enabled', False))")
  APP_DEV_TLS=$(echo "$APP_DEV_CONFIG" | python3 -c "import json,sys; print(json.load(sys.stdin).get('minTlsVersion', '1.2'))")
  APP_DEV_ALWAYS_ON=$(echo "$APP_DEV_CONFIG" | python3 -c "import json,sys; print(json.load(sys.stdin).get('alwaysOn', True))")
  APP_DEV_HTTPS=$(az webapp show --name "$DEV_BACKEND_APP" --resource-group "$DEV_RG" \
    --query "httpsOnly" -o tsv 2>/dev/null || echo "true")
  APP_DEV_CLIENT_AFFINITY=$(az webapp show --name "$DEV_BACKEND_APP" --resource-group "$DEV_RG" \
    --query "clientAffinityEnabled" -o tsv 2>/dev/null || echo "false")
  az webapp update \
    --name "$BACKEND_APP_NAME" \
    --resource-group "$RG" \
    --https-only "$APP_DEV_HTTPS" \
    --client-affinity-enabled "$APP_DEV_CLIENT_AFFINITY" > /dev/null

  az resource update \
    --resource-group "$RG" \
    --name "scm" \
    --namespace "Microsoft.Web" \
    --resource-type "basicPublishingCredentialsPolicies" \
    --parent "sites/${BACKEND_APP_NAME}" \
    --set properties.allow=true > /dev/null
  az webapp config set \
    --name "$BACKEND_APP_NAME" \
    --resource-group "$RG" \
    --min-tls-version "$APP_DEV_TLS" \
    --http20-enabled "$APP_DEV_HTTP" \
    --always-on "$APP_DEV_ALWAYS_ON" > /dev/null

  PROD_SWA_URL=$(az staticwebapp show --name "$SWA_NAME" --resource-group "$RG" \
    --query "defaultHostname" -o tsv 2>/dev/null | tr -d '\r' || echo "")

  if [ -n "$PROD_SWA_URL" ]; then
    echo "  Sätter CORS för App Service -> https://$PROD_SWA_URL..."
    az webapp cors add \
      --name "$BACKEND_APP_NAME" \
      --resource-group "$RG" \
      --allowed-origins "https://$PROD_SWA_URL" > /dev/null
  fi
else
  echo "  Skapar App Service '$BACKEND_APP_NAME'..."
  echo "  Hämtar App Service-konfiguration från dev..."
  APP_DEV_CONFIG=$(az webapp config show \
    --name "$DEV_BACKEND_APP" --resource-group "$DEV_RG" -o json 2>/dev/null || echo "{}")
  APP_DEV_HTTP=$(echo "$APP_DEV_CONFIG" | python3 -c "import json,sys; print(json.load(sys.stdin).get('http20Enabled', False))" )
  APP_DEV_TLS=$(echo "$APP_DEV_CONFIG" | python3 -c "import json,sys; print(json.load(sys.stdin).get('minTlsVersion', '1.2'))")
  APP_DEV_ALWAYS_ON=$(echo "$APP_DEV_CONFIG" | python3 -c "import json,sys; print(json.load(sys.stdin).get('alwaysOn', True))")
  APP_DEV_HTTPS=$(az webapp show --name "$DEV_BACKEND_APP" --resource-group "$DEV_RG" \
    --query "httpsOnly" -o tsv 2>/dev/null || echo "true")
  APP_DEV_CLIENT_AFFINITY=$(az webapp show --name "$DEV_BACKEND_APP" --resource-group "$DEV_RG" \
    --query "clientAffinityEnabled" -o tsv 2>/dev/null || echo "false")

  echo "  Tilldelar Managed Identity till App Service..."
  az webapp create \
    --name "$BACKEND_APP_NAME" \
    --resource-group "$RG" \
    --plan "$ASP_NAME" \
    --runtime "$BACKEND_APP_RUNTIME" \
    --assign-identity "[system]"

  APP_SERVICE_CREATED=true

  az webapp update \
    --name "$BACKEND_APP_NAME" \
    --resource-group "$RG" \
    --https-only "$APP_DEV_HTTPS" \
    --client-affinity-enabled "$APP_DEV_CLIENT_AFFINITY"

  az resource update \
    --resource-group "$RG" \
    --name "scm" \
    --namespace "Microsoft.Web" \
    --resource-type "basicPublishingCredentialsPolicies" \
    --parent "sites/${BACKEND_APP_NAME}" \
    --set properties.allow=true

  az webapp config set \
    --name "$BACKEND_APP_NAME" \
    --resource-group "$RG" \
    --min-tls-version "$APP_DEV_TLS" \
    --http20-enabled "$APP_DEV_HTTP" \
    --always-on "$APP_DEV_ALWAYS_ON"

  PROD_SWA_URL=$(az staticwebapp show --name "$SWA_NAME" --resource-group "$RG" \
    --query "defaultHostname" -o tsv 2>/dev/null | tr -d '\r' || echo "")

  if [ -n "$PROD_SWA_URL" ]; then
    echo "  Sätter CORS för App Service -> https://$PROD_SWA_URL..."
    az webapp cors add \
      --name "$BACKEND_APP_NAME" \
      --resource-group "$RG" \
      --allowed-origins "https://$PROD_SWA_URL"
  fi
fi

# Autoskalning – skapas bara om App Service skapades i detta körning
if [ "$APP_SERVICE_CREATED" = true ]; then
  echo "  Hämtar autoskalningskonfiguration från dev..."
  DEV_AUTOSCALE=$(az monitor autoscale list \
    --resource-group "$DEV_RG" \
    --query "[0]" -o json 2>/dev/null || echo "{}")
  AS_MIN=$(echo "$DEV_AUTOSCALE" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('profiles',[{}])[0].get('capacity',{}).get('minimum', $AUTOSCALE_MIN))")
  AS_MAX=$(echo "$DEV_AUTOSCALE" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('profiles',[{}])[0].get('capacity',{}).get('maximum', $AUTOSCALE_MAX))")
  AS_DEFAULT=$(echo "$DEV_AUTOSCALE" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('profiles',[{}])[0].get('capacity',{}).get('default', $AUTOSCALE_DEFAULT))")

  # Hämta CPU-trösklar från dev-regler
  AS_RULES=$(echo "$DEV_AUTOSCALE" | python3 -c "
import json, sys
d = json.load(sys.stdin)
rules = d.get('profiles', [{}])[0].get('rules', [])
out = {'cpu_out': $AUTOSCALE_CPU_OUT, 'cooldown_out': 5, 'cpu_in': $AUTOSCALE_CPU_IN, 'cooldown_in': 10}
for r in rules:
    trigger = r.get('metricTrigger', {})
    action = r.get('scaleAction', {})
    if action.get('direction') == 'Increase':
        out['cpu_out'] = trigger.get('threshold', out['cpu_out'])
        out['cooldown_out'] = int(action.get('cooldown','PT5M').replace('PT','').replace('M',''))
    elif action.get('direction') == 'Decrease':
        out['cpu_in'] = trigger.get('threshold', out['cpu_in'])
        out['cooldown_in'] = int(action.get('cooldown','PT15M').replace('PT','').replace('M',''))
print(json.dumps(out))
")
  AS_CPU_OUT=$(echo "$AS_RULES" | python3 -c "import json,sys; print(json.load(sys.stdin)['cpu_out'])")
  AS_COOLDOWN_OUT=$(echo "$AS_RULES" | python3 -c "import json,sys; print(json.load(sys.stdin)['cooldown_out'])")
  AS_CPU_IN=$(echo "$AS_RULES" | python3 -c "import json,sys; print(json.load(sys.stdin)['cpu_in'])")
  AS_COOLDOWN_IN=$(echo "$AS_RULES" | python3 -c "import json,sys; print(json.load(sys.stdin)['cooldown_in'])")

  # Hämta minnesregel från dev om den finns
  AS_MEM_OUT=$(echo "$DEV_AUTOSCALE" | python3 -c "
import json, sys
d = json.load(sys.stdin)
rules = d.get('profiles', [{}])[0].get('rules', [])
for r in rules:
    trigger = r.get('metricTrigger', {})
    action = r.get('scaleAction', {})
    if 'Memory' in trigger.get('metricName', '') and action.get('direction') == 'Increase':
        print(trigger.get('threshold', $AUTOSCALE_MEM_OUT))
        exit()
print($AUTOSCALE_MEM_OUT)
" 2>/dev/null || echo "$AUTOSCALE_MEM_OUT")

  echo "  Skapar autoskalningsregler (min=$AS_MIN, max=$AS_MAX, cpu_out=$AS_CPU_OUT%, mem_out=$AS_MEM_OUT%, cpu_in=$AS_CPU_IN%)..."
  ASP_ID=$(az appservice plan show --name "$ASP_NAME" --resource-group "$RG" --query id -o tsv)

  az monitor autoscale create \
    --name "autoscale-backend-${ENV}" \
    --resource-group "$RG" \
    --resource "$ASP_ID" \
    --min-count "$AS_MIN" \
    --max-count "$AS_MAX" \
    --count "$AS_DEFAULT"

  # Skala ut: CPU > AS_CPU_OUT% i 5 min → +1 instans
  az monitor autoscale rule create \
    --autoscale-name "autoscale-backend-${ENV}" \
    --resource-group "$RG" \
    --scale out 1 \
    --condition "CpuPercentage > ${AS_CPU_OUT} avg 5m" \
    --cooldown "$AS_COOLDOWN_OUT"

  # Skala ut: Minne > AS_MEM_OUT% i 5 min → +1 instans
  az monitor autoscale rule create \
    --autoscale-name "autoscale-backend-${ENV}" \
    --resource-group "$RG" \
    --scale out 1 \
    --condition "MemoryPercentage > ${AS_MEM_OUT} avg 5m" \
    --cooldown "$AS_COOLDOWN_OUT"

  # Skala in: CPU < AS_CPU_IN% i 15 min → -1 instans
  az monitor autoscale rule create \
    --autoscale-name "autoscale-backend-${ENV}" \
    --resource-group "$RG" \
    --scale in 1 \
    --condition "CpuPercentage < ${AS_CPU_IN} avg 15m" \
    --cooldown 15
else
  echo "  Kontrollerar autoskalning (App Service fanns redan)..."
  if az monitor autoscale show --name "autoscale-backend-${ENV}" --resource-group "$RG" &>/dev/null; then
    echo "  Autoskalning finns redan – uppdaterar..."
    az monitor autoscale update \
      --name "autoscale-backend-${ENV}" \
      --resource-group "$RG" \
      --min-count "$AUTOSCALE_MIN" \
      --max-count "$AUTOSCALE_MAX" \
      --count "$AUTOSCALE_DEFAULT" > /dev/null
  else
    echo "  Skapar autoskalningsregler..."
    ASP_ID=$(az appservice plan show --name "$ASP_NAME" --resource-group "$RG" --query id -o tsv)

    az monitor autoscale create \
      --name "autoscale-backend-${ENV}" \
      --resource-group "$RG" \
      --resource "$ASP_ID" \
      --min-count "$AUTOSCALE_MIN" \
      --max-count "$AUTOSCALE_MAX" \
      --count "$AUTOSCALE_DEFAULT"

    az monitor autoscale rule create \
      --autoscale-name "autoscale-backend-${ENV}" \
      --resource-group "$RG" \
      --scale out 1 \
      --condition "CpuPercentage > ${AUTOSCALE_CPU_OUT} avg 5m" \
      --cooldown 5

    az monitor autoscale rule create \
      --autoscale-name "autoscale-backend-${ENV}" \
      --resource-group "$RG" \
      --scale out 1 \
      --condition "MemoryPercentage > ${AUTOSCALE_MEM_OUT} avg 5m" \
      --cooldown 5

    az monitor autoscale rule create \
      --autoscale-name "autoscale-backend-${ENV}" \
      --resource-group "$RG" \
      --scale in 1 \
      --condition "CpuPercentage < ${AUTOSCALE_CPU_IN} avg 15m" \
      --cooldown 15
  fi
fi

# ---------------------------------------------------------------------------
# 4. VIRTUAL NETWORK + NSG:er
# ---------------------------------------------------------------------------
log "4. Kontrollerar VNet och NSG:er..."

if az network vnet show --name "$VNET" --resource-group "$RG" &>/dev/null; then
  echo "  VNet '$VNET' finns redan – hoppar över."
else
  echo "  Skapar VNet '$VNET'..."
  az network vnet create \
    --name "$VNET" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --address-prefixes "$VNET_ADDRESS"
fi

if az network nsg show --name "nsg-app" --resource-group "$RG" &>/dev/null; then
  echo "  NSG 'nsg-app' finns redan – hoppar över."
else
  echo "  Skapar NSG 'nsg-app'..."
  az network nsg create \
    --name "nsg-app" \
    --resource-group "$RG" \
    --location "$LOCATION"
fi

if az network nsg show --name "nsg-private" --resource-group "$RG" &>/dev/null; then
  echo "  NSG 'nsg-private' finns redan – hoppar över."
else
  echo "  Skapar NSG 'nsg-private'..."
  az network nsg create \
    --name "nsg-private" \
    --resource-group "$RG" \
    --location "$LOCATION"
fi

if az network vnet subnet show --name "$SUBNET_DEFAULT" --vnet-name "$VNET" --resource-group "$RG" &>/dev/null; then
  echo "  Subnät '$SUBNET_DEFAULT' finns redan – hoppar över."
else
  echo "  Skapar subnät '$SUBNET_DEFAULT'..."
  az network vnet subnet create \
    --name "$SUBNET_DEFAULT" \
    --vnet-name "$VNET" \
    --resource-group "$RG" \
    --address-prefixes "$SUBNET_DEFAULT_PREFIX"
fi

if az network vnet subnet show --name "$SUBNET_APP" --vnet-name "$VNET" --resource-group "$RG" &>/dev/null; then
  echo "  Subnät '$SUBNET_APP' finns redan – hoppar över."
else
  echo "  Skapar subnät '$SUBNET_APP'..."
  az network vnet subnet create \
    --name "$SUBNET_APP" \
    --vnet-name "$VNET" \
    --resource-group "$RG" \
    --address-prefixes "$SUBNET_APP_PREFIX" \
    --network-security-group "nsg-app"
fi

if az network vnet subnet show --name "$SUBNET_PRIVATE" --vnet-name "$VNET" --resource-group "$RG" &>/dev/null; then
  echo "  Subnät '$SUBNET_PRIVATE' finns redan – hoppar över."
else
  echo "  Skapar subnät '$SUBNET_PRIVATE'..."
  az network vnet subnet create \
    --name "$SUBNET_PRIVATE" \
    --vnet-name "$VNET" \
    --resource-group "$RG" \
    --address-prefixes "$SUBNET_PRIVATE_PREFIX" \
    --network-security-group "nsg-private" \
    --disable-private-endpoint-network-policies true
fi

if az network vnet subnet show --name "$SUBNET_FUNC" --vnet-name "$VNET" --resource-group "$RG" &>/dev/null; then
  echo "  Subnät '$SUBNET_FUNC' finns redan – hoppar över."
else
  echo "  Skapar subnät '$SUBNET_FUNC'..."
  az network vnet subnet create \
    --name "$SUBNET_FUNC" \
    --vnet-name "$VNET" \
    --resource-group "$RG" \
    --address-prefixes "$SUBNET_FUNC_PREFIX" \
    --network-security-group "nsg-app" \
    --delegations Microsoft.Web/serverFarms
fi

VNET_ID=$(az network vnet show --name "$VNET" --resource-group "$RG" --query id -o tsv)
SUBNET_PRIVATE_ID=$(az network vnet subnet show \
  --name "$SUBNET_PRIVATE" --vnet-name "$VNET" --resource-group "$RG" \
  --query id -o tsv)

# VNet Integration – koppla App Service till snet-app
echo "  Kontrollerar subnätsdelegering för App Service VNet Integration..."
if az network vnet subnet show --name "$SUBNET_APP" --vnet-name "$VNET" --resource-group "$RG" \
    --query "delegations[?serviceName=='Microsoft.Web/serverFarms'] | [0].id" -o tsv 2>/dev/null | grep -q .; then
  echo "  Subnätsdelegering för App Service finns redan – hoppar över."
else
  echo "  Lägger till delegering Microsoft.Web/serverFarms på '$SUBNET_APP'..."
  az network vnet subnet update \
    --name "$SUBNET_APP" \
    --vnet-name "$VNET" \
    --resource-group "$RG" \
    --delegations Microsoft.Web/serverFarms
fi

echo "  Kontrollerar VNet Integration för App Service..."
EXISTING_VNET_INTEGRATION=$(az webapp vnet-integration list \
  --name "$BACKEND_APP_NAME" --resource-group "$RG" \
  --query "[0].id" -o tsv 2>/dev/null || echo "")

if [ -n "$EXISTING_VNET_INTEGRATION" ]; then
  echo "  VNet Integration finns redan – hoppar över."
else
  echo "  Kopplar App Service till VNet '$VNET' (snet-app)..."
  az webapp vnet-integration add \
    --name "$BACKEND_APP_NAME" \
    --resource-group "$RG" \
    --vnet "$VNET" \
    --subnet "$SUBNET_APP"
fi

# ---------------------------------------------------------------------------
# 5. STORAGE ACCOUNT + Private Endpoint
# ---------------------------------------------------------------------------
log "5. Kontrollerar Storage Account (blob-storage)..."
STORAGE_ACCOUNT_CREATED=false

if az storage account show --name "$STORAGE_ACCOUNT" --resource-group "$RG" &>/dev/null; then
  echo "  Storage Account '$STORAGE_ACCOUNT' finns redan – uppdaterar inställningar..."
  STORAGE_DEV_SKU=$(az storage account show --name "$DEV_STORAGE" --resource-group "$DEV_RG" \
    --query "sku.name" -o tsv 2>/dev/null || echo "Standard_LRS")
  STORAGE_DEV_TLS=$(az storage account show --name "$DEV_STORAGE" --resource-group "$DEV_RG" \
    --query "minimumTlsVersion" -o tsv 2>/dev/null || echo "TLS1_2")
  STORAGE_DEV_SHARED_KEY=$(az storage account show --name "$DEV_STORAGE" --resource-group "$DEV_RG" \
    --query "allowSharedKeyAccess" -o tsv 2>/dev/null || echo "true")
  az storage account update \
    --name "$STORAGE_ACCOUNT" \
    --resource-group "$RG" \
    --sku "$STORAGE_DEV_SKU" \
    --min-tls-version "$STORAGE_DEV_TLS" \
    --allow-blob-public-access false \
    --public-network-access Enabled \
    --https-only true \
    --allow-shared-key-access "$STORAGE_DEV_SHARED_KEY" > /dev/null
else
  echo "  Hämtar Storage-konfiguration från dev..."
  STORAGE_DEV_SKU=$(az storage account show --name "$DEV_STORAGE" --resource-group "$DEV_RG" \
    --query "sku.name" -o tsv 2>/dev/null || echo "Standard_LRS")
  STORAGE_DEV_KIND=$(az storage account show --name "$DEV_STORAGE" --resource-group "$DEV_RG" \
    --query "kind" -o tsv 2>/dev/null || echo "StorageV2")
  STORAGE_DEV_TLS=$(az storage account show --name "$DEV_STORAGE" --resource-group "$DEV_RG" \
    --query "minimumTlsVersion" -o tsv 2>/dev/null || echo "TLS1_2")
  STORAGE_DEV_SHARED_KEY=$(az storage account show --name "$DEV_STORAGE" --resource-group "$DEV_RG" \
    --query "allowSharedKeyAccess" -o tsv 2>/dev/null || echo "true")
  STORAGE_ACCOUNT_CREATED=true
  echo "  Skapar Storage Account '$STORAGE_ACCOUNT'..."
  az storage account create \
    --name "$STORAGE_ACCOUNT" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --sku "$STORAGE_DEV_SKU" \
    --kind "$STORAGE_DEV_KIND" \
    --public-network-access Enabled \
    --allow-blob-public-access false \
    --min-tls-version "$STORAGE_DEV_TLS" \
    --https-only true \
    --allow-shared-key-access "$STORAGE_DEV_SHARED_KEY"
fi

STORAGE_ID=$(az storage account show --name "$STORAGE_ACCOUNT" --resource-group "$RG" --query id -o tsv)

if [ "$STORAGE_ACCOUNT_CREATED" = true ]; then
  echo "  Kopierar blob service-inställningar från dev..."
  DEV_BLOB_PROPS=$(az storage account blob-service-properties show \
    --account-name "$DEV_STORAGE" --resource-group "$DEV_RG" -o json 2>/dev/null || echo "{}")

BLOB_DELETE_ENABLED=$(echo "$DEV_BLOB_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('deleteRetentionPolicy',{}).get('enabled', False))")
BLOB_DELETE_DAYS=$(echo "$DEV_BLOB_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('deleteRetentionPolicy',{}).get('days', 7))")
CONTAINER_DELETE_ENABLED=$(echo "$DEV_BLOB_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('containerDeleteRetentionPolicy',{}).get('enabled', False))")
CONTAINER_DELETE_DAYS=$(echo "$DEV_BLOB_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('containerDeleteRetentionPolicy',{}).get('days', 7))")
VERSIONING_ENABLED=$(echo "$DEV_BLOB_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('isVersioningEnabled', False))")
CHANGE_FEED=$(echo "$DEV_BLOB_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('changeFeed',{}).get('enabled', False))")

az storage account blob-service-properties update \
  --account-name "$STORAGE_ACCOUNT" \
  --resource-group "$RG" \
  --enable-delete-retention "$BLOB_DELETE_ENABLED" \
  --delete-retention-days "$BLOB_DELETE_DAYS" \
  --enable-container-delete-retention "$CONTAINER_DELETE_ENABLED" \
  --container-delete-retention-days "$CONTAINER_DELETE_DAYS" \
  --enable-versioning "$VERSIONING_ENABLED" \
  --enable-change-feed "$CHANGE_FEED" > /dev/null

echo "  Blob service-inställningar kopierade."
fi

log "5b. Kontrollerar Private DNS Zone – Blob..."
if az network private-dns zone show --resource-group "$RG" --name "privatelink.blob.core.windows.net" &>/dev/null; then
  echo "  DNS-zon 'privatelink.blob.core.windows.net' finns redan – hoppar över."
else
  echo "  Skapar DNS-zon 'privatelink.blob.core.windows.net'..."
  az network private-dns zone create \
    --resource-group "$RG" \
    --name "privatelink.blob.core.windows.net"
fi

if az network private-dns link vnet show --resource-group "$RG" --zone-name "privatelink.blob.core.windows.net" --name "${ENV}-link" &>/dev/null; then
  echo "  VNet-länk för Blob DNS finns redan – hoppar över."
else
  echo "  Skapar VNet-länk för Blob DNS..."
  az network private-dns link vnet create \
    --resource-group "$RG" \
    --zone-name "privatelink.blob.core.windows.net" \
    --name "${ENV}-link" \
    --virtual-network "$VNET_ID" \
    --registration-enabled false
fi

if az network private-endpoint show --name "pe-blob-${ENV}" --resource-group "$RG" &>/dev/null; then
  echo "  Private endpoint 'pe-blob-${ENV}' finns redan – hoppar över."
else
  echo "  Skapar private endpoint 'pe-blob-${ENV}'..."
  az network private-endpoint create \
    --name "pe-blob-${ENV}" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --subnet "$SUBNET_PRIVATE_ID" \
    --private-connection-resource-id "$STORAGE_ID" \
    --group-id blob \
    --connection-name "pe-blob-${ENV}-conn"
fi

BLOB_PE_NIC=$(az network private-endpoint show \
  --name "pe-blob-${ENV}" --resource-group "$RG" \
  --query "networkInterfaces[0].id" -o tsv)

BLOB_PE_IP=$(az network nic show --ids "$BLOB_PE_NIC" \
  --query "ipConfigurations[0].privateIPAddress" -o tsv)

if az network private-dns record-set a show --resource-group "$RG" --zone-name "privatelink.blob.core.windows.net" --name "$STORAGE_ACCOUNT" &>/dev/null; then
  echo "  DNS A-record för Blob finns redan – hoppar över."
else
  az network private-dns record-set a add-record \
    --resource-group "$RG" \
    --zone-name "privatelink.blob.core.windows.net" \
    --record-set-name "$STORAGE_ACCOUNT" \
    --ipv4-address "$BLOB_PE_IP"
fi


# ---------------------------------------------------------------------------
# 6. FUNCTION APP (Service Bus worker) – skapas om den inte redan finns
# ---------------------------------------------------------------------------

log "6. Kontrollerar Function App..."

# Flex Consumption hanterar sin egen plan – ingen separat az appservice plan create
if az functionapp show --name "$FUNCTION_APP_NAME" --resource-group "$RG" &>/dev/null; then
  echo "  Function App '$FUNCTION_APP_NAME' finns redan – uppdaterar inställningar..."
  FA_DEV_CONFIG=$(az functionapp config show \
    --name "$DEV_FUNCTION_APP" --resource-group "$DEV_RG" -o json 2>/dev/null || echo "{}")
  FA_DEV_TLS=$(echo "$FA_DEV_CONFIG" | python3 -c "import json,sys; print(json.load(sys.stdin).get('minTlsVersion', '1.2'))")
  FA_DEV_HTTP=$(echo "$FA_DEV_CONFIG" | python3 -c "import json,sys; print(json.load(sys.stdin).get('http20Enabled', False))")
  FA_DEV_HTTPS=$(az functionapp show --name "$DEV_FUNCTION_APP" --resource-group "$DEV_RG" \
    --query "httpsOnly" -o tsv 2>/dev/null || echo "true")
  az functionapp update \
    --name "$FUNCTION_APP_NAME" \
    --resource-group "$RG" \
    --set siteConfig.minTlsVersion="$FA_DEV_TLS" \
    --set siteConfig.http20Enabled="$FA_DEV_HTTP" \
    --set httpsOnly="$FA_DEV_HTTPS" > /dev/null

  az resource update \
    --resource-group "$RG" \
    --name "$FUNCTION_APP_NAME" \
    --resource-type "Microsoft.Web/sites" \
    --set properties.httpsOnly=true \
    --set properties.siteConfig.http20Enabled="$FA_DEV_HTTP" > /dev/null

  echo "  Kontrollerar Managed Identity för Function App..."
  FA_IDENTITY=$(az functionapp identity show \
    --name "$FUNCTION_APP_NAME" \
    --resource-group "$RG" \
    --query principalId -o tsv 2>/dev/null || echo "")
  if [ -z "$FA_IDENTITY" ]; then
    echo "  Managed Identity saknas – tilldelar..."
    az functionapp identity assign \
      --name "$FUNCTION_APP_NAME" \
      --resource-group "$RG" \
      --system-assigned > /dev/null
    echo "  Managed Identity tilldelad."
  else
    echo "  Managed Identity finns redan – hoppar över."
  fi
else
  echo "  Hämtar Function App-konfiguration från dev..."
  FA_DEV_CONFIG=$(az functionapp config show \
    --name "$DEV_FUNCTION_APP" --resource-group "$DEV_RG" -o json 2>/dev/null || echo "{}")
  FA_DEV_TLS=$(echo "$FA_DEV_CONFIG" | python3 -c "import json,sys; print(json.load(sys.stdin).get('minTlsVersion', '1.2'))")
  FA_DEV_HTTP=$(echo "$FA_DEV_CONFIG" | python3 -c "import json,sys; print(json.load(sys.stdin).get('http20Enabled', False))")
  FA_DEV_HTTPS=$(az functionapp show --name "$DEV_FUNCTION_APP" --resource-group "$DEV_RG" \
    --query "httpsOnly" -o tsv 2>/dev/null || echo "true")

echo "  Skapar Function App '$FUNCTION_APP_NAME' (Flex Consumption)..."
az functionapp create \
    --name "$FUNCTION_APP_NAME" \
    --resource-group "$RG" \
    --runtime "$FUNCTION_RUNTIME" \
    --runtime-version "$FUNCTION_RUNTIME_VERSION" \
    --storage-account "$FUNCTION_STORAGE" \
    --flexconsumption-location "$LOCATION" \
    --configure-networking-later

echo "  Kopplar Function App till VNet '$VNET' (snet-func)..."
az functionapp vnet-integration add \
    --name "$FUNCTION_APP_NAME" \
    --resource-group "$RG" \
    --vnet "$VNET" \
    --subnet "$SUBNET_FUNC"

  echo "  Tilldelar Managed Identity..."
  az functionapp identity assign \
    --name "$FUNCTION_APP_NAME" \
    --resource-group "$RG" \
    --system-assigned

  echo "  Konfigurerar TLS, HTTP/2 och HTTPS..."
  AI_KEY=$(az monitor app-insights component show \
    --app "$AI_NAME" --resource-group "$RG" \
    --query "instrumentationKey" -o tsv 2>/dev/null || echo "")
  AI_CONN=$(az monitor app-insights component show \
    --app "$AI_NAME" --resource-group "$RG" \
    --query "connectionString" -o tsv 2>/dev/null || echo "")

  az functionapp update \
    --name "$FUNCTION_APP_NAME" \
    --resource-group "$RG" \
    --set siteConfig.minTlsVersion="$FA_DEV_TLS" \
    --set siteConfig.http20Enabled="$FA_DEV_HTTP" \
    --set httpsOnly="$FA_DEV_HTTPS"

  az resource update \
    --resource-group "$RG" \
    --name "$FUNCTION_APP_NAME" \
    --resource-type "Microsoft.Web/sites" \
    --set properties.httpsOnly=true \
    --set properties.siteConfig.http20Enabled="$FA_DEV_HTTP" > /dev/null

  az functionapp config appsettings set \
    --name "$FUNCTION_APP_NAME" \
    --resource-group "$RG" \
    --settings \
      "APPINSIGHTS_INSTRUMENTATIONKEY=$AI_KEY" \
      "APPLICATIONINSIGHTS_CONNECTION_STRING=$AI_CONN" > /dev/null
  echo "  Kopplade Function App till Application Insights '$AI_NAME'."
fi

# ---------------------------------------------------------------------------
# 7. KEY VAULT + Private Endpoint
# ---------------------------------------------------------------------------
log "7. Kontrollerar Key Vault..."

if az keyvault show --name "$KEY_VAULT" --resource-group "$RG" &>/dev/null; then
  echo "  Key Vault '$KEY_VAULT' finns redan – uppdaterar inställningar..."
  az keyvault update \
    --name "$KEY_VAULT" \
    --resource-group "$RG" \
    --enable-rbac-authorization true \
    --public-network-access Enabled > /dev/null
else
  echo "  Hämtar Key Vault-konfiguration från dev..."
  KV_DEV_SKU=$(az keyvault show --name "$DEV_KEY_VAULT" --resource-group "$DEV_RG" \
    --query "properties.sku.name" -o tsv 2>/dev/null || echo "standard")
  echo "  Skapar Key Vault '$KEY_VAULT'..."
  az keyvault create \
    --name "$KEY_VAULT" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --sku "$KV_DEV_SKU" \
    --enable-rbac-authorization true \
    --public-network-access Enabled
fi

KV_ID=$(az keyvault show --name "$KEY_VAULT" --resource-group "$RG" --query id -o tsv)

# Ge MSI åtkomst till KV
if az role assignment list --assignee "$MSI_PRINCIPAL" --role "Key Vault Secrets User" --scope "$KV_ID" --query "[0].id" -o tsv 2>/dev/null | grep -q .; then
  echo "  Roll 'Key Vault Secrets User' finns redan för MSI – hoppar över."
else
  echo "  Tilldelar 'Key Vault Secrets User' till MSI..."
  az role assignment create \
    --role "Key Vault Secrets User" \
    --assignee "$MSI_PRINCIPAL" \
    --scope "$KV_ID"
fi

echo "  Tilldelar Key Vault Secrets Officer till inloggad användare..."
CURRENT_USER=$(az ad signed-in-user show --query id -o tsv 2>/dev/null || echo "")
if [ -n "$CURRENT_USER" ]; then
  az role assignment create \
    --role "Key Vault Secrets Officer" \
    --assignee "$CURRENT_USER" \
    --scope "$KV_ID" > /dev/null 2>&1 || true
  sleep 15
fi

log "7b. Kontrollerar Private DNS Zone – Key Vault..."
if az network private-dns zone show --resource-group "$RG" --name "privatelink.vaultcore.azure.net" &>/dev/null; then
  echo "  DNS-zon 'privatelink.vaultcore.azure.net' finns redan – hoppar över."
else
  echo "  Skapar DNS-zon 'privatelink.vaultcore.azure.net'..."
  az network private-dns zone create \
    --resource-group "$RG" \
    --name "privatelink.vaultcore.azure.net"
fi

if az network private-dns link vnet show --resource-group "$RG" --zone-name "privatelink.vaultcore.azure.net" --name "${ENV}-link" &>/dev/null; then
  echo "  VNet-länk för Key Vault DNS finns redan – hoppar över."
else
  echo "  Skapar VNet-länk för Key Vault DNS..."
  az network private-dns link vnet create \
    --resource-group "$RG" \
    --zone-name "privatelink.vaultcore.azure.net" \
    --name "${ENV}-link" \
    --virtual-network "$VNET_ID" \
    --registration-enabled false
fi

if az network private-endpoint show --name "pe-kv-${ENV}" --resource-group "$RG" &>/dev/null; then
  echo "  Private endpoint 'pe-kv-${ENV}' finns redan – hoppar över."
else
  echo "  Skapar private endpoint 'pe-kv-${ENV}'..."
  az network private-endpoint create \
    --name "pe-kv-${ENV}" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --subnet "$SUBNET_PRIVATE_ID" \
    --private-connection-resource-id "$KV_ID" \
    --group-id vault \
    --connection-name "pe-kv-${ENV}-conn"
fi

KV_PE_NIC=$(az network private-endpoint show \
  --name "pe-kv-${ENV}" --resource-group "$RG" \
  --query "networkInterfaces[0].id" -o tsv)

KV_PE_IP=$(az network nic show --ids "$KV_PE_NIC" \
  --query "ipConfigurations[0].privateIPAddress" -o tsv)

if az network private-dns record-set a show --resource-group "$RG" --zone-name "privatelink.vaultcore.azure.net" --name "$KEY_VAULT" &>/dev/null; then
  echo "  DNS A-record för Key Vault finns redan – hoppar över."
else
  az network private-dns record-set a add-record \
    --resource-group "$RG" \
    --zone-name "privatelink.vaultcore.azure.net" \
    --record-set-name "$KEY_VAULT" \
    --ipv4-address "$KV_PE_IP"
fi

# ---------------------------------------------------------------------------
# 8. SQL SERVER + DATABAS + Private Endpoint
# ---------------------------------------------------------------------------
log "8. Kontrollerar SQL Server och databas..."
SQL_DB_CREATED=false

if [ -z "$SQL_ADMIN_PASSWORD" ]; then
  echo "FEL: SQL_ADMIN_PASSWORD är inte satt. Avbryter." >&2
  exit 1
fi

if az sql server show --name "$SQL_SERVER" --resource-group "$RG" &>/dev/null; then
  echo "  SQL Server '$SQL_SERVER' finns redan – uppdaterar inställningar..."
  SQL_DEV_SERVER_PROPS=$(az sql server show \
    --name "$DEV_SQL_SERVER" --resource-group "$DEV_RG" -o json 2>/dev/null || echo "{}")
  SQL_DEV_TLS=$(echo "$SQL_DEV_SERVER_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('minimalTlsVersion', '1.2'))")
  az sql server update \
    --name "$SQL_SERVER" \
    --resource-group "$RG" \
    --set publicNetworkAccess=Enabled \
    --minimal-tls-version "$SQL_DEV_TLS" > /dev/null

  SQL_DEV_AAD_LOGIN=$(az sql server ad-admin list \
    --server "$DEV_SQL_SERVER" --resource-group "$DEV_RG" \
    --query "[0].login" -o tsv 2>/dev/null || echo "")
  SQL_DEV_AAD_SID=$(az sql server ad-admin list \
    --server "$DEV_SQL_SERVER" --resource-group "$DEV_RG" \
    --query "[0].sid" -o tsv 2>/dev/null || echo "")

  if [ -n "$SQL_DEV_AAD_LOGIN" ]; then
    echo "  Uppdaterar Entra ID-administratör '$SQL_DEV_AAD_LOGIN'..."
    az sql server ad-admin create \
      --server "$SQL_SERVER" \
      --resource-group "$RG" \
      --display-name "$SQL_DEV_AAD_LOGIN" \
      --object-id "$SQL_DEV_AAD_SID" > /dev/null
  fi
else
  echo "  Skapar SQL Server '$SQL_SERVER'..."
  az sql server create \
    --name "$SQL_SERVER" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --admin-user "$SQL_ADMIN_USER" \
    --admin-password "$SQL_ADMIN_PASSWORD"

  echo "  Hämtar SQL Server-konfiguration från dev..."
  SQL_DEV_SERVER_PROPS=$(az sql server show \
    --name "$DEV_SQL_SERVER" --resource-group "$DEV_RG" -o json 2>/dev/null || echo "{}")
  SQL_DEV_TLS=$(echo "$SQL_DEV_SERVER_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('minimalTlsVersion', '1.2'))")

  az sql server update \
    --name "$SQL_SERVER" \
    --resource-group "$RG" \
    --set publicNetworkAccess=Enabled \
    --minimal-tls-version "$SQL_DEV_TLS"

  SQL_DEV_AAD_LOGIN=$(az sql server ad-admin list \
    --server "$DEV_SQL_SERVER" --resource-group "$DEV_RG" \
    --query "[0].login" -o tsv 2>/dev/null || echo "")
  SQL_DEV_AAD_SID=$(az sql server ad-admin list \
    --server "$DEV_SQL_SERVER" --resource-group "$DEV_RG" \
    --query "[0].sid" -o tsv 2>/dev/null || echo "")

  if [ -n "$SQL_DEV_AAD_LOGIN" ]; then
    echo "  Sätter Entra ID-administratör '$SQL_DEV_AAD_LOGIN'..."
    az sql server ad-admin create \
      --server "$SQL_SERVER" \
      --resource-group "$RG" \
      --display-name "$SQL_DEV_AAD_LOGIN" \
      --object-id "$SQL_DEV_AAD_SID"
  fi
fi

if az sql db show --name "$SQL_DB" --server "$SQL_SERVER" --resource-group "$RG" &>/dev/null; then
  echo "  SQL databas '$SQL_DB' finns redan – uppdaterar inställningar..."
  SQL_DEV_PROPS=$(az sql db show \
    --name "$DEV_SQL_DB" --server "$DEV_SQL_SERVER" --resource-group "$DEV_RG" -o json 2>/dev/null || echo "{}")
  SQL_DEV_TIER=$(echo "$SQL_DEV_PROPS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('edition', 'GeneralPurpose'))")
  SQL_DEV_CAPACITY=$(echo "$SQL_DEV_PROPS" | python3 -c "
import json,sys
d=json.load(sys.stdin)
tier = d.get('edition', 'GeneralPurpose')
if tier == 'Basic':
    print(5)
else:
    print(d.get('capacity', 2))
")
  SQL_DEV_ZONE=$(echo "$SQL_DEV_PROPS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(str(d.get('zoneRedundant', False)).lower())")
  az sql db update \
    --name "$SQL_DB" \
    --server "$SQL_SERVER" \
    --resource-group "$RG" \
    --tier "$SQL_DEV_TIER" \
    --capacity "$SQL_DEV_CAPACITY" \
    --zone-redundant "$SQL_DEV_ZONE" > /dev/null
else
  echo "  Hämtar SQL-konfiguration från dev..."
  SQL_DEV_PROPS=$(az sql db show \
    --name "$DEV_SQL_DB" --server "$DEV_SQL_SERVER" --resource-group "$DEV_RG" -o json 2>/dev/null || echo "{}")
  SQL_DEV_TIER=$(echo "$SQL_DEV_PROPS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('edition', 'GeneralPurpose'))")
  SQL_DEV_FAMILY=$(echo "$SQL_DEV_PROPS" | python3 -c "
import json,sys
d=json.load(sys.stdin)
tier = d.get('edition', 'GeneralPurpose')
name = d.get('currentServiceObjectiveName','')
if tier in ('Basic', 'Standard', 'Premium'):
    print('None')
else:
    print(name.split('_')[1] if '_' in name else 'Gen5')
")
  SQL_DEV_CAPACITY=$(echo "$SQL_DEV_PROPS" | python3 -c "
import json,sys
d=json.load(sys.stdin)
tier = d.get('edition', 'GeneralPurpose')
if tier == 'Basic':
    print(5)
else:
    print(d.get('capacity', 2))
")
  SQL_DEV_ZONE=$(echo "$SQL_DEV_PROPS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(str(d.get('zoneRedundant', False)).lower())")
  echo "  Skapar SQL databas '$SQL_DB'..."
  SQL_DB_CREATED=true
az sql db create \
    --name "$SQL_DB" \
    --server "$SQL_SERVER" \
    --resource-group "$RG" \
    --tier "$SQL_DEV_TIER" \
    $( [ "$SQL_DEV_FAMILY" != "None" ] && echo "--family $SQL_DEV_FAMILY" ) \
    --capacity "$SQL_DEV_CAPACITY" \
    --zone-redundant "$SQL_DEV_ZONE"
fi

if [ "$SQL_DB_CREATED" = true ]; then
echo "  Exporterar databas från dev..."
EXPORT_STORAGE_KEY=$(az storage account keys list \
  --account-name "$DEV_STORAGE" \
  --resource-group "$DEV_RG" \
  --query "[0].value" -o tsv 2>/dev/null || echo "")

if [ -z "$EXPORT_STORAGE_KEY" ]; then
  echo "  Kunde inte hämta storage-nyckel – hoppar över databasexport." >&2
else
  # Skapa container om den inte finns
  az rest \
    --method put \
    --url "https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${DEV_RG}/providers/Microsoft.Storage/storageAccounts/${DEV_STORAGE}/blobServices/default/containers/sqlexport?api-version=2023-01-01" \
    --body '{"properties": {"publicAccess": "None"}}' > /dev/null 2>&1 || true

  echo "  Tar bort eventuell gammal exportfil..."
  az storage blob delete \
    --account-name "$DEV_STORAGE" \
    --container-name "sqlexport" \
    --name "schema-export.bacpac" \
    --account-key "$EXPORT_STORAGE_KEY" > /dev/null 2>&1 || true
  
  echo "  Startar export från dev-databas..."
  az sql db export \
    --name "$DEV_SQL_DB" \
    --server "$DEV_SQL_SERVER" \
    --resource-group "$DEV_RG" \
    --admin-user "$SQL_ADMIN_USER" \
    --admin-password "$SQL_ADMIN_PASSWORD" \
    --storage-key-type StorageAccessKey \
    --storage-key "$EXPORT_STORAGE_KEY" \
    --storage-uri "https://${DEV_STORAGE}.blob.core.windows.net/sqlexport/schema-export.bacpac" > /dev/null

  echo "  Väntar på att export ska slutföras..."
  while true; do
    STATUS=$(az sql db operation list \
      --name "$DEV_SQL_DB" \
      --server "$DEV_SQL_SERVER" \
      --resource-group "$DEV_RG" \
      --query "[?operation=='ExportDatabase'].percentComplete" \
      -o tsv 2>/dev/null || echo "")
    if [ -z "$STATUS" ] || [ "$STATUS" = "100" ]; then
      echo "  Export klar."
      break
    fi
    echo "  Export pågår ($STATUS%)..."
    sleep 10
  done
  
  echo "  Importerar databas till prod..."
  az sql db import \
    --name "$SQL_DB" \
    --server "$SQL_SERVER" \
    --resource-group "$RG" \
    --admin-user "$SQL_ADMIN_USER" \
    --admin-password "$SQL_ADMIN_PASSWORD" \
    --storage-key-type StorageAccessKey \
    --storage-key "$EXPORT_STORAGE_KEY" \
    --storage-uri "https://${DEV_STORAGE}.blob.core.windows.net/sqlexport/schema-export.bacpac" > /dev/null

  echo "  Databas importerad till prod."
fi
fi

SQL_ID=$(az sql server show --name "$SQL_SERVER" --resource-group "$RG" --query id -o tsv)

log "8b. Kontrollerar Private DNS Zone – SQL..."
if az network private-dns zone show --resource-group "$RG" --name "privatelink.database.windows.net" &>/dev/null; then
  echo "  DNS-zon 'privatelink.database.windows.net' finns redan – hoppar över."
else
  echo "  Skapar DNS-zon 'privatelink.database.windows.net'..."
  az network private-dns zone create \
    --resource-group "$RG" \
    --name "privatelink.database.windows.net"
fi

if az network private-dns link vnet show --resource-group "$RG" --zone-name "privatelink.database.windows.net" --name "${ENV}-link" &>/dev/null; then
  echo "  VNet-länk för SQL DNS finns redan – hoppar över."
else
  echo "  Skapar VNet-länk för SQL DNS..."
  az network private-dns link vnet create \
    --resource-group "$RG" \
    --zone-name "privatelink.database.windows.net" \
    --name "${ENV}-link" \
    --virtual-network "$VNET_ID" \
    --registration-enabled false
fi

if az network private-endpoint show --name "pe-sql-${ENV}" --resource-group "$RG" &>/dev/null; then
  echo "  Private endpoint 'pe-sql-${ENV}' finns redan – hoppar över."
else
  echo "  Skapar private endpoint 'pe-sql-${ENV}'..."
  az network private-endpoint create \
    --name "pe-sql-${ENV}" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --subnet "$SUBNET_PRIVATE_ID" \
    --private-connection-resource-id "$SQL_ID" \
    --group-id sqlServer \
    --connection-name "pe-sql-${ENV}-conn"
fi

SQL_PE_NIC=$(az network private-endpoint show \
  --name "pe-sql-${ENV}" --resource-group "$RG" \
  --query "networkInterfaces[0].id" -o tsv)

SQL_PE_IP=$(az network nic show --ids "$SQL_PE_NIC" \
  --query "ipConfigurations[0].privateIPAddress" -o tsv)

if az network private-dns record-set a show --resource-group "$RG" --zone-name "privatelink.database.windows.net" --name "$SQL_SERVER" &>/dev/null; then
  echo "  DNS A-record för SQL finns redan – hoppar över."
else
  az network private-dns record-set a add-record \
    --resource-group "$RG" \
    --zone-name "privatelink.database.windows.net" \
    --record-set-name "$SQL_SERVER" \
    --ipv4-address "$SQL_PE_IP"
fi

# ---------------------------------------------------------------------------
# 9. SERVICE BUS
# ---------------------------------------------------------------------------
log "9. Kontrollerar Service Bus..."

if az servicebus namespace show --name "$SERVICE_BUS" --resource-group "$RG" &>/dev/null; then
  echo "  Service Bus '$SERVICE_BUS' finns redan – uppdaterar inställningar..."
  SB_DEV_SKU=$(az servicebus namespace show \
    --name "$DEV_SERVICE_BUS" --resource-group "$DEV_RG" \
    --query "sku.name" -o tsv 2>/dev/null || echo "Standard")
  az servicebus namespace update \
    --name "$SERVICE_BUS" \
    --resource-group "$RG" \
    --sku "$SB_DEV_SKU" > /dev/null
else
  echo "  Hämtar Service Bus-konfiguration från dev..."
  SB_DEV_SKU=$(az servicebus namespace show \
    --name "$DEV_SERVICE_BUS" --resource-group "$DEV_RG" \
    --query "sku.name" -o tsv 2>/dev/null || echo "Standard")
  echo "  Skapar Service Bus '$SERVICE_BUS'..."
  az servicebus namespace create \
    --name "$SERVICE_BUS" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --sku "$SB_DEV_SKU"
fi

# ---------------------------------------------------------------------------
# 10. AZURE OPENAI + Private Endpoint
# ---------------------------------------------------------------------------
log "10. Kontrollerar Azure OpenAI..."

if az cognitiveservices account show --name "$OPENAI_ACCOUNT" --resource-group "$RG" &>/dev/null; then
  echo "  Azure OpenAI '$OPENAI_ACCOUNT' finns redan – uppdaterar inställningar..."
  az cognitiveservices account update \
    --name "$OPENAI_ACCOUNT" \
    --resource-group "$RG" \
    --custom-domain "$OPENAI_ACCOUNT" > /dev/null

  az rest \
    --method patch \
    --url "https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.CognitiveServices/accounts/${OPENAI_ACCOUNT}?api-version=2023-05-01" \
    --body '{"properties": {"publicNetworkAccess": "Enabled"}}' > /dev/null
else
  echo "  Hämtar Azure OpenAI-konfiguration från dev..."
  OAI_DEV_SKU=$(az cognitiveservices account show \
    --name "$DEV_OPENAI" --resource-group "$DEV_RG" \
    --query "sku.name" -o tsv 2>/dev/null || echo "S0")
  echo "  Skapar Azure OpenAI '$OPENAI_ACCOUNT'..."
  az cognitiveservices account create \
    --name "$OPENAI_ACCOUNT" \
    --resource-group "$RG" \
    --location "$OPENAI_LOCATION" \
    --kind OpenAI \
    --sku "$OAI_DEV_SKU" \
    --custom-domain "$OPENAI_ACCOUNT" \
    --public-network-access Enabled
fi

OAI_ID=$(az cognitiveservices account show \
  --name "$OPENAI_ACCOUNT" --resource-group "$RG" --query id -o tsv)

log "10b. Kontrollerar Private DNS Zone – OpenAI..."
if az network private-dns zone show --resource-group "$RG" --name "privatelink.openai.azure.com" &>/dev/null; then
  echo "  DNS-zon 'privatelink.openai.azure.com' finns redan – hoppar över."
else
  echo "  Skapar DNS-zon 'privatelink.openai.azure.com'..."
  az network private-dns zone create \
    --resource-group "$RG" \
    --name "privatelink.openai.azure.com"
fi

if az network private-dns link vnet show --resource-group "$RG" --zone-name "privatelink.openai.azure.com" --name "${ENV}-link" &>/dev/null; then
  echo "  VNet-länk för OpenAI DNS finns redan – hoppar över."
else
  echo "  Skapar VNet-länk för OpenAI DNS..."
  az network private-dns link vnet create \
    --resource-group "$RG" \
    --zone-name "privatelink.openai.azure.com" \
    --name "${ENV}-link" \
    --virtual-network "$VNET_ID" \
    --registration-enabled false
fi

if az network private-endpoint show --name "pe-oai-${ENV}" --resource-group "$RG" &>/dev/null; then
  echo "  Private endpoint 'pe-oai-${ENV}' finns redan – hoppar över."
else
  echo "  Skapar private endpoint 'pe-oai-${ENV}'..."
  az network private-endpoint create \
    --name "pe-oai-${ENV}" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --subnet "$SUBNET_PRIVATE_ID" \
    --private-connection-resource-id "$OAI_ID" \
    --group-id account \
    --connection-name "pe-oai-${ENV}-conn"
fi

OAI_PE_NIC=$(az network private-endpoint show \
  --name "pe-oai-${ENV}" --resource-group "$RG" \
  --query "networkInterfaces[0].id" -o tsv)

OAI_PE_IP=$(az network nic show --ids "$OAI_PE_NIC" \
  --query "ipConfigurations[0].privateIPAddress" -o tsv)

if az network private-dns record-set a show --resource-group "$RG" --zone-name "privatelink.openai.azure.com" --name "$OPENAI_ACCOUNT" &>/dev/null; then
  echo "  DNS A-record för OpenAI finns redan – hoppar över."
else
  az network private-dns record-set a add-record \
    --resource-group "$RG" \
    --zone-name "privatelink.openai.azure.com" \
    --record-set-name "$OPENAI_ACCOUNT" \
    --ipv4-address "$OAI_PE_IP"
fi

# ---------------------------------------------------------------------------
# 11. AZURE FRONT DOOR (CDN-profil + endpoint)
# ---------------------------------------------------------------------------
log "11. Kontrollerar Azure Front Door..."

if az afd profile show --profile-name "$AFD_PROFILE" --resource-group "$RG" &>/dev/null; then
  echo "  Front Door '$AFD_PROFILE' finns redan – uppdaterar inställningar..."
  AFD_DEV_TIMEOUT=$(az afd profile show \
    --profile-name "$DEV_AFD_PROFILE" --resource-group "$DEV_RG" \
    --query "originResponseTimeoutSeconds" -o tsv 2>/dev/null || echo "60")
  az afd profile update \
    --profile-name "$AFD_PROFILE" \
    --resource-group "$RG" \
    --origin-response-timeout-seconds "$AFD_DEV_TIMEOUT" > /dev/null
else
  echo "  Hämtar Front Door-konfiguration från dev..."
  AFD_DEV_SKU=$(az afd profile show \
    --profile-name "$DEV_AFD_PROFILE" --resource-group "$DEV_RG" \
    --query "sku.name" -o tsv 2>/dev/null || echo "Standard_AzureFrontDoor")
  AFD_DEV_TIMEOUT=$(az afd profile show \
    --profile-name "$DEV_AFD_PROFILE" --resource-group "$DEV_RG" \
    --query "originResponseTimeoutSeconds" -o tsv 2>/dev/null || echo "60")
  echo "  Skapar Front Door '$AFD_PROFILE'..."
  az afd profile create \
    --profile-name "$AFD_PROFILE" \
    --resource-group "$RG" \
    --sku "$AFD_DEV_SKU" \
    --origin-response-timeout-seconds "$AFD_DEV_TIMEOUT"
fi

if az afd endpoint show --endpoint-name "$AFD_ENDPOINT" --profile-name "$AFD_PROFILE" --resource-group "$RG" &>/dev/null; then
  echo "  Front Door endpoint '$AFD_ENDPOINT' finns redan – hoppar över."
else
  echo "  Skapar Front Door endpoint '$AFD_ENDPOINT'..."
  az afd endpoint create \
    --endpoint-name "$AFD_ENDPOINT" \
    --profile-name "$AFD_PROFILE" \
    --resource-group "$RG"
fi

# ---------------------------------------------------------------------------
# 12. MONITORING – Action Groups
# ---------------------------------------------------------------------------
log "12. Kontrollerar Action Groups..."

if [ -z "$ALERT_EMAIL" ]; then
  echo "VARNING: ALERT_EMAIL är inte satt – e-postaviseringar skapas utan mottagare." >&2
fi

# E-post notifiering
if az monitor action-group show --name "Email notification" --resource-group "$RG" &>/dev/null; then
  echo "  Action group 'Email notification' finns redan – hoppar över."
else
  echo "  Skapar action group 'Email notification'..."
  az monitor action-group create \
    --name "Email notification" \
    --resource-group "$RG" \
    --short-name "email-notif" \
    ${ALERT_EMAIL:+--action email emailreceiver "$ALERT_EMAIL"}
fi

if az monitor action-group show --name "ag-alerts-${ENV}" --resource-group "$RG" &>/dev/null; then
  echo "  Action group 'ag-alerts-${ENV}' finns redan – hoppar över."
else
  echo "  Skapar action group 'ag-alerts-${ENV}'..."
  az monitor action-group create \
    --name "ag-alerts-${ENV}" \
    --resource-group "$RG" \
    --short-name "ag-alerts" \
    ${ALERT_EMAIL:+--action email emailreceiver "$ALERT_EMAIL"}
fi

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
# 13. MONITORING – Metric Alerts (CPU, requests, response time)
# ---------------------------------------------------------------------------
log "13. Kontrollerar Metric Alerts..."

# Hämta App Service resource ID
BACKEND_APP_ID=$(az webapp show \
  --name "$BACKEND_APP_NAME" --resource-group "$RG" --query id -o tsv 2>/dev/null || echo "")
ASP_ID=$(az appservice plan show \
  --name "$ASP_NAME" --resource-group "$RG" --query id -o tsv 2>/dev/null || echo "")

if [ -n "$BACKEND_APP_ID" ]; then

  get_alert_prop() {
    local alert_name="$1" prop="$2" default="$3"
    az monitor metrics alert show --name "$alert_name" --resource-group "$DEV_RG" \
      --query "$prop" -o tsv 2>/dev/null || echo "$default"
  }

  if az monitor metrics alert show --name "alert-cpu-${ENV}" --resource-group "$RG" &>/dev/null; then
    echo "  Metric alert 'alert-cpu-${ENV}' finns redan – hoppar över."
  else
    echo "  Hämtar Metric Alert-konfiguration från dev..."
    CPU_THRESHOLD=$(az monitor metrics alert show --name "alert-cpu-dev" --resource-group "$DEV_RG" \
      --query "criteria.allOf[0].threshold" -o tsv 2>/dev/null || echo "80")
    CPU_WINDOW=$(get_alert_prop "alert-cpu-dev" "windowSize" "PT5M")
    CPU_FREQ=$(get_alert_prop "alert-cpu-dev" "evaluationFrequency" "PT1M")
    CPU_SEVERITY=$(get_alert_prop "alert-cpu-dev" "severity" "2")
    echo "  Skapar metric alert 'alert-cpu-${ENV}'..."
az monitor metrics alert create \
  --name "alert-cpu-${ENV}" \
  --resource-group "$RG" \
  --scopes "$ASP_ID" \
  --condition "avg CpuPercentage > $CPU_THRESHOLD" \
  --window-size "$CPU_WINDOW" \
  --evaluation-frequency "$CPU_FREQ" \
  --severity "$CPU_SEVERITY" \
  --description "CPU > $CPU_THRESHOLD% på backend" \
  --action "$AG_ALERTS_ID"
  fi

  if az monitor metrics alert show --name "alert-requests-${ENV}" --resource-group "$RG" &>/dev/null; then
    echo "  Metric alert 'alert-requests-${ENV}' finns redan – hoppar över."
  else
    REQ_THRESHOLD=$(az monitor metrics alert show --name "alert-requests-dev" --resource-group "$DEV_RG" \
      --query "criteria.allOf[0].threshold" -o tsv 2>/dev/null || echo "10")
    REQ_WINDOW=$(get_alert_prop "alert-requests-dev" "windowSize" "PT5M")
    REQ_FREQ=$(get_alert_prop "alert-requests-dev" "evaluationFrequency" "PT1M")
    REQ_SEVERITY=$(get_alert_prop "alert-requests-dev" "severity" "2")
    echo "  Skapar metric alert 'alert-requests-${ENV}'..."
az monitor metrics alert create \
  --name "alert-requests-${ENV}" \
  --resource-group "$RG" \
  --scopes "$BACKEND_APP_ID" \
  --condition "total Http5xx > $REQ_THRESHOLD" \
  --window-size "$REQ_WINDOW" \
  --evaluation-frequency "$REQ_FREQ" \
  --severity "$REQ_SEVERITY" \
  --description "Fler än $REQ_THRESHOLD HTTP 5xx-fel" \
  --action "$AG_ALERTS_ID"
  fi

  if az monitor metrics alert show --name "alert-response-${ENV}" --resource-group "$RG" &>/dev/null; then
    echo "  Metric alert 'alert-response-${ENV}' finns redan – hoppar över."
  else
    RESP_THRESHOLD=$(az monitor metrics alert show --name "alert-response-dev" --resource-group "$DEV_RG" \
      --query "criteria.allOf[0].threshold" -o tsv 2>/dev/null || echo "3")
    RESP_WINDOW=$(get_alert_prop "alert-response-dev" "windowSize" "PT5M")
    RESP_FREQ=$(get_alert_prop "alert-response-dev" "evaluationFrequency" "PT1M")
    RESP_SEVERITY=$(get_alert_prop "alert-response-dev" "severity" "3")
    echo "  Skapar metric alert 'alert-response-${ENV}'..."
az monitor metrics alert create \
  --name "alert-response-${ENV}" \
  --resource-group "$RG" \
  --scopes "$BACKEND_APP_ID" \
  --condition "avg HttpResponseTime > $RESP_THRESHOLD" \
  --window-size "$RESP_WINDOW" \
  --evaluation-frequency "$RESP_FREQ" \
  --severity "$RESP_SEVERITY" \
  --description "Svarstid > $RESP_THRESHOLD sek" \
  --action "$AG_ALERTS_ID"
  fi

else
  echo "VARNING: Kunde inte hitta App Service '$BACKEND_APP_NAME' – metric alerts för backend skapas inte." >&2
fi

# ---------------------------------------------------------------------------
# 14. APPLICATION INSIGHTS – skapas om de inte redan finns
# ---------------------------------------------------------------------------
log "14. Kontrollerar Application Insights..."

AI_NAME="app-${ENV}-api"
if az monitor app-insights component show --app "$AI_NAME" --resource-group "$RG" &>/dev/null; then
  echo "  Application Insights '$AI_NAME' finns redan – uppdaterar retention..."
  AI_DEV_RETENTION=$(az monitor app-insights component show \
    --app "$DEV_BACKEND_APP" --resource-group "$DEV_RG" \
    --query "retentionInDays" -o tsv 2>/dev/null || echo "90")
  az monitor app-insights component update \
    --app "$AI_NAME" \
    --resource-group "$RG" \
    --retention-time "$AI_DEV_RETENTION" > /dev/null
else
  echo "  Skapar Application Insights '$AI_NAME'..."
  echo "  Hämtar Application Insights-konfiguration från dev..."
  AI_DEV_PROPS=$(az monitor app-insights component show \
    --app "$DEV_BACKEND_APP" --resource-group "$DEV_RG" -o json 2>/dev/null || echo "{}")
  AI_DEV_KIND=$(echo "$AI_DEV_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('kind', 'web'))")
  AI_DEV_TYPE=$(echo "$AI_DEV_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('applicationType', 'web'))")
  AI_DEV_RETENTION=$(echo "$AI_DEV_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('retentionInDays', 90))")

  az monitor app-insights component create \
    --app "$AI_NAME" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --kind "$AI_DEV_KIND" \
    --application-type "$AI_DEV_TYPE" \
    --retention-time "$AI_DEV_RETENTION"
fi

AI_ID=$(az monitor app-insights component show \
  --app "$AI_NAME" --resource-group "$RG" --query id -o tsv)

echo "  Kopplar App Service till Application Insights..."
AI_KEY=$(az monitor app-insights component show \
  --app "$AI_NAME" --resource-group "$RG" \
  --query "instrumentationKey" -o tsv 2>/dev/null || echo "")
AI_CONN=$(az monitor app-insights component show \
  --app "$AI_NAME" --resource-group "$RG" \
  --query "connectionString" -o tsv 2>/dev/null || echo "")

if [ -n "$AI_KEY" ]; then
  az webapp config appsettings set \
    --name "$BACKEND_APP_NAME" \
    --resource-group "$RG" \
    --settings \
      "APPINSIGHTS_INSTRUMENTATIONKEY=$AI_KEY" \
      "APPLICATIONINSIGHTS_CONNECTION_STRING=$AI_CONN" > /dev/null
  echo "  App Service kopplad till Application Insights '$AI_NAME'."
fi


# ---------------------------------------------------------------------------
# 15. AVAILABILITY TESTS
# ---------------------------------------------------------------------------
log "15. Skapar Availability Tests..."

BACKEND_APP_URL=$(az webapp show --name "$BACKEND_APP_NAME" --resource-group "$RG" \
  --query "defaultHostName" -o tsv 2>/dev/null | tr -d '\r' || echo "")
BACKEND_APP_URL="https://${BACKEND_APP_URL}/health"
if [ -n "$BACKEND_APP_URL" ]; then
  echo "  Hämtar Availability Test-konfiguration från dev..."
  DEV_AVAIL_PROPS=$(az monitor app-insights web-test show \
    --name "availability test-app-dev-api" --resource-group "$DEV_RG" -o json 2>/dev/null || echo "{}")
  AVAIL_FREQUENCY=$(echo "$DEV_AVAIL_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('frequency', 300))")
  AVAIL_TIMEOUT=$(echo "$DEV_AVAIL_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('timeout', 30))")
  AVAIL_LOCATIONS=$(echo "$DEV_AVAIL_PROPS" | python3 -c "
import json, sys
d = json.load(sys.stdin)
locs = d.get('locations', [{'id': 'emea-se-sto-edge'}])
print(' '.join(f'Id={l[\"id\"]}' for l in locs))
" 2>/dev/null || echo "Id=emea-se-sto-edge")

  if az monitor app-insights web-test show --name "availability test-app-${ENV}-api" --resource-group "$RG" &>/dev/null; then
    echo "  Availability test 'availability test-app-${ENV}-api' finns redan – hoppar över."
  else
    echo "  Skapar availability test för API mot '$BACKEND_APP_URL'..."
az rest --method put \
    --url "https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.Insights/webtests/availability-test-app-${ENV}-api?api-version=2022-06-15" \
    --body "{
        \"location\": \"${LOCATION}\",
        \"tags\": {\"hidden-link:${AI_ID}\": \"Resource\"},
        \"properties\": {
            \"Name\": \"availability test-app-${ENV}-api\",
            \"SyntheticMonitorId\": \"availability-test-app-${ENV}-api\",
            \"Description\": \"Availability test för API\",
            \"Enabled\": true,
            \"Frequency\": ${AVAIL_FREQUENCY},
            \"Timeout\": ${AVAIL_TIMEOUT},
            \"Kind\": \"ping\",
            \"Locations\": [{\"Id\": \"emea-se-sto-edge\"}],
            \"Configuration\": {
                \"WebTest\": \"<WebTest Name='availability test-app-${ENV}-api' Enabled='True' Timeout='${AVAIL_TIMEOUT}' xmlns='http://microsoft.com/schemas/VisualStudio/TeamTest/2010'><Items><Request Method='GET' Version='1.1' Url='${BACKEND_APP_URL}' /></Items></WebTest>\"
            }
        }
    }" > /dev/null
  if [ $? -eq 0 ]; then
    echo "  Availability test skapat för '$BACKEND_APP_URL'."
  else
    echo "  FEL: Kunde inte skapa availability test för '$BACKEND_APP_URL'." >&2
  fi
  fi

  # Availability test alert-regel
  AVAIL_TEST_ID=$(az monitor app-insights web-test show \
    --name "availability test-app-${ENV}-api" --resource-group "$RG" \
    --query id -o tsv 2>/dev/null || echo "")

  if [ -n "$AVAIL_TEST_ID" ]; then
    if az monitor metrics alert show --name "availability test-app-${ENV}-api" --resource-group "$RG" &>/dev/null; then
      echo "  Alert-regel för availability test finns redan – hoppar över."
    else
      echo "  Skapar alert-regel för availability test..."

      AVAIL_ALERT_FREQ=$(az monitor metrics alert show \
        --name "availability test-app-dev-api" --resource-group "$DEV_RG" \
        --query evaluationFrequency -o tsv 2>/dev/null || echo "PT1M")
      AVAIL_ALERT_WINDOW=$(az monitor metrics alert show \
        --name "availability test-app-dev-api" --resource-group "$DEV_RG" \
        --query windowSize -o tsv 2>/dev/null || echo "PT5M")
      AVAIL_ALERT_SEVERITY=$(az monitor metrics alert show \
        --name "availability test-app-dev-api" --resource-group "$DEV_RG" \
        --query severity -o tsv 2>/dev/null || echo "1")

      az monitor metrics alert create \
        --name "availability test-app-${ENV}-api" \
        --resource-group "$RG" \
        --scopes "$AVAIL_TEST_ID" "$AI_ID" \
        --condition "avg availabilityResults/availabilityPercentage < 100" \
        --window-size "$AVAIL_ALERT_WINDOW" \
        --evaluation-frequency "$AVAIL_ALERT_FREQ" \
        --severity "$AVAIL_ALERT_SEVERITY" \
        --description "Automatically created alert rule for availability test \"availability test-app-${ENV}-api\"" \
        --action "$AG_ALERTS_ID"
    fi
  fi
else
  echo "  VARNING: BACKEND_APP_URL är inte satt – availability test för API skapas inte." >&2
fi


# ---------------------------------------------------------------------------
# 16. KOPIERA INSTÄLLNINGAR FRÅN DEV
# ---------------------------------------------------------------------------

log "16. Kopierar inställningar från dev-miljön..."

# --- App Service: app settings och connection strings ---
echo "  Static Web App: kopierar environment variables..."
DEV_SWA_VARS=$(az staticwebapp appsettings list \
  --name "$DEV_SWA" --resource-group "$DEV_RG" \
  --query "properties" -o json 2>/dev/null || echo "{}")

if [ "$DEV_SWA_VARS" != "{}" ] && [ -n "$DEV_SWA_VARS" ]; then
  az staticwebapp appsettings set \
    --name "$SWA_NAME" \
    --resource-group "$RG" \
    --setting-names "$(echo "$DEV_SWA_VARS" | python3 -c "
import json, sys
props = json.load(sys.stdin)
print(' '.join(f'{k}={v}' for k, v in props.items()))
")" > /dev/null
  echo "  SWA environment variables kopierade."
else
  echo "  Inga environment variables hittades i dev."
fi

echo "  Static Web App: kopplar backend..."
BACKEND_APP_ID=$(az webapp show \
  --name "$BACKEND_APP_NAME" --resource-group "$RG" \
  --query "id" -o tsv 2>/dev/null || echo "")

if [ -n "$BACKEND_APP_ID" ]; then
  EXISTING_BACKEND=$(az staticwebapp backends show \
    --name "$SWA_NAME" --resource-group "$RG" 2>/dev/null || echo "[]")
  if echo "$EXISTING_BACKEND" | python3 -c "import json,sys; d=json.load(sys.stdin); exit(0 if len(d) > 0 else 1)" 2>/dev/null; then
    echo "  Backend finns redan kopplad – hoppar över."
  else
    az staticwebapp backends link \
      --name "$SWA_NAME" \
      --resource-group "$RG" \
      --backend-resource-id "$BACKEND_APP_ID" \
      --backend-region "$LOCATION" > /dev/null
    echo "  Backend kopplad till SWA."
  fi
else
  echo "  VARNING: Kunde inte hitta App Service – hoppar över backend-koppling." >&2
fi

echo "  App Service: kopierar app settings..."
DEV_APPSETTINGS=$(az webapp config appsettings list   --name "$DEV_BACKEND_APP" --resource-group "$DEV_RG" -o json 2>/dev/null || echo "[]")

if [ "$DEV_APPSETTINGS" != "[]" ] && [ -n "$DEV_APPSETTINGS" ]; then
  echo "$DEV_APPSETTINGS" | python3 -c "
import json, sys
settings = json.load(sys.stdin)
for s in settings:
    s['value'] = s['value'].replace('-dev', '-prod').replace('dev01', 'prod01')
print(json.dumps(settings))
" > /tmp/prod_appsettings.json
az webapp config appsettings set \
    --name "$BACKEND_APP_NAME" \
    --resource-group "$RG" \
    --settings "$(cat /tmp/prod_appsettings.json)" > /dev/null
  rm -f /tmp/prod_appsettings.json
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
if isinstance(cs, list):
    items = {item['name']: {'value': item['value'], 'type': item['type']} for item in cs}
else:
    items = cs
for name, obj in items.items():
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
  echo "$DEV_FA_SETTINGS" | python3 -c "
import json, sys
settings = json.load(sys.stdin)
skip = {'FUNCTIONS_WORKER_RUNTIME', 'FUNCTIONS_EXTENSION_VERSION', 'AzureWebJobsStorage', 'WEBSITE_RUN_FROM_PACKAGE'}
settings = [s for s in settings if s['name'] not in skip]
for s in settings:
    s['value'] = s['value'].replace('-dev', '-prod').replace('dev01', 'prod01')
print(json.dumps(settings))
" > /tmp/prod_fa_settings.json
az functionapp config appsettings set \
    --name "$FUNCTION_APP_NAME" \
    --resource-group "$RG" \
    --settings "$(cat /tmp/prod_fa_settings.json)" > /dev/null
  rm -f /tmp/prod_fa_settings.json
  echo "  Function App settings kopierade."
else
  echo "  Inga app settings hittades för Function App i dev."
fi


# --- Service Bus: köer och topics ---
echo "  Service Bus: kopierar köer..."
DEV_QUEUES=$(az servicebus queue list   --namespace-name "$DEV_SERVICE_BUS"   --resource-group "$DEV_RG"   --query "[].name" -o tsv 2>/dev/null || echo "")

if [ -n "$DEV_QUEUES" ]; then
  while IFS= read -r queue_name; do
    queue_name=$(echo "$queue_name" | tr -d '\r')
    if az servicebus queue show --namespace-name "$SERVICE_BUS" --resource-group "$RG" --name "$queue_name" &>/dev/null; then
      echo "    Kö '$queue_name' finns redan – uppdaterar inställningar..."
      QUEUE_PROPS=$(az servicebus queue show \
        --namespace-name "$DEV_SERVICE_BUS" \
        --resource-group "$DEV_RG" \
        --name "$queue_name" -o json)
      LOCK_DURATION=$(echo "$QUEUE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin)['lockDuration'])")
      MAX_SIZE=$(echo "$QUEUE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin)['maxSizeInMegabytes'])")
      MAX_DELIVERY=$(echo "$QUEUE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin)['maxDeliveryCount'])")
      DEFAULT_TTL=$(echo "$QUEUE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin)['defaultMessageTimeToLive'])")
      az servicebus queue update \
        --namespace-name "$SERVICE_BUS" \
        --resource-group "$RG" \
        --name "$queue_name" \
        --max-size "$MAX_SIZE" \
        --max-delivery-count "$MAX_DELIVERY" \
        --lock-duration "$LOCK_DURATION" \
        --default-message-time-to-live "$DEFAULT_TTL" > /dev/null
    else
      QUEUE_PROPS=$(az servicebus queue show \
        --namespace-name "$DEV_SERVICE_BUS" \
        --resource-group "$DEV_RG" \
        --name "$queue_name" -o json)
      LOCK_DURATION=$(echo "$QUEUE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin)['lockDuration'])")
      MAX_SIZE=$(echo "$QUEUE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin)['maxSizeInMegabytes'])")
      MAX_DELIVERY=$(echo "$QUEUE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin)['maxDeliveryCount'])")
      DEFAULT_TTL=$(echo "$QUEUE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin)['defaultMessageTimeToLive'])")
      DUPLICATE_DETECTION=$(echo "$QUEUE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('requiresDuplicateDetection', False))")
      DUPLICATE_WINDOW=$(echo "$QUEUE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('duplicateDetectionHistoryTimeWindow', 'PT10M'))")
      DEAD_LETTERING=$(echo "$QUEUE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('deadLetteringOnMessageExpiration', False))")
      BATCHED_OPS=$(echo "$QUEUE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('enableBatchedOperations', True))")

      az servicebus queue create \
      --namespace-name "$SERVICE_BUS" \
      --resource-group "$RG" \
      --name "$queue_name" \
      --max-size "$MAX_SIZE" \
      --max-delivery-count "$MAX_DELIVERY" \
      --lock-duration "$LOCK_DURATION" \
      --default-message-time-to-live "$DEFAULT_TTL" \
      $( [ "$DUPLICATE_DETECTION" = "True" ] && echo "--duplicate-detection --duplicate-detection-history-time-window $DUPLICATE_WINDOW" ) \
      $( [ "$DEAD_LETTERING" = "True" ] && echo "--dead-lettering-on-message-expiration true" ) \
      $( [ "$BATCHED_OPS" = "True" ] && echo "--enable-batched-operations true" )
      echo "    Skapade kö: $queue_name"
    fi
  done <<< "$DEV_QUEUES"
fi

echo "  Service Bus: kopierar topics..."
DEV_TOPICS=$(az servicebus topic list   --namespace-name "$DEV_SERVICE_BUS"   --resource-group "$DEV_RG"   --query "[].name" -o tsv 2>/dev/null || echo "")

if [ -n "$DEV_TOPICS" ]; then
  while IFS= read -r topic_name; do
    if az servicebus topic show --namespace-name "$SERVICE_BUS" --resource-group "$RG" --name "$topic_name" &>/dev/null; then
      echo "    Topic '$topic_name' finns redan – uppdaterar inställningar..."
      TOPIC_PROPS=$(az servicebus topic show \
        --namespace-name "$DEV_SERVICE_BUS" \
        --resource-group "$DEV_RG" \
        --name "$topic_name" -o json)
      MAX_SIZE=$(echo "$TOPIC_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin)['maxSizeInMegabytes'])")
      az servicebus topic update \
        --namespace-name "$SERVICE_BUS" \
        --resource-group "$RG" \
        --name "$topic_name" \
        --max-size "$MAX_SIZE" > /dev/null
    else
      TOPIC_PROPS=$(az servicebus topic show \
        --namespace-name "$DEV_SERVICE_BUS" \
        --resource-group "$DEV_RG" \
        --name "$topic_name" -o json)
      MAX_SIZE=$(echo "$TOPIC_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin)['maxSizeInMegabytes'])")
      az servicebus topic create \
        --namespace-name "$SERVICE_BUS" \
        --resource-group "$RG" \
        --name "$topic_name" \
        --max-size "$MAX_SIZE" > /dev/null
      echo "    Skapade topic: $topic_name"
    fi

    # Kopiera subscriptions per topic
    DEV_SUBS=$(az servicebus topic subscription list \
      --namespace-name "$DEV_SERVICE_BUS" \
      --resource-group "$DEV_RG" \
      --topic-name "$topic_name" \
      --query "[].name" -o tsv 2>/dev/null || echo "")

    if [ -n "$DEV_SUBS" ]; then
      while IFS= read -r sub_name; do
        if az servicebus topic subscription show --namespace-name "$SERVICE_BUS" --resource-group "$RG" --topic-name "$topic_name" --name "$sub_name" &>/dev/null; then
          echo "      Subscription '$sub_name' finns redan – hoppar över."
        else
          az servicebus topic subscription create \
            --namespace-name "$SERVICE_BUS" \
            --resource-group "$RG" \
            --topic-name "$topic_name" \
            --name "$sub_name" > /dev/null
          echo "      Skapade subscription: $sub_name"
        fi
      done <<< "$DEV_SUBS"
    fi
  done <<< "$DEV_TOPICS"
fi

# --- Storage: containers ---
echo "  Storage: kopierar containers..."
DEV_CONTAINERS=$(az rest \
  --method get \
  --url "https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${DEV_RG}/providers/Microsoft.Storage/storageAccounts/${DEV_STORAGE}/blobServices/default/containers?api-version=2023-01-01" \
  --query "value[].name" -o tsv 2>/dev/null | tr -d '\r' || echo "")

if [ -n "$DEV_CONTAINERS" ]; then
  while IFS= read -r container_name; do
    container_name=$(echo "$container_name" | tr -d '\r')
    if az rest \
      --method get \
      --url "https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.Storage/storageAccounts/${STORAGE_ACCOUNT}/blobServices/default/containers/${container_name}?api-version=2023-01-01" \
      &>/dev/null; then
      echo "    Container '$container_name' finns redan – hoppar över."
    else
      az rest \
        --method put \
        --url "https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.Storage/storageAccounts/${STORAGE_ACCOUNT}/blobServices/default/containers/${container_name}?api-version=2023-01-01" \
        --body '{"properties": {"publicAccess": "None"}}' > /dev/null
      echo "    Skapade container: $container_name"
    fi
  done <<< "$DEV_CONTAINERS"
else
  echo "  Inga containers hittades i dev."
fi

# --- Azure OpenAI: deployments ---
echo "  Azure OpenAI: kopierar model deployments..."
DEV_DEPLOYMENTS=$(az cognitiveservices account deployment list   --name "$DEV_OPENAI"   --resource-group "$DEV_RG" -o json 2>/dev/null || echo "[]")

if [ "$DEV_DEPLOYMENTS" != "[]" ] && [ -n "$DEV_DEPLOYMENTS" ]; then
  while IFS= read -r deployment; do
    name=$(echo "$deployment" | python3 -c "import json,sys; print(json.load(sys.stdin)['name'])")
    model_name=$(echo "$deployment" | python3 -c "import json,sys; print(json.load(sys.stdin)['properties']['model']['name'])")
    model_version=$(echo "$deployment" | python3 -c "import json,sys; print(json.load(sys.stdin)['properties']['model']['version'])")
    model_format=$(echo "$deployment" | python3 -c "import json,sys; print(json.load(sys.stdin)['properties']['model']['format'])")
    capacity=$(echo "$deployment" | python3 -c "import json,sys; print(json.load(sys.stdin)['sku']['capacity'])")
    sku_name=$(echo "$deployment" | python3 -c "import json,sys; print(json.load(sys.stdin)['sku']['name'])")

    if az cognitiveservices account deployment show \
        --name "$OPENAI_ACCOUNT" \
        --resource-group "$RG" \
        --deployment-name "$name" &>/dev/null; then
      echo "    Deployment '$name' finns redan – hoppar över."
    else
      echo "    Deploying $name ($model_name $model_version)..."
      az cognitiveservices account deployment create \
        --name "$OPENAI_ACCOUNT" \
        --resource-group "$RG" \
        --deployment-name "$name" \
        --model-name "$model_name" \
        --model-version "$model_version" \
        --model-format "$model_format" \
        --sku-name "$sku_name" \
        --sku-capacity "$capacity"
    fi
  done < <(echo "$DEV_DEPLOYMENTS" | python3 -c "
import json, sys
deployments = json.load(sys.stdin)
for d in deployments:
    print(json.dumps(d))
")
  echo "  OpenAI deployments kopierade."
else
  echo "  Inga OpenAI deployments hittades i dev."
fi

# ---------------------------------------------------------------------------
# 16b. KOPIERA AZURE OPENAI ASSISTENTER
# ---------------------------------------------------------------------------

echo "  Azure OpenAI: kopierar AI-assistenter..."

DEV_OAI_KEY=$(az cognitiveservices account keys list \
  --name "$DEV_OPENAI" --resource-group "$DEV_RG" \
  --query "key1" -o tsv 2>/dev/null || echo "")

PROD_OAI_KEY=$(az cognitiveservices account keys list \
  --name "$OPENAI_ACCOUNT" --resource-group "$RG" \
  --query "key1" -o tsv 2>/dev/null || echo "")

DEV_OAI_ENDPOINT="https://${DEV_OPENAI}.openai.azure.com"
PROD_OAI_ENDPOINT="https://oai-app-${ENV}01.openai.azure.com"
OAI_API_VERSION="2024-05-01-preview"

if [ -z "$DEV_OAI_KEY" ] || [ -z "$PROD_OAI_KEY" ]; then
  echo "  VARNING: Kunde inte hämta API-nycklar – hoppar över AI-assistenter." >&2
else
  DEV_ASSISTANTS=$(curl -s \
    -H "api-key: $DEV_OAI_KEY" \
    "${DEV_OAI_ENDPOINT}/openai/assistants?api-version=${OAI_API_VERSION}" | \
    python3 -c "
import sys, json
data = json.loads(sys.stdin.buffer.read().decode('utf-8'))
print(json.dumps(data, ensure_ascii=False))
")

  echo "$DEV_ASSISTANTS" | python3 -c "
import json, sys
data = json.load(sys.stdin)
assistants = data.get('data', [])
print(f'  Hittade {len(assistants)} AI-assistenter i dev.')
for a in assistants:
    print(a['name'])
"

  while IFS= read -r assistant; do
    PROD_VS_IDS="[]"
    PROD_ASSISTANTS=$(curl -s \
      -H "api-key: $PROD_OAI_KEY" \
      "${PROD_OAI_ENDPOINT}/openai/assistants?api-version=${OAI_API_VERSION}" | \
      python3 -c "
import sys, json
data = json.loads(sys.stdin.buffer.read().decode('utf-8'))
print(json.dumps(data, ensure_ascii=False))
")
    name=$(echo "$assistant" | python3 -c "import json,sys; print(json.load(sys.stdin)['name'].strip())" | tr -d '\r')
    instructions=$(echo "$assistant" | python3 -c "import json,sys; print(json.load(sys.stdin).get('instructions',''))" | tr -d '\r')
    model=$(echo "$assistant" | python3 -c "import json,sys; print(json.load(sys.stdin)['model'])" | tr -d '\r')
    tools=$(echo "$assistant" | python3 -c "import json,sys; print(json.dumps(json.load(sys.stdin).get('tools',[])))" | tr -d '\r')
    temperature=$(echo "$assistant" | python3 -c "import json,sys; print(json.load(sys.stdin).get('temperature',1.0))" | tr -d '\r')
    top_p=$(echo "$assistant" | python3 -c "import json,sys; print(json.load(sys.stdin).get('top_p',1.0))" | tr -d '\r')

    # Kolla om AI-assistenten redan finns i prod
    EXISTS=$(echo "$PROD_ASSISTANTS" | python3 -c "
import json, sys, unicodedata

def normalize(s):
    return unicodedata.normalize('NFC', s).strip()

data = json.load(sys.stdin)
assistants = data.get('data', [])
name = normalize('${name}')
match = [a for a in assistants if normalize(a['name']) == name]
print('true' if match else 'false')
" 2>/dev/null || echo "false")

    if [ "$EXISTS" = "true" ]; then
      echo "    AI-assistent '$name' finns redan – uppdaterar..."
      ASSISTANT_ID=$(echo "$PROD_ASSISTANTS" | python3 -c "
import json, sys
data = json.load(sys.stdin)
name = '${name}'.strip()
match = [a for a in data.get('data', []) if a['name'].strip() == name]
print(match[0]['id'] if match else '')
")
      if [ -n "$ASSISTANT_ID" ]; then
        echo "$assistant" | python3 -c "
import json, sys, urllib.request

data = json.loads(sys.stdin.buffer.read().decode('utf-8'))
prod_vs_ids = json.loads('${PROD_VS_IDS}')

body = {
    'name': data['name'].strip(),
    'instructions': data.get('instructions', ''),
    'model': data['model'],
    'tools': data.get('tools', []),
    'temperature': data.get('temperature', 1.0),
    'top_p': data.get('top_p', 1.0),
    'tool_resources': {'file_search': {'vector_store_ids': prod_vs_ids}}
}
body_bytes = json.dumps(body, ensure_ascii=False).encode('utf-8')

req = urllib.request.Request(
    '${PROD_OAI_ENDPOINT}/openai/assistants/${ASSISTANT_ID}?api-version=${OAI_API_VERSION}',
    data=body_bytes,
    headers={
        'api-key': '${PROD_OAI_KEY}',
        'Content-Type': 'application/json; charset=utf-8'
    },
    method='POST'
)
try:
    with urllib.request.urlopen(req) as resp:
        response = json.loads(resp.read().decode('utf-8'))
        print(f'    Uppdaterade AI-assistent: {response[\"name\"]}')
except urllib.error.HTTPError as e:
    error = json.loads(e.read().decode('utf-8'))
    print(f'    FEL: {error[\"error\"][\"message\"]}')
"
      fi
    else
      echo "    Skapar vector stores för AI-assistent: $name"
      VS_IDS=$(echo "$assistant" | python3 -c "
import json, sys
d = json.load(sys.stdin)
vs_ids = d.get('tool_resources', {}).get('file_search', {}).get('vector_store_ids', [])
print(json.dumps(vs_ids))
")
      VS_COUNT=$(echo "$VS_IDS" | python3 -c "import json,sys; print(len(json.load(sys.stdin)))")

      PROD_VS_IDS="[]"
      if [ "$VS_COUNT" -gt 0 ]; then
        PROD_VS_IDS_LIST=""
        for i in $(seq 0 $(( VS_COUNT - 1 ))); do
          DEV_VS_ID=$(echo "$VS_IDS" | python3 -c "import json,sys; print(json.load(sys.stdin)[$i])")
          DEV_VS_NAME=$(curl -s \
            -H "api-key: $DEV_OAI_KEY" \
            "${DEV_OAI_ENDPOINT}/openai/vector_stores/${DEV_VS_ID}?api-version=${OAI_API_VERSION}" | \
            python3 -c "import json,sys; print(json.load(sys.stdin).get('name',''))" | tr -d '\r')

          echo "      Skapar vector store: $DEV_VS_NAME"
          PROD_VS_ID=$(curl -s -X POST \
            -H "api-key: $PROD_OAI_KEY" \
            -H "Content-Type: application/json" \
            -d "{\"name\": \"${DEV_VS_NAME}\"}" \
            "${PROD_OAI_ENDPOINT}/openai/vector_stores?api-version=${OAI_API_VERSION}" | \
            python3 -c "import json,sys; print(json.load(sys.stdin)['id'])" | tr -d '\r')

          if [ -z "$PROD_VS_IDS_LIST" ]; then
            PROD_VS_IDS_LIST="\"$PROD_VS_ID\""
          else
            PROD_VS_IDS_LIST="$PROD_VS_IDS_LIST, \"$PROD_VS_ID\""
          fi
        done
        PROD_VS_IDS="[$PROD_VS_IDS_LIST]"
      fi

      echo "    Skapar AI-assistent: $name"
      echo "$assistant" | python3 -c "
import json, sys, urllib.request

data = json.loads(sys.stdin.buffer.read().decode('utf-8'))
prod_vs_ids = json.loads('${PROD_VS_IDS}')

body = {
    'name': data['name'].strip(),
    'instructions': data.get('instructions', ''),
    'model': data['model'],
    'tools': data.get('tools', []),
    'temperature': data.get('temperature', 1.0),
    'top_p': data.get('top_p', 1.0),
    'tool_resources': {'file_search': {'vector_store_ids': prod_vs_ids}}
}

instructions_clean = data.get('instructions', '').encode('utf-8', errors='ignore').decode('utf-8')
body['instructions'] = instructions_clean
body_bytes = json.dumps(body, ensure_ascii=False).encode('utf-8')

req = urllib.request.Request(
    '${PROD_OAI_ENDPOINT}/openai/assistants?api-version=${OAI_API_VERSION}',
    data=body_bytes,
    headers={
        'api-key': '${PROD_OAI_KEY}',
        'Content-Type': 'application/json; charset=utf-8'
    },
    method='POST'
)
try:
    with urllib.request.urlopen(req) as resp:
        response = json.loads(resp.read().decode('utf-8'))
        print(f'    Skapade AI-assistent: {response[\"name\"]}')
except urllib.error.HTTPError as e:
    error = json.loads(e.read().decode('utf-8'))
    print(f'    FEL: {error[\"error\"][\"message\"]}')
"
    fi
  done < <(echo "$DEV_ASSISTANTS" | python3 -c "
import json, sys
data = json.load(sys.stdin)
for a in data.get('data', []):
    print(json.dumps(a))
")

  echo "  AI-assistenter kopierade."
fi

echo "  Key Vault: kopierar secrets från dev..."
DEV_SECRETS=$(az keyvault secret list --vault-name "$DEV_KEY_VAULT" --query "[].name" -o tsv 2>/dev/null || echo "")

if [ -n "$DEV_SECRETS" ]; then
  while IFS= read -r secret_name; do
    secret_name=$(echo "$secret_name" | tr -d '\r')
    SECRET_VALUE=$(az keyvault secret show \
      --vault-name "$DEV_KEY_VAULT" \
      --name "$secret_name" \
      --query "value" -o tsv 2>/dev/null || echo "")

    if az keyvault secret show --vault-name "$KEY_VAULT" --name "$secret_name" &>/dev/null; then
      echo "    Secret '$secret_name' finns redan – uppdaterar värde..."
      az keyvault secret set \
        --vault-name "$KEY_VAULT" \
        --name "$secret_name" \
        --value "$SECRET_VALUE" > /dev/null
    else
      az keyvault secret set \
        --vault-name "$KEY_VAULT" \
        --name "$secret_name" \
        --value "$SECRET_VALUE" > /dev/null
      echo "    Kopierade secret: $secret_name"
    fi
  done <<< "$DEV_SECRETS"
fi

# --- Front Door: origin groups, origins och routes ---
echo "  Front Door: kopierar origin groups, origins och routes..."
DEV_ORIGIN_GROUPS=$(az afd origin-group list   --profile-name "$DEV_AFD_PROFILE"   --resource-group "$DEV_RG"   --query "[].name" -o tsv 2>/dev/null || echo "")

if [ -n "$DEV_ORIGIN_GROUPS" ]; then
  while IFS= read -r og_name; do
    og_name=$(echo "$og_name" | tr -d '\r')
    if az afd origin-group show --origin-group-name "$og_name" --profile-name "$AFD_PROFILE" --resource-group "$RG" &>/dev/null; then
      echo "    Origin group '$og_name' finns redan – uppdaterar inställningar..."
      OG_PROPS=$(az afd origin-group show \
        --profile-name "$DEV_AFD_PROFILE" \
        --resource-group "$DEV_RG" \
        --origin-group-name "$og_name" -o json)
      PROBE_PATH=$(echo "$OG_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('healthProbeSettings',{}).get('probePath','/'))")
      PROBE_INTERVAL=$(echo "$OG_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('healthProbeSettings',{}).get('probeIntervalInSeconds',100))")
      PROBE_PROTOCOL=$(echo "$OG_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('healthProbeSettings',{}).get('probeProtocol','Https'))")
      PROBE_REQUEST_TYPE=$(echo "$OG_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('healthProbeSettings',{}).get('probeRequestType','HEAD'))")
      LB_SAMPLE_SIZE=$(echo "$OG_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('loadBalancingSettings',{}).get('sampleSize',4))")
      LB_SUCCESSFUL_SAMPLES=$(echo "$OG_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('loadBalancingSettings',{}).get('successfulSamplesRequired',3))")
      LB_LATENCY=$(echo "$OG_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('loadBalancingSettings',{}).get('additionalLatencyInMilliseconds',50))")
      az afd origin-group update \
        --origin-group-name "$og_name" \
        --profile-name "$AFD_PROFILE" \
        --resource-group "$RG" \
        --probe-path "$PROBE_PATH" \
        --probe-interval-in-seconds "$PROBE_INTERVAL" \
        --probe-protocol "$PROBE_PROTOCOL" \
        --probe-request-type "$PROBE_REQUEST_TYPE" \
        --sample-size "$LB_SAMPLE_SIZE" \
        --successful-samples-required "$LB_SUCCESSFUL_SAMPLES" \
        --additional-latency-in-milliseconds "$LB_LATENCY" > /dev/null
    else
      OG_PROPS=$(az afd origin-group show \
        --profile-name "$DEV_AFD_PROFILE" \
        --resource-group "$DEV_RG" \
        --origin-group-name "$og_name" -o json)
      PROBE_PATH=$(echo "$OG_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('healthProbeSettings',{}).get('probePath','/'))")
      PROBE_INTERVAL=$(echo "$OG_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('healthProbeSettings',{}).get('probeIntervalInSeconds',100))")
      PROBE_PROTOCOL=$(echo "$OG_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('healthProbeSettings',{}).get('probeProtocol','Https'))")
      PROBE_REQUEST_TYPE=$(echo "$OG_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('healthProbeSettings',{}).get('probeRequestType','HEAD'))")
      LB_SAMPLE_SIZE=$(echo "$OG_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('loadBalancingSettings',{}).get('sampleSize',4))")
      LB_SUCCESSFUL_SAMPLES=$(echo "$OG_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('loadBalancingSettings',{}).get('successfulSamplesRequired',3))")
      LB_LATENCY=$(echo "$OG_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('loadBalancingSettings',{}).get('additionalLatencyInMilliseconds',50))")

      az afd origin-group create \
        --origin-group-name "$og_name" \
        --profile-name "$AFD_PROFILE" \
        --resource-group "$RG" \
        --probe-path "$PROBE_PATH" \
        --probe-interval-in-seconds "$PROBE_INTERVAL" \
        --probe-protocol "$PROBE_PROTOCOL" \
        --probe-request-type "$PROBE_REQUEST_TYPE" \
        --sample-size "$LB_SAMPLE_SIZE" \
        --successful-samples-required "$LB_SUCCESSFUL_SAMPLES" \
        --additional-latency-in-milliseconds "$LB_LATENCY" > /dev/null
      echo "    Skapade origin group: $og_name"
    fi

    # Kopiera origins inom gruppen
    DEV_ORIGINS=$(az afd origin list \
      --profile-name "$DEV_AFD_PROFILE" \
      --resource-group "$DEV_RG" \
      --origin-group-name "$og_name" -o json 2>/dev/null || echo "[]")

    while IFS= read -r origin; do
      origin_name=$(echo "$origin" | python3 -c "import json,sys; print(json.load(sys.stdin)['name'])" | tr -d '\r')
      origin_host=$(echo "$origin" | python3 -c "import json,sys; print(json.load(sys.stdin)['hostName'].replace('-dev','-prod').replace('dev01','prod01'))" | tr -d '\r')
      origin_http=$(echo "$origin" | python3 -c "import json,sys; print(json.load(sys.stdin).get('httpPort',80))" | tr -d '\r')
      origin_https=$(echo "$origin" | python3 -c "import json,sys; print(json.load(sys.stdin).get('httpsPort',443))" | tr -d '\r')
      origin_priority=$(echo "$origin" | python3 -c "import json,sys; print(json.load(sys.stdin).get('priority',1))" | tr -d '\r')
      origin_weight=$(echo "$origin" | python3 -c "import json,sys; print(json.load(sys.stdin).get('weight',1000))" | tr -d '\r')

      if az afd origin show \
          --origin-name "$origin_name" \
          --origin-group-name "$og_name" \
          --profile-name "$AFD_PROFILE" \
          --resource-group "$RG" &>/dev/null; then
        echo "      Origin '$origin_name' finns redan – hoppar över."
      else
        echo "      Skapar origin: $origin_name -> $origin_host"
        az afd origin create \
          --origin-name "$origin_name" \
          --origin-group-name "$og_name" \
          --profile-name "$AFD_PROFILE" \
          --resource-group "$RG" \
          --host-name "$origin_host" \
          --http-port "$origin_http" \
          --https-port "$origin_https" \
          --priority "$origin_priority" \
          --weight "$origin_weight"
      fi
    done < <(echo "$DEV_ORIGINS" | python3 -c "
import json, sys
origins = json.load(sys.stdin)
for o in origins:
    print(json.dumps(o))
")
  done <<< "$DEV_ORIGIN_GROUPS"
fi

# Hämta prod SWA och App Service URL:er för origin-uppdatering
PROD_SWA_HOST=$(az staticwebapp show --name "$SWA_NAME" --resource-group "$RG" \
  --query "defaultHostname" -o tsv 2>/dev/null | tr -d '\r' || echo "")
PROD_APP_HOST=$(az webapp show --name "$BACKEND_APP_NAME" --resource-group "$RG" \
  --query "defaultHostName" -o tsv 2>/dev/null | tr -d '\r' || echo "")
PROD_STORAGE_HOST="${STORAGE_ACCOUNT}.blob.core.windows.net"

# Uppdatera frontend-origin med rätt prod SWA-URL
if [ -n "$PROD_SWA_HOST" ]; then
  echo "  Uppdaterar frontend-origin till $PROD_SWA_HOST..."
  az afd origin update \
    --origin-name "swa-origin" \
    --origin-group-name "og-frontend" \
    --profile-name "$AFD_PROFILE" \
    --resource-group "$RG" \
    --host-name "$PROD_SWA_HOST" \
    --origin-host-header "$PROD_SWA_HOST" > /dev/null 2>&1 || true
fi

# Uppdatera backend-origin med rätt prod App Service URL
if [ -n "$PROD_APP_HOST" ]; then
  echo "  Uppdaterar backend-origin till $PROD_APP_HOST..."
  az afd origin update \
    --origin-name "api-origin" \
    --origin-group-name "og-backend" \
    --profile-name "$AFD_PROFILE" \
    --resource-group "$RG" \
    --host-name "$PROD_APP_HOST" \
    --origin-host-header "$PROD_APP_HOST" > /dev/null 2>&1 || true
fi

# Uppdatera blob-origin med rätt prod storage URL
echo "  Uppdaterar blob-origin till $PROD_STORAGE_HOST..."
az afd origin update \
  --origin-name "blob-origin-group" \
  --origin-group-name "blob-origin-group" \
  --profile-name "$AFD_PROFILE" \
  --resource-group "$RG" \
  --host-name "$PROD_STORAGE_HOST" \
  --origin-host-header "$PROD_STORAGE_HOST" > /dev/null 2>&1 || true

# Kopiera routes
DEV_ROUTES=$(az afd route list \
  --profile-name "$DEV_AFD_PROFILE" \
  --resource-group "$DEV_RG" \
  --endpoint-name "video-endpoint" \
  --query "[].name" -o tsv 2>/dev/null || echo "")

if [ -n "$DEV_ROUTES" ]; then
  while IFS= read -r route_name; do
    route_name=$(echo "$route_name" | tr -d '\r')
    if az afd route show --route-name "$route_name" --profile-name "$AFD_PROFILE" --resource-group "$RG" --endpoint-name "$AFD_ENDPOINT" &>/dev/null; then
      echo "    Route '$route_name' finns redan – uppdaterar inställningar..."
      ROUTE_PROPS=$(az afd route show \
        --profile-name "$DEV_AFD_PROFILE" \
        --resource-group "$DEV_RG" \
        --endpoint-name "video-endpoint" \
        --route-name "$route_name" -o json)
      PATTERNS=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; print(' '.join(json.load(sys.stdin).get('patternsToMatch', ['/*'])))")
      OG=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['originGroup']['id'].split('/')[-1])")
      HTTPS=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('httpsRedirect','Enabled'))")
      FORWARDING=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('forwardingProtocol','HttpsOnly'))")
      PROTOCOLS=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; print(' '.join(json.load(sys.stdin).get('supportedProtocols',['Https'])))")
      LINK_DEFAULT=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('linkToDefaultDomain','Enabled'))")
      ORIGIN_PATH=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('originPath','') or '')")
      QUERY_STRING=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('cacheConfiguration',{}).get('queryStringCachingBehavior','IgnoreQueryString') if d.get('cacheConfiguration') else 'IgnoreQueryString')")
      az afd route update \
        --route-name "$route_name" \
        --profile-name "$AFD_PROFILE" \
        --resource-group "$RG" \
        --endpoint-name "$AFD_ENDPOINT" \
        --origin-group "$OG" \
        --patterns-to-match $PATTERNS \
        --https-redirect "$HTTPS" \
        --forwarding-protocol "$FORWARDING" \
        --supported-protocols $PROTOCOLS \
        --link-to-default-domain "$LINK_DEFAULT" \
        ${ORIGIN_PATH:+--origin-path "$ORIGIN_PATH"} \
        --enable-caching true \
        --query-string-caching-behavior "$QUERY_STRING" > /dev/null
    else
      ROUTE_PROPS=$(az afd route show \
        --profile-name "$DEV_AFD_PROFILE" \
        --resource-group "$DEV_RG" \
        --endpoint-name "video-endpoint" \
        --route-name "$route_name" -o json)
      PATTERNS=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; print(' '.join(json.load(sys.stdin).get('patternsToMatch', ['/*'])))")
      OG=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['originGroup']['id'].split('/')[-1])")
      HTTPS=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('httpsRedirect','Enabled'))")
      FORWARDING=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('forwardingProtocol','HttpsOnly'))")
      PROTOCOLS=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; print(' '.join(json.load(sys.stdin).get('supportedProtocols',['Https'])))")
      LINK_DEFAULT=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('linkToDefaultDomain','Enabled'))")
      ORIGIN_PATH=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('originPath','') or '')")
      CACHE_ENABLED=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; d=json.load(sys.stdin); print('true' if d.get('cacheConfiguration') else 'false')")
      QUERY_STRING=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('cacheConfiguration',{}).get('queryStringCachingBehavior','IgnoreQueryString') if d.get('cacheConfiguration') else 'IgnoreQueryString')")

      az afd route create \
        --route-name "$route_name" \
        --profile-name "$AFD_PROFILE" \
        --resource-group "$RG" \
        --endpoint-name "$AFD_ENDPOINT" \
        --origin-group "$OG" \
        --patterns-to-match $PATTERNS \
        --https-redirect "$HTTPS" \
        --forwarding-protocol "$FORWARDING" \
        --supported-protocols $PROTOCOLS \
        --link-to-default-domain "$LINK_DEFAULT" \
        ${ORIGIN_PATH:+--origin-path "$ORIGIN_PATH"} \
        --enable-caching true \
        --query-string-caching-behavior UseQueryString > /dev/null
      echo "    Skapade route: $route_name"
    fi
  done <<< "$DEV_ROUTES"
fi

# --- SQL: brandväggsregler ---
echo "  SQL: kopierar brandväggsregler..."

DEV_FW_RULES=$(az sql server firewall-rule list \
  --server "$DEV_SQL_SERVER" \
  --resource-group "$DEV_RG" -o json 2>/dev/null || echo "[]")

if [ "$DEV_FW_RULES" != "[]" ] && [ -n "$DEV_FW_RULES" ]; then
  while IFS= read -r rule; do
    rule_name=$(echo "$rule" | python3 -c "import json,sys; print(json.load(sys.stdin)['name'])" | tr -d '\r')
    rule_start=$(echo "$rule" | python3 -c "import json,sys; print(json.load(sys.stdin)['startIpAddress'])" | tr -d '\r')
    rule_end=$(echo "$rule" | python3 -c "import json,sys; print(json.load(sys.stdin)['endIpAddress'])" | tr -d '\r')

    if az sql server firewall-rule show \
        --server "$SQL_SERVER" \
        --resource-group "$RG" \
        --name "$rule_name" &>/dev/null; then
      echo "    Brandväggsregel '$rule_name' finns redan – hoppar över."
    else
      az sql server firewall-rule create \
        --server "$SQL_SERVER" \
        --resource-group "$RG" \
        --name "$rule_name" \
        --start-ip-address "$rule_start" \
        --end-ip-address "$rule_end" > /dev/null
      echo "    Kopierade brandväggsregel: $rule_name"
    fi
  done < <(echo "$DEV_FW_RULES" | python3 -c "
import json, sys
rules = json.load(sys.stdin)
for r in rules:
    print(json.dumps(r))
")
fi

# ---------------------------------------------------------------------------
# 17. IAM-ROLLER
# ---------------------------------------------------------------------------

log "17. Sätter upp IAM-roller..."

echo "  Hämtar systemidentiteter..."
APP_PRINCIPAL=$(az webapp identity show \
  --name "$BACKEND_APP_NAME" --resource-group "$RG" \
  --query principalId -o tsv 2>/dev/null || echo "")

FA_PRINCIPAL=$(az functionapp identity show \
  --name "$FUNCTION_APP_NAME" --resource-group "$RG" \
  --query principalId -o tsv 2>/dev/null || echo "")

# Hämta resurs-IDs
STORAGE_ID=$(az storage account show --name "$STORAGE_ACCOUNT" --resource-group "$RG" --query id -o tsv)
SB_ID=$(az servicebus namespace show --name "$SERVICE_BUS" --resource-group "$RG" --query id -o tsv)
KV_ID=$(az keyvault show --name "$KEY_VAULT" --resource-group "$RG" --query id -o tsv)
OAI_ID=$(az cognitiveservices account show --name "$OPENAI_ACCOUNT" --resource-group "$RG" --query id -o tsv)

# Hjälpfunktion för idempotent rolltilldelning
assign_role() {
  local principal="$1"
  local role="$2"
  local scope="$3"
  if az role assignment list --assignee "$principal" --role "$role" --scope "$scope" --query "[0].id" -o tsv 2>/dev/null | grep -q .; then
    echo "  Roll '$role' finns redan för $principal – hoppar över."
  else
    echo "  Tilldelar '$role'..."
    az role assignment create \
      --assignee "$principal" \
      --role "$role" \
      --scope "$scope" > /dev/null
  fi
}

# --- App Service roller ---
if [ -n "$APP_PRINCIPAL" ]; then
  echo "  App Service roller..."
  assign_role "$APP_PRINCIPAL" "Storage Blob Data Contributor"  "$STORAGE_ID"
  assign_role "$APP_PRINCIPAL" "Azure Service Bus Data Sender"   "$SB_ID"
  assign_role "$APP_PRINCIPAL" "Key Vault Secrets User"          "$KV_ID"
  assign_role "$APP_PRINCIPAL" "Cognitive Services OpenAI User"  "$OAI_ID"
else
  echo "  VARNING: Kunde inte hitta systemidentitet för App Service – roller sätts inte upp." >&2
fi

# --- Function App roller ---
if [ -n "$FA_PRINCIPAL" ]; then
  echo "  Function App roller..."
  assign_role "$FA_PRINCIPAL" "Azure Service Bus Data Receiver" "$SB_ID"
else
  echo "  VARNING: Kunde inte hitta systemidentitet för Function App – roller sätts inte upp." >&2
fi

# --- MSI (user-assigned) roller ---
echo "  Följande roller har tilldelats i prod:"
echo "    App Service '$BACKEND_APP_NAME':"
echo "      - Storage Blob Data Contributor -> $STORAGE_ACCOUNT"
echo "      - Azure Service Bus Data Sender -> $SERVICE_BUS"
echo "      - Key Vault Secrets User -> $KEY_VAULT"
echo "      - Cognitive Services OpenAI User -> $OPENAI_ACCOUNT"
echo "    Function App '$FUNCTION_APP_NAME':"
echo "      - Azure Service Bus Data Receiver -> $SERVICE_BUS"

# ---------------------------------------------------------------------------
# KLART
# ---------------------------------------------------------------------------
log "Klart! Alla prod-resurser är skapade i '$RG'."
echo "Kom ihåg att:"
echo "  1. Sätt hemligheter i Key Vault '$KEY_VAULT'"
echo "  2. Verifiera VNet Integration för App Service i Azure Portal"
echo "  4. Verifiera att kopierade app settings/connection strings pekar rätt i prod"