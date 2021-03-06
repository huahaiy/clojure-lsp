(ns clojure-lsp.config
  (:require
    [clojure-lsp.shared :as shared]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [taoensso.timbre :as log]))

(def change-debounce-ms 300)

(def clojure-lsp-version (string/trim (slurp (io/resource "CLOJURE_LSP_VERSION"))))

(def clj-kondo-version (string/trim (slurp (io/resource "CLJ_KONDO_VERSION"))))

(defn kondo-for-paths [paths]
  {:cache true
   :parallel true
   :lint [(string/join (System/getProperty "path.separator") paths)]
   :config {:output {:analysis {:arglists true
                                :locals false
                                :keywords true}
                     :canonical-paths true}}})

(defn kondo-for-single-file [uri]
  {:cache true
   :lint ["-"]
   :lang (shared/uri->file-type uri)
   :filename (shared/uri->filename uri)
   :config {:output {:analysis {:arglists true
                                :locals true
                                :keywords true}
                     :canonical-paths true}}})

(defn ^:private read-edn-file [^java.io.File file]
  (try
    (->> (slurp file)
         (edn/read-string {:readers {'re re-pattern}})
         shared/keywordize-first-depth)
    (catch Exception e
      (log/error "WARNING: error while reading" (.getCanonicalPath file) (format "(%s)" (.getMessage e))))))

(defn get-property [p]
  (System/getProperty p))

(defn get-env [p]
  (System/getenv p))

(defn ^:private file-exists? [^java.io.File f]
  (.exists f))

(defn ^:private get-home-config-file []
  (if-let [xdg-config-home (get-env "XDG_CONFIG_HOME")]
    (io/file xdg-config-home ".lsp" "config.edn")
    (io/file (get-property "user.home") ".lsp" "config.edn")))

(defn ^:private resolve-home-config [^java.io.File home-dir-file]
  (when (file-exists? home-dir-file)
    (read-edn-file home-dir-file)))

(defn ^:private resolve-project-configs [project-root ^java.io.File home-dir-file]
  (loop [dir (io/file (shared/uri->filename project-root))
         configs []]
    (let [file (io/file dir ".lsp" "config.edn")
          parent (.getParentFile dir)]
      (if parent
        (recur parent (cond-> configs
                        (and (file-exists? file)
                             (not (= (.getAbsolutePath home-dir-file) (.getAbsolutePath file))))
                        (conj (read-edn-file file))))
        configs))))

(defn resolve-config [project-root]
  (let [home-dir-file (get-home-config-file)]
    (reduce shared/deep-merge
            (merge {}
                   (resolve-home-config home-dir-file))
            (resolve-project-configs project-root home-dir-file))))
