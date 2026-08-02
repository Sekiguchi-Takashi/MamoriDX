# MamoriDX（守りのDX 2.0）HANDOFF

## 現在バージョン: v8.0（タブを5大分類に再編＋バッテリー劣化度＋フォルダ集計）

## タブ構成（v8.0）★重要：大分類5＋サブタブ方式
```
状態(0)  : 端末情報(0) / バッテリー(1) / フォルダ集計(2)
通信(1)  : 通信ログ(0) / SaaS接続(1・ToolsActivity起動) / ルーター(2・ToolsActivity起動)
診断(2)  : アプリ棚卸し(0) / 端末診断(1) / 緊急対応(2) / その他(3)
PC(3)    : サブタブなし
説明書(4): サブタブなし（内部に9ページの切替）
```
- サブタブ状態は statusSub / commsSub / diagSub。`showTab()`は大分類、`showSub()`は小分類、実描画は`renderContent()`
- **画面内の自己再描画はすべて `renderContent()` を呼ぶこと**（`showTab(n)`で番号直書きしない。過去にタブ追加のたび番号ズレ事故が発生したため、この方式に統一した）
- 説明書内ページ: 概要(0)/状態(1)/電池・集計(2)/通信(3)/棚卸し(4)/端末診断(5)/緊急(6)/PC(7)/その他(8)。5+4＋ダミーの2段
- `buildToolsMenuView()`は現在未使用（将来の再利用のため残置）

## v8.0 新機能
- `BatteryHealth`(object): 設計容量を①android内部リソース`config_batteryCapacity`②リフレクションで`com.android.internal.os.PowerProfile.getBatteryCapacity()`の順に取得。現在容量は BATTERY_PROPERTY_CHARGE_COUNTER(µAh) ÷ 残量% で満充電容量を推定。健康度=現在/設計、劣化度=100-健康度。**取得不可の端末が一定数あるため available フラグで分岐し、その場合はOS報告値(EXTRA_HEALTH/温度/電圧)のみ表示**。API34+はCYCLE_COUNTも試行
- `FolderDigest`(object): SAFで選んだフォルダを幅優先集計（最大4000ファイル）。カテゴリ別個数（文書/画像/動画/音声/圧縮/その他/拡張子なし/危険）＋危険拡張子の一覧。SharedPreferencesに対象フォルダと最大30回分のスナップショット履歴を保存し、前回との差分（総数増減・危険増加・カテゴリ別増減）を表示。同一URIを選ぶと既存記録に追記される
- REQ_DIGEST_TREE=3002 で MainActivity の onActivityResult が処理（VPN_REQUESTと分岐）

## タブ構成（v7.0・2段×4）
1段目: 状態(0) / 棚卸し(1) / 端末診断(2) / 通信ログ(3)
2段目: 緊急対応(4) / パソコン(5) / ツール(6) / 説明書(7)
説明書内ページ（2段・9項目、5+4＋ダミー）:
概要(0)/状態(1)/棚卸し(2)/診断(3)/通信(4)/緊急(5)/パソコン(6)/ツール(7)/関所(8)
※タブ追加時は各機能内の self-refresh showTab(n) を必ず追従修正すること（過去にズレ事故あり）

## 状態タブ実装メモ（`DeviceStatus`）
- ストレージ: Environment.getDataDirectory()のStatFsで内部、getExternalFilesDirs()の[1]以降で着脱可能領域。合算して使用率バー表示（90%以上赤/75%以上黄）
- 外部デバイス: UsbManager.deviceList（productName/manufacturerName/vendorId/productId、deviceClass=0ならinterface[0]のクラスで種別判定）＋マウント済み外部ストレージ
- バージョン: Build.VERSION.RELEASE/SDK_INT/SECURITY_PATCH、パッチ経過日数を算出。**更新有無の直接取得はOS仕様で不可**なため、古さ判定＋ACTION SYSTEM_UPDATE_SETTINGS→DEVICE_INFO_SETTINGS→ACTION_SETTINGSの順にフォールバックして案内
- 起動中アプリ: runningAppProcesses＋getRunningServices(200)。**Android 5.0以降の制限で他アプリはほぼ取得不可**。取得数<=2なら制限の説明noteを返し、設定画面への導線を出す
- Wi-Fi: WifiManager.connectionInfo。位置情報権限がないとSSIDが`<unknown ssid>`になるため、権限要求ボタンを表示。API31+はcurrentSecurityTypeで暗号方式も表示
- Bluetooth: BluetoothManager.adapter。API31+はBLUETOOTH_CONNECT権限必須（未許可なら要求ボタン）。getConnectedDevices(GATT/GATT_SERVER)＋bondedDevices。**A2DP等のプロファイルはgetProfileProxy非同期が必要なため接続中判定に出ないことがある**旨をnoteとUIに明記
- 権限要求: REQ_STATUS_PERM=3001でACCESS_FINE_LOCATIONとBLUETOOTH_CONNECTをまとめて要求、結果でshowTab(0)再描画
- Manifest追加: BLUETOOTH_CONNECT / BLUETOOTH(maxSdkVersion=30)

## タブ構成（v6.0・2段表示）
1段目: 棚卸し(0) / 端末診断(1) / 通信ログ(2) / 緊急対応(3)
2段目: パソコン(4) / ツール(5) / 説明書(6) ＋幅揃え用ダミーView
説明書内ページ（2段・8項目）: 概要(0)/棚卸し(1)/診断(2)/通信(3)/緊急(4)/パソコン(5)/ツール(6)/関所(7)

## パソコンタブ実装メモ
- `PcAdvisor`(object): チェック項目を全39件保持。purpose= P_COMMON(-1)/P_OUTDOOR(0)/P_CONFIDENTIAL(1)/P_SHARED(2)。共通10＋屋外9＋機密10＋シェア10
- 各Itemは id/purpose/title/why/weight/adviceWin/adviceMac を持ち、OS選択に応じて対処手順を切替（BitLocker⇔FileVault等）
- 重みづけ: W_MUST=3 / W_IMPORTANT=2 / W_RECOMMEND=1。percent = 達成重み/満点
- 評価: musts>=3→D、musts>=1→C、importants空→A、percent>=80→B、他→C
  ※「必須が1つでも未対応ならA不可」「必須+重要を全部満たせばA（推奨の残りは許容）」という整合を取っている
- 提言は musts/importants/recommends の3ブロックで、各項目に「なぜ必要か」＋「対処（OS別）」を併記
- 状態は SharedPreferences("mamoridx_pc") に os / purposes(csv) / checked(csv) を保存。CheckBoxのonCheckedChangeで即保存（画面再構築せずスクロール位置を保つ）
- 評価結果はチェックリストより上に描画（押下後の再構築で結果がすぐ見えるようにするため）
- 限界を説明書に明記: 自己申告ベースであり、スマホからPCの実設定値は読めない

## タブ構成（v5.0）
棚卸し(0) / 端末診断(1) / 通信ログ(2) / 緊急対応(3) / ツール(4) / 説明書(5)
説明書内ページ: 概要(0)/棚卸し(1)/診断(2)/通信(3)/緊急(4)/ツール(5)/関所(6)
ツールタブはメニューのみ。各機能は `ToolsActivity` を EXTRA_PAGE(0-3) で起動

## Phase 5〜8 実装メモ
- `MediaScanner`(object): SAF(ACTION_OPEN_DOCUMENT_TREE→takePersistableUriPermission)で選択したフォルダをDocumentsContractで幅優先走査(最大1500ファイル)。検出=実行形式/ショートカット/マクロ付Office/二重拡張子/RTL制御文字(U+202A-E,200E,200F,061C)によるファイル名偽装/autorun.inf/長大名/大量空白/マジックバイト不一致(MZ,ELF,%PDF,JPEG,PNG,PK,OLE)/サイズ0。ウイルス定義は持たない
- `SaasGuard`(object): SharedPreferences(JSON)にSite一覧。probe()はHttpURLConnection(instanceFollowRedirects=false)で最大8ホップ手動追跡し、HttpsURLConnection.serverCertificates[0]のSHA-256をFP化。registerBaseline()で安全な回線の基準を保存、verify()で最終ホスト不一致→危険/FP不一致→危険(正規更新の可能性も併記)/http混在→注意/接続失敗→注意(captive portal示唆)
- `RouterCheck`(object): WifiManager.connectionInfo(SSID/BSSID)、API31+はWifiInfo.currentSecurityType、未満はscanResults.capabilitiesで暗号方式判定(WPS検出含む)。dhcpInfoからgateway/dns。gatewayへ14ポートを並列Socket接続(500msタイムアウト、join 2500ms)。21/23/139/445/1723/5555は危険判定。DNSが外部の非パブリックリゾルバなら乗っ取り疑い。A/B/C評価
- `AssetLedger`(object): 機器台帳。checkUrlをGET(最大80万字)→タグ除去→正規表現(既定 DEFAULT_PATTERN)で最大バージョンを抽出＋本文SHA-256。前回値と比較しchangedフラグ。changed時はカード背景を#3A1E1Eに変更。acknowledge()で台帳を最新値に同期
- `ToolsActivity`: 4ページ切替。SAFはREQ_TREE=2001、位置情報許可はREQ_LOCATION=2002。ネットワーク/走査は全てThread+runOnUiThread

## 権限追加（v5.0）
INTERNET / ACCESS_NETWORK_STATE（Phase6,8）、ACCESS_WIFI_STATE / ACCESS_FINE_LOCATION（Phase7・実行時要求）
→ 説明書「概要」の通信説明を更新済み（外部通信は利用者が登録したURLのみ、開発者へは無送信）

## 未実装（意図的に見送り）
- Phase 9: 定時通知（現在地＋接続SSID、Pebble向け）。バックグラウンド位置情報権限と電池影響が大きいため保留
- Phase 2.5: DNSブロックの実効化とパケット実転送

## Phase 4 実装メモ（悪意あるリンク対策）
- タブ構成が5つに: 棚卸し / 端末診断 / 通信ログ / 緊急対応(index3) / 説明書(index4)。説明書内ページは6つ(概要/棚卸し/診断/通信/緊急/関所)
- `UrlChecker`(object): 完全オフラインのフィッシングURL判定。@偽装/Punycode/非ASCIIホスト/IP直打ち/短縮URL/危険TLD/多階層サブドメイン/長大ホスト/ブランド詐称/ハイフン乱用/認証誘導パス/過剰エンコード/APK直リンクの13項目。ブランド照合は誤検知回避のため「.-_」で分割したトークン単位（"online"に"line"が一致する事故を防止）。登録可能ドメインはjp二次レベル(co/ne/or等)を考慮して近似算出。ShareGateActivityからも呼ばれ、共有テキスト内のURLを自動検査
- `ThreatScanner`(object): 侵害チェック6分類。①firstInstallTimeで直近24h/7dの新規アプリ ②Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES ③"enabled_notification_listeners" ④DevicePolicyManager.activeAdmins ⑤REQUEST_INSTALL_PACKAGES付与済みアプリ ⑥既定SMSアプリ(Telephony.Sms.getDefaultSmsPackage)と既定ブラウザの乗っ取り。結果はseverity降順
- 対処動線: ACTION_DELETE(アンインストール) / ACTION_APPLICATION_DETAILS_SETTINGS / 各種Settings画面へのIntent。全てtry-catchで囲みopenSafely()経由（機種により存在しない画面があるため）
- 応急処置チェックリスト10手順（通信遮断→アプリ削除→ユーザー補助確認→提供元不明取消→パスワード変更→二段階認証→カード停止→Cookie削除→OS更新→会社報告）
- 説明書「緊急」ページに「できないこと（正直な限界）」を明記：盗まれた情報は取り戻せない、高度なマルウェアは検出不可、危険信号なし≠安全

## v3.1の内容
- アイコン: res/mipmap-*/ic_launcher.png（DFDX2.0デザイン、PNG5密度、PILで生成）
- 説明書タブ: ページ切替式。各ページに「これは何？→使い方手順（手順1から）→見方→FAQ」構成
- アイコン: res/mipmap-*/ic_launcher.png（DFDX2.0デザイン、PNG5密度、PILで生成）
- 説明書タブ: 概要/棚卸し/診断/通信/関所 の5ページ切替式。各ページに「これは何？→使い方手順（手順1から）→見方→FAQ」構成

## Phase 3 実装メモ
- `ShareGateActivity`: ACTION_SEND (text/plain, image/*) を受ける関所。exported=true、label「守りのDX関所」
- テキスト検査: 全角→半角正規化後、数字列（ハイフン/スペース区切り許容）を抽出し、12桁=マイナンバー（JISチェックデジット検証、テスト値123456789018で確認済）、14-16桁=クレカ（Luhn）。機密キーワード（社外秘/部外秘/極秘/マル秘/機密/取扱注意/confidential等）
- 画像検査: ExifInterface(InputStream)でTAG_GPS_LATITUDEの有無を確認
- 検出時アクション: マスクして転送（末尾4桁以外を*化）/ そのまま転送 / 中止。SNSアプリ（X/Instagram/Facebook/LINE/TikTok）導入端末では追加警告
- 転送はcreateChooser + EXTRA_EXCLUDE_COMPONENTSで自分自身を除外（無限ループ防止）
- 検査は全て端末内完結・外部送信なし

## デプロイ規約（v1.1から・Appathy標準）
- 各ZIPのプロジェクト直下に `deploy.sh` を同梱する
- ユーザーの実行手順は毎回固定の4行のみ（cd ~ / cp / unzip -o / bash deploy.sh "メッセージ"）
- **対話入力（read等）を含むコマンドブロックは禁止**（ブロック一括貼り付けとreadは両立しないため）
- トークンは `git config --global github.token` に一度だけ登録し、deploy.shが読み出す
- deploy.shは冪等：リポジトリ作成済み(422)・init済み・remote設定済み・変更なし、いずれも安全に続行

## コンセプト
シャドーIT対策の発想転換：「見つけて即削除」→「可視化 → 許可 → ガードレール設置」。
有益なSaaSは使い続けられるようにし、その代わり漏洩しない仕組みを置く。

## Phase 構成
- **Phase 1（実装済）**: 可視化。アプリ棚卸し（3分類）＋端末診断（6項目A/B/C判定）＋アプリ内説明書
- **Phase 2（本バージョン・A案）**: VpnServiceによるDNS可視化。「通信ログ」タブを追加。記録専用モード（パケットは転送せずDNSクエリ名のみ抽出、VPN有効中は通信が流れない）。ドメイン単位で「許可/記録のみ/ブロック」ポリシーをSharedPreferencesに保存。ブロックは現状ポリシー記録のみ（実遮断なし）
- **Phase 2.5（未着手）**: ブロックの実効化＋パケット実転送（B案・常時ON対応）。TCP/UDP転送を自前実装
- **Phase 3（本バージョンで実装済）**: 共有インテントに割り込む関所（DLP）。マイナンバー（チェックデジット検証）、クレカ番号（Luhn）、「社外秘」等キーワード検査。SNS宛のみ警告強化

## Phase 2 実装メモ（A案）
- `DnsMonitorService`(VpnService): Builder で addRoute("0.0.0.0",0)+setBlocking(true)。establish後、入力FDをreadしIPv4/UDP/dport53のパケットのみDNSパース。QNAMEをラベル長方式で抽出しDnsLogStore.recordへ。**パケットは書き戻さない=記録専用（A案）**。自パッケージはaddDisallowedApplicationで除外
- `DnsLogStore`(object): domain->Entry(count) をLinkedHashMapで保持しSharedPreferences(JSON)に永続化。最大500件でFIFO。ポリシーは domain->Int(0許可/1記録のみ/2ブロック)
- MainActivityは VpnService.prepare→startActivityForResult(VPN_REQUEST=1001)→RESULT_OKでstartForegroundService。onActivityResultで開始
- Manifest追加: FOREGROUND_SERVICE / POST_NOTIFICATIONS / FOREGROUND_SERVICE_SPECIAL_USE、service(BIND_VPN_SERVICE, foregroundServiceType=specialUse, PROPERTY_SPECIAL_USE_FGS_SUBTYPE)
- タブは4つに（アプリ棚卸し/端末診断/通信ログ/説明書）。tabName index: 説明書は3、通信ログは2

## ビルドスタック（変更禁止・Appathy標準）
- AGP 8.5.2 / Kotlin 1.9.24 / Gradle 8.9（GitHub Actionsで直接インストール、wrapperなし）
- 外部依存ゼロ / プログラマティックUIのみ（XMLレイアウトなし）
- debug.keystore をリポジトリにコミット（storepass/keypass: android, alias: androiddebugkey）
- minSdk 26 / targetSdk 34 / compileSdk 34

## ファイル構成
```
MamoriDX/
├── .github/workflows/build.yml   … Gradle 8.9 DL → assembleDebug → artifact
├── build.gradle.kts              … AGP/Kotlin バージョンピン
├── settings.gradle.kts
├── debug.keystore
├── app/build.gradle.kts          … 署名設定・依存ゼロ
└── app/src/main/
    ├── AndroidManifest.xml       … QUERY_ALL_PACKAGES + VpnService登録 + FGS権限
    └── java/com/appathy/mamoridx/
        ├── MainActivity.kt       … UI全体（4タブ）＋VPN制御
        ├── DnsMonitorService.kt  … 記録専用VpnService（DNSパース）
        └── DnsLogStore.kt        … ログ/ポリシー永続化（object）
```

## 実装メモ
- リスクスコア: 付与済み危険権限 1つ=1点、ストア外入手=+3点
- 分類: 0-2点=許可済み / 3-5点=要監視 / 6点以上またはストア外=要対策
- インストール元: API 30+ は getInstallSourceInfo、それ未満は getInstallerPackageName
- INTERNET権限なし＝外部送信ゼロ（説明書タブで信頼性アピールに使用）
- Play配布は不可の前提（QUERY_ALL_PACKAGESの審査が厳しい）。Appathyテスター向け直接配布

## ⚠️ 頻出の落とし穴
- **`git init` はホームディレクトリではなく必ず `~/MamoriDX` 内で実行すること。**
  ホームで実行するとトークン等がステージされ Push Protection (GH013) で弾かれる事故が過去に複数回発生。
- QUERY_ALL_PACKAGES を消すと棚卸しがほぼ空になる（Android 11+）

## 次にやる場合の候補
1. 棚卸し結果のCSVエクスポート（共有インテント）
2. システムアプリ表示トグル
3. Phase 2 着手時は新チャットで本ファイル＋Phase 2要件を貼って開始
