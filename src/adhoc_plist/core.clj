(ns adhoc-plist.core
  (:require [clojure.java.io :as io]
            [clojure.string :refer [replace-first]]
            [net.cgrand.enlive-html :as en :refer [deftemplate]])
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
          <key>bundle-identifier</key><identifier/>
          <key>bundle-version</key><version/>
          <key>title</key><title/>
        </dict>
        <key>assets</key>
        <array>
          <dict>
            <key>kind</key><string>software-package</string>
            <key>url</key><package/>
          </dict>
          <dict>
            <key>kind</key><string>display-image</string>
            <key>needs-shine</key><needs-shine/>
            <key>url</key><icon/>
          </dict>
          <dict>
            <key>kind</key><string>full-size-image</string>
            <key>needs-shine</key><needs-shine/>
            <key>url</key><artwork/>
          </dict>
        </array>
      </dict>
    </array>
  </dict>
</plist>
")

(deftemplate ^:private plist {:parser en/xml-parser}
  (java.io.StringReader. plist-template)
  [id ver name needs-shine package-url icon-url artwork-url]
  [:identifier] (en/do->
                 (en/html-content "<string>" id "</string>")
                 en/unwrap)
  [:version] (en/do->
              (en/html-content "<string>" ver "</string>")
              en/unwrap)
  [:title] (en/do->
            (en/html-content "<string>" name "</string>")
            en/unwrap)
  [:package] (en/do->
              (en/html-content "<string>" package-url "</string>")
              en/unwrap)
  [:icon] (en/do->
           (en/html-content "<string>" icon-url "</string>")
           en/unwrap)
  [:artwork] (en/do->
              (en/html-content "<string>" artwork-url "</string>")
              en/unwrap)
  [:needs-shine] (en/do->
                  (en/html-content (if needs-shine "<true/>" "<false/>"))
                  en/unwrap))

(defn- info-plist-entry
  [seq]
  (first (filter #(re-matches #"Payload/[^/]*\.app/Info\.plist"
                              (.getName ^ZipEntry %))
                 seq)))

(defmulti ^:private nsobject->object class)

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
        (map (fn [^"java.util.LinkedHashMap$Entry" e]
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
              (not (get % "UIPrerenderedIcon"))
              (str base-url name)
              (str base-url name' "-icon.png")
              (str base-url name' "-artwork.png")))
           (apply str)
           (spit dest))
      (str "itms-services://?action=download-manifest&url="
           base-url name' ".plist"))))

#_(defn -main
  [source base-url]
  (println (write-plist source base-url)))
