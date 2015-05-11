(ns adhoc-plist.core
  (:require [clojure.java.io :as io]
            [clojure.string :refer [join replace-first]])
  (:import [java.util.zip ZipFile ZipEntry]
           [com.dd.plist PropertyListParser NSDictionary]))

(def ^:private plist-template
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">
<plist version=\"1.0\">
  <dict>
    <key>items</key>
    <array>
      <dict>
        <key>metadata</key>
        <dict>
          <key>kind</key><string>software</string>
          <key>bundle-identifier</key><string>%s</string>
          <key>bundle-version</key><string>%s</string>
          <key>title</key><string>%s</string>
        </dict>
        <key>assets</key>
        <array>
          <dict>
            <key>kind</key><string>software-package</string>
            <key>url</key><string>%s</string>
          </dict>
          <dict>
            <key>kind</key><string>display-image</string>
            <key>needs-shine</key><false/>
            <key>url</key><string>%s</string>
          </dict>
          <dict>
            <key>kind</key><string>full-size-image</string>
            <key>needs-shine</key><false/>
            <key>url</key><string>%s</string>
          </dict>
        </array>
      </dict>
    </array>
  </dict>
</plist>
")

(defn- plist
  [id ver name package-url icon-url artwork-url]
  (format plist-template id ver name package-url icon-url artwork-url))

(defn- info-plist-entry
  [seq]
  (first (filter #(re-matches #"Payload/[^/]*\.app/Info\.plist"
                              (.getName ^ZipEntry %))
                 seq)))

(defn write-plist
  "Generate .plist file with AdHoc .ipa package for OTA."
  [^String source base-url]
  (let [dest (replace-first source #"\.[^.]+$" ".plist")
        name (.getName (io/file source))
        name' (replace-first name #"\.[^.]+$" "")]
    (with-open [z (ZipFile. source)]
      (->> (iterator-seq (.entries z))
           (#(with-open [f (.getInputStream z (info-plist-entry %))]
               (.getHashMap ^NSDictionary (PropertyListParser/parse f))))
           (#(plist
              (get % "CFBundleIdentifier")
              (get % "CFBundleShortVersionString") ;"CFBundleVersion"
              (get % "CFBundleDisplayName")
              (join [base-url name])
              (join [base-url name' "-icon.png"])
              (join [base-url name' "-artwork.png"])))
           (spit dest))
      (join ["itms-services://?action=download-manifest&url="
             base-url name' ".plist"]))))

#_(defn -main
  [source base-url]
  (println (write-plist source base-url)))
