#!/data/data/com.termux/files/usr/bin/bash
# Appathy標準デプロイスクリプト
# 使い方: bash deploy.sh "コミットメッセージ"
# 前提: git config --global github.token にPAT登録済み（初回1回のみ）
set -e

# 必ずスクリプト自身のあるフォルダで実行（ホームでgit initする事故を構造的に防止）
cd "$(dirname "$0")"
REPO=$(basename "$PWD")
MSG="${1:-update}"
USER_NAME="Sekiguchi-Takashi"

TOKEN=$(git config --global github.token || true)
if [ -z "$TOKEN" ]; then
  printf "NG: トークン未登録です。次を一度だけ実行してから再実行してください:\n"
  printf "git config --global github.token ghp_xxxxxxxx\n"
  exit 1
fi

# リポジトリ作成（作成済みなら422が返るのでそのまま続行）
CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: token $TOKEN" \
  -d "{\"name\":\"$REPO\",\"private\":true}" \
  https://api.github.com/user/repos)
case "$CODE" in
  201) printf "リポジトリ %s を新規作成しました\n" "$REPO" ;;
  422) printf "リポジトリ %s は作成済み（続行）\n" "$REPO" ;;
  401) printf "NG: トークンが無効です (HTTP 401)。再発行してください\n"; exit 1 ;;
  *)   printf "NG: リポジトリ作成失敗 (HTTP %s)\n" "$CODE"; exit 1 ;;
esac

if [ ! -d .git ]; then
  git init
fi
git branch -M main
git remote remove origin 2>/dev/null || true
git remote add origin "https://${USER_NAME}:${TOKEN}@github.com/${USER_NAME}/${REPO}.git"

git add -A
git commit -m "$MSG" || printf "変更なし（コミットはスキップ）\n"
git push -u origin main

printf "\n完了。ビルド確認: https://github.com/%s/%s/actions\n" "$USER_NAME" "$REPO"
