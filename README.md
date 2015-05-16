# adhoc-plist

[![Clojars Project](http://clojars.org/adhoc-plist/latest-version.svg)](http://clojars.org/adhoc-plist)

A Clojure library designed to create .plist with iOS .ipa package for OTA.

## Usage

```
(require '[adhoc-plist.core :refer :all])
(write-plist "source.ipa" "https://host/base/path/")
```

Also you can read Info.plist from iOS .ipa package file.

```
(read-info-plist "source.ipa")
```

## License

Copyright © 2015 Kazuo Koga

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
