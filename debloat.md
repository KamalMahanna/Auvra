# BitChord Debloat Ledger

This document tracks all features, subsystems, UI screens, network services, third-party integrations, and dependencies removed from BitChord to create a fast, streamlined, bloat-free YouTube Music player.

---

## 1. Remote Party & Social Features
### Listen Together (Jam / Shared Queues)
- **Deleted UI & Screens:**
  - `ListenTogetherScreen.kt`: Party host/join screen, invite code input, member management.
  - `ListenTogetherSheets.kt`: Party creation, profile customization, member sheets.
  - `PartyServerEditor.kt`: Custom party server endpoint configuration dialog.
  - `ListenTogetherMembersSheet.kt`: Active party member roster bottom sheet in player.
- **Deleted Networking & Models:**
  - `ListenTogether.kt` & `JamInviteLink.kt`: Party client socket, REST calls, deep link consumer.
  - `JamInvite.kt`, `PartyModels.kt`, `PartyQueueReading.kt`, `ServerClock.kt`: Shared KMP party data models and NTP clock sync.
- **Deleted Playback & Sync Engines:**
  - `PartySync.kt`: Multi-device clock reconciliation, pause/seek state broadcast, queue dragging batching.
  - `PartyPersonalQueueStash.kt`: Local queue stash/restore when entering or leaving a party.
- **Removed Code & Dependencies:**
  - Removed party synchronization hooks, party lock gates, transport deferrals, autoplay host election, and version-swap locks from `PlaybackService.kt`, `Autoplay.kt`, `CrossfadeController.kt`, `DirectSourceGate.kt`, `MusicOnlyPlayback.kt`, `TrackAnalyzer.kt`, and `QueueCoordinator.kt`.
  - Removed `LISTEN_TOGETHER_SERVER` buildConfigField and custom server configurations from `app/build.gradle.kts` and `tv/build.gradle.kts`.
  - Removed deep-link intent filters for `https://bitchord.kushagrasingh.in/invite/*` and `bitchord://jam/*` from Android manifests (`app` and `tv`).
  - Removed top-bar ping indicator, settings menu row, and output toggle buttons.
- **Deleted Unit Tests:**
  - `JamInviteLinkTest.kt`, `ListenTogetherIdleHealthTest.kt`, `PartyAutoplayTest.kt`, `PartyHostOnlyControlTest.kt`, `PartyPersonalQueueStashTest.kt`, `PartyQueueMoveTest.kt`, `JamInviteTest.kt`, `PartyQueueReadingTest.kt`, `ServerClockTest.kt`.

---

## 2. Audio Engine, DSP & Playback Processing
### Software Equalizer & Audio Pipeline
- **Removed Custom DSP Equalizer:**
  - `EqualizerProcessor.kt`: 10-band IIR biquad software audio filter deleted.
  - Removed `EqualizerProcessor` from ExoPlayer's `DspChain.kt` and `FloatAudioProcessor.kt`.
  - Equalizer actions redirect directly to the Android system equalizer (`AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL`).
  - Deleted unit tests in `EqualizerProcessorTest.kt`.
### Playback Controls & Settings Cleanup
- **Removed Output Precision:** Stripped custom output precision configuration (forced clean 320kbps playback).
- **Removed Bit-Perfect Output:** Removed direct USB DAC / bit-perfect bypass settings.
- **Removed Crossfade & Automix Options:** Removed redundant transition options from settings.
- **Removed Silence Skipping:** Removed silence detection and skipping options.
- **Removed Spatial Audio Options:** Cleaned out spatializer options from audio preferences.

---

## 3. Remote Storage & External Media Sources
### Network Shares & Remote Files
- **SMB (Windows File Shares):**
  - `SmbClient.kt`, `SmbConfig.kt`, `SmbConnection.kt`, `SmbCoverFetcher.kt`, `SmbRepository.kt`, `SmbDataSource.kt`.
  - Removed Java SMB client dependency (`com.hierynomus:smbj:0.15.0`) from `app/build.gradle.kts`.
  - Deleted `SmbTest.kt`.
- **WebDAV Remote Servers:**
  - `WebDavClient.kt`, `WebDavCoilAuth.kt`, `WebDavConfig.kt`, `WebDavRepository.kt`, `WebDavUploadNotifications.kt`, `WebDavUploads.kt`, `WebDavAuth.kt`.
  - Deleted `WebDavTest.kt`.
### Third-Party Music Services
- **JioSaavn:** `JioSaavnService.kt`, `JioSaavnSource.kt`.
- **Sources Management:** `SourcesScreen.kt`.

---

## 4. Third-Party Integrations & Presence
### Discord Rich Presence (RPC)
- **Deleted Discord Modules:**
  - `DiscordRPC.kt`, `DiscordAudioQuality.kt`, `SuperProperties.kt`.
  - `DiscordLoginScreen.kt`, `DiscordScreen.kt`, `ic_discord.xml`.
  - `DiscordApplication.kt`, `DiscordWebSocket.kt`, KizzyRPC gateway entities (`HeartBeat.kt`, `Identify.kt`, `Payload.kt`, `Ready.kt`, `Resume.kt`, `OpCode.kt`, `Presence.kt`, `Activity.kt`, `ArtworkCache.kt`, etc.).
  - Deleted `DiscordAudioQualityTest.kt`.
### Scrobbling Services
- **Last.fm & ListenBrainz:**
  - `LastFM.kt`, `ListenBrainzManager.kt`, `ScrobbleManager.kt`.
  - Removed `LASTFM_API_KEY` and `LASTFM_SECRET` build config fields from Gradle.

---

## 5. Spotify Sync & Secondary Accounts
### Spotify Library & Canvas Auth
- **Deleted Components:**
  - `SpotifyLibrary.kt`, `SpotifyLoginScreen.kt`, `SpotifyLibraryScreen.kt`, `SpotifyCanvasAuthScreen.kt`.
### Multi-Account & Session Management
- **Deleted Account Components:**
  - `AccountSessions.kt`, `AuthStore.kt`, `AccountChannelDialog.kt`, `AccountProfileSelector.kt`, `AccountAndScrobblingScreen.kt`.
  - Removed redundant login screens (`YtMusicLoginScreen.kt`, `TvSignIn.kt`).
  - Deleted `AccountChannelTest.kt`, `AccountSessionsTest.kt`, `AuthCookieTest.kt`.

---

## 6. Wear OS Companion (Completely Purged)
- **Deleted WearOS App Module:**
  - Completely deleted the entire `wearos/` project directory (watch UI, ambient mode, protocol, views, and watch playback service).
- **Deleted Wear Support in Phone & TV:**
  - Deleted `app/src/main/java/com/music/bitchord/wear/` package: `WatchInstaller.kt`, `WatchInstallTypes.kt`, `WatchOutputState.kt`, `WearBridge.kt`, and `WearCommandService.kt`.
  - Deleted `tv/src/main/java/com/music/bitchord/wear/` package (TV no-op stubs).
  - Deleted Wear capabilities descriptor `app/src/main/res/values/wear.xml` (`android_wear_capabilities`).
- **Removed Wear Dependencies & Proguard Rules:**
  - Removed `com.google.android.gms:play-services-wearable:20.0.1` and `androidx.wear:wear-remote-interactions:1.1.0` from `app/build.gradle.kts`.
  - Removed watch wireless ADB install pipeline dependencies: `com.github.MuntashirAkon:libadb-android:3.1.1`, `org.conscrypt:conscrypt-android:2.5.3`, and `org.bouncycastle:bcpkix-jdk18on:1.85`.
  - Removed watch protocol source set `../wearos/protocol` from `app/build.gradle.kts`.
  - Removed ProGuard rules for ADB/Conscrypt (`io.github.muntashirakon.adb`, `org.conscrypt`) in `app/proguard-rules.pro`.
  - Removed WearCommandService declaration and wear intent filters (`wear://*/bitchord/command` and `wear://*/bitchord/stream`) from `app/src/main/AndroidManifest.xml`.
  - Removed Wear OS APK artifact upload job from `.github/workflows/android.yml`.
- **Decoupled Playback & UI Subsystems:**
  - Removed `WearBridge` lifecycle publishing, remote watch speaker (`watchSpeaker`), streams attachment, and watch session foreground duration extension from `PlaybackService.kt`.
  - Removed `WearBridge` watch output state, watch volume forwarding, and output routing hooks from `AndroidPlayerHost.kt`.
  - Removed watch output active check from `DacVolumeHud.kt`.
  - Cleaned `WatchRow`, watch output collector, and strings (`watch_output`, `watch_output_subtitle`) from `AudioOutputSheet.kt` and `PlayerHost.kt`.
  - Removed unused `onWearOs` callback parameter from `SettingsSheet.kt`.

---

## 7. UI, Appearance & Content Debloating
### Settings Reductions
- **Performance Tab:** Removed performance settings page (High Performance mode set to always ON by default).
- **Language Tab:** Removed language category and app language picker from settings (app language is locked to English).
- **Cached Song Folder Toggle:** Removed "Show cached songs folder" from Storage settings and removed the cached songs folder from Library device links.
- **Miscellaneous Options:** Removed toggles from Miscellaneous settings for Stop Music on Close (always stops playback and kills service when swiped from recents), Volume Bar (always hidden), Status Bar in Player (always hidden), Song Status (always hidden), Stats for Nerds (disabled), and App Icon selector (fixed to Monochrome default launcher alias).
- **Footer Metadata:** Removed build metadata, GitHub links, and contributor / version text (`bitchord 1.9...`).
### Content Preferences Default Optimizations
- Always prefer music video version; video thumbnails kept standard (no squaring).
- Removed options that hid recents, home videos, top picks, or mood playlists.
- Local Music: Disabled checking and filtering for non-music audio.
### Visual & Appearance Simplification
- Theme fixed to system default; font set to system font.
- Animation and dynamic blur reductions disabled; blend effects enabled.
- Liquid glass enabled by default; progressive blur enabled by default.
- Fullscreen cover art enabled for players, playlists, and albums.
- Animated cover art enabled over cellular networks.
- Artist signatures removed.
- Synced lyrics enabled by default; blur disabled on unfocused lyrics (text stays sharp and readable); lyrics sources hidden; translation feature removed.
### Full Player UI
- **Removed Lyrics Translation & Romanization:** Deleted the on-demand lyrics translation engine (`LyricsTranslation.kt`), `TranslationLanguages.kt`, `TranslationLanguageDialog.kt`, settings preference (`translationLanguage`), and the floating translation/romanization toggle buttons (`TranslationToggleButton`, `RomanizationToggleButton`) from the lyrics view (`NowPlayingScreen.kt`, `LandscapePlayer.kt`, `PlayerSidePanels.kt`). Lyrics are always displayed directly in their original language/English as served by the lyrics provider, with no machine translation or secondary translated sub-lines.
- **Replaced Like Button with Download Button:** Replaced the like button (star glyph) next to track credits in both portrait (`NowPlayingScreen.kt`) and landscape (`LandscapePlayer.kt`) with a download button. When the track is downloaded, the active `DownloadDone` checkmark is shown; clicking triggers a toast indicating it is saved. When not downloaded, `BitChordIcons.Download` is displayed, and clicking queues the track for download.
- **Removed Lyrics, Cast, and Queue Buttons:** Removed the bottom action buttons (Lyrics button, Cast / Audio Output button with device caption, and Queue button) from the full player screen (`NowPlayingScreen.kt` and `PlayerControls.kt`). Playback modes (shuffle, repeat, autoplay) remain accessible within the queue view. Tapping the single-line lyrics preview strip directly above the progress bar expands the full lyrics panel.
- **Removed Triple-Dot Menu & Added Song Info Long-Press:** Removed the 3-dot (⋯) overflow menu button from both portrait (`NowPlayingScreen.kt`) and landscape (`LandscapePlayer.kt`) full player UI, as all former actions (Share, Upgrade Quality, Convert to Video/Audio, Start Radio, Play Next, Add to Queue, Save to Downloads, Remove from Cache, Open Album, Open Artist, Sleep Timer, Lyrics Offset, Copy Log, ratings, and playlist actions) were eliminated or consolidated into direct player controls (download toggle next to track credits). Added long-press gesture on the song title in the full player UI (`NowPlayingScreen.kt` and `LandscapePlayer.kt`) to trigger the Song Info popup dialog. Updated Song Info UI (`SongInfoDialog.kt`) to adopt the themed iOS-style menu panel styling (`IosMenuColors` & `IosMenuShape`) and liquid glass / frosted backdrop sampling (`playerBackdrop` / `livePlayerBackdrop`) with spring motion, additive chrome blending, and scrim overlay. Removed the in-sheet player `IosMenuPopover` from `MainActivity.kt`.
- **Omitted Progress Bar & Transport In Queue View:** When swiping up into the queue view in the player (`NowPlayingScreen.kt`), the music progress bar (`PlayerScrubber`), lyric strip, and play/pause transport row (`TransportRow`) smoothly collapse, translate, and fade out, preserving only the shuffle, loop (repeat), and autoplay pill section docked at the foot of the queue. Closing from the queue to the full player UI smoothly expands the deck height, translates, and fades the progress bar and transport controls back into place without abrupt popping or stutter.
- **Queue UI Simplification:** Removed the redundant "Now playing" header and current track row from the queue list (`PlayerQueue.kt`), since the active song is already displayed in the player header above. Removed the "Next from: ..." header (`header-context`) so upcoming queue tracks render directly under the queue without section titles. Increased vertical spacing after the "Queue" title header from 4.dp to 16.dp for cleaner separation.
- **Removed "Lyrics by ..." Provider Attribution:** Removed the "Lyrics by <Provider>" credit status text from the lyrics view (`PlayerLyrics.kt`). When lyrics are present and displaying, the provider attribution label is suppressed, rendering a clean, uncluttered lyric view without source branding.
### Library Screen
- **Removed Local Music from Library:** Removed the "Local Music" device folder entry (`"local:all"`) from the phone Library screen folder links (`LibraryPhoneParts.kt`) and the "On This Device" entry from the TV Library screen (`TvLibraryScreen.kt`). The Library links section now cleanly focuses on "Downloads" without prompting or listing on-device audio scans.
### Permissions & System Access
- **Removed Music & Storage Permissions (`READ_MEDIA_AUDIO` & `READ_EXTERNAL_STORAGE`):** Stripped music and storage read permissions from `app/src/main/AndroidManifest.xml` and `tv/src/main/AndroidManifest.xml`. Removed runtime storage permission dialog launcher (`mediaPermissionLauncher`) and checks from `MainActivity.kt`, `LocalMediaRepository.kt`, and `TvDetailScreen.kt`. Re-targeted Search → Library to search offline downloads (`Downloads.getDownloadedSongs`) instead of querying local files on the device, eliminating all storage permission popups.
- **Removed Notification Permission (`POST_NOTIFICATIONS`):** Removed `android.permission.POST_NOTIFICATIONS` declaration from Android manifests (`app`, `tv`, and `wearos`). Removed runtime notification permission prompt and launcher (`notifyPermission`) from `MainActivity.kt` and `WearActivity.kt`. MediaStyle playback notifications continue to be managed automatically by the Android system without runtime prompts.
### Google Account & Authentication
- **Removed Google Account Sign-In Prompts:** Removed the "Sign in to YouTube Music" / "Sign in with Google" mesh banner from the Home screen (`HomeScreen.kt`). Removed the "Sign in to your Google account" description and action button from the Library screen (`LibraryScreen.kt`). Removed the "Sign in to see what you have been listening to" requirement from the History screen (`MainViewModel.kt`). The application operates smoothly in guest mode without sign-in prompts.
- **Removed Syncing Personal YouTube Music Cloud Playlists:** Disabled remote cloud playlist querying and mutations (`userPlaylists`, `libraryPlaylists`, `createPlaylist`, `addToPlaylist`, `removeFromPlaylist`, `reorderPlaylist`, `renamePlaylist`, `deletePlaylist`, `setSaved`) in `Innertube.kt` and `YtMusicRepository.kt`. Removed cloud playlist fetching from `MainViewModel.loadPlaylists()` and Android Auto browse tree (`PlaybackService.fetchPlaylistCards`). Removed the empty cloud "Playlists" shelf and "New playlist (Saved to YouTube Music)" card from the guest Library screen (`LibraryScreen.kt`).
- **Removed Liking Songs to Remote Account:** Disabled remote rating network calls (`Innertube.rate`, `Innertube.ratePlaylist`, `YtMusicRepository.rate`, `YtMusicRepository.setSaved`). Removed background multi-page Liked Music continuation sync (`syncLikedMusic`) from `YtMusicRepository.kt` and `MainViewModel.kt`. Constrained notification favorite button (`canLike`) in `PlaybackService.kt` to local/downloaded files only (`OfflinePlaylists`), removing remote rating dispatch (`toggleFavoriteFromNotification`). Simplified `MainViewModel.likeStatuses` to rely directly on in-session/local state (`LikeState.overrides`).
### Language & Localization
- **App Language Fixed to English:** Enforced application locale to always default to and initialize as English (`"en"`). Removed fallback to host device system locale across the application UI, lyrics translation dialog, and Innertube / YouTube Music API requests (`hl=en`).
### Codebase Cleanup & Dead Feature Removal
- **Purged Dead Dislike System:** Completely removed `SongActionsMenu` dislike quick action tile, `onToggleDislike` callback, `MainViewModel.toggleDislike`, `MainActivity.shouldSkipAfterDislike`, `DislikeSkipPolicyTest`, and deleted the unused `BrokenHeart` icon vector (`BrokenHeart.kt`).
- **Removed Dead Account & Channel Routing Remnants:** Removed unused multi-channel and profile routing state and methods from `MainViewModel` (`loadChannels`, `selectChannel`, `stepProfile`, `_channels`, `_selectedChannelKey`, `_selectedChannelName`, `_activeAccountId`, `_activeProfileId`, `loadAccount`, `signOut`). Removed unused parameters (`signedIn`, `account`, `onSignIn`, `onSignOut`) from `SettingsScreen` (`SettingsSheet.kt`) and `MainActivity.kt`.
- **Removed Unused Cloud Playlist Ownership System:** Removed `resolvePlaylistOwnership`, `editablePlaylist`, `_playlistOwned`, and `ownershipInFlight` from `MainViewModel` and `MainActivity`, eliminating unnecessary playlist ownership network crawls and checks for removed cloud playlists.
- **Removed Remote Song Menu State:** Removed `loadSongMenu`, `_songMenu`, `songMenu`, `songMenuJob`, and `forgetFromLibrary` from `MainViewModel`, eliminating unused watch queue menu fetches.
- **Cleaned Up HomeScreen & Common UI:** Deleted obsolete `ServiceCard` helper and dead branches (`"local:all"`, `"local:cache"`, `"local:webdav"`, `"local:smb"`) from `HomeScreen.kt`. Deleted unused `SignInBanner` composable from `Common.kt` and removed its asset `ic_google_g.xml`. Removed unused `onSignIn` parameter from `HomeScreen.kt` and `LibraryScreen.kt`.
- **Cleaned Up TV Module:** Deleted obsolete 366-line `TvEqualizerDialog.kt` which targeted removed software equalizer settings. Removed `SettingsSheet.Equalizer` and sound row from `TvSettingsScreen.kt`. Removed `SongSheet.Playlist` and `SongSheet.Equalizer` from `TvSongActionsDialog.kt`, including deleting the 84-line `TvAddToPlaylistDialog` cloud playlist dialog. Removed dead `activeProfileId` and `account` observations from `TvApp.kt` and `TvSettingsScreen.kt`.
- **Cleaned Up Equalizer Presets Route:** Removed dummy `EqualizerPresetPage` stub from `EqualizerScreen.kt`, removed `onOpenPresets` parameter, and removed `AppRoute.EqualizerPresets` from `AppNavigator.kt` and `MainActivity.kt`.
- **Cleaned Up Unused Imports:** Removed unused `ConfirmationAlert` and `ChoiceAlert` imports from `MainActivity.kt`, and cleaned over 100 unused imports across `MainActivity.kt`, `PlaybackService.kt`, `MainViewModel.kt`, `SettingsSheet.kt`, `TvSettingsScreen.kt`, and `TvSongActionsDialog.kt`.
- **Updated Repository & Update Links to Auvra:** Retargeted GitHub repository URLs and in-app release update endpoint in `AppUpdateChecker.kt` (`LATEST_RELEASE_URL`), `BitChordApplication.kt`, `README.md`, and backend/web scripts to `KamalMahanna/Auvra`.
