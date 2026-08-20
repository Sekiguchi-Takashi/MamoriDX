# 南国ゲーム『蒼海の秘宝 ～七つの羅針盤～』HANDOFF

## 現在バージョン: v9.0
リポジトリは `MamoriDX` のまま。**旧「守りのDX」を完全に置き換えた**（applicationIdも
`com.appathy.mamoridx` のままなので、アプリストア上で上書き更新として現れる）。
旧セキュリティ機能17ファイルは全削除済み（機能はSecHQAppへ移設済み）。

## アプリ概要
リアル脱出ゲーム。島の9エリアを探索して羅針盤の欠片を7つ集め、
各欠片が語る手がかりを**組み合わせて**宝の在り処を論理的に絞り込む。

## ファイル構成
```
app/src/main/java/com/appathy/mamoridx/
├── MainActivity.kt … 全画面UI（タイトル/マップ/探索/日誌/推理/エンディング）
├── GameData.kt     … 9エリア定義＋属性、欠片7種、アイテム8種、手がかり文言、エンディング
├── Puzzle.kt       … 謎の自動生成と検証、段階ヒント
├── GameState.kt    … 進行状況の保存（SharedPreferences "nangoku_save"）
└── Art.kt          … 画像ローダー＋プレースホルダ生成
app/src/main/res/mipmap-*/ic_launcher.png … 南の島ステッカーアイコン（5密度）
```

## 謎解きの設計（中核）
- 宝は9エリアのいずれかにランダム配置。`seed` で再現可能
- 各エリアは6つの属性を持つ: seaside / highGround / manMade / hasWater / nightOnly / dayOnly
- 欠片7つがそれぞれ1つの手がかり（属性の断定 or エリアの名指し除外）を持つ
- **生成時に「7つ揃えたとき答えが一意に定まるか」を必ず検証**してから採用。
  失敗時は名指し除外7つのフォールバックに落ちる（Python再現で3000シード全て一意解を確認済み）
- 手がかりに嘘は含まれない（正解エリアの実際の属性のみを述べる）

## 画像について
- `Art.get()` が `res/drawable/<名前>` を探し、**無ければ色板を自動生成**する。
  よって画像が1枚も無い状態でもアプリは完全に動作する
- 画像を置くだけで自動的に差し替わる。命名は `南国ゲーム_画像プロンプト集.md` に準拠
  - エリア: `area_beach` `area_forest` `area_cave` `area_lighthouse` `area_volcano`
    `area_temple` `area_waterfall` `area_pier` `area_ruins`
  - 夜版: 上記＋`_night`（例 `area_beach_night`）
  - タイトル: `ui_title` / エンディング: `end_true` `end_treasure` `end_legend` `end_timeover`
  - 欠片: `compass_1_sun` 〜 `compass_7_shell` / アイテム: `item_*`

## 未実装（企画書にあるが今回は入れていない）
- 協力モード(2〜20人)・対戦モード … 通信基盤が必要。単独プレイを先に完成させる方針
- AR / GPS / QRコード探索 … 実地開催時に追加
- 天候（雨）・潮の満ち引き・星座イベント … 昼夜は実装済み。拡張の土台はある
- NPC会話生成・リプレイ分析 … BonsaiApp連携で実現できる可能性あり
- エンディングは4種（TRUE/TREASURE/LEGEND/TIMEOVER）。BETRAYAL/SECRETは協力・対戦実装後

## 納品規約（恒久・変更なし）
1. deploy.sh は push → pull --rebase → タグ発行まで1コマンド。
   次タグは `git tag --list 'v*' | sort -V | tail -1` から算出しローカル発行（API禁止）。
   第2引数 `notag` で push のみ
2. build.yml は作らない・同梱しない。CIは release.yml（タグ起動）のみ
3. `ci/` と `.github/workflows/release.yml` は削除・追跡解除しない
4. ファイルを削除する納品では deploy.sh に `rm -f` を足す
   → **v9.0では旧17ファイル分の rm -f を記載済み**
5. 納品はバージョン番号付きZIP＋実行4行ブロック。冒頭に【本番】か【テスト】を明示
