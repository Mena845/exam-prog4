#!/bin/bash
curl_with_retry() {
  local max_attempts=5
  local attempt=1
  while [ $attempt -le $max_attempts ]; do
    local response
    response=$(curl -w "\n%{http_code}" "$@")
    local http_code
    http_code=$(echo "$response" | tail -n1)
    local body
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" = "429" ]; then
      if [ $attempt -lt $max_attempts ]; then
        local wait_time=$((2 ** (attempt - 1)))
        echo "Rate limited (429). Attempt $attempt/$max_attempts. Waiting ${wait_time}s..."
        sleep $wait_time
        attempt=$((attempt + 1))
        continue
      fi
      echo "$body"
      echo "API Error: HTTP 429 after $max_attempts attempts"
      return 1
    elif [ "$http_code" -ge 400 ]; then
      echo "$body"
      echo "API Error: HTTP $http_code"
      return 1
    else
      echo "$body"
      return 0
    fi
  done
  return 1
}
