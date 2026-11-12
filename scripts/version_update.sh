# NOTE: THIS SCRIPT IS NOT BULLET-PROOF!!

OLD_VERSION=6.4.2-SNAPSHOT
NEW_VERSION=6.5.0-SNAPSHOT
for f in $(grep -R $OLD_VERSION |grep /pom.xml: |cut -d: -f1); do
    sed -i "s/$OLD_VERSION/$NEW_VERSION/g" $f
done

sed -i "s/$OLD_VERSION/$NEW_VERSION/g" jsystem-core-projects/jsystemCore/log/properties.js
sed -i "s/$OLD_VERSION/$NEW_VERSION/g" README.md
