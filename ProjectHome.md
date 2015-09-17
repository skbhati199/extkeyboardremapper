# What it does #
When connected to an android device, external (USB/BT) keyboards default to qwerty mapping.
This InputMethodService allows non root users to define custom keymappings and glyph mappings to enable some missing keys.

# Maturity #
This is considered a proof-of-concept of key remapping / localization for external keyboards. It is currently useable but very rough around the edges.

# Installation #
A keyboard is a special kind of software in android. After installation of the apk you **must**:
  1. "Enable it's enability" in the Parameters/Language & Keyboard panel (There's a security concern when using _any_ android keyboard applications)
  1. Enable it by long-press on any text entry input and selecting it

# Key remapping #
To enable key remapping you write you own key substitution (text) configuration files then restart the keyboard.

# Disclaimer #
The source code is based on a modification of the SoftKeyboard sample in sdk [r8](https://code.google.com/p/extkeyboardremapper/source/detail?r=8)
This is provided as-is. Any contribution are welcome.

# Additional help #
You might consider the [ugly translation of my original french post](http://translate.google.fr/translate?sl=fr&tl=en&js=n&prev=_t&hl=fr&ie=UTF-8&layout=2&eotf=1&u=http%3A%2F%2Fwww.archoslounge.net%2Fforum%2Fshowthread.php%3Ft%3D20319&act=url) useful.

[french version](http://www.archoslounge.net/forum/showthread.php?t=20319)