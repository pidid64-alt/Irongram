# 🐾 Nekogram
[![Crowdin](https://badges.crowdin.net/e/a094217ac83905ae1625526d59bba8dc/localized.svg)](https://neko.crowdin.com/nekogram)  
Nekogram is a third-party Telegram client with not many but useful modifications.

- Website: https://nekogram.app
- Telegram channel: https://t.me/nekoupdates
- Downloads: https://nekogram.app/download
- Feedback: https://github.com/Nekogram/Nekogram/issues

## API, Protocol documentation

Telegram API manuals: https://core.telegram.org/api

MTProto protocol manuals: https://core.telegram.org/mtproto

## Compilation Guide

1. Download the Nekogram source code ( `git clone --recursive --shallow-submodules https://github.com/Nekogram/Nekogram.git` )
1. Fill out storeFile, storePassword, keyAlias, keyPassword in local.properties to access your release.keystore
1. Go to https://console.firebase.google.com/, create two android apps with application IDs tw.nekomimi.nekogram and tw.nekomimi.nekogram.beta, turn on firebase messaging and download `google-services.json`, which should be copied into `TMessagesProj` folder.
1. Open the project in the Studio (note that it should be opened, NOT imported).
1. Fill out values in `TMessagesProj/src/main/java/tw/nekomimi/nekogram/Extra.java` – there’s a link for each of the variables showing where and which data to obtain.
1. You are ready to compile Nekogram.

## Plugins

Irongram has a built-in plugin engine. It is a port of the idea behind
[exteraGram](https://github.com/exteraSquad/exteraGram) plugins: the same concepts (plugin archives
with metadata, per-plugin preferences, declarative settings, hooks and a safe mode), but plugins are
plain JVM bytecode that is loaded with a `DexClassLoader`, so no Python runtime or other external
dependency is required.

* A plugin is a `.plugin` archive that contains `plugin.json`, `classes.dex` and an optional `icon.png`.
* Installed plugins are managed in **Neko Settings -> Plugins**: enable/disable, reload, delete,
  safe mode and "install from file".
* Plugins run inside the app process and can use the client API directly, so install only plugins
  you trust.

Read [Plugins/README.md](Plugins/README.md) for the archive format and the API, and check
[Plugins/examples/hello-world](Plugins/examples/hello-world) for a complete, buildable example.

## Localization

Nekogram is forked from Telegram, thus most locales follows the translations of Telegram for Android, checkout https://translations.telegram.org/en/android/.

As for the Nekogram specialized strings, we use Crowdin to translate Nekogram. Join project at https://neko.crowdin.com/nekogram. Help us bring Nekogram to the world!
