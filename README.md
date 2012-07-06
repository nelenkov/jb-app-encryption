Jelly Bean app encryption
=================

Sample app illustrating how to install encrypted apps on Jelly Bean.
Requires a rooted device or an emulator to run. 

Installation: 

1. Export a signed APK

2. Remount the system partition as r/w
```shell
  $ adb shell
  $ su
  # mount -o remount,rw /system
```shell

3. Push the APK to external storage and copy to /system/app:
```shell
  $ adb push jb-app-encryption.apk /mnt/sdcard
  $ adb shell
  $ su
  # cp /mnt/sdcard/jb-app-encryption.apk /system/app/
```

Accompanying blog post with more details at 

http://nelenkov.blogspot.jp/2012/07/using-app-encryption-in-jelly-bean.html


