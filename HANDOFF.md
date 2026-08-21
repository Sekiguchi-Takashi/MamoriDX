# 南国ゲーム『蒼海の秘宝 ～七つの羅針盤～』HANDOFF

## 現在バージョン: v9.4（羅針盤の欠片7種＋完成形、遺跡の全時間帯）

## v9.4で追加した表示
- **日誌画面**: 欠片7種をカード＋アイコンで一覧表示。未取得はアルファ0.18の影絵。
  7つ揃うと最上部に `compass_complete` を大きく表示し「七つの欠片が繋がった」と告知
- **欠片発見時**: AlertDialogを `setView` に変更し、欠片の絵＋手がかり文を表示。
  7つ目を取ったときは完成形の絵に差し替わり、専用の一文が出る
- **エリア画面**「ここで見つけたもの」も欠片アイコン付きに
- **TRUE / LEGEND エンディング**: 専用絵が未配置なら `compass_complete` にフォールバック

## エリア背景の充足状況（v9.4）
**9エリアすべてで 昼／夜／雨／夕暮れ の4種が揃った（36枚）。** 欠品なし。

## 画像の総量（v9.4・drawable計約9MB）
エリア36 + 欠片8 + NPC7 + 実績8 + ui_title/ui_island = 61枚
**10MBが目安の上限。これ以上増やす場合は quality を 82→75 に落とすか、
エリアの夕暮れ版を間引くことを検討する。**

## 未配置（色板で自動代替）
アイテム11種（item_*）、エンディング4種（end_*）、guide_ai

## エリア背景の充足状況（v9.3・drawable計約7.7MB）
昼／夜／雨／夕暮れ の4種が揃っているエリア:
海岸・森・洞窟・灯台・火山・神殿・滝・桟橋（8エリア×4＝32枚）
**遺跡だけ昼の1枚のみ。** 遺跡は nightOnly（夜しか入れない）エリアなので、
実プレイでは常に夜の絵が必要になる。`area_ruins_night` の生成が最優先の欠品。

## 画像フォールバックの順序（`areaImageNames`）
1. 雨なら `_rain`（さらに夜なら `_night`）
2. 夜なら `_night`
3. 夜で `_night` が無ければ `_dusk`（昼の絵より夕暮れのほうが夜に近いため）
4. 通常名
5. どれも無ければ Art が色板を生成

## 未配置（色板または未使用）
羅針盤の欠片7種、アイテム11種、エンディング4種、guide_ai、area_ruins の夜・雨・夕暮れ

## v9.2で追加した機能
- **NPC**: 各エリアに住人がいる（GameData.npcs）。エリア画面に立ち絵＋挨拶を表示し、
  「話しかける」で言い伝えを聞ける。**行動回数を消費しない**。
  初回の会話でだけ「まだ見つけていない欠片があるエリア」を1つ教えてくれる（自分の居るエリアは除外）。
  配置: 桟橋=船乗り / 海岸=少女 / 遺跡=学者 / 洞窟=漁師 / 森=少年 / 灯台=灯台守 / 滝=オウム
  ※火山・神殿には住人なし
- **実績8種**（GameData.achievements、GameState.achievements に周回をまたいで保持）:
  first_step / all_areas / night_owl / all_compass / speedrun(20手以内) /
  solo(ヒント0) / team(住人7人と会話) / true_end
  `GameState.refreshAchievements()` を探索時・会話時・クリア時・実績画面表示時に呼ぶ
- 画面IDに SC_ACHIEVE=6 を追加。タイトルとエンディングから開ける
- マップ画面の上部に島の絵（ui_island）を表示

## 画像の配置状況（v9.2・計約3.2MB）
- エリア背景13枚（v9.1）: beach/forest/cave/lighthouse の 昼・夜・雨・夕暮れ
- NPC立ち絵7枚: **白背景を外周からのフラッドフィルで透過**し、アルファ境界で切り詰め済み
- 実績アイコン8枚、ui_title、ui_island
- 未配置（色板で自動代替）: area_volcano / area_temple / area_waterfall / area_pier / area_ruins、
  羅針盤の欠片7種、アイテム類、エンディング4種、guide_ai

**PNGのままだと肥大化する。長辺640(NPC)〜1024(背景)・WebP quality82〜85 で処理すること。**

## 画像の配置状況（v9.1）
配置済み（`app/src/main/res/drawable/*.webp`、長辺1024・quality82・計約2.5MB）:
- 海岸: area_beach / area_beach_night
- 森: area_forest（夕暮れ絵）/ area_forest_night / area_forest_rain
- 洞窟: area_cave / area_cave_night / area_cave_rain / area_cave_dusk
- 灯台: area_lighthouse / area_lighthouse_night / area_lighthouse_rain / area_lighthouse_dusk
未配置（色板で自動代替中）: volcano / temple / waterfall / pier / ruins

**PNGのままだと13枚で20MBに膨らむ。WebP(quality82)必須。**

## 表示仕様
- `imageBannerChain()` が候補名を順に試す: 雨→`_rain`、夜→`_night`、最後に通常名、それも無ければ色板
- ステッカー絵は正方形なので **ScaleType.FIT_CENTER・4:3枠**で表示（CENTER_CROPだと上下が切れる）
- `_dusk` は現状コードから参照していない予備（夕暮れ演出を入れる場合に使う）

## 天候（v9.1で追加・見た目のみ）
- `GameState.raining`。時間を送るたび30%で雨。**謎解きの条件には一切影響しない**
- ステータスバーに「☂ 雨」、エリア説明に一文追加、背景が`_rain`に差し替わる

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
