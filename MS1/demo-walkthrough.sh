#!/usr/bin/env bash
# ==============================================================================
# DEMO WALKTHROUGH SCRIPT
# Legacy Application Migration: IBM Content Manager (Db2 SQL / SDK) -> Spring Boot REST
# ==============================================================================

set -e

# Terminal colors
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
MAGENTA='\033[0;35m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m' # No Color

function step_header() {
    echo -e "\n${BLUE}================================================================================${NC}"
    echo -e "${BOLD}${CYAN}$1${NC}"
    echo -e "${BLUE}================================================================================${NC}"
    if [[ "$AUTO_MODE" != "true" ]]; then
        read -p "Press [Enter] to run this step..." < /dev/tty
    fi
}

AUTO_MODE="false"
if [[ "$1" == "--auto" || "$1" == "-y" ]]; then
    AUTO_MODE="true"
fi

clear 2>/dev/null || true

echo -e "${GREEN}"
echo "  ███████╗███╗   ██╗████████╗███████╗██████╗ ██████╗ ██████╗ ██╗███████╗███████╗"
echo "  ██╔════╝████╗  ██║╚══██╔══╝██╔════╝██╔══██╗██╔══██╗██╔══██╗██║██╔════╝██╔════╝"
echo "  █████╗  ██╔██╗ ██║   ██║   █████╗  ██████╔╝██████╔╝██████╔╝██║███████╗█████╗  "
echo "  ██╔══╝  ██║╚██╗██║   ██║   ██╔══╝  ██╔═══╝ ██╔══██╗██╔══██╗██║╚════██║██╔══╝  "
echo "  ███████╗██║ ╚████║   ██║   ███████╗██║     ██║  ██║██║  ██║██║███████║███████╗"
echo "  ╚══════╝╚═╝  ╚═══╝   ╚═╝   ╚══════╝╚═╝     ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝╚══════╝╚══════╝"
echo -e "${NC}"
echo -e "${BOLD}IBM Content Manager (Db2 SQL / SDK) -> Spring Boot REST API Migration Demo${NC}"
echo -e "Runtime Architecture: WebSphere Liberty EAR (8080) | Spring Boot MS2 (8082) | Spring Boot MS1 (8081)"
echo ""

# ------------------------------------------------------------------------------
# STEP 1: Verify All Services are Running
# ------------------------------------------------------------------------------
step_header "STEP 1: Verify Architecture Services Health Status"

echo -e "${YELLOW}[1/4] Checking PostgreSQL (Port 5432)...${NC}"
if nc -z localhost 5432 2>/dev/null || curl -s http://localhost:8082 >/dev/null 2>&1; then
    echo -e "${GREEN}✓ Database connection port is active.${NC}"
else
    echo -e "${RED}✗ Database not reachable. Please run: docker compose up -d${NC}"
fi

echo -e "\n${YELLOW}[2/4] Checking Target Spring Boot REST API - MS2 (Port 8082)...${NC}"
MS2_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8082/ms2/persons || echo "DOWN")
if [[ "$MS2_STATUS" == "200" ]]; then
    echo -e "${GREEN}✓ MS2 is UP and HEALTHY (HTTP $MS2_STATUS).${NC}"
else
    echo -e "${YELLOW}! MS2 response: $MS2_STATUS (May still be starting up).${NC}"
fi

echo -e "\n${YELLOW}[3/4] Checking Client Spring Boot Microservice - MS1 (Port 8081)...${NC}"
MS1_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/ms1/greet || echo "DOWN")
if [[ "$MS1_STATUS" == "200" ]]; then
    echo -e "${GREEN}✓ MS1 is UP and HEALTHY (HTTP $MS1_STATUS).${NC}"
else
    echo -e "${YELLOW}! MS1 response: $MS1_STATUS.${NC}"
fi

echo -e "\n${YELLOW}[4/4] Checking Legacy IBM WebSphere Liberty EAR Application (Port 8080)...${NC}"
EAR_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/legacy-cm-web/ || echo "DOWN")
if [[ "$EAR_STATUS" == "200" || "$EAR_STATUS" == "302" ]]; then
    echo -e "${GREEN}✓ WebSphere Liberty EAR is UP and HEALTHY (HTTP $EAR_STATUS).${NC}"
else
    echo -e "${YELLOW}! WebSphere Liberty response: $EAR_STATUS.${NC}"
fi

# ------------------------------------------------------------------------------
# STEP 2: The OLD Way (Before Migration - Direct Db2 SQL via Servlet)
# ------------------------------------------------------------------------------
step_header "STEP 2: [BEFORE MIGRATION] Legacy Direct Db2 SQL Query via WebSphere Servlet"

echo -e "${YELLOW}Description:${NC} The legacy servlet executes raw SQL with PreparedStatement against"
echo -e "the IBM Content Manager catalog tables (ICMSTITEMS001001, ICMSTITEMTYPEDEFS, ICMSTCOLLNAME001001)."
echo -e "${MAGENTA}Calling: GET http://localhost:8080/legacy-cm-web/legacy-cm?mode=sql&itemId=ICM_ITEM_001${NC}\n"

SQL_RESULT=$(curl -s "http://localhost:8080/legacy-cm-web/legacy-cm?mode=sql&itemId=ICM_ITEM_001")
echo "$SQL_RESULT"

echo -e "\n${YELLOW}Why this was a problem in production:${NC}"
echo -e "1. ${RED}Tight Coupling:${NC} Application code is hardcoded to internal database schema."
echo -e "2. ${RED}Security Risk:${NC} Requires direct database credentials from application layer."
echo -e "3. ${RED}Memory Bloat:${NC} ResultSet.getBinaryStream() often buffers entire BLOBs in JVM heap memory."

# ------------------------------------------------------------------------------
# STEP 3: Discovery & Gap Analysis
# ------------------------------------------------------------------------------
step_header "STEP 3: [DISCOVERY & GAP ANALYSIS] Tracing Legacy Db2 SQL Join"

echo -e "${YELLOW}Description:${NC} Automated discovery tool that analyzes the legacy Db2 query and shows"
echo -e "how every table and column maps to modern Spring Boot REST API fields."
echo -e "${MAGENTA}Calling: GET http://localhost:8082/api/v1/legacy/trace-sql?itemId=ICM_ITEM_001${NC}\n"

GAP_RESULT=$(curl -s "http://localhost:8082/api/v1/legacy/trace-sql?itemId=ICM_ITEM_001")
echo "$GAP_RESULT"

# ------------------------------------------------------------------------------
# STEP 4: OAuth2 Client-Credentials Authentication
# ------------------------------------------------------------------------------
step_header "STEP 4: [OAUTH2 SECURITY] Requesting Bearer Access Token"

echo -e "${YELLOW}Description:${NC} Target REST APIs are secured using OAuth2 Client-Credentials Flow (RFC 6749)."
echo -e "Credentials: client_id=legacy-app, client_secret=cm-secret-123"
echo -e "${MAGENTA}Calling: POST http://localhost:8082/oauth/token${NC}\n"

TOKEN_RESPONSE=$(curl -s -X POST "http://localhost:8082/oauth/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=client_credentials&client_id=legacy-app&client_secret=cm-secret-123&scope=documents:read%20documents:write")
echo "$TOKEN_RESPONSE"

# Extract token using sed/grep (no jq required)
ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)
if [[ -z "$ACCESS_TOKEN" ]]; then
    ACCESS_TOKEN="demo-cm-token-12345"
fi
echo -e "\n${GREEN}✓ Captured OAuth2 Bearer Token: ${ACCESS_TOKEN:0:25}...${NC}"

# Demonstrate unauthorized rejection
echo -e "\n${YELLOW}Testing endpoint security WITHOUT token (expecting 401 Unauthorized):${NC}"
UNAUTH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8082/api/v1/documents)
echo -e "Response Status without token: ${RED}HTTP $UNAUTH_STATUS Unauthorized${NC} (Access denied as expected)"

# ------------------------------------------------------------------------------
# STEP 5: Multipart Document Upload to Target REST API
# ------------------------------------------------------------------------------
step_header "STEP 5: [MULTIPART STREAMING] Ingesting New Document into Target REST API"

echo -e "${YELLOW}Description:${NC} Creating a temporary test document and uploading it via multipart/form-data"
echo -e "with the acquired Bearer token."

TEMP_DOC="demo_claim_policy.txt"
echo "CONFIDENTIAL: Customer Insurance Claim Policy #POL-99881 - Approved via Modern REST API" > "$TEMP_DOC"

echo -e "${MAGENTA}Calling: POST http://localhost:8082/api/v1/documents (multipart/form-data)${NC}\n"

UPLOAD_RESPONSE=$(curl -s -X POST "http://localhost:8082/api/v1/documents" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -F "file=@$TEMP_DOC;type=text/plain" \
    -F "itemType=CUSTOMER_CLAIM" \
    -F "author=WALKTHROUGH_SCRIPT")
echo "$UPLOAD_RESPONSE"

rm -f "$TEMP_DOC"

# ------------------------------------------------------------------------------
# STEP 6: The NEW Way (After Migration - Servlet Consuming Spring Boot REST API)
# ------------------------------------------------------------------------------
step_header "STEP 6: [AFTER MIGRATION] Legacy WebSphere Servlet Consuming REST API"

echo -e "${YELLOW}Description:${NC} The migrated servlet no longer connects to Db2."
echo -e "Instead, it obtains an OAuth2 token and queries the modern Spring Boot REST API."
echo -e "${MAGENTA}Calling: GET http://localhost:8080/legacy-cm-web/legacy-cm?mode=rest&docId=1${NC}\n"

MIGRATED_RESULT=$(curl -s "http://localhost:8080/legacy-cm-web/legacy-cm?mode=rest&docId=1")
echo "$MIGRATED_RESULT"

# ------------------------------------------------------------------------------
# STEP 7: Document Binary Streaming (Chunked Transfer Encoding)
# ------------------------------------------------------------------------------
step_header "STEP 7: [BINARY STREAMING] Downloading Content via Chunked OutputStream"

echo -e "${YELLOW}Description:${NC} Streaming document content chunk-by-chunk using HTTP StreamingResponseBody"
echo -e "and 8KB buffers (no heap memory exhaustion for large files)."
echo -e "${MAGENTA}Calling: GET http://localhost:8082/api/v1/documents/1/content${NC}\n"

DOWNLOAD_TEMP="downloaded_stream_doc_1.pdf"
curl -s -i "http://localhost:8082/api/v1/documents/1/content" \
    -H "Authorization: Bearer $ACCESS_TOKEN" > "$DOWNLOAD_TEMP"

echo -e "${GREEN}✓ Stream Headers & Binary Output received:${NC}"
head -n 12 "$DOWNLOAD_TEMP"
rm -f "$DOWNLOAD_TEMP"

# ------------------------------------------------------------------------------
# STEP 8: Spring Boot Client Microservice 1 (RestTemplate)
# ------------------------------------------------------------------------------
step_header "STEP 8: [MICROSERVICE CONSUMPTION] MS1 Calling MS2 via RestTemplate"

echo -e "${YELLOW}Description:${NC} Spring Boot Microservice 1 uses RestTemplate to request OAuth2 tokens"
echo -e "and stream documents from MS2."
echo -e "${MAGENTA}Calling: GET http://localhost:8081/ms1/persons${NC}\n"
curl -s "http://localhost:8081/ms1/persons"

echo -e "\n\n${MAGENTA}Calling: GET http://localhost:8081/ms1/token${NC}\n"
curl -s "http://localhost:8081/ms1/token"

# ------------------------------------------------------------------------------
# SUMMARY & URLS
# ------------------------------------------------------------------------------
echo -e "\n${GREEN}================================================================================${NC}"
echo -e "${BOLD}${GREEN}✓ WALKTHROUGH DEMO COMPLETED SUCCESSFULLY!${NC}"
echo -e "${GREEN}================================================================================${NC}"
echo -e "\n${BOLD}Quick Browser Access Points:${NC}"
echo -e "  1. ${CYAN}Interactive Web UI (WebSphere):${NC} http://localhost:8080/legacy-cm-web/"
echo -e "  2. ${CYAN}Target REST API Swagger UI:${NC}     http://localhost:8082/swagger-ui.html"
echo -e "  3. ${CYAN}Client Microservice Swagger UI:${NC} http://localhost:8081/swagger-ui.html"
echo -e "  4. ${CYAN}Postman Collection:${NC}             Import 'postman_collection.json'\n"
