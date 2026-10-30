(ns jtk-dvlp.lein-tpl-helpers.core
  (:require
   [clojure.set :refer [difference]]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.java.classpath :as cp]
   [clojure.pprint :as pprint]

   [leiningen.core.main :refer [abort warn info debug]]
   [leiningen.new.templates :as lein-tpl]

   [stencil.core :as stencil]
   [stencil.parser :as stencil-parser]

   ,,,)

  (:import
   [java.io File]
   [java.util.jar JarFile JarEntry]))


(def ^:dynamic *template-dir* nil)
(def ^:dynamic *project-dir* nil)

(defn- ppstr [x]
  (str "\n"
       (with-out-str
         (pprint/pprint x))))

(defn- relativize
  [dir file]
  (let [dirpath
        (.getAbsolutePath (io/file dir))

        filepath
        (.getAbsolutePath (io/file file))]

    (cond
      (not (str/starts-with? filepath dirpath))
      (->> (format "File \"%s\" is not located in dir \"%s\"" filepath dirpath)
           (IllegalArgumentException.)
           (throw))

      (= dirpath filepath)
      (io/file ".")

      :else
      (-> filepath
          (subs (inc (count dirpath)))
          (io/file)))))

(defn- template-relativize
  [file]
  (->> file
       (relativize *template-dir*)
       (relativize ".")))

(defn- matches-file?
  [file extension]
  (str/ends-with? (.getName file) extension))

(defn- matches-ext?
  [file extensions]
  (->> extensions
       (filter (partial matches-file? file))
       (first)
       (some?)))

(def ^:private template-extensions
  [".clj" ".cljs" ".cljc" ".edn" ".json"])

(def ^:private ignorable-extensions
  [".DS_Store" ".~undo-tree~"])

(defn- template-files
  [path]
  (->> path
       (io/file *template-dir*)
       (file-seq)
       (remove #(matches-ext? % ignorable-extensions))))

(defn- render-file
  [template-src data-map]
  (let [parser-options
        (cond-> stencil-parser/parser-defaults
          (matches-ext? template-src template-extensions)
          (assoc :tag-open "<%", :tag-close "%>"))]

    (-> (io/file *template-dir* template-src)
        (lein-tpl/slurp-resource)
        (stencil-parser/parse parser-options)
        (stencil/render data-map))))

(defn generate-file!
  [raw-file data]
  (let [rendered-filepath
        (-> raw-file (.getPath) (lein-tpl/render-text data) (io/file))

        absolute-filepath
        (io/file *project-dir* rendered-filepath)]

    (debug (format "Generating file \"%s\"" rendered-filepath))
    (.mkdirs (.getParentFile absolute-filepath))

    (-> raw-file
        (render-file data)
        (io/copy absolute-filepath))))

(defn generate-directory!
  [dir data]
  (let [dir (-> dir (.getPath) (lein-tpl/render-text data))]
    (debug (format "Generating dir \"%s\"" dir))
    (.mkdirs (io/file *project-dir* dir))))

(defn generate-structure!
  [path exclude? data]
  (doseq [template-file (template-files path)
          :let [relative-file (template-relativize template-file)]]
    (cond
      (exclude? relative-file)
      (debug "Skip file" relative-file)

      (.isFile template-file)
      (generate-file! relative-file data)

      (.isDirectory template-file)
      (generate-directory! relative-file data))))

(defn- expand-directory
  [file-or-dir]
  (let [real-path
        (io/file *template-dir* file-or-dir)]

    (if (.isDirectory real-path)
      (->> (file-seq real-path)
           (map (partial relativize *template-dir*)))
      [file-or-dir])))

(defn excludes
  [opts-files opts]
  (let [all-files
        (->> opts-files
             (vals)
             (apply concat)
             (into #{}))

        files-to-include
        (->> opts
             (select-keys opts-files)
             (vals)
             (apply concat)
             (into #{}))]

    (->> files-to-include
         (difference all-files)
         (map io/file)
         (mapcat expand-directory)
         (into #{}))))

(defn project-data
  [name opts]
  (let [group
        (lein-tpl/group-name name)

        project
        (lein-tpl/project-name name)

        options
        (reduce
         (fn [m option]
           (assoc m option true))
         {} opts)]

    (merge
     {:product (str (when group (str (str/replace group "." "-") "-")) project)
      :product-uri name
      :product-ns (lein-tpl/sanitize-ns name)
      :product-path (lein-tpl/name-to-path name)

      :group (or group "TODO")
      :group-path (lein-tpl/name-to-path (or group "TODO"))

      :project project
      :project-path (lein-tpl/name-to-path project)}

     options)))

(defn project-dir
  [{:keys [product] :as _data-map}]
  (io/file
   (or lein-tpl/*dir*
       (-> (System/getProperty "leiningen.original.pwd")
           (io/file product)
           (.getPath)))))

(def ^:private pwd
  (-> "."
      (io/file)
      (.getCanonicalFile)))

(defn- copy-fs-file
  [target-dir source-dir file]
  (let [in-file (io/file source-dir file)
        out-file (io/file target-dir file)]
    (.mkdirs (.getParentFile out-file))
    (with-open [in (io/input-stream in-file)
                out (io/output-stream out-file)]
      (io/copy in out)))
  nil)

(defn- copy-jar-entry
  [target-dir jar entry]
  (let [out-file (io/file target-dir (.getName entry))]
    (.mkdirs (.getParentFile out-file))
    (with-open [in (.getInputStream jar entry)
                out (io/output-stream out-file)]
      (io/copy in out)))
  nil)

(defn- extract-resources
  [pred target]
  ;; NOTE: für die Entwicklung vom FS
  (let [resource-dir (io/file "resources")
        source-dir (io/file pwd "resources")]
    (->> resource-dir
         (file-seq)
         (map #(.getCanonicalFile %))
         (remove #(.isDirectory %))
         (map (partial relativize source-dir))
         (filter (comp pred #(.getPath %)))
         (run! (partial copy-fs-file target source-dir))))

  ;; NOTE: für die Nutzung ausm JAR
  ;; TODO: kenne wir nicht eigentlich genau das JAR?
  (run!
   (fn [^JarFile jar]
     (->> jar
          (.entries)
          (enumeration-seq)
          (remove #(.isDirectory %))
          (filter (comp pred #(.getName %)))
          (run! (partial copy-jar-entry target jar))))
   (cp/classpath-jarfiles))
  nil)

(defn- temp-dir
  [prefix suffix]
  (let [dir (File/createTempFile prefix suffix)]
    (.delete dir)
    dir))

(defn- template-dir
  [path]
  (let [tmp-dir (temp-dir "tpl-resources_" "")]
    (extract-resources #(str/starts-with? % path) tmp-dir)
    (io/file tmp-dir path)))

(defn generate-project!
  [feature-files name features]
  (let [template-data
        (project-data name features)]

    (binding [*template-dir*
              (template-dir "leiningen/new/project")

              *project-dir*
              (project-dir template-data)]

      (let [excludes
            (excludes feature-files features)]

        (info "Generating fresh project")
        (debug "Generating fresh project:"
               (ppstr {:name name
                       :feature-files feature-files
                       :features features
                       :excludes excludes
                       :template-data template-data
                       :*template-dir* *template-dir*
                       :*project-dir* *project-dir*}))

        (generate-structure! "." excludes template-data)))))

(defn- stream-to-fn
  [fun stream]
  (->> stream
       (java.io.InputStreamReader.)
       (java.io.BufferedReader.)
       (line-seq)
       (run! fun)))

(defn- exec!
  [& command]
  (let [runtime
        (Runtime/getRuntime)

        process
        (.exec runtime (into-array command) nil *project-dir*)

        std-out
        (.getInputStream process)

        std-err
        (.getErrorStream process)]

    (future (stream-to-fn (partial info "..") std-out))
    (future (stream-to-fn (partial warn "..") std-err))

    (.waitFor process)))

(defn check-dependencies!
  []
  (info "Checking template dependencies.")
  (when-not (= 0 (exec! "lein" "ancient" "check" ":all"))
    (abort "Checking dependencies failed")))

(defn upgrade-dependencies!
  []
  (info "Upgrading template dependencies and running tests.")
  (when-not (= 0 (exec! "lein" "ancient" "upgrade" ":all"))
    (abort "Upgrading dependencies failed")))
