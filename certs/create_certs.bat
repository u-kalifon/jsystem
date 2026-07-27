@echo off
:: Imports the dlcdn.apache.org certificates (suffixed 1-3) into the JVM trust store.
:: Required so that Maven can download artifacts from dlcdn.apache.org over HTTPS.
:: The .pem files must be present in the same directory before running this script.
:: NOTE: Must be run with administrator privileges (right-click -> Run as administrator).

:: Password for the JVM cacerts trust store.
:: "changeit" is the default password shipped with the JDK.
:: Edit this value if your organisation uses a custom trust store password.
set STORE_PASS=changeit

for %%c in (1 2 3) do (
    echo yes | keytool -importcert -trustcacerts -alias dlcdn-apache-org%%c -cacerts -storepass %STORE_PASS% -file dlcdn-apache-org%%c.pem
)
