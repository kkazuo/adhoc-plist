(ns adhoc-plist.core
  (:require [clojure.java.io :as io]
            [clojure.string :refer [replace-first]])
  (:import (com.dd.plist NSArray NSDictionary NSNumber NSString
                         PropertyListParser)
           (java.util.zip ZipEntry ZipFile)))

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

(defmulti nsobject->object class)

(defmethod nsobject->object NSNumber [^NSNumber obj]
  (cond (.isBoolean obj) (.boolValue obj)
        (.isInteger obj) (.longValue obj)
        :else (.doubleValue obj)))

(defmethod nsobject->object NSString [^NSString obj]
  (.getContent obj))

(defmethod nsobject->object NSArray [^NSArray obj]
  (map nsobject->object (.getArray obj)))

(defmethod nsobject->object NSDictionary [^NSDictionary obj]
  (into {}
        (map (fn [e]
               [(.getKey e) (nsobject->object (.getValue e))])
             (.. obj getHashMap entrySet))))

(defn read-info-plist
  [^String source]
  (nsobject->object
   (with-open [z (ZipFile. source)]
     (->> (iterator-seq (.entries z))
          (#(with-open [f (.getInputStream z (info-plist-entry %))]
              (PropertyListParser/parse f)))))))

(defn write-plist
  "Generate .plist file with AdHoc .ipa package for OTA."
  [^String source base-url]
  (let [dest (replace-first source #"\.[^.]+$" ".plist")
        name (.getName (io/file source))
        name' (replace-first name #"\.[^.]+$" "")]
    (with-open [z (ZipFile. source)]
      (->> (read-info-plist source)
           (#(plist
              (get % "CFBundleIdentifier")
              (get % "CFBundleShortVersionString") ;"CFBundleVersion"
              (get % "CFBundleDisplayName")
              (str base-url name)
              (str base-url name' "-icon.png")
              (str base-url name' "-artwork.png")))
           (spit dest))
      (str "itms-services://?action=download-manifest&url="
           base-url name' ".plist"))))

#_(defn -main
  [source base-url]
  (println (write-plist source base-url)))
