<img src="./ic_launcher.svg" width="48" alt="MatLog icon"> MatLog
=========
A log reader by crDroid Team, built with Material 3 Expressive.

MatLog shows a live, scrolling view of the Android logcat and kernel logs, helps you find what
matters in them, and saves, records or shares them when something goes wrong.

It ships as a system app (`com.crdroid.logcat`) and grew out of Daniel Ciao's [MatLog][1], which
was itself based on Nolan Lawson's [CatLog][2].

Features
---------
- **Live log** of the logcat buffers chosen in settings, with pause and resume
- **Search** that highlights or filters matches, with regex, `tag:` and `pid:` support and
  saved searches
- **Filters** that include or exclude lines by tag, package, text, log level or time range
- **Kernel log** from dmesg and **pstore** logs from the previous boot
- **Crash detection** for app crashes, ANRs and native crashes, with notifications and a
  history of crashes and exceptions
- **Recording** to a file in the background, controlled from the notification, the
  home-screen widget or the launcher shortcut
- **Saved logs** that you can open, search, rename, sort, share and export
- **Sharing** a log, a selection of lines or a single field, or exporting everything with
  device info as a zip
- **Privacy scrubber** that hides email addresses, phone numbers, URLs, IP addresses and
  account details

Building
---------
MatLog is built as part of crDroid with `Android.bp`. It is signed with the platform key so it
can read logs without asking, and it uses the `matlog_dmesg` helper in [dmesg](./dmesg) to read
the kernel log and pstore.

FAQ
---------
#### Where are saved logs kept?

On shared storage, under `/sdcard/matlog/saved_logs/`.

#### I can't see any logs!

Check the log buffers in MatLog's settings, and make sure logging hasn't been turned off in the
system settings. Reboot after changing either.

License
---------
Code carried over from MatLog and CatLog is licensed under the GNU General Public License v3, see
[LICENSE.txt](./LICENSE.txt). Files added for crDroid carry their own SPDX headers.

```
Copyright (C) 2017  Daniel Ciao

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
```

[1]: https://github.com/plusCubed/matlog
[2]: https://github.com/nolanlawson/Catlog
