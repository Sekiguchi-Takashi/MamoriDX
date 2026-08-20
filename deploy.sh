#!/data/data/com.termux/files/usr/bin/bash
cd "$(dirname "$0")"
TOKEN=$(git config --global github.token)
GHUSER=Sekiguchi-Takashi
REPO=MamoriDX
rm -f .github/workflows/build.yml
rm -f app/src/main/java/com/appathy/mamoridx/AccessibilityAudit.kt
rm -f app/src/main/java/com/appathy/mamoridx/ApkCleaner.kt
rm -f app/src/main/java/com/appathy/mamoridx/AssetLedger.kt
rm -f app/src/main/java/com/appathy/mamoridx/BatteryHealth.kt
rm -f app/src/main/java/com/appathy/mamoridx/DeviceStatus.kt
rm -f app/src/main/java/com/appathy/mamoridx/DnsLogStore.kt
rm -f app/src/main/java/com/appathy/mamoridx/DnsMonitorService.kt
rm -f app/src/main/java/com/appathy/mamoridx/FolderDigest.kt
rm -f app/src/main/java/com/appathy/mamoridx/MediaScanner.kt
rm -f app/src/main/java/com/appathy/mamoridx/PcAdvisor.kt
rm -f app/src/main/java/com/appathy/mamoridx/RouterCheck.kt
rm -f app/src/main/java/com/appathy/mamoridx/SaasGuard.kt
rm -f app/src/main/java/com/appathy/mamoridx/ShareGateActivity.kt
rm -f app/src/main/java/com/appathy/mamoridx/ThreatScanner.kt
rm -f app/src/main/java/com/appathy/mamoridx/ToolsActivity.kt
rm -f app/src/main/java/com/appathy/mamoridx/UrlChecker.kt
if [ ! -d .git ]; then git init -b main; fi
git remote remove origin 2>/dev/null
git remote add origin "https://${GHUSER}:${TOKEN}@github.com/${GHUSER}/${REPO}.git"
git add -A
git commit -m "${1:-update}"
git pull --rebase origin main
git push -u origin main
if [ "$2" = "notag" ]; then printf 'pushed (no tag)\n'; exit 0; fi
git fetch --tags --force
LATEST=$(git tag --list 'v*' | sort -V | tail -1)
NEXT=$(printf '%s' "$LATEST" | awk '/^v[0-9]+\.[0-9]+\.[0-9]+$/ { split($0,a,"."); sub("v","",a[1]); print "v" a[1] "." a[2] "." a[3]+1; next } /^v[0-9]+\.[0-9]+$/ { split($0,a,"."); sub("v","",a[1]); print "v" a[1] "." a[2] ".1"; next } /[0-9]+$/ { match($0,/[0-9]+$/); p=substr($0,1,RSTART-1); n=substr($0,RSTART)+1; print p n; next } { print "v1.0.0" }')
if [ -z "$NEXT" ]; then NEXT=v1.0.0; fi
git tag "$NEXT"
git push origin "$NEXT"
printf 'pushed and tagged %s\n' "$NEXT"
