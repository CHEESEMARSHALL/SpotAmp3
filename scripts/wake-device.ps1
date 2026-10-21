$powerState = ((adb shell dumpsys power 2>$null) -join ' ')
if ($powerState -match 'Display Power: state=OFF') {
  adb shell input keyevent 26 2>$null
}
adb shell input keyevent 224 2>$null
adb shell wm dismiss-keyguard 2>$null
