# Removes the dlcdn.apache.org certificates (suffixed 1-3) from the JVM trust store.
# Run this to undo what create_certs.sh imported.
# NOTE: Must be run with administrator/root privileges.

# Password for the JVM cacerts trust store.
# "changeit" is the default password shipped with the JDK.
# Edit this value if your organisation uses a custom trust store password.
STORE_PASS="changeit"

for c in 1 2 3; do 
	keytool -delete -alias dlcdn-apache-org$c -cacerts -storepass "$STORE_PASS"
done
