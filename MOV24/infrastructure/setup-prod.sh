#!/bin/bash

# =============================================================================
# SAMMANFATTNING
# =============================================================================
#
# Detta skript sätter upp en komplett prod-miljö (rg-app-prod) i westeurope
# baserat på konfigurationen i dev-miljön (rg-app-dev).
#
# Skriptet är helt idempotent – varje resurs kontrolleras först och skapas
# bara om den inte redan finns. Det innebär att skriptet kan köras flera
# gånger utan att något skapas dubbelt eller misslyckas.
#
# Följande resurser hanteras:
#   - Resursgrupp
#   - Managed Identity
#   - Static Web App (frontend)
#   - App Service Plan + App Service (backend, Java 21, S1)
#   - Autoskalningsregler (konfiguration hämtas från dev)
#   - Function App (Flex Consumption, Java 21, Service Bus worker)
#   - VNet med tre subnät (default, snet-app, snet-private) + NSG:er
#   - Key Vault (private endpoint + DNS-zon)
#   - Storage Account (private endpoint + DNS-zon)
#   - SQL Server + databas (private endpoint + DNS-zon)
#   - Service Bus
#   - Azure OpenAI (private endpoint + DNS-zon)
#   - Azure Front Door (profil + endpoint)
#   - Action Groups + Metric Alerts (CPU, HTTP 5xx, svarstid)
#   - Application Insights
#   - Availability Test
#
# All konfiguration (SKU, tier, trösklar m.m.) hämtas dynamiskt från
# motsvarande resurser i dev-miljön för att säkerställa paritet mellan
# miljöerna. Vid eventuella fel mot dev används rimliga defaultvärden.
#
# Slutligen kopieras inställningar från dev till prod:
#   - App Service app settings + connection strings
#   - Function App app settings
#   - Service Bus köer, topics och subscriptions
#   - Storage containers
#   - Azure OpenAI model deployments
#   - Front Door origin groups, origins och routes
#   - SQL brandväggsregler
# =============================================================================

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
SQL_ADMIN_PASSWORD='L&95$Qn#v432GV4qCvJq'          # Fyll i eller hämta från KeyVault/env-variabel

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
BACKEND_APP_URL="https://app-prod-api-f6cag3ctetdpc3gf.westeurope-01.azurewebsites.net/health"             # t.ex. https://app-prod-api.azurewebsites.net/health


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
log "2. Kontrollerar Static Web App..."

if az staticwebapp show --name "$SWA_NAME" --resource-group "$RG" &>/dev/null; then
  echo "  Static Web App '$SWA_NAME' finns redan – hoppar över."
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
log "3. Kontrollerar App Service Plan och backend..."

APP_SERVICE_CREATED=false

if az appservice plan show --name "$ASP_NAME" --resource-group "$RG" &>/dev/null; then
  echo "  App Service Plan '$ASP_NAME' finns redan – hoppar över."
else
  echo "  Hämtar App Service Plan-konfiguration från dev..."
  DEV_ASP_NAME=$(az webapp show --name "$DEV_BACKEND_APP" --resource-group "$DEV_RG" \
    --query "appServicePlanId" -o tsv 2>/dev/null | xargs az appservice plan show --ids \
    --query "name" -o tsv 2>/dev/null || echo "")
  ASP_DEV_SKU=$(az appservice plan show --name "$DEV_ASP_NAME" --resource-group "$DEV_RG" \
    --query "sku.name" -o tsv 2>/dev/null || echo "$ASP_SKU")

  echo "  Skapar App Service Plan '$ASP_NAME' (SKU: $ASP_DEV_SKU)..."
  az appservice plan create \
    --name "$ASP_NAME" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --sku "$ASP_DEV_SKU" \
    --is-linux
fi

if az webapp show --name "$BACKEND_APP_NAME" --resource-group "$RG" &>/dev/null; then
  echo "  App Service '$BACKEND_APP_NAME' finns redan – hoppar över."
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

  az webapp create \
    --name "$BACKEND_APP_NAME" \
    --resource-group "$RG" \
    --plan "$ASP_NAME" \
    --runtime "$BACKEND_APP_RUNTIME" \
    --assign-identity "$MSI_ID"

  APP_SERVICE_CREATED=true

  az webapp update \
    --name "$BACKEND_APP_NAME" \
    --resource-group "$RG" \
    --https-only "$APP_DEV_HTTPS"

  az webapp config set \
    --name "$BACKEND_APP_NAME" \
    --resource-group "$RG" \
    --min-tls-version "$APP_DEV_TLS" \
    --http20-enabled "$APP_DEV_HTTP" \
    --always-on "$APP_DEV_ALWAYS_ON"
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
    --location "$LOCATION" \
    --runtime "$FUNCTION_RUNTIME" \
    --runtime-version "$FUNCTION_RUNTIME_VERSION" \
    --storage-account "$FUNCTION_STORAGE" \
    --flexconsumption-location "$LOCATION" \
    --assign-identity "$MSI_ID"

  az functionapp config set \
    --name "$FUNCTION_APP_NAME" \
    --resource-group "$RG" \
    --min-tls-version "$FA_DEV_TLS" \
    --http20-enabled "$FA_DEV_HTTP"

  az functionapp update \
    --name "$FUNCTION_APP_NAME" \
    --resource-group "$RG" \
    --set httpsOnly="$FA_DEV_HTTPS"
fi

# ---------------------------------------------------------------------------
# 4. VIRTUAL NETWORK + NSG:er
# ---------------------------------------------------------------------------
log "4. Kontrollerar VNet och NSG:er..."

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

VNET_ID=$(az network vnet show --name "$VNET" --resource-group "$RG" --query id -o tsv)
SUBNET_PRIVATE_ID=$(az network vnet subnet show \
  --name "$SUBNET_PRIVATE" --vnet-name "$VNET" --resource-group "$RG" \
  --query id -o tsv)

# VNet Integration – koppla App Service till snet-app
# Görs här (efter steg 4) så att VNet och subnät garanterat finns
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
# 3. KEY VAULT + Private Endpoint
# ---------------------------------------------------------------------------
log "5. Skapar Key Vault..."

echo "  Hämtar Key Vault-konfiguration från dev..."
KV_DEV_SKU=$(az keyvault show --name "$DEV_KEY_VAULT" --resource-group "$DEV_RG" \
  --query "properties.sku.name" -o tsv 2>/dev/null || echo "standard")

if az keyvault show --name "$KEY_VAULT" --resource-group "$RG" &>/dev/null; then
  echo "  Key Vault '$KEY_VAULT' finns redan – hoppar över."
else
  echo "  Skapar Key Vault '$KEY_VAULT'..."
  az keyvault create \
    --name "$KEY_VAULT" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --sku "$KV_DEV_SKU" \
    --enable-rbac-authorization true \
    --public-network-access Disabled
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

log "3b. Kontrollerar Private DNS Zone – Key Vault..."
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
# 4. STORAGE ACCOUNT + Private Endpoint
# ---------------------------------------------------------------------------
log "6. Skapar Storage Account..."

echo "  Hämtar Storage-konfiguration från dev..."
STORAGE_DEV_SKU=$(az storage account show --name "$DEV_STORAGE" --resource-group "$DEV_RG" \
  --query "sku.name" -o tsv 2>/dev/null || echo "Standard_LRS")
STORAGE_DEV_KIND=$(az storage account show --name "$DEV_STORAGE" --resource-group "$DEV_RG" \
  --query "kind" -o tsv 2>/dev/null || echo "StorageV2")
STORAGE_DEV_TLS=$(az storage account show --name "$DEV_STORAGE" --resource-group "$DEV_RG" \
  --query "minimumTlsVersion" -o tsv 2>/dev/null || echo "TLS1_2")

if az storage account show --name "$STORAGE_ACCOUNT" --resource-group "$RG" &>/dev/null; then
  echo "  Storage Account '$STORAGE_ACCOUNT' finns redan – hoppar över."
else
  echo "  Skapar Storage Account '$STORAGE_ACCOUNT'..."
  az storage account create \
    --name "$STORAGE_ACCOUNT" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --sku "$STORAGE_DEV_SKU" \
    --kind "$STORAGE_DEV_KIND" \
    --public-network-access Disabled \
    --allow-blob-public-access false \
    --min-tls-version "$STORAGE_DEV_TLS"
fi

STORAGE_ID=$(az storage account show --name "$STORAGE_ACCOUNT" --resource-group "$RG" --query id -o tsv)

log "4b. Kontrollerar Private DNS Zone – Blob..."
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
# 5. SQL SERVER + DATABAS + Private Endpoint
# ---------------------------------------------------------------------------
log "7. Skapar SQL Server och databas..."

if [ -z "$SQL_ADMIN_PASSWORD" ]; then
  echo "FEL: SQL_ADMIN_PASSWORD är inte satt. Avbryter." >&2
  exit 1
fi

if az sql server show --name "$SQL_SERVER" --resource-group "$RG" &>/dev/null; then
  echo "  SQL Server '$SQL_SERVER' finns redan – hoppar över."
else
  echo "  Skapar SQL Server '$SQL_SERVER'..."
  az sql server create \
    --name "$SQL_SERVER" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --admin-user "$SQL_ADMIN_USER" \
    --admin-password "$SQL_ADMIN_PASSWORD"
fi

echo "  Hämtar SQL Server-konfiguration från dev..."
SQL_DEV_SERVER_PROPS=$(az sql server show \
  --name "$DEV_SQL_SERVER" --resource-group "$DEV_RG" -o json 2>/dev/null || echo "{}")
SQL_DEV_TLS=$(echo "$SQL_DEV_SERVER_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('minimalTlsVersion', '1.2'))")

SQL_DEV_AAD_LOGIN=$(az sql server ad-admin list \
  --server "$DEV_SQL_SERVER" --resource-group "$DEV_RG" \
  --query "[0].login" -o tsv 2>/dev/null || echo "")
SQL_DEV_AAD_SID=$(az sql server ad-admin list \
  --server "$DEV_SQL_SERVER" --resource-group "$DEV_RG" \
  --query "[0].sid" -o tsv 2>/dev/null || echo "")
SQL_DEV_AAD_TENANT=$(az sql server ad-admin list \
  --server "$DEV_SQL_SERVER" --resource-group "$DEV_RG" \
  --query "[0].tenantId" -o tsv 2>/dev/null || echo "")

az sql server update \
  --name "$SQL_SERVER" \
  --resource-group "$RG" \
  --set publicNetworkAccess=Disabled \
  --minimal-tls-version "$SQL_DEV_TLS"

# Sätt Entra ID-administratör om det finns i dev
if [ -n "$SQL_DEV_AAD_LOGIN" ]; then
  if az sql server ad-admin show --server "$SQL_SERVER" --resource-group "$RG" &>/dev/null; then
    echo "  Entra ID-administratör finns redan – hoppar över."
  else
    echo "  Sätter Entra ID-administratör '$SQL_DEV_AAD_LOGIN'..."
    az sql server ad-admin create \
      --server "$SQL_SERVER" \
      --resource-group "$RG" \
      --display-name "$SQL_DEV_AAD_LOGIN" \
      --object-id "$SQL_DEV_AAD_SID"
  fi
fi

echo "  Hämtar SQL-konfiguration från dev..."
SQL_DEV_PROPS=$(az sql db show \
  --name "$DEV_SQL_DB" --server "$DEV_SQL_SERVER" --resource-group "$DEV_RG" -o json 2>/dev/null || echo "{}")
SQL_DEV_TIER=$(echo "$SQL_DEV_PROPS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('edition', 'GeneralPurpose'))")
SQL_DEV_FAMILY=$(echo "$SQL_DEV_PROPS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('currentServiceObjectiveName','').split('_')[1] if '_' in d.get('currentServiceObjectiveName','') else 'Gen5')")
SQL_DEV_CAPACITY=$(echo "$SQL_DEV_PROPS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('capacity', 2))")
SQL_DEV_ZONE=$(echo "$SQL_DEV_PROPS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(str(d.get('zoneRedundant', False)).lower())")

if az sql db show --name "$SQL_DB" --server "$SQL_SERVER" --resource-group "$RG" &>/dev/null; then
  echo "  SQL databas '$SQL_DB' finns redan – hoppar över."
else
  echo "  Skapar SQL databas '$SQL_DB'..."
  az sql db create \
    --name "$SQL_DB" \
    --server "$SQL_SERVER" \
    --resource-group "$RG" \
    --tier "$SQL_DEV_TIER" \
    --family "$SQL_DEV_FAMILY" \
    --capacity "$SQL_DEV_CAPACITY" \
    --zone-redundant "$SQL_DEV_ZONE"
fi

SQL_ID=$(az sql server show --name "$SQL_SERVER" --resource-group "$RG" --query id -o tsv)

log "5b. Kontrollerar Private DNS Zone – SQL..."
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
# 6. SERVICE BUS
# ---------------------------------------------------------------------------
log "8. Skapar Service Bus..."

echo "  Hämtar Service Bus-konfiguration från dev..."
SB_DEV_SKU=$(az servicebus namespace show \
  --name "$DEV_SERVICE_BUS" --resource-group "$DEV_RG" \
  --query "sku.name" -o tsv 2>/dev/null || echo "Standard")

if az servicebus namespace show --name "$SERVICE_BUS" --resource-group "$RG" &>/dev/null; then
  echo "  Service Bus '$SERVICE_BUS' finns redan – hoppar över."
else
  echo "  Skapar Service Bus '$SERVICE_BUS'..."
  az servicebus namespace create \
    --name "$SERVICE_BUS" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --sku "$SB_DEV_SKU"
fi

# ---------------------------------------------------------------------------
# 7. AZURE OPENAI + Private Endpoint
# ---------------------------------------------------------------------------
log "9. Skapar Azure OpenAI..."

echo "  Hämtar Azure OpenAI-konfiguration från dev..."
OAI_DEV_SKU=$(az cognitiveservices account show \
  --name "$DEV_OPENAI" --resource-group "$DEV_RG" \
  --query "sku.name" -o tsv 2>/dev/null || echo "S0")

if az cognitiveservices account show --name "$OPENAI_ACCOUNT" --resource-group "$RG" &>/dev/null; then
  echo "  Azure OpenAI '$OPENAI_ACCOUNT' finns redan – hoppar över."
else
  echo "  Skapar Azure OpenAI '$OPENAI_ACCOUNT'..."
  az cognitiveservices account create \
    --name "$OPENAI_ACCOUNT" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --kind OpenAI \
    --sku "$OAI_DEV_SKU" \
    --custom-domain "$OPENAI_ACCOUNT" \
    --public-network-access Disabled
fi

OAI_ID=$(az cognitiveservices account show \
  --name "$OPENAI_ACCOUNT" --resource-group "$RG" --query id -o tsv)

log "7b. Kontrollerar Private DNS Zone – OpenAI..."
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
# 8. AZURE FRONT DOOR (CDN-profil + endpoint)
# ---------------------------------------------------------------------------
log "10. Skapar Azure Front Door..."

echo "  Hämtar Front Door-konfiguration från dev..."
AFD_DEV_SKU=$(az afd profile show \
  --profile-name "$DEV_AFD_PROFILE" --resource-group "$DEV_RG" \
  --query "sku.name" -o tsv 2>/dev/null || echo "Standard_AzureFrontDoor")

if az afd profile show --profile-name "$AFD_PROFILE" --resource-group "$RG" &>/dev/null; then
  echo "  Front Door '$AFD_PROFILE' finns redan – hoppar över."
else
  echo "  Skapar Front Door '$AFD_PROFILE'..."
  az afd profile create \
    --profile-name "$AFD_PROFILE" \
    --resource-group "$RG" \
    --sku "$AFD_DEV_SKU"
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
# 9. MONITORING – Action Groups
# ---------------------------------------------------------------------------
log "11. Skapar Action Groups..."

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
# 10. MONITORING – Metric Alerts (CPU, requests, response time)
# ---------------------------------------------------------------------------
log "12. Skapar Metric Alerts..."

# Hämta App Service resource ID
BACKEND_APP_ID=$(az webapp show \
  --name "$BACKEND_APP_NAME" --resource-group "$RG" --query id -o tsv 2>/dev/null || echo "")

if [ -n "$BACKEND_APP_ID" ]; then

  echo "  Hämtar Metric Alert-konfiguration från dev..."
  get_alert_prop() {
    local alert_name="$1" prop="$2" default="$3"
    az monitor metrics alert show --name "$alert_name" --resource-group "$DEV_RG" \
      --query "$prop" -o tsv 2>/dev/null || echo "$default"
  }

  CPU_THRESHOLD=$(az monitor metrics alert show --name "alrt-cpu-dev" --resource-group "$DEV_RG" \
    --query "criteria.allOf[0].threshold" -o tsv 2>/dev/null || echo "80")
  CPU_WINDOW=$(get_alert_prop "alrt-cpu-dev" "windowSize" "PT5M")
  CPU_FREQ=$(get_alert_prop "alrt-cpu-dev" "evaluationFrequency" "PT1M")
  CPU_SEVERITY=$(get_alert_prop "alrt-cpu-dev" "severity" "2")

  REQ_THRESHOLD=$(az monitor metrics alert show --name "alert-requests-dev" --resource-group "$DEV_RG" \
    --query "criteria.allOf[0].threshold" -o tsv 2>/dev/null || echo "10")
  REQ_WINDOW=$(get_alert_prop "alert-requests-dev" "windowSize" "PT5M")
  REQ_FREQ=$(get_alert_prop "alert-requests-dev" "evaluationFrequency" "PT1M")
  REQ_SEVERITY=$(get_alert_prop "alert-requests-dev" "severity" "2")

  RESP_THRESHOLD=$(az monitor metrics alert show --name "alert-response-dev" --resource-group "$DEV_RG" \
    --query "criteria.allOf[0].threshold" -o tsv 2>/dev/null || echo "3")
  RESP_WINDOW=$(get_alert_prop "alert-response-dev" "windowSize" "PT5M")
  RESP_FREQ=$(get_alert_prop "alert-response-dev" "evaluationFrequency" "PT1M")
  RESP_SEVERITY=$(get_alert_prop "alert-response-dev" "severity" "3")

  if az monitor metrics alert show --name "alrt-cpu-${ENV}" --resource-group "$RG" &>/dev/null; then
    echo "  Metric alert 'alrt-cpu-${ENV}' finns redan – hoppar över."
  else
    echo "  Skapar metric alert 'alrt-cpu-${ENV}'..."
  az monitor metrics alert create \
    --name "alrt-cpu-${ENV}" \
    --resource-group "$RG" \
    --scopes "$BACKEND_APP_ID" \
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
# 13. APPLICATION INSIGHTS – skapas om de inte redan finns
# ---------------------------------------------------------------------------
log "13. Kontrollerar Application Insights..."

AI_NAME="app-${ENV}-api"
if az monitor app-insights component show --app "$AI_NAME" --resource-group "$RG" &>/dev/null; then
  echo "  Application Insights '$AI_NAME' finns redan – hoppar över."
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


# ---------------------------------------------------------------------------
# 14. AVAILABILITY TESTS
# ---------------------------------------------------------------------------
log "14. Skapar Availability Tests..."

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
    echo "  Skapar availability test för API..."
  az monitor app-insights web-test create \
    --name "availability test-app-${ENV}-api" \
    --resource-group "$RG" \
    --location "$LOCATION" \
    --defined-web-test-kind ping \
    --description "Availability test för API" \
    --enabled true \
    --frequency "$AVAIL_FREQUENCY" \
    --timeout "$AVAIL_TIMEOUT" \
    --locations $AVAIL_LOCATIONS \
    --request-url "$BACKEND_APP_URL" \
    --app-insights-id "$AI_ID"
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
# 15. KOPIERA INSTÄLLNINGAR FRÅN DEV
# ---------------------------------------------------------------------------
log "15. Kopierar inställningar från dev-miljön..."

# --- App Service: app settings och connection strings ---
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
    --settings @/tmp/prod_appsettings.json > /dev/null
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
    --settings @/tmp/prod_fa_settings.json > /dev/null
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
    if az servicebus queue show --namespace-name "$SERVICE_BUS" --resource-group "$RG" --name "$queue_name" &>/dev/null; then
      echo "    Kö '$queue_name' finns redan – hoppar över."
    else
      QUEUE_PROPS=$(az servicebus queue show \
        --namespace-name "$DEV_SERVICE_BUS" \
        --resource-group "$DEV_RG" \
        --name "$queue_name" -o json)
      LOCK_DURATION=$(echo "$QUEUE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin)['lockDuration'])")
      MAX_SIZE=$(echo "$QUEUE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin)['maxSizeInMegabytes'])")
      MAX_DELIVERY=$(echo "$QUEUE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin)['maxDeliveryCount'])")
      az servicebus queue create \
        --namespace-name "$SERVICE_BUS" \
        --resource-group "$RG" \
        --name "$queue_name" \
        --lock-duration "$LOCK_DURATION" \
        --max-size "$MAX_SIZE" \
        --max-delivery-count "$MAX_DELIVERY" > /dev/null
      echo "    Skapade kö: $queue_name"
    fi
  done <<< "$DEV_QUEUES"
fi

echo "  Service Bus: kopierar topics..."
DEV_TOPICS=$(az servicebus topic list   --namespace-name "$DEV_SERVICE_BUS"   --resource-group "$DEV_RG"   --query "[].name" -o tsv 2>/dev/null || echo "")

if [ -n "$DEV_TOPICS" ]; then
  while IFS= read -r topic_name; do
    if az servicebus topic show --namespace-name "$SERVICE_BUS" --resource-group "$RG" --name "$topic_name" &>/dev/null; then
      echo "    Topic '$topic_name' finns redan – hoppar över."
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
DEV_STORAGE_KEY=$(az storage account keys list   --account-name "$DEV_STORAGE"   --resource-group "$DEV_RG"   --query "[0].value" -o tsv 2>/dev/null || echo "")

if [ -n "$DEV_STORAGE_KEY" ]; then
  DEV_CONTAINERS=$(az storage container list     --account-name "$DEV_STORAGE"     --account-key "$DEV_STORAGE_KEY"     --query "[].name" -o tsv 2>/dev/null || echo "")

  PROD_STORAGE_KEY=$(az storage account keys list     --account-name "$STORAGE_ACCOUNT"     --resource-group "$RG"     --query "[0].value" -o tsv)

  if [ -n "$DEV_CONTAINERS" ]; then
    while IFS= read -r container_name; do
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
    result = subprocess.run([
        'az', 'cognitiveservices', 'account', 'deployment', 'show',
        '--name', '$OPENAI_ACCOUNT',
        '--resource-group', '$RG',
        '--deployment-name', name
    ], capture_output=True)
    if result.returncode == 0:
        print(f'    Deployment {name} finns redan – hoppar över.')
        continue
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
    if az afd origin-group show --origin-group-name "$og_name" --profile-name "$AFD_PROFILE" --resource-group "$RG" &>/dev/null; then
      echo "    Origin group '$og_name' finns redan – hoppar över."
    else
      OG_PROPS=$(az afd origin-group show \
        --profile-name "$DEV_AFD_PROFILE" \
        --resource-group "$DEV_RG" \
        --origin-group-name "$og_name" -o json)
      PROBE_PATH=$(echo "$OG_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('healthProbeSettings',{}).get('probePath','/'))")
      PROBE_INTERVAL=$(echo "$OG_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('healthProbeSettings',{}).get('probeIntervalInSeconds',100))")
      az afd origin-group create \
        --origin-group-name "$og_name" \
        --profile-name "$AFD_PROFILE" \
        --resource-group "$RG" \
        --probe-path "$PROBE_PATH" \
        --probe-interval-in-seconds "$PROBE_INTERVAL" > /dev/null
      echo "    Skapade origin group: $og_name"
    fi

    # Kopiera origins inom gruppen
    DEV_ORIGINS=$(az afd origin list \
      --profile-name "$DEV_AFD_PROFILE" \
      --resource-group "$DEV_RG" \
      --origin-group-name "$og_name" -o json 2>/dev/null || echo "[]")

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
    result = subprocess.run([
        'az', 'afd', 'origin', 'show',
        '--origin-name', name,
        '--origin-group-name', '$og_name',
        '--profile-name', '$AFD_PROFILE',
        '--resource-group', '$RG'
    ], capture_output=True)
    if result.returncode == 0:
        print(f'      Origin {name} finns redan – hoppar över.')
        continue
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
DEV_ROUTES=$(az afd route list \
  --profile-name "$DEV_AFD_PROFILE" \
  --resource-group "$DEV_RG" \
  --endpoint-name "video-endpoint" \
  --query "[].name" -o tsv 2>/dev/null || echo "")

if [ -n "$DEV_ROUTES" ]; then
  while IFS= read -r route_name; do
    if az afd route show --route-name "$route_name" --profile-name "$AFD_PROFILE" --resource-group "$RG" --endpoint-name "$AFD_ENDPOINT" &>/dev/null; then
      echo "    Route '$route_name' finns redan – hoppar över."
    else
      ROUTE_PROPS=$(az afd route show \
        --profile-name "$DEV_AFD_PROFILE" \
        --resource-group "$DEV_RG" \
        --endpoint-name "video-endpoint" \
        --route-name "$route_name" -o json)
      PATTERNS=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; print(' '.join(json.load(sys.stdin).get('patternsToMatch', ['/*'])))")
      OG=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['originGroup']['id'].split('/')[-1])")
      HTTPS=$(echo "$ROUTE_PROPS" | python3 -c "import json,sys; print(json.load(sys.stdin).get('httpsRedirect','Enabled'))")
      az afd route create \
        --route-name "$route_name" \
        --profile-name "$AFD_PROFILE" \
        --resource-group "$RG" \
        --endpoint-name "$AFD_ENDPOINT" \
        --origin-group "$OG" \
        --patterns-to-match $PATTERNS \
        --https-redirect "$HTTPS" \
        --forwarding-protocol MatchRequest > /dev/null
      echo "    Skapade route: $route_name"
    fi
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
    result = subprocess.run([
        'az', 'sql', 'server', 'firewall-rule', 'show',
        '--server', '$SQL_SERVER',
        '--resource-group', '$RG',
        '--name', r['name']
    ], capture_output=True)
    if result.returncode == 0:
        print(f'    Brandväggsregel {r[\"name\"]} finns redan – hoppar över.')
        continue
    subprocess.run([
        'az', 'sql', 'server', 'firewall-rule', 'create',
        '--server', '$SQL_SERVER',
        '--resource-group', '$RG',
        '--name', r['name'],
        '--start-ip-address', r['startIpAddress'],
        '--end-ip-address', r['endIpAddress']
    ])
    print(f'    Kopierade brandväggsregel: {r[\"name\"]}')
"
fi

# ---------------------------------------------------------------------------
# 16. IAM-ROLLER
# ---------------------------------------------------------------------------
# Väntar tills App Service och Function App har sina systemidentiteter
# ---------------------------------------------------------------------------

log "16. Sätter upp IAM-roller..."

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
# Website Contributor på app-dev-web hoppas över då app-prod-web inte finns i prod.
echo "  MSI-roller: inga att tilldela i prod."

# ---------------------------------------------------------------------------
# KLART
# ---------------------------------------------------------------------------
log "Klart! Alla prod-resurser är skapade i '$RG'."
echo "Kom ihåg att:"
echo "  1. Sätt hemligheter i Key Vault '$KEY_VAULT'"
echo "  2. Verifiera VNet Integration för App Service i Azure Portal"
echo "  4. Verifiera att kopierade app settings/connection strings pekar rätt i prod"